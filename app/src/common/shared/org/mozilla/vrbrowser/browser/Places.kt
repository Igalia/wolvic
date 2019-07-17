/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage

/**
 * Entry point for interacting with places-backed storage layers.
 */
class Places(context: Context) {
    val bookmarks by lazy { PlacesBookmarksStorage(context) }
}
