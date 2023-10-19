/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ProcessLifecycleOwner
import com.igalia.wolvic.BuildConfig
import com.igalia.wolvic.R
import com.igalia.wolvic.browser.api.WAllowOrDeny
import com.igalia.wolvic.browser.api.WResult
import com.igalia.wolvic.browser.api.WSession
import com.igalia.wolvic.browser.engine.EngineProvider
import com.igalia.wolvic.telemetry.TelemetryService
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate
import com.igalia.wolvic.utils.ConnectivityReceiver
import com.igalia.wolvic.utils.SystemUtils
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


class Services(val context: Context, places: Places): WSession.NavigationDelegate {

    private val LOGTAG = SystemUtils.createLogtag(Services::class.java)

    companion object {
        const val CLIENT_ID = "a2270f727f45f648"
        const val REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob:oauth-redirect-webchannel"
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

        GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to lazy {places.bookmarks})
        GlobalSyncableStoreProvider.configureStore(SyncEngine.History to lazy {places.history})
    }

    // Process received device events, only handling received tabs for now.
    // They'll come from other FxA devices (e.g. Firefox Desktop).
    private val deviceEventObserver = object : AccountEventsObserver {
        private val logTag = "DeviceEventsObserver"

        override fun onEvents(events: List<AccountEvent>) {
            CoroutineScope(Dispatchers.Main).launch {
                Logger(logTag).info("Received ${events.size} device event(s)")
                events
                        .filterIsInstance<AccountEvent.DeviceCommandIncoming>()
                        .map { it.command }
                        .filterIsInstance<DeviceCommandIncoming.TabReceived>()
                        .forEach { command ->
                            command.from?.deviceType?.let { TelemetryService.FxA.receivedTab(it) }
                            tabReceivedDelegate?.onTabsReceived(command.entries)
                        }
            }
        }
    }
    val serverConfig = ServerConfig(if (BuildConfig.FXA_USE_CHINA_SERVER) Server.CHINA else Server.RELEASE, CLIENT_ID, REDIRECT_URL)

    val accountManager = FxaAccountManager(
        context = context,
        serverConfig = serverConfig,
        deviceConfig = DeviceConfig(
            // This is a default name, and can be changed once user is logged in.
            // E.g. accountManager.authenticatedAccount()?.deviceConstellation()?.setDeviceName("new name", context)
            name = com.igalia.wolvic.utils.DeviceType.getDeviceName(context),
            type = DeviceType.VR,
            capabilities = setOf(DeviceCapability.SEND_TAB)
        ),
        syncConfig = SyncConfig(setOf(SyncEngine.History, SyncEngine.Bookmarks, SyncEngine.Passwords), PeriodicSyncConfig(periodMinutes = 1440))

    ).also {
        it.registerForAccountEvents(deviceEventObserver, ProcessLifecycleOwner.get(), true)
    }

    init {
        if (ConnectivityReceiver.isNetworkAvailable(context)) {
            init()
        }

        (context as WidgetManagerDelegate).servicesProvider.connectivityReceiver.addListener {
            if (it) {
                init()
            }
        }
    }

    private fun init() {
        CoroutineScope(Dispatchers.Main).launch {
            accountManager.start()
        }
    }

    override fun onLoadRequest(aSession: WSession, loadRequest: WSession.NavigationDelegate.LoadRequest): WResult<WAllowOrDeny>? {
        if (loadRequest.uri.startsWith(REDIRECT_URL)) {
            val parsedUri = Uri.parse(loadRequest.uri)

            parsedUri.getQueryParameter("code")?.let { code ->
                val state = parsedUri.getQueryParameter("state") as String
                val action = parsedUri.getQueryParameter("action") as String

                val wResult = WResult.create<WAllowOrDeny>();

                // Notify the state machine about our success.
                CoroutineScope(Dispatchers.Main).launch {
                    val result = accountManager.finishAuthentication(FxaAuthData(action.toAuthType(), code = code, state = state))
                    if (!result) {
                        android.util.Log.e(LOGTAG, "Authentication finish error.")
                        wResult.complete(WAllowOrDeny.DENY)

                    } else {
                        android.util.Log.e(LOGTAG, "Authentication successfully completed.")
                        wResult.complete(WAllowOrDeny.ALLOW)
                    }
                }

                return wResult
            }
            return WResult.deny()
        }

        return WResult.allow()
    }

}