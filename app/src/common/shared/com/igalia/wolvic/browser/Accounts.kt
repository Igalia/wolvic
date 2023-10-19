/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import com.igalia.wolvic.R
import com.igalia.wolvic.VRBrowserApplication
import com.igalia.wolvic.telemetry.TelemetryService
import com.igalia.wolvic.telemetry.TelemetryService.FxA
import com.igalia.wolvic.utils.BitmapCache
import com.igalia.wolvic.utils.SystemUtils
import com.igalia.wolvic.utils.ViewUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.sync.*
import mozilla.components.service.fxa.FirefoxAccount
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.service.fxa.sync.getLastSynced
import java.net.URL
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

const val PROFILE_PICTURE_TAG = "fxa_profile_picture"

class Accounts constructor(val context: Context) {

    private val LOGTAG = SystemUtils.createLogtag(Accounts::class.java)

    enum class AccountStatus {
        SIGNED_IN,
        SIGNED_OUT,
        NEEDS_RECONNECT
    }

    enum class LoginOrigin {
        BOOKMARKS,
        HISTORY,
        SETTINGS,
        SEND_TABS,
        NONE
    }

    var profilePicture: BitmapDrawable? = loadDefaultProfilePicture()
    var loginOrigin: LoginOrigin = LoginOrigin.NONE
        private set
    var originSessionId: String? = null
        private set
    var accountStatus = AccountStatus.SIGNED_OUT
    private val accountListeners = ArrayList<AccountObserver>()
    private val syncListeners = ArrayList<SyncStatusObserver>()
    private val deviceConstellationListeners = ArrayList<DeviceConstellationObserver>()
    private val services = (context.applicationContext as VRBrowserApplication).services
    private var otherDevices = emptyList<Device>()
    private val syncStorage = SyncEnginesStorage(context)
    var isSyncing = false

