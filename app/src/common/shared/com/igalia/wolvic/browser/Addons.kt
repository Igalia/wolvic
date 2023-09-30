package com.igalia.wolvic.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationManagerCompat
import com.igalia.wolvic.BuildConfig
import com.igalia.wolvic.R
import com.igalia.wolvic.browser.adapter.ComponentsAdapter
import com.igalia.wolvic.browser.api.WSession
import com.igalia.wolvic.browser.components.WolvicEngineSession
import com.igalia.wolvic.browser.engine.EngineProvider
import com.igalia.wolvic.browser.engine.Session
import com.igalia.wolvic.browser.engine.SessionStore
import com.igalia.wolvic.crashreporting.GlobalExceptionHandler
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import mozilla.components.concept.engine.CancellableOperation
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.concept.engine.webextension.EnableSource
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.amo.AMOAddonsProvider
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.worker.Frequency
import mozilla.components.support.webextensions.WebExtensionSupport
import mozilla.components.support.webextensions.WebExtensionSupport.installedExtensions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val DAY_IN_MINUTES = 24 * 60L

class Addons(val context: Context, private val sessionStore: SessionStore) {

    interface AddonsListener {
        fun onAddonsUpdated()
    }

    val delegate: WidgetManagerDelegate = (context as WidgetManagerDelegate)
    val listeners: ArrayList<AddonsListener> = ArrayList()

    val addonCollectionProvider by lazy {
        if (BuildConfig.AMO_COLLECTION.isNotEmpty()) {
            AMOAddonsProvider(
                    context,
                    EngineProvider.getDefaultClient(context),
                    collectionName = BuildConfig.AMO_COLLECTION,
                    maxCacheAgeInMinutes = DAY_IN_MINUTES
            )
        } else {
            AMOAddonsProvider(
                    context,
                    EngineProvider.getDefaultClient(context),
                    maxCacheAgeInMinutes = DAY_IN_MINUTES)
        }
    }

    @Suppress("MagicNumber")
    private val addonUpdater by lazy {
        DefaultAddonUpdater(context, Frequency(12, TimeUnit.HOURS), NotificationsDelegate(
            NotificationManagerCompat.from(context))
        )
    }

    private val addonManager by lazy {
        AddonManager(
                ComponentsAdapter.get().store,
                sessionStore.webExtensionRuntime,
                addonCollectionProvider,
                addonUpdater)
    }

    init {
        initializeWebExtensionSupport()
    }

    private fun initializeWebExtensionSupport() {
        try {
            GlobalAddonDependencyProvider.initialize(
                    addonManager,
                    addonUpdater,
                    onCrash = { exception ->
                        GlobalExceptionHandler.mInstance.mCrashHandler.uncaughtException(Thread.currentThread(), exception)
                    }
            )
            WebExtensionSupport.initialize(
                    sessionStore.webExtensionRuntime,
                    ComponentsAdapter.get().store,
                    false,
                    onNewTabOverride = {
                        _, engineSession, url ->
                        val session = (engineSession as WolvicEngineSession).session
                        session.loadUri(url, WSession.LOAD_FLAGS_REPLACE_HISTORY)
                        session.id
                    },
                    onCloseTabOverride = {
                        _, sessionId ->
                        val session: Session? = sessionStore.getSession(sessionId)
                        if (session != null) {
                            delegate.windows.closeTab(session)
                        }
                    },
                    onSelectTabOverride = {
                        _, sessionId ->
                        val session: Session? = sessionStore.getSession(sessionId)
                        if (session != null) {
                            delegate.windows.selectTab(session)
                        }
                    },
                    onExtensionsLoaded = { extensions ->
                        addonUpdater.registerForFutureUpdates(extensions)
                    },
                    onUpdatePermissionRequest = addonUpdater::onUpdatePermissionRequest
            )
        } catch (e: UnsupportedOperationException) {
            Logger.error("Failed to initialize web extension support", e)
        }
    }

