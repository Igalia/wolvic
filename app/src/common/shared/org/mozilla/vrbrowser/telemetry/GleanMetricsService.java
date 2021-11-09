package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import org.mozilla.vrbrowser.BuildConfig;
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
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void start() {
        Glean.INSTANCE.setUploadEnabled(true);
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void stop() {
        Glean.INSTANCE.setUploadEnabled(false);
    }

    public static void startPageLoadTime(String aUrl) {
        /* intentionally left empty */
    }

    public static void stopPageLoadTimeWithURI(String uri) {
        /* intentionally left empty */
    }

    public static void windowsResizeEvent() {
        /* intentionally left empty */
    }

    public static void windowsMoveEvent() {
        /* intentionally left empty */
    }

    public static void activePlacementEvent(int from, boolean active) {
        /* intentionally left empty */
    }

    public static void openWindowsEvent(int from, int to, boolean isPrivate) {
        /* intentionally left empty */
    }

    public static void resetOpenedWindowsCount(int number, boolean isPrivate) {
        /* intentionally left empty */
    }

    public static void sessionStop() {
        domainMap.clear();
        loadingTimerId.clear();
        windowLifeTimerId.clear();
        activeWindowTimerId = new GleanTimerId[MAX_WINDOWS];
        openWindowTimerId = new GleanTimerId[MAX_WINDOWS];
        openPrivateWindowTimerId = new GleanTimerId[MAX_WINDOWS];

        /* intentionally left empty */
    }

    @UiThread
    public static void urlBarEvent(boolean aIsUrl) {
        /* intentionally left empty */
    }

    @UiThread
    public static void voiceInputEvent() {
        /* intentionally left empty */
    }

    public static void startImmersive() {
        /* intentionally left empty */
    }

    public static void stopImmersive() {
        /* intentionally left empty */
    }

    public static void openWindowEvent(int windowId) {
        /* intentionally left empty */
    }

    public static void closeWindowEvent(int windowId) {
        /* intentionally left empty */
    }

    private static String getDefaultSearchEngineIdentifierForTelemetry() {
        return SearchEngineWrapper.get(context).getIdentifier();
    }

    public static void newWindowOpenEvent() {
        /* intentionally left empty */
    }

    public static class FxA {

        public static void signIn() {
            /* intentionally left empty */
        }

        public static void signInResult(boolean status) {
            /* intentionally left empty */
        }

        public static void signOut() {
            /* intentionally left empty */
        }

        public static void bookmarksSyncStatus(boolean status) {
            /* intentionally left empty */
        }

        public static void historySyncStatus(boolean status) {
            /* intentionally left empty */
        }

        public static void sentTab() {
            /* intentionally left empty */
        }

        public static void receivedTab(@NonNull mozilla.components.concept.sync.DeviceType source) {
            /* intentionally left empty */
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
            /* intentionally left empty */
        }

        public static void activatedEvent() {
            /* intentionally left empty */
        }
    }
}
