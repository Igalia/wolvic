/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.components.concept.storage.VisitType
import org.mozilla.vrbrowser.VRBrowserApplication
import java.util.concurrent.CompletableFuture

class HistoryStore constructor(val context: Context) {
    private var listeners = ArrayList<HistoryListener>()
    private val storage = (context.applicationContext as VRBrowserApplication).places.history

    interface HistoryListener {
        fun onHistoryUpdated()
    }

    fun addListener(aListener: HistoryListener) {
        if (!listeners.contains(aListener)) {
            listeners.add(aListener)
        }
    }

    fun removeListener(aListener: HistoryListener) {
        listeners.remove(aListener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    fun getHistory(): CompletableFuture<List<String>?> = GlobalScope.future {
        storage.getVisited()
    }

    fun addHistory(aURL: String, visitType: VisitType) = GlobalScope.future {
        storage.recordVisit(aURL, visitType)
        notifyListeners()
    }

    fun deleteHistory(aUrl: String, timestamp: Long) = GlobalScope.future {
        storage.deleteVisit(aUrl, timestamp)
        notifyListeners()
    }

    fun isInHistory(aURL: String): CompletableFuture<Boolean> = GlobalScope.future {
        storage.getVisited(listOf(aURL)).size != 0
    }

    private fun notifyListeners() {
        if (listeners.size > 0) {
            val listenersCopy = ArrayList(listeners)
            Handler(Looper.getMainLooper()).post {
                for (listener in listenersCopy) {
                    listener.onHistoryUpdated()
                }
            }
        }
    }
}

