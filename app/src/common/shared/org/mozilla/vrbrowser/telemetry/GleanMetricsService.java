package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.GleanMetrics.Distribution;
import org.mozilla.vrbrowser.GleanMetrics.FirefoxAccount;
import org.mozilla.vrbrowser.GleanMetrics.Pings;
import org.mozilla.vrbrowser.GleanMetrics.Searches;
import org.mozilla.vrbrowser.GleanMetrics.Url;
import org.mozilla.vrbrowser.GleanMetrics.Control;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import mozilla.components.service.glean.Glean;
import mozilla.components.service.glean.config.Configuration;


public class GleanMetricsService {

    private final static String APP_NAME = "FirefoxReality";
    private final static String LOGTAG = SystemUtils.createLogtag(GleanMetricsService.class);
    private static boolean initialized = false;
    private static Context context = null;
    private static HashSet<String> domainMap = new HashSet<String>();

    // We should call this at the application initial stage.
    public static void init(Context aContext) {
        if (initialized)
            return;

        context = aContext;
        initialized = true;

        final boolean telemetryEnabled = SettingsStore.getInstance(aContext).isTelemetryEnabled();
        if (telemetryEnabled) {
            GleanMetricsService.start();
        } else {
            GleanMetricsService.stop();
        }
        Configuration config = new Configuration(Configuration.DEFAULT_TELEMETRY_ENDPOINT, BuildConfig.BUILD_TYPE);
        Glean.INSTANCE.initialize(aContext, true, config);
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

    public static void startPageLoadTime() {
       // TODO: Blocked by Bug 1595914.
       // pageLoadingTimerId = Pages.INSTANCE.getPageLoad().start();
    }

    public static void stopPageLoadTimeWithURI(String uri) {
        // TODO: Blocked by Bug 1595914.
        // Pages.INSTANCE.getPageLoad().stopAndAccumulate(pageLoadingTimerId);

        try {
            URI uriLink = URI.create(uri);
            if (uriLink.getHost() == null) {
                return;
            }

            if (domainMap.add(UrlUtils.stripCommonSubdomains(uriLink.getHost()))) {
                Url.INSTANCE.getDomains().add();
            }
            Url.INSTANCE.getVisits().add();

        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "Invalid URL", e);
        }

    }

    public static void sessionStop() {
        domainMap.clear();
        Pings.INSTANCE.getSessionEnd().send();
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
        // TODO: Blocked by Bug 1595914 and 1595723.
        // immersiveTimerId = Durarion.INSTANCE.getImmersiveMode().start();
    }

    public static void stopImmersive() {
        // TODO: Blocked by Bug 1595914 and 1595723.
        // Durarion.INSTANCE.getImmersiveMode().stopAndAccumulate(immersiveTimerId);
    }

    // TODO: Confirm if we don't need multiple metrics for tracking window open duration.
    // like WindowLifetime1 ~ WindowLifetimeN for multiple windows.
    public static void openWindowEvent(int windowId) {
        // TODO: Blocked by Bug 1595914 and Bug 1595723.
        // GleanTimerId id = Durarion.INSTANCE.getWindowLifetime().start();
        // windowLifetimeId.put(windowId, id);
    }

    public static void closeWindowEvent(int windowId) {
        // TODO: Blocked by Bug 1595914 and Bug 1595723.
        // if (windowLifetimeId.containsKey(windowId)) {
        //    Durarion.INSTANCE.getWindowLifetime().stopAndAccumulate(windowLifetimeId.get(windowId));
        //    windowLifetimeId.remove(windowId);
        // } else {
        //    Log.e(LOGTAG, "Can't find window id.");
        // }
    }

    private static String getDefaultSearchEngineIdentifierForTelemetry() {
        return SearchEngineWrapper.get(context).getIdentifier();
    }

    public static void newWindowOpenEvent() {
        Control.INSTANCE.getOpenNewWindow().add();
    }

    private static void setStartupMetrics() {
        Distribution.INSTANCE.getChannelName().set(DeviceType.isOculusBuild() ? "oculusvr" : BuildConfig.FLAVOR_platform);
    }

    @VisibleForTesting
    public static void testSetStartupMetrics() {
        setStartupMetrics();
    }

    public static class FxA {

        public static void signIn() {
            FirefoxAccount.INSTANCE.getSignIn().record();
        }

        public static void signInResult(boolean status) {
            Map<FirefoxAccount.signInResultKeys, String> map = new HashMap<>();
            map.put(FirefoxAccount.signInResultKeys.state, String.valueOf(status));
            FirefoxAccount.INSTANCE.getSignInResult().record(map);
        }

        public static void signOut() {
            FirefoxAccount.INSTANCE.getSignOut().record();
        }

        public static void bookmarksSyncStatus(boolean status) {
            FirefoxAccount.INSTANCE.getBookmarksSyncStatus().set(status);
        }

        public static void historySyncStatus(boolean status) {
            FirefoxAccount.INSTANCE.getHistorySyncStatus().set(status);
        }

        public static void sentTab() {
            FirefoxAccount.INSTANCE.getTabSent().add();
        }

        public static void receivedTab(@NonNull mozilla.components.concept.sync.DeviceType source) {
            FirefoxAccount.INSTANCE.getReceivedTab().get(source.name()).add();
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
            org.mozilla.vrbrowser.GleanMetrics.Tabs.INSTANCE.getOpened().get(source.name()).add();
        }

        public static void activatedEvent() {
            org.mozilla.vrbrowser.GleanMetrics.Tabs.INSTANCE.getActivated().add();
        }
    }
}
