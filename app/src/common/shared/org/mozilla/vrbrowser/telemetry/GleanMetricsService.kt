package org.mozilla.vrbrowser.telemetry;

import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
//import mozilla.components.service.glean.Glean
//import mozilla.components.service.glean.config.Configuration
//import mozilla.telemetry.glean.GleanTimerId
//import mozilla.components.service.glean.Glean
import org.mozilla.telemetry.TelemetryHolder
import org.mozilla.vrbrowser.BuildConfig
import org.mozilla.vrbrowser.GleanMetrics.*
import org.mozilla.vrbrowser.browser.SettingsStore
import org.mozilla.vrbrowser.search.SearchEngineWrapper
import org.mozilla.vrbrowser.ui.widgets.Windows.MAX_WINDOWS
import org.mozilla.vrbrowser.utils.DeviceType
import org.mozilla.vrbrowser.utils.SystemUtils
import org.mozilla.vrbrowser.utils.UrlUtils

import org.mozilla.vrbrowser.ui.widgets.Windows.WindowPlacement;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import mozilla.components.service.glean.Glean;
import mozilla.components.service.glean.config.Configuration;
import mozilla.telemetry.glean.GleanInternalAPI
import mozilla.telemetry.glean.GleanTimerId;


open class GleanMetricsServiceInternal internal constructor () {
    companion object {
        private val APP_NAME = "FirefoxReality";
        private val LOGTAG = SystemUtils.createLogtag(this.javaClass)
    }
    private var initialized = false
    private lateinit var context: Context
    private val domainMap = HashSet<String>()
    private val loadingTimerId = HashMap<String, GleanTimerId?>()
    private var immersiveTimerId: GleanTimerId? = null;
    private val windowLifeTimerId = Hashtable<Int, GleanTimerId>()
    private var activeWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)
    private var openWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)
    private var openPrivateWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)

    // We should call this at the application initial stage.
    fun init(aContext: Context) {
        if (initialized)
            return

        context = aContext
        initialized = true

        val telemetryEnabled = SettingsStore.getInstance(aContext).isTelemetryEnabled()
        if (telemetryEnabled) {
            GleanMetricsService.start()
        } else {
            GleanMetricsService.stop()
        }

        LegacyTelemetry.clientId.set(UUID.fromString(TelemetryHolder.get().getClientId()))
        val config = Configuration(Configuration.DEFAULT_TELEMETRY_ENDPOINT, BuildConfig.BUILD_TYPE)
        Glean.initialize(aContext, true, config)
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    open fun start() {
        Glean.setUploadEnabled(true)
        setStartupMetrics();
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    open fun stop() {
        Glean.setUploadEnabled(false)
    }

    open fun startPageLoadTime(aUrl: String) {
        val pageLoadingTimerId = Pages.pageLoad.start()
        loadingTimerId.put(aUrl, pageLoadingTimerId)
    }

    open fun stopPageLoadTimeWithURI(uri: String) {
        if (loadingTimerId.containsKey(uri)) {
            val pageLoadingTimerId = loadingTimerId.get(uri)
            Pages.pageLoad.stopAndAccumulate(pageLoadingTimerId)
            loadingTimerId.remove(uri)
        } else {
            Log.e(LOGTAG, "Can't find page loading url.")
        }

        try {
            val uriLink = URI.create(uri)
            if (uriLink.getHost() == null) {
                return;
            }

            if (domainMap.add(UrlUtils.stripCommonSubdomains(uriLink.getHost()))) {
                Url.domains.add()
            }
            Url.visits.add();
        } catch (e: IllegalArgumentException) {
            Log.e(LOGTAG, "Invalid URL", e)
        }
    }

    open fun windowsResizeEvent() {
        Windows.resize.add()
    }

    open fun windowsMoveEvent() {
        Windows.movement.add()
    }

    open fun activePlacementEvent(from: Int, active: Boolean) {
        if (active) {
            if (from == WindowPlacement.FRONT.getValue()) {
                activeWindowTimerId.set(from,
                        Windows.activeInFrontTime.start());
            } else if (from == WindowPlacement.LEFT.getValue()) {
                activeWindowTimerId[from] =
                        Windows.activeInLeftTime.start()
            } else if (from == WindowPlacement.RIGHT.getValue()) {
                activeWindowTimerId[from] =
                        Windows.activeInRightTime.start()
            } else {
                Log.d(LOGTAG,"Undefined WindowPlacement type: " +  from);
            }
        } else if (activeWindowTimerId[from] != null) {
            if (from == WindowPlacement.FRONT.getValue()) {
                Windows.activeInFrontTime.
                        stopAndAccumulate(activeWindowTimerId[from])
            } else if (from == WindowPlacement.LEFT.getValue()) {
                Windows.activeInLeftTime.
                        stopAndAccumulate(activeWindowTimerId[from])
            } else if (from == WindowPlacement.RIGHT.getValue()) {
                Windows.activeInRightTime.
                        stopAndAccumulate(activeWindowTimerId[from])
            } else {
                Log.d(LOGTAG,"Undefined WindowPlacement type: " +  from)
            }
        }
    }

    open fun openWindowsEvent(from: Int, to: Int, isPrivate: Boolean) {
        if (isPrivate) {
            if (from > 0 && openPrivateWindowTimerId[from - 1] != null) {
                when (from) {
                    1 -> {
                        Windows.singlePriWindowOpenedTime.
                           stopAndAccumulate(openPrivateWindowTimerId[from - 1])
                    }
                    2 -> {
                        Windows.doublePriWindowOpenedTime.
                           stopAndAccumulate(openPrivateWindowTimerId[from - 1])
                    }
                    3 -> {
                        Windows.triplePriWindowOpenedTime.
                           stopAndAccumulate(openPrivateWindowTimerId[from - 1])
                    }
                    else -> {
                       Log.d(LOGTAG, "Undefined PriWindowOpenedTime type: " + from)
                    }
                }
            }
            if (to > 0) {
                var label = ""
                when (to) {
                     1 -> {
                         label = "single"
                         openPrivateWindowTimerId[to - 1] =
                                 Windows.singlePriWindowOpenedTime.start()
                     }
                     2 -> {
                         label = "double"
                         openPrivateWindowTimerId[to - 1] =
                                 Windows.doublePriWindowOpenedTime.start()
                     }
                     3 -> {
                         label = "triple";
                         openPrivateWindowTimerId[to - 1] =
                                 Windows.triplePriWindowOpenedTime.start()
                     }
                     else -> {
                         Log.d(LOGTAG,"Undefined OpenedPriWindowCount type: " +  to)
                     }
                }
                Windows.openedPriWindowCount.get(label).add();
            }
        } else {
            if (from > 0 && openWindowTimerId[from - 1] != null) {
                when (from) {
                    1 -> {
                        Windows.singleWindowOpenedTime.
                            stopAndAccumulate(openWindowTimerId[from - 1])
                    }
                    2 -> {
                        Windows.doubleWindowOpenedTime.
                            stopAndAccumulate(openWindowTimerId[from - 1])
                    }
                    3 -> {
                        Windows.tripleWindowOpenedTime.
                            stopAndAccumulate(openWindowTimerId[from - 1])
                    }
                    else -> {
                        Log.d(LOGTAG,"Undefined WindowOpenedTime type: " +  from);
                    }
                }
            }
            if (to > 0) {
                var label = "";
                when (to) {
                    1 -> {
                        label = "single";
                        openWindowTimerId[to - 1] =
                                Windows.singleWindowOpenedTime.start()
                    }
                    2 -> {
                        label = "double";
                        openWindowTimerId[to - 1] =
                                Windows.doubleWindowOpenedTime.start()
                    }
                    3 -> {
                        label = "triple";
                        openWindowTimerId[to - 1] =
                                Windows.tripleWindowOpenedTime.start()
                    }
                    else -> {
                        Log.d(LOGTAG,"Undefined OpenedWindowCount type: " +  to);
                    }
                }
                Windows.openedWindowCount.get(label).add();
            }
        }
    }

    open fun resetOpenedWindowsCount(number: Int, isPrivate: Boolean) {
        if (number == 0) {
            return
        }

        var label = "";
        when (number) {
            1 -> {
                label = "single";
            }
            2 -> {
                label = "double";
            }
            3 -> {
                label = "triple";
            }
            else -> {
                Log.d(LOGTAG, String.format("Undefined OpenedWindowCount type: %d, private? %d",
                        number, if (isPrivate == true) 1 else 0))
            }
        }

        if (isPrivate) {
            Windows.openedPriWindowCount.get(label).add()
        } else {
            Windows.openedWindowCount.get(label).add()
        }
    }

    open fun sessionStop() {
        domainMap.clear()
        loadingTimerId.clear()
        windowLifeTimerId.clear()
        activeWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)
        openWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)
        openPrivateWindowTimerId = arrayOfNulls<GleanTimerId?>(MAX_WINDOWS)

        Pings.sessionEnd.submit();
    }

    @UiThread
    open fun urlBarEvent(aIsUrl: Boolean) {
        if (aIsUrl) {
            Url.queryType.get("type_link").add();
        } else {
            Url.queryType.get("type_query").add();
            // Record search engines.
            val searchEngine = getDefaultSearchEngineIdentifierForTelemetry();
            Searches.counts.get(searchEngine).add();
        }
    }

    @UiThread
    open fun voiceInputEvent() {
        Url.queryType.get("voice_query").add();

        // Record search engines.
        val searchEngine = getDefaultSearchEngineIdentifierForTelemetry();
        Searches.counts.get(searchEngine).add();
    }

    open fun startImmersive() {
        immersiveTimerId = Immersive.duration.start()
    }

    open fun stopImmersive() {
        Immersive.duration.stopAndAccumulate(immersiveTimerId);
    }

    open fun openWindowEvent(windowId: Int) {
         val timerId = Windows.duration.start();
         windowLifeTimerId.put(windowId, timerId);
    }

    open fun closeWindowEvent(windowId: Int) {
        if (windowLifeTimerId.containsKey((windowId))) {
            val timerId = windowLifeTimerId.get(windowId);
            Windows.duration.stopAndAccumulate(timerId);
            windowLifeTimerId.remove(windowId);
        } else {
            Log.e(LOGTAG, "Can't find close window id.");
        }
    }

    open fun newWindowOpenEvent() {
        Control.openNewWindow.add();
    }

    private fun getDefaultSearchEngineIdentifierForTelemetry(): String {
        return SearchEngineWrapper.get(context).getIdentifier();
    }

    private fun setStartupMetrics() {
        Distribution.channelName.set(DeviceType.getDeviceTypeId());
    }

    @VisibleForTesting
    open fun testSetStartupMetrics() {
        setStartupMetrics()
    }

    class FxAInternal {

        open fun signIn() {
            FirefoxAccount.signIn.record()
        }

        open fun signInResult(status: Boolean) {
            val map = HashMap<FirefoxAccount.signInResultKeys, String>()
            map.put(FirefoxAccount.signInResultKeys.state, status.toString())
            FirefoxAccount.signInResult.record(map)
        }

        open fun signOut() {
            FirefoxAccount.signOut.record()
        }

        open fun bookmarksSyncStatus(status: Boolean) {
            FirefoxAccount.bookmarksSyncStatus.set(status)
        }

        open fun historySyncStatus(status: Boolean) {
            FirefoxAccount.historySyncStatus.set(status)
        }

        open fun sentTab() {
            FirefoxAccount.tabSent.add()
        }

        open fun receivedTab(source: mozilla.components.concept.sync.DeviceType) {
            FirefoxAccount.receivedTab.get(source.name.toLowerCase()).add()
        }
    }

    enum class TabSource {
        CONTEXT_MENU,       // Tab opened from the browsers long click context menu
        TABS_DIALOG,        // Tab opened from the tabs dialog
        BOOKMARKS,          // Tab opened from the bookmarks panel
        HISTORY,            // Tab opened from the history panel
        DOWNLOADS,          // Tab opened from the downloads panel
        FXA_LOGIN,          // Tab opened by the FxA login flow
        RECEIVED,           // Tab opened by FxA when a tab is received
        PRE_EXISTING,       // Tab opened as a result of restoring the last session
        BROWSER,            // Tab opened by the browser as a result of a new window open
    }

    open class TabsInternal {
        open fun openedCounter(source: TabSource) {
            org.mozilla.vrbrowser.GleanMetrics.Tabs.opened.get(source.name.toLowerCase()).add();
        }

        open fun activatedEvent() {
            org.mozilla.vrbrowser.GleanMetrics.Tabs.activated.add();
        }
    }

    val FxA = FxAInternal()
    open val Tabs = TabsInternal()
}

object GleanMetricsService : GleanMetricsServiceInternal()
