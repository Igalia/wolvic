/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.vrbrowser.R
import org.mozilla.vrbrowser.VRBrowserApplication
import org.mozilla.vrbrowser.utils.SystemUtils
import java.util.concurrent.CompletableFuture

const val DESKTOP_ROOT = "fake_desktop_root"

class BookmarksStore constructor(val context: Context) {

    private val LOGTAG = SystemUtils.createLogtag(BookmarksStore::class.java)

    companion object {
        private val coreRoots = listOf(
                DESKTOP_ROOT,
                BookmarkRoot.Mobile.id,
                BookmarkRoot.Unfiled.id,
                BookmarkRoot.Toolbar.id,
                BookmarkRoot.Menu.id
        )

        @JvmStatic
        fun allowDeletion(guid: String): Boolean {
            return coreRoots.contains(guid)
        }

        /**
         * User-friendly titles for various internal bookmark folders.
         */
        fun rootTitles(context: Context): Map<String, String> {
            return mapOf(
                // "Virtual" desktop folder.
                DESKTOP_ROOT to context.getString(R.string.bookmarks_desktop_folder_title),
                // Our main root, in actuality the "mobile" root:
                BookmarkRoot.Mobile.id to context.getString(R.string.bookmarks_mobile_folder_title),
                // What we consider the "desktop" roots:
                BookmarkRoot.Menu.id to context.getString(R.string.bookmarks_desktop_menu_title),
                BookmarkRoot.Toolbar.id to context.getString(R.string.bookmarks_desktop_toolbar_title),
                BookmarkRoot.Unfiled.id to context.getString(R.string.bookmarks_desktop_unfiled_title)
            )
        }
    }

    private val listeners = ArrayList<BookmarkListener>()
    private var storage = (context.applicationContext as VRBrowserApplication).places.bookmarks
    private val titles = rootTitles(context)
    private val accountManager = (context.applicationContext as VRBrowserApplication).services.accountManager

    // Bookmarks might have changed during sync, so notify our listeners.
    private val syncStatusObserver = object : SyncStatusObserver {
        override fun onStarted() {}

        override fun onIdle() {
            Logger(LOGTAG).debug("Detected that sync is finished, notifying listeners")
            notifyListeners()
        }

        override fun onError(error: Exception?) {}
    }

    init {
        accountManager.registerForSyncEvents(
            syncStatusObserver, ProcessLifecycleOwner.get(), false
        )
    }

    interface BookmarkListener {
        fun onBookmarksUpdated()
        fun onBookmarkAdded()
    }

    fun addListener(aListener: BookmarkListener) {
        if (!listeners.contains(aListener)) {
            listeners.add(aListener)
        }
    }

    fun removeListener(aListener: BookmarkListener) {
        listeners.remove(aListener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    internal fun updateStorage() {
        storage = (context.applicationContext as VRBrowserApplication).places.bookmarks
        notifyListeners()
    }

    fun getBookmarks(guid: String): CompletableFuture<List<BookmarkNode>?> = GlobalScope.future {
        when (guid) {
            BookmarkRoot.Mobile.id -> {
                // Construct a "virtual" desktop folder as the first bookmark item in the list.
                val withDesktopFolder = mutableListOf(
                    BookmarkNode(
                        BookmarkNodeType.FOLDER,
                        DESKTOP_ROOT,
                        BookmarkRoot.Mobile.id,
                        title = titles[DESKTOP_ROOT],
                        children = emptyList(),
                        position = null,
                        url = null
                    )
                )
                // Append all of the bookmarks in the mobile root.
                storage.getTree(BookmarkRoot.Mobile.id)?.children?.let { withDesktopFolder.addAll(it) }
                withDesktopFolder
            }
            DESKTOP_ROOT -> {
                val root = storage.getTree(BookmarkRoot.Root.id)
                root?.children
                    ?.filter { it.guid != BookmarkRoot.Mobile.id }
                    ?.map {
                        it.copy(title = titles[it.guid])
                    }
                }
            else -> {
                storage.getTree(guid)?.children?.toList()
            }
        }
    }

    fun addBookmark(aURL: String, aTitle: String) = GlobalScope.future {
        storage.addItem(BookmarkRoot.Mobile.id, aURL, aTitle, null)
        notifyAddedListeners()
    }

    fun deleteBookmarkByURL(aURL: String) = GlobalScope.future {
        val bookmark = getBookmarkByUrl(aURL)
        if (bookmark != null) {
            storage.deleteNode(bookmark.guid)
        }
        notifyListeners()
    }

    fun deleteBookmarkById(aId: String) = GlobalScope.future {
        storage.deleteNode(aId)
        notifyListeners()
    }

    fun isBookmarked(aURL: String): CompletableFuture<Boolean> = GlobalScope.future {
        getBookmarkByUrl(aURL) != null
    }

    fun getTree(guid: String, recursive: Boolean): CompletableFuture<List<BookmarkNode>?> = GlobalScope.future {
        storage.getTree(guid, recursive)?.children
                ?.map { it.copy(title = titles[it.guid]) }
    }

    fun searchBookmarks(query: String, limit: Int): CompletableFuture<List<BookmarkNode>> = GlobalScope.future {
        storage.searchBookmarks(query, limit)
    }

    private suspend fun getBookmarkByUrl(aURL: String): BookmarkNode? {
        val bookmarks: List<BookmarkNode>? = storage.getBookmarksWithUrl(aURL)
        if (bookmarks == null || bookmarks.isEmpty()) {
            return null
        }

        for (bookmark in bookmarks) {
            if (bookmark.url.equals(aURL)) {
                return bookmark
            }
        }

        return null
    }

    private fun notifyListeners() {
        if (listeners.size > 0) {
            val listenersCopy = ArrayList(listeners)
            Handler(Looper.getMainLooper()).post {
                for (listener in listenersCopy) {
                    listener.onBookmarksUpdated()
                }
            }
        }
    }

    private fun notifyAddedListeners() {
        if (listeners.size > 0) {
            val listenersCopy = ArrayList(listeners)
            Handler(Looper.getMainLooper()).post {
                for (listener in listenersCopy) {
                    listener.onBookmarkAdded()
                }
            }
        }
    }
}
