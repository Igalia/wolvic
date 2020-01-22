/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.vrbrowser.browser.engine.SessionStore
import org.mozilla.vrbrowser.utils.SystemUtils

/**
 * Entry point for interacting with places-backed storage layers.
 */
class Places(var context: Context) {

    private val LOGTAG = SystemUtils.createLogtag(Places::class.java)

    var bookmarks = PlacesBookmarksStorage(context)
    var history = PlacesHistoryStorage(context)

    fun clear() {
        val files = context.filesDir.listFiles { dir, name ->
            name.matches("places\\.sqlite.*".toRegex())
        }
        for (file in files) {
            if (!file.delete()) {
                Logger(LOGTAG).debug("Can't remove " + file.absolutePath)
            }
        }

        bookmarks.cleanup()
        // We create a new storage, otherwise we would need to restart the app so it's created in the Application onCreate
        bookmarks = PlacesBookmarksStorage(context)
        // Update the storage in the proxy class
        SessionStore.get().bookmarkStore.updateStorage()

        // We are supposed to call this but it fails internally as apparently the PlacesStorage is common
        // and it's already being cleaned after bookmarks.cleanup()
        // history.cleanup()
        // We create a new storage, otherwise we would need to restart the app so it's created in the Application onCreate
        history = PlacesHistoryStorage(context)
        // Update the storage in the proxy class
        SessionStore.get().historyStore.updateStorage()
    }
}
