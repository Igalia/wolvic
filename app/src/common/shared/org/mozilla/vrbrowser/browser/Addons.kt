package org.mozilla.vrbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.amo.AddonCollectionProvider
import mozilla.components.feature.addons.update.AddonUpdater
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.BuildConfig
import org.mozilla.vrbrowser.R
import org.mozilla.vrbrowser.browser.adapter.ComponentsAdapter
import org.mozilla.vrbrowser.browser.components.GeckoEngineSession
import org.mozilla.vrbrowser.browser.engine.EngineProvider
import org.mozilla.vrbrowser.browser.engine.Session
import org.mozilla.vrbrowser.browser.engine.SessionStore
import org.mozilla.vrbrowser.crashreporting.GlobalExceptionHandler
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val DAY_IN_MINUTES = 24 * 60L

class Addons(val context: Context, private val sessionStore: SessionStore) {

    val delegate: WidgetManagerDelegate = (context as WidgetManagerDelegate)

    val addonCollectionProvider by lazy {
        if (BuildConfig.AMO_COLLECTION.isNotEmpty()) {
            AddonCollectionProvider(
                    context,
                    EngineProvider.getDefaultClient(context),
                    collectionName = BuildConfig.AMO_COLLECTION,
                    maxCacheAgeInMinutes = DAY_IN_MINUTES
            )
        } else {
            AddonCollectionProvider(
                    context,
                    EngineProvider.getDefaultClient(context),
                    maxCacheAgeInMinutes = DAY_IN_MINUTES)
        }
    }

    @Suppress("MagicNumber")
    val addonUpdater by lazy {
        DefaultAddonUpdater(context, AddonUpdater.Frequency(12, TimeUnit.HOURS))
    }

    val addonManager by lazy {
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
                        val session = sessionStore.getSession((engineSession as GeckoEngineSession).geckoSession)
                        session?.loadUri(url, GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
                        session!!.id
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

    fun getAddons(waitForPendingActions: Boolean = true): CompletableFuture<List<Addon>> =
            GlobalScope.future {
                addonManager.getAddons(waitForPendingActions)
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