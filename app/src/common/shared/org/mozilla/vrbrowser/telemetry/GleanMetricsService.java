package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.GleanMetrics.Control;
import org.mozilla.vrbrowser.GleanMetrics.Distribution;
import org.mozilla.vrbrowser.GleanMetrics.FirefoxAccount;
import org.mozilla.vrbrowser.GleanMetrics.Immersive;
import org.mozilla.vrbrowser.GleanMetrics.Pages;
import org.mozilla.vrbrowser.GleanMetrics.Pings;
import org.mozilla.vrbrowser.GleanMetrics.Searches;
import org.mozilla.vrbrowser.GleanMetrics.Url;
import org.mozilla.vrbrowser.GleanMetrics.Windows;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

import mozilla.components.concept.fetch.Client;
import mozilla.components.service.glean.Glean;
import mozilla.components.service.glean.config.Configuration;
import mozilla.components.service.glean.net.ConceptFetchHttpUploader;
import mozilla.telemetry.glean.GleanTimerId;

import static org.mozilla.vrbrowser.ui.widgets.Windows.MAX_WINDOWS;
import static org.mozilla.vrbrowser.ui.widgets.Windows.WindowPlacement;


public class GleanMetricsService {

    private final static String APP_NAME = "FirefoxReality";
    private final static String LOGTAG = SystemUtils.createLogtag(GleanMetricsService.class);
    private static boolean initialized = false;
    private static Context context = null;
    private static HashSet<String> domainMap = new HashSet<String>();
    private static Map<String, GleanTimerId> loadingTimerId = new Hashtable<>();
    private static GleanTimerId immersiveTimerId;
    private static Map<Integer, GleanTimerId> windowLifeTimerId = new Hashtable<>();
    private static GleanTimerId activeWindowTimerId[] = new GleanTimerId[MAX_WINDOWS];
    private static GleanTimerId openWindowTimerId[] = new GleanTimerId[MAX_WINDOWS];
    private static GleanTimerId openPrivateWindowTimerId[] = new GleanTimerId[MAX_WINDOWS];

