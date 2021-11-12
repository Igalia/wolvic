/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.engine

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.vrbrowser.BuildConfig
import org.mozilla.vrbrowser.browser.SettingsStore
import org.mozilla.vrbrowser.browser.content.TrackingProtectionPolicy
import org.mozilla.vrbrowser.browser.content.TrackingProtectionStore
import org.mozilla.vrbrowser.crashreporting.CrashReporterService

object EngineProvider {

    private var runtime: GeckoRuntime? = null
    private var executor: GeckoWebExecutor? = null
    private var client: GeckoViewFetchClient? = null

    @Synchronized
    fun getOrCreateRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val builder = GeckoRuntimeSettings.Builder()
            val settingsStore = SettingsStore.getInstance(context)

            val policy : TrackingProtectionPolicy = TrackingProtectionStore.getTrackingProtectionPolicy(context);
            builder.crashHandler(CrashReporterService::class.java)
            builder.contentBlocking(ContentBlocking.Settings.Builder()
                    .antiTracking(policy.antiTrackingPolicy)
                    .enhancedTrackingProtectionLevel(settingsStore.trackingProtectionLevel)
                    .build())
            builder.displayDensityOverride(settingsStore.displayDensity)
            builder.remoteDebuggingEnabled(settingsStore.isRemoteDebuggingEnabled)
            builder.displayDpiOverride(settingsStore.displayDpi)
            builder.screenSizeOverride(settingsStore.maxWindowWidth, settingsStore.maxWindowHeight)
            builder.inputAutoZoomEnabled(false)
            builder.doubleTapZoomingEnabled(false)
            builder.debugLogging(settingsStore.isDebugLoggingEnabled)
            builder.consoleOutput(settingsStore.isDebugLoggingEnabled)
            builder.loginAutofillEnabled(settingsStore.isAutoFillEnabled)
            builder.configFilePath(SessionUtils.prepareConfigurationPath(context))

            if (settingsStore.transparentBorderWidth > 0) {
                builder.useMaxScreenDepth(true)
            }

            if (BuildConfig.DEBUG) {
                builder.arguments(arrayOf("-purgecaches"))
                builder.aboutConfigEnabled(true)
            }

            val msaa = SettingsStore.getInstance(context).msaaLevel
            if (msaa > 0) {
                builder.glMsaaLevel(if (msaa == 2) 4 else 2)
            } else {
                builder.glMsaaLevel(0)
            }

            runtime = GeckoRuntime.create(context, builder.build())
        }

        return runtime!!
    }

    @Synchronized
    fun isRuntimeCreated(): Boolean {
        return runtime != null
    }

    private fun createGeckoWebExecutor(context: Context): GeckoWebExecutor {
        return GeckoWebExecutor(getOrCreateRuntime(context))
    }

     fun getDefaultGeckoWebExecutor(context: Context): GeckoWebExecutor {
        if (executor == null) {
            executor = createGeckoWebExecutor(context)
            client?.let { it.executor = executor }

        }

        return executor!!
    }

    fun createClient(context: Context): GeckoViewFetchClient {
        val client = GeckoViewFetchClient(context)
        client.executor = executor
        return client
    }

    fun getDefaultClient(context: Context): GeckoViewFetchClient {
        if (client == null) {
            client = createClient(context)
        }

        return client!!
    }

}
