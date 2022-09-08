/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.engine

import android.content.Context
import com.igalia.wolvic.BuildConfig
import com.igalia.wolvic.browser.SettingsStore
import com.igalia.wolvic.browser.api.WContentBlocking
import com.igalia.wolvic.browser.api.WFactory
import com.igalia.wolvic.browser.api.WRuntime
import com.igalia.wolvic.browser.api.WRuntimeSettings
import com.igalia.wolvic.browser.content.TrackingProtectionPolicy
import com.igalia.wolvic.browser.content.TrackingProtectionStore
import com.igalia.wolvic.browser.api.impl.CrashReporterServiceImpl
import mozilla.components.concept.fetch.Client

object EngineProvider {

    private var runtime: WRuntime? = null
    private var client: Client? = null

    @Synchronized
    fun getOrCreateRuntime(context: Context): WRuntime {
        if (runtime == null) {
            val builder = WRuntimeSettings.Builder()
            val settingsStore = SettingsStore.getInstance(context)

            val policy : TrackingProtectionPolicy = TrackingProtectionStore.getTrackingProtectionPolicy(context);
            builder.crashHandler(CrashReporterServiceImpl::class.java)
            builder.contentBlocking(
                WContentBlocking.Settings.Builder()
                    .antiTracking(policy.antiTrackingPolicy)
                    .strictSocialTrackingProtection(policy.shouldBlockContent())
                    .cookieBehavior(policy.cookiePolicy)
                    .cookieBehaviorPrivateMode(policy.cookiePolicy)
                    .enhancedTrackingProtectionLevel(settingsStore.trackingProtectionLevel)
                    .build())
            builder.displayDensityOverride(settingsStore.displayDensity)
            builder.displayDpiOverride(settingsStore.displayDpi)
            builder.screenSizeOverride(
                (settingsStore.maxWindowWidth * settingsStore.displayDensity).toInt(),
                (settingsStore.maxWindowHeight * settingsStore.displayDensity).toInt()
            )
            builder.enterpriseRootsEnabled(settingsStore.isSystemRootCAEnabled)
            builder.inputAutoZoomEnabled(false)
            builder.doubleTapZoomingEnabled(false)
            builder.forceUserScalableEnabled(false)
            builder.debugLogging(settingsStore.isDebugLoggingEnabled)
            builder.consoleOutput(settingsStore.isDebugLoggingEnabled)
            builder.remoteDebuggingEnabled(settingsStore.isRemoteDebuggingEnabled)
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

            runtime = WFactory.createRuntime(context, builder.build())
        }

        return runtime!!
    }

    @Synchronized
    fun isRuntimeCreated(): Boolean {
        return runtime != null
    }

    fun createClient(context: Context): Client {
        return runtime!!.createFetchClient(context)
    }

    fun getDefaultClient(context: Context): Client {
        if (client == null) {
            client = createClient(context)
        }

        return client!!
    }

}
