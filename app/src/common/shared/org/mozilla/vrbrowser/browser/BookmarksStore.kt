/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarksStorage
import java.util.concurrent.CompletableFuture

class BookmarksStore constructor(aContext: Context) {
    private var mContext: Context
    private var mStorage: BookmarksStorage
    private var mListeners = ArrayList<BookmarkListener>()

    interface BookmarkListener {
        fun onBookmarksUpdated()
    }

    init {
        mContext = aContext
        mStorage = PlacesBookmarksStorage(aContext)
    }

    fun addListener(aListener: BookmarkListener) {
        if (!mListeners.contains(aListener)) {
            mListeners.add(aListener)
        }
    }

    fun removeListener(aListener: BookmarkListener) {
        mListeners.remove(aListener)
    }

    fun removeAllListeners() {
        mListeners.clear()
    }

    fun getBookmarks(): CompletableFuture<List<BookmarkNode>?> = GlobalScope.future {
        mStorage.getTree(BookmarkRoot.Mobile.id, true)?.children?.toMutableList()
    }

    fun addBookmark(aURL: String, aTitle: String) = GlobalScope.future {
        mStorage.addItem(BookmarkRoot.Mobile.id, aURL, aTitle, null)
        notifyListeners()
    }

    fun deleteBookmarkByURL(aURL: String) = GlobalScope.future {
        val bookmark = getBookmarkByUrl(aURL)
        if (bookmark != null) {
            mStorage.deleteNode(bookmark.guid)
        }
        notifyListeners()
    }

    fun deleteBookmarkById(aId: String) = GlobalScope.future {
        mStorage.deleteNode(aId)
        notifyListeners()
    }

    fun isBookmarked(aURL: String): CompletableFuture<Boolean> = GlobalScope.future {
        getBookmarkByUrl(aURL) != null
    }


    private suspend fun getBookmarkByUrl(aURL: String): BookmarkNode? {
        val bookmarks: List<BookmarkNode>? = mStorage.getBookmarksWithUrl(aURL)
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
        if (mListeners.size > 0) {
            val listenersCopy = ArrayList(mListeners)
            Handler(Looper.getMainLooper()).post {
                for (listener in listenersCopy) {
                    listener.onBookmarksUpdated()
                }
            }
        }
    }
}

