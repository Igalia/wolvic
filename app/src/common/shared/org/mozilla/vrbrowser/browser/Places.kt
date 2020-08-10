/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.lib.dataprotect.generateEncryptionKey
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.vrbrowser.browser.engine.SessionStore
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate
import org.mozilla.vrbrowser.utils.SystemUtils

/**
 * Entry point for interacting with places-backed storage layers.
 */
class Places(var context: Context) {

    private val LOGTAG = SystemUtils.createLogtag(Places::class.java)

    companion object {
        private const val LOG_TAG = "LoginStorage"
        private const val KEY_STRENGTH = 256
        private const val KEY_STORAGE_NAME = "fxr_secure_prefs"
        private const val PASSWORDS_KEY = "passwords"
    }

    /**
     * Shared Preferences that encrypt/decrypt using Android KeyStore and lib-dataprotect for 23+
     * only on Nightly/Debug for now, otherwise simply stored.
     * See https://github.com/mozilla-mobile/fenix/issues/8324
     */
    private fun getSecureAbove22Preferences() =
            SecureAbove22Preferences(
                    context = context,
                    name = KEY_STORAGE_NAME
            )

    private val passwordsEncryptionKey by lazy {
        getSecureAbove22Preferences().getString(PASSWORDS_KEY)
                ?: generateEncryptionKey(KEY_STRENGTH).also {
                    if (SettingsStore.getInstance(context).isPasswordsEncryptionKeyGenerated) {
                        // We already had previously generated an encryption key, but we have lost it
                        Log.d(LOGTAG,"Passwords encryption key for passwords storage was lost and we generated a new one")
                    }
                    SettingsStore.getInstance(context).recordPasswordsEncryptionKeyGenerated()
                    getSecureAbove22Preferences().putString(PASSWORDS_KEY, it)
                }
    }

    var bookmarks = PlacesBookmarksStorage(context)
    var history = PlacesHistoryStorage(context)
    var logins = lazy { SyncableLoginsStorage(context, passwordsEncryptionKey) }

    fun clear() {
        val files = context.filesDir.listFiles { _, name ->
            name.matches("places\\.sqlite.*".toRegex())
        }
        if (files != null) {
            for (file in files) {
                if (!file.delete()) {
                    Logger(LOGTAG).debug("Can't remove " + file.absolutePath)
                }
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

        CoroutineScope(Dispatchers.IO).launch {
            // The login storage has a wipe method the should bring us back to the state before the first sync
            // (although it actually just deletes everything) so there is no need to delete the whole database.
            logins.value.wipeLocal()
        }
    }
}
