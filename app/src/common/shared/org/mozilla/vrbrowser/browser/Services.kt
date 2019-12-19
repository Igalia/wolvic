/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.appservices.Megazord
import mozilla.appservices.rustlog.LogAdapterCannotEnable
import mozilla.components.concept.sync.*
import mozilla.components.service.fxa.*
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.rustlog.RustLog
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.R
import org.mozilla.vrbrowser.browser.engine.EngineProvider
import org.mozilla.vrbrowser.utils.SystemUtils
import org.mozilla.vrbrowser.telemetry.GleanMetricsService


class Services(val context: Context, places: Places): GeckoSession.NavigationDelegate {

    private val LOGTAG = SystemUtils.createLogtag(Services::class.java)

    companion object {
        const val CLIENT_ID = "7ad9917f6c55fb77"
        const val REDIRECT_URL = "https://accounts.firefox.com/oauth/success/$CLIENT_ID"
    }
    interface TabReceivedDelegate {
        fun onTabsReceived(uri: List<TabData>)
    }

    var tabReceivedDelegate: TabReceivedDelegate? = null

    // This makes bookmarks storage accessible to background sync workers.
    init {
        Megazord.init()
        try {
            RustLog.enable()
        } catch (e: LogAdapterCannotEnable) {
            android.util.Log.w(LOGTAG, "RustLog has been enabled.")
        }
        RustHttpConfig.setClient(lazy { EngineProvider.createClient(context) })

        // Make sure we get logs out of our android-components.
        Log.addSink(AndroidLogSink())

        GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to places.bookmarks)
        GlobalSyncableStoreProvider.configureStore(SyncEngine.History to places.history)

        // TODO this really shouldn't be necessary, since WorkManager auto-initializes itself, unless
        // auto-initialization is disabled in the manifest file. We don't disable the initialization,
        // but i'm seeing crashes locally because WorkManager isn't initialized correctly...
        // Maybe this is a race of sorts? We're trying to access it before it had a chance to auto-initialize?
        // It's not well-documented _when_ that auto-initialization is supposed to happen.

        // For now, let's just manually initialize it here, and swallow failures (it's already initialized).
        try {
            WorkManager.initialize(
                    context,
                    Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
            )
        } catch (e: IllegalStateException) {}
    }

    // Process received device events, only handling received tabs for now.
    // They'll come from other FxA devices (e.g. Firefox Desktop).
    private val deviceEventObserver = object : DeviceEventsObserver {
        private val logTag = "DeviceEventsObserver"

        override fun onEvents(events: List<DeviceEvent>) {
            CoroutineScope(Dispatchers.Main).launch {
                Logger(logTag).info("Received ${events.size} device event(s)")
                val filteredEvents = events.filterIsInstance(DeviceEvent.TabReceived::class.java)
                if (filteredEvents.isNotEmpty()) {
                    filteredEvents.map { event -> event.from?.deviceType?.let { GleanMetricsService.FxA.receivedTab(it) } }
                    val tabs = filteredEvents.map {
                        event -> event.entries
                    }.flatten()
                    tabReceivedDelegate?.onTabsReceived(tabs)
                }
            }
        }
    }
    val accountManager = FxaAccountManager(
        context = context,
        serverConfig = ServerConfig.release(CLIENT_ID, REDIRECT_URL),
        deviceConfig = DeviceConfig(
            // This is a default name, and can be changed once user is logged in.
            // E.g. accountManager.authenticatedAccount()?.deviceConstellation()?.setDeviceNameAsync("new name")
            name = "${context.getString(R.string.app_name)} on ${Build.MANUFACTURER} ${Build.MODEL}",
            type = DeviceType.VR,
            capabilities = setOf(DeviceCapability.SEND_TAB)
        ),
        syncConfig = SyncConfig(setOf(SyncEngine.History, SyncEngine.Bookmarks), syncPeriodInMinutes = 1440L)

    ).also {
        it.registerForDeviceEvents(deviceEventObserver, ProcessLifecycleOwner.get(), true)
    }

    init {
        CoroutineScope(Dispatchers.Main).launch {
            accountManager.initAsync().await()
        }
    }

    override fun onLoadRequest(geckoSession: GeckoSession, loadRequest: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        if (loadRequest.uri.startsWith(REDIRECT_URL)) {
            val parsedUri = Uri.parse(loadRequest.uri)

            parsedUri.getQueryParameter("code")?.let { code ->
                val state = parsedUri.getQueryParameter("state") as String
                val action = parsedUri.getQueryParameter("action") as String

                // Notify the state machine about our success.
                accountManager.finishAuthenticationAsync(FxaAuthData(action.toAuthType(), code = code, state = state))

                return GeckoResult.ALLOW
            }
            return GeckoResult.DENY
        }

        return GeckoResult.ALLOW
    }
}