/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
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
import org.mozilla.vrbrowser.VRBrowserActivity
import org.mozilla.vrbrowser.browser.engine.EngineProvider
import org.mozilla.vrbrowser.telemetry.GleanMetricsService
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate
import org.mozilla.vrbrowser.utils.ConnectivityReceiver
import org.mozilla.vrbrowser.utils.SystemUtils


class Services(val context: Context, places: Places): GeckoSession.NavigationDelegate {

    private val LOGTAG = SystemUtils.createLogtag(Services::class.java)

    companion object {
        const val CLIENT_ID = "7ad9917f6c55fb77"
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
                            command.from?.deviceType?.let { GleanMetricsService.FxA.receivedTab(it) }
                            tabReceivedDelegate?.onTabsReceived(command.entries)
                        }
            }
        }
    }
    val serverConfig = ServerConfig(Server.RELEASE, CLIENT_ID, REDIRECT_URL)

    val accountManager = FxaAccountManager(
        context = context,
        serverConfig = serverConfig,
        deviceConfig = DeviceConfig(
            // This is a default name, and can be changed once user is logged in.
            // E.g. accountManager.authenticatedAccount()?.deviceConstellation()?.setDeviceNameAsync("new name")
            name = "${context.getString(R.string.app_name)} on ${Build.MANUFACTURER} ${Build.MODEL}",
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

    override fun onLoadRequest(geckoSession: GeckoSession, loadRequest: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        if (loadRequest.uri.startsWith(REDIRECT_URL)) {
            val parsedUri = Uri.parse(loadRequest.uri)

            parsedUri.getQueryParameter("code")?.let { code ->
                val state = parsedUri.getQueryParameter("state") as String
                val action = parsedUri.getQueryParameter("action") as String

                val geckoResult = GeckoResult<AllowOrDeny>()

                // Notify the state machine about our success.
                CoroutineScope(Dispatchers.Main).launch {
                    val result = accountManager.finishAuthentication(FxaAuthData(action.toAuthType(), code = code, state = state))
                    if (!result) {
                        android.util.Log.e(LOGTAG, "Authentication finish error.")
                        geckoResult.complete(AllowOrDeny.DENY)

                    } else {
                        android.util.Log.e(LOGTAG, "Authentication successfully completed.")
                        geckoResult.complete(AllowOrDeny.ALLOW)
                    }
                }

                return geckoResult
            }
            return GeckoResult.deny()
        }

        return GeckoResult.allow()
    }

}