    // We should call this at the application initial stage.
    public static void init(@NonNull Context aContext, @NonNull Client client) {
        if (initialized)
            return;

        context = aContext;
        initialized = true;

        final boolean telemetryEnabled = SettingsStore.getInstance(aContext).isTelemetryEnabled();
        Configuration config = new Configuration(
                ConceptFetchHttpUploader.fromClient(client),
                Configuration.DEFAULT_TELEMETRY_ENDPOINT,
                BuildConfig.BUILD_TYPE);

        Glean.INSTANCE.initialize(aContext, telemetryEnabled, config);
        setStartupMetrics();
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void start() {
        Glean.INSTANCE.setUploadEnabled(true);
        setStartupMetrics();
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void stop() {
        Glean.INSTANCE.setUploadEnabled(false);
    }

    public static void startPageLoadTime(String aUrl) {
        GleanTimerId pageLoadingTimerId = Pages.INSTANCE.pageLoad().start();
        if (pageLoadingTimerId != null) loadingTimerId.put(aUrl, pageLoadingTimerId);
    }

    public static void stopPageLoadTimeWithURI(String uri) {
        if (loadingTimerId.containsKey(uri)) {
            GleanTimerId pageLoadingTimerId = loadingTimerId.get(uri);
            Pages.INSTANCE.pageLoad().stopAndAccumulate(pageLoadingTimerId);
            loadingTimerId.remove(uri);
        } else {
            Log.e(LOGTAG, "Can't find page loading url.");
        }

        try {
            URI uriLink = UrlUtils.parseUri(uri);
            if (uriLink.getHost() == null) {
                return;
            }

            if (domainMap.add(UrlUtils.stripCommonSubdomains(uriLink.getHost()))) {
                Url.INSTANCE.domains().add();
            }
            Url.INSTANCE.visits().add();
        } catch (URISyntaxException e) {
            Log.e(LOGTAG, "Invalid URL", e);
        }
    }

    public static void windowsResizeEvent() {
        Windows.INSTANCE.resize().add();
    }

    public static void windowsMoveEvent() {
        Windows.INSTANCE.movement().add();
    }

    public static void activePlacementEvent(int from, boolean active) {
        if (active) {
            if (from == WindowPlacement.FRONT.getValue()) {
                activeWindowTimerId[from] =
                        Windows.INSTANCE.activeInFrontTime().start();
            } else if (from == WindowPlacement.LEFT.getValue()) {
                activeWindowTimerId[from] =
                        Windows.INSTANCE.activeInLeftTime().start();
            } else if (from == WindowPlacement.RIGHT.getValue()) {
                activeWindowTimerId[from] =
                        Windows.INSTANCE.activeInRightTime().start();
            } else {
                Log.d(LOGTAG,"Undefined WindowPlacement type: " +  from);
            }
        } else if (activeWindowTimerId[from] != null) {
            if (from == WindowPlacement.FRONT.getValue()) {
                Windows.INSTANCE.activeInFrontTime().
                        stopAndAccumulate(activeWindowTimerId[from]);
            } else if (from == WindowPlacement.LEFT.getValue()) {
                Windows.INSTANCE.activeInLeftTime().
                        stopAndAccumulate(activeWindowTimerId[from]);
            } else if (from == WindowPlacement.RIGHT.getValue()) {
                Windows.INSTANCE.activeInRightTime().
                        stopAndAccumulate(activeWindowTimerId[from]);
            } else {
                Log.d(LOGTAG,"Undefined WindowPlacement type: " +  from);
            }
        }
    }

    public static void openWindowsEvent(int from, int to, boolean isPrivate) {
        if (isPrivate) {
            if (from > 0 && openPrivateWindowTimerId[from - 1] != null) {
                switch (from) {
                    case 1:
                        Windows.INSTANCE.singlePriWindowOpenedTime().
                                stopAndAccumulate(openPrivateWindowTimerId[from - 1]);
                        break;
                    case 2:
                        Windows.INSTANCE.doublePriWindowOpenedTime().
                                stopAndAccumulate(openPrivateWindowTimerId[from - 1]);
                        break;
                    case 3:
                        Windows.INSTANCE.triplePriWindowOpenedTime().
                                stopAndAccumulate(openPrivateWindowTimerId[from - 1]);
                        break;
                    default:
                        Log.d(LOGTAG,"Undefined PriWindowOpenedTime type: " +  from);
                        break;
                }
            }
            if (to > 0) {
                String label = "";
                switch (to) {
                    case 1:
                        label = "single";
                        openPrivateWindowTimerId[to - 1] =
                                Windows.INSTANCE.singlePriWindowOpenedTime().start();
                        break;
                    case 2:
                        label = "double";
                        openPrivateWindowTimerId[to - 1] =
                                Windows.INSTANCE.doublePriWindowOpenedTime().start();
                        break;
                    case 3:
                        label = "triple";
                        openPrivateWindowTimerId[to - 1] =
                                Windows.INSTANCE.triplePriWindowOpenedTime().start();
                        break;
                    default:
                        Log.d(LOGTAG,"Undefined OpenedPriWindowCount type: " +  to);
                        break;
                }
                Windows.INSTANCE.getOpenedPriWindowCount().get(label).add();
            }
        } else {
            if (from > 0 && openWindowTimerId[from - 1] != null) {
                switch (from) {
                    case 1:
                        Windows.INSTANCE.singleWindowOpenedTime().
                                stopAndAccumulate(openWindowTimerId[from - 1]);
                        break;
                    case 2:
                        Windows.INSTANCE.doubleWindowOpenedTime().
                                stopAndAccumulate(openWindowTimerId[from - 1]);
                        break;
                    case 3:
                        Windows.INSTANCE.tripleWindowOpenedTime().
                                stopAndAccumulate(openWindowTimerId[from - 1]);
                        break;
                    default:
                        Log.d(LOGTAG,"Undefined WindowOpenedTime type: " +  from);
                        break;
                }
            }
            if (to > 0) {
                String label = "";
                switch (to) {
                    case 1:
                        label = "single";
                        openWindowTimerId[to - 1] =
                                Windows.INSTANCE.singleWindowOpenedTime().start();
                        break;
                    case 2:
                        label = "double";
                        openWindowTimerId[to - 1] =
                                Windows.INSTANCE.doubleWindowOpenedTime().start();
                        break;
                    case 3:
                        label = "triple";
                        openWindowTimerId[to - 1] =
                                Windows.INSTANCE.tripleWindowOpenedTime().start();
                        break;
                    default:
                        Log.d(LOGTAG,"Undefined OpenedWindowCount type: " +  to);
                        break;
                }
                Windows.INSTANCE.getOpenedWindowCount().get(label).add();
            }
        }
    }

    public static void resetOpenedWindowsCount(int number, boolean isPrivate) {
        if (number == 0) {
            return;
        }

        String label = "";
        switch (number) {
            case 1:
                label = "single";
                break;
            case 2:
                label = "double";
                break;
            case 3:
                label = "triple";
                break;
            default:
                Log.d(LOGTAG, String.format("Undefined OpenedWindowCount type: %d, private? %d",
                        number, isPrivate == true ? 1: 0));
                break;
        }

        if (isPrivate) {
            Windows.INSTANCE.getOpenedPriWindowCount().get(label).add();
        } else {
            Windows.INSTANCE.getOpenedWindowCount().get(label).add();
        }
    }

    public static void sessionStop() {
        domainMap.clear();
        loadingTimerId.clear();
        windowLifeTimerId.clear();
        activeWindowTimerId = new GleanTimerId[MAX_WINDOWS];
        openWindowTimerId = new GleanTimerId[MAX_WINDOWS];
        openPrivateWindowTimerId = new GleanTimerId[MAX_WINDOWS];

        Pings.INSTANCE.sessionEnd().submit();
    }

    @UiThread
    public static void urlBarEvent(boolean aIsUrl) {
        if (aIsUrl) {
            Url.INSTANCE.getQueryType().get("type_link").add();
        } else {
            Url.INSTANCE.getQueryType().get("type_query").add();
            // Record search engines.
            String searchEngine = getDefaultSearchEngineIdentifierForTelemetry();
            Searches.INSTANCE.getCounts().get(searchEngine).add();
        }
    }

    @UiThread
    public static void voiceInputEvent() {
        Url.INSTANCE.getQueryType().get("voice_query").add();

        // Record search engines.
        String searchEngine = getDefaultSearchEngineIdentifierForTelemetry();
        Searches.INSTANCE.getCounts().get(searchEngine).add();
    }

    public static void startImmersive() {
        immersiveTimerId =  Immersive.INSTANCE.duration().start();
    }

    public static void stopImmersive() {
        Immersive.INSTANCE.duration().stopAndAccumulate(immersiveTimerId);
    }

    public static void openWindowEvent(int windowId) {
         GleanTimerId timerId = Windows.INSTANCE.duration().start();
         if (timerId != null) {
            windowLifeTimerId.put(windowId, timerId);
         }
    }

    public static void closeWindowEvent(int windowId) {
        if (windowLifeTimerId.containsKey((windowId))) {
            GleanTimerId timerId = windowLifeTimerId.get(windowId);
            Windows.INSTANCE.duration().stopAndAccumulate(timerId);
            windowLifeTimerId.remove(windowId);
        } else {
            Log.e(LOGTAG, "Can't find close window id.");
        }
    }

    private static String getDefaultSearchEngineIdentifierForTelemetry() {
        return SearchEngineWrapper.get(context).getIdentifier();
    }

    public static void newWindowOpenEvent() {
        Control.INSTANCE.openNewWindow().add();
    }

    private static void setStartupMetrics() {
        Distribution.INSTANCE.channelName().set(DeviceType.getDeviceTypeId());
    }

    @VisibleForTesting
    public static void testSetStartupMetrics() {
        setStartupMetrics();
    }

    public static class FxA {

        public static void signIn() {
            FirefoxAccount.INSTANCE.signIn().record();
        }

        public static void signInResult(boolean status) {
            Map<FirefoxAccount.signInResultKeys, String> map = new HashMap<>();
            map.put(FirefoxAccount.signInResultKeys.state, String.valueOf(status));
            FirefoxAccount.INSTANCE.signInResult().record(map);
        }

        public static void signOut() {
            FirefoxAccount.INSTANCE.signOut().record();
        }

        public static void bookmarksSyncStatus(boolean status) {
            FirefoxAccount.INSTANCE.bookmarksSyncStatus().set(status);
        }

        public static void historySyncStatus(boolean status) {
            FirefoxAccount.INSTANCE.historySyncStatus().set(status);
        }

        public static void sentTab() {
            FirefoxAccount.INSTANCE.tabSent().add();
        }

        public static void receivedTab(@NonNull mozilla.components.concept.sync.DeviceType source) {
            FirefoxAccount.INSTANCE.getReceivedTab().get(source.name().toLowerCase()).add();
        }
    }

    public static class Tabs {

        public enum TabSource {
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

        public static void openedCounter(@NonNull TabSource source) {
            org.mozilla.vrbrowser.GleanMetrics.Tabs.INSTANCE.getOpened().get(source.name().toLowerCase()).add();
        }

        public static void activatedEvent() {
            org.mozilla.vrbrowser.GleanMetrics.Tabs.INSTANCE.activated().add();
        }
    }
}