    fun addListener(listener: AddonsListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: AddonsListener) {
        listeners.remove(listener)
    }

    fun installAddon(addon: Addon,
                     onSuccess: ((Addon) -> Unit) = { },
                     onError: ((String, Throwable) -> Unit) = { _, _ -> }): CancellableOperation {
        return addonManager.installAddon(addon, { addon1: Addon ->
            onSuccess.invoke(addon1)
            notifyListeners()

        }, { s: String, throwable: Throwable ->
            onError.invoke(s, throwable)
        })
    }

    fun uninstallAddon(addon: Addon,
                       onSuccess: (() -> Unit) = { },
                       onError: ((String, Throwable) -> Unit) = { _, _ -> }) {
        addonManager.uninstallAddon(addon, {
            onSuccess.invoke()
            notifyListeners()

        }, { s: String, throwable: Throwable ->
            onError.invoke(s, throwable)
        })
    }

    fun setAddonAllowedInPrivateBrowsing(addon: Addon,
                                         allowed: Boolean,
                                         onSuccess: ((Addon) -> Unit) = { },
                                         onError: ((Throwable) -> Unit) = { }) {
        addonManager.setAddonAllowedInPrivateBrowsing(addon, allowed, {
            onSuccess.invoke(it)
            notifyListeners()

        }, { throwable: Throwable ->
            onError.invoke(throwable)
        })
    }

    fun enableAddon(addon: Addon,
                    source: EnableSource = EnableSource.USER,
                    onSuccess: ((Addon) -> Unit) = { },
                    onError: ((Throwable) -> Unit) = { }) {
            addonManager.enableAddon(addon, source, {
            onSuccess.invoke(it)
            notifyListeners()

        }, { throwable: Throwable ->
            onError.invoke(throwable)
        })
    }

    fun disableAddon(addon: Addon,
                    source: EnableSource = EnableSource.USER,
                    onSuccess: ((Addon) -> Unit) = { },
                    onError: ((Throwable) -> Unit) = { }) {
        addonManager.disableAddon(addon, source, {
            onSuccess.invoke(it)
            notifyListeners()

        }, { throwable: Throwable ->
            onError.invoke(throwable)
        })
    }

    fun notifyListeners() {
        if (listeners.size > 0) {
            val listenersCopy = ArrayList(listeners)
            Handler(Looper.getMainLooper()).post {
                for (listener in listenersCopy) {
                    listener.onAddonsUpdated()
                }
            }
        }
    }

    fun getAddons(waitForPendingActions: Boolean = true): CompletableFuture<List<Addon>> = GlobalScope.future {
        val addons = addonManager.getAddons(waitForPendingActions).toMutableList()
        // Set the correct enabled state and icon for unsupported addons
        for (i in addons.indices) {
            if (!addons[i].isSupported()) {
                val enabled = installedExtensions[addons[i].id]?.isEnabled() ?: false
                val icon = CoroutineScope(Dispatchers.Main).future {
                    try {
                        installedExtensions[addons[i].id]?.loadIcon(AddonManager.ADDON_ICON_SIZE)
                    } catch (throwable: Throwable) {
                        Logger.warn("Failed to load addon icon.", throwable)
                        null
                    }
                }.await()
                addons[i] = addons[i].copy(installedState = addons[i].installedState?.copy(enabled = enabled, icon = icon))
            }
        }
        addons.toList()
    }

    companion object {
        fun loadActionIcon(context: Context, action: Action, height: Int): CompletableFuture<Drawable?> =
                CoroutineScope(Dispatchers.Main).future {
                    try {
                        val bitmap: Bitmap? = action.loadIcon?.invoke(height)
                        BitmapDrawable(context.resources, bitmap)
                    } catch (throwable: Throwable) {
                        Logger.warn("Failed to load browser action icon, falling back to default.", throwable)
                        AppCompatResources.getDrawable(context, R.drawable.ic_icon_addons)
                    }
                }
    }
}