    private val syncStatusObserver = object : SyncStatusObserver {
        override fun onStarted() {
            Log.d(LOGTAG, "Account syncing has started")

            isSyncing = true
            syncListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onStarted()
                }
            }
        }

        override fun onIdle() {
            Log.d(LOGTAG, "Account syncing has finished")

            isSyncing = false

            services.accountManager.accountProfile()?.email?.let {
                SettingsStore.getInstance(context).setFxALastSync(it, getLastSynced(context))
            }

            syncListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onIdle()
                }
            }
        }

        override fun onError(error: Exception?) {
            Log.d(LOGTAG, "There was an error while syncing the account: " + error?.localizedMessage)

            isSyncing = false
            syncListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onError(error)
                }
            }
        }
    }

    private val deviceConstellationObserver = object : DeviceConstellationObserver {
        override fun onDevicesUpdate(constellation: ConstellationState) {
            Log.d(LOGTAG, "Device constellation has been updated: " + constellation.otherDevices.toString())

            otherDevices = constellation.otherDevices
            deviceConstellationListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onDevicesUpdate(constellation)
                }
            }
        }
    }

    private val accountObserver = object : AccountObserver {
        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            Log.d(LOGTAG, "The user has been successfully logged in")

            if (authType !== AuthType.Existing) {
                TelemetryService.FxA.signInResult(true)
            }

            accountStatus = AccountStatus.SIGNED_IN

            // We must delay applying the device name from settings after we are authenticated
            // as we will stuck if we get it directly when initializing services.accountManager
            runBlocking { setDeviceName(SettingsStore.getInstance(context).deviceName) }

            // Enable syncing after signing in
            syncNowAsync(SyncReason.EngineChange, true)

            Handler(Looper.getMainLooper()).post {
                // Update device list
                account.deviceConstellation().registerDeviceObserver(
                    deviceConstellationObserver,
                    ProcessLifecycleOwner.get(),
                    true
                )

                refreshDevicesAsync()

                accountListeners.toMutableList().forEach {
                    it.onAuthenticated(account, authType)
                }
                originSessionId = null
            }
        }

        override fun onAuthenticationProblems() {
            Log.d(LOGTAG, "There was a problem authenticating the user")

            TelemetryService.FxA.signInResult(false)

            originSessionId = null

            accountStatus = AccountStatus.NEEDS_RECONNECT
            accountListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onAuthenticationProblems()
                }
            }
        }

        override fun onLoggedOut() {
            Log.d(LOGTAG, "The user has been logged out")

            originSessionId = null

            accountStatus = AccountStatus.SIGNED_OUT
            accountListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onLoggedOut()
                }
            }

            loadDefaultProfilePicture()
        }

        override fun onProfileUpdated(profile: Profile) {
            Log.d(LOGTAG, "The user profile has been updated")

            accountListeners.toMutableList().forEach {
                Handler(Looper.getMainLooper()).post {
                    it.onProfileUpdated(profile)
                }
            }

            loadProfilePicture(profile)
        }
    }

    init {
        services.accountManager.registerForSyncEvents(
                syncStatusObserver, ProcessLifecycleOwner.get(), false
        )
        services.accountManager.register(accountObserver)
        accountStatus = if (services.accountManager.authenticatedAccount() != null) {
            if (services.accountManager.accountNeedsReauth()) {
                AccountStatus.NEEDS_RECONNECT

            } else {
                AccountStatus.SIGNED_IN
            }

        } else {
            AccountStatus.SIGNED_OUT
        }
    }

    private fun loadProfilePicture(profile: Profile) {
        thread {
            try {
                val url = URL(profile.avatar!!.url)
                BitmapFactory.decodeStream(url.openStream())?.let {
                    val bitmap = ViewUtils.getRoundedCroppedBitmap(it)
                    profilePicture = BitmapDrawable(context.resources, bitmap)
                    BitmapCache.getInstance(context).addBitmap(PROFILE_PICTURE_TAG, bitmap)

                } ?: throw IllegalArgumentException()

            } catch (e: Exception) {
                loadDefaultProfilePicture()

            } finally {
                accountListeners.toMutableList().forEach {
                    Handler(Looper.getMainLooper()).post {
                        it.onProfileUpdated(profile)
                    }
                }
            }
        }
    }

    private fun loadDefaultProfilePicture(): BitmapDrawable? {
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_icon_settings_account)?.let {
            try {
                BitmapCache.getInstance(context).addBitmap(PROFILE_PICTURE_TAG, it)
            } catch (e: NullPointerException) {
                Log.w(LOGTAG, "Bitmap is a null pointer.")
                return null
            }
            profilePicture = BitmapDrawable(context.resources, ViewUtils.getRoundedCroppedBitmap(it))
        }

        return profilePicture
    }

    fun addAccountListener(aListener: AccountObserver) {
        if (!accountListeners.contains(aListener)) {
            accountListeners.add(aListener)
        }
    }

    fun removeAccountListener(aListener: AccountObserver) {
        accountListeners.remove(aListener)
    }

    fun removeAllAccountListeners() {
        accountListeners.clear()
    }

    fun addSyncListener(aListener: SyncStatusObserver) {
        if (!syncListeners.contains(aListener)) {
            syncListeners.add(aListener)
        }
    }

    fun removeSyncListener(aListener: SyncStatusObserver) {
        syncListeners.remove(aListener)
    }

    fun removeAllSyncListeners() {
        syncListeners.clear()
    }

    fun addDeviceConstellationListener(aListener: DeviceConstellationObserver) {
        if (!deviceConstellationListeners.contains(aListener)) {
            deviceConstellationListeners.add(aListener)
        }
    }

    fun removeDeviceConstellationListener(aListener: DeviceConstellationObserver) {
        deviceConstellationListeners.remove(aListener)
    }

    fun removeAllDeviceConstellationListeners() {
        deviceConstellationListeners.clear()
    }

    fun authUrlAsync(): CompletableFuture<String?>? {
        TelemetryService.FxA.signIn()
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.beginAuthentication(null, FxA())
        }
    }

    fun refreshDevicesAsync(): CompletableFuture<Boolean?>? {
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.authenticatedAccount()?.deviceConstellation()?.refreshDevices()
        }
    }

    fun pollForEventsAsync(): CompletableFuture<Boolean?>? {
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.authenticatedAccount()?.deviceConstellation()?.pollForCommands()
        }
    }

    fun updateProfileAsync(): CompletableFuture<Profile?>? {
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.accountProfile()
        }
    }

    fun syncNowAsync(reason: SyncReason = SyncReason.User,
                     debounce: Boolean = false): CompletableFuture<Unit?>?{
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.syncNow(reason, debounce)
        }
    }

    fun setSyncStatus(engine: SyncEngine, value: Boolean) {
        when(engine) {
            SyncEngine.Bookmarks -> {
                TelemetryService.FxA.bookmarksSyncStatus(value)
            }
            SyncEngine.History -> {
                TelemetryService.FxA.historySyncStatus(value)
            }
            else -> {}
        }

        syncStorage.setStatus(engine, value)
    }

    fun accountProfile(): Profile? {
        return services.accountManager.accountProfile()
    }

    fun logoutAsync(): CompletableFuture<Unit?>? {
        TelemetryService.FxA.signOut()

        otherDevices = emptyList()
        return CoroutineScope(Dispatchers.Main).future {
            services.accountManager.logout()
        }
    }

    fun isEngineEnabled(engine: SyncEngine): Boolean {
        return syncStorage.getStatus()[engine]?: false
    }

    fun isSignedIn(): Boolean {
        return (accountStatus == AccountStatus.SIGNED_IN)
    }

    fun lastSync(): Long {
        services.accountManager.accountProfile()?.email?.let {
            return SettingsStore.getInstance(context).getFxALastSync(it)
        }
        return 0
    }

    fun devicesByCapability(capabilities: List<DeviceCapability>): List<Device> {
        return otherDevices.filter { it.capabilities.containsAll(capabilities) }
    }

    fun sendTabs(targetDevices: List<Device>, url: String, title: String) {
        CoroutineScope(Dispatchers.Main).launch {
            services.accountManager.authenticatedAccount()?.deviceConstellation()?.let { constellation ->
                // Ignore devices that can't receive tabs or are not in the received list
                val targets = constellation.state()?.otherDevices?.filter {
                    it.capabilities.contains(DeviceCapability.SEND_TAB)
                    targetDevices.contains(it)
                }

                targets?.forEach { it ->
                    constellation.sendCommandToDevice(
                            it.id, DeviceCommandOutgoing.SendTab(title, url)
                    ).also { if (it) TelemetryService.FxA.sentTab() }
                }
            }
        }
    }

    fun getConnectionSuccessURL(): String {
        return (services.accountManager.authenticatedAccount() as FirefoxAccount).getConnectionSuccessURL()
    }

    fun setOrigin(origin: LoginOrigin, sessionId: String?) {
        loginOrigin = origin
        originSessionId = sessionId
    }

    suspend fun setDeviceName(deviceName: String) {
        services.accountManager.authenticatedAccount()?.deviceConstellation()?.setDeviceName(deviceName, context);
    }

}