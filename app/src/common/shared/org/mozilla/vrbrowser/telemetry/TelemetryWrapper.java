package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.content.res.Resources;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.mozilla.telemetry.Telemetry;
import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.telemetry.config.TelemetryConfiguration;
import org.mozilla.telemetry.event.TelemetryEvent;
import org.mozilla.telemetry.measurement.SearchesMeasurement;
import org.mozilla.telemetry.net.TelemetryClient;
import org.mozilla.telemetry.ping.TelemetryCorePingBuilder;
import org.mozilla.telemetry.ping.TelemetryMobileEventPingBuilder;
import org.mozilla.telemetry.schedule.TelemetryScheduler;
import org.mozilla.telemetry.schedule.jobscheduler.JobSchedulerTelemetryScheduler;
import org.mozilla.telemetry.serialize.JSONPingSerializer;
import org.mozilla.telemetry.storage.FileTelemetryStorage;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;

import static java.lang.Math.toIntExact;
import static org.mozilla.vrbrowser.ui.widgets.Windows.MAX_WINDOWS;
import static org.mozilla.vrbrowser.ui.widgets.Windows.WindowPlacement;


public class TelemetryWrapper {
    private final static String APP_NAME = "FirefoxReality";
    private final static String LOGTAG = SystemUtils.createLogtag(TelemetryWrapper.class);
    private final static int MIN_LOAD_TIME = 40;
    private final static int LOADING_BUCKET_SIZE_MS = 100;
    private final static int MIN_IMMERSIVE_TIME = 1000;
    private final static int IMMERSIVE_BUCKET_SIZE_MS = 10000;
    private final static int HISTOGRAM_MIN_INDEX = 0;
    private final static int HISTOGRAM_SIZE = 200;

    private static HashSet<String> domainMap = new HashSet<String>();
    private static int[] loadingTimeHistogram = new int[HISTOGRAM_SIZE];
    private static int[] immersiveHistogram = new int[HISTOGRAM_SIZE];
    private static int numUri = 0;
    private static long startLoadPageTime = 0;
    private static long startImmersiveTime = 0;
    private static long sessionStartTime = 0;

    // Multi-window events
    private final static int MULTI_WINDOW_BIN_SIZE_MS = 10000;
    private static HashMap<Integer, Long> windowLifetime = new HashMap<>();
    private static int windowsMovesCount = 0;
    private static int windowsResizesCount = 0;
    private static long[] activePlacementStartTime = new long[MAX_WINDOWS];
    private static long[] activePlacementTime = new long[MAX_WINDOWS];
    private static long[] openWindowsStartTime = new long[MAX_WINDOWS];
    private static long[] openPrivateWindowsStartTime = new long[MAX_WINDOWS];
    private static long[] openWindowsTime = new long[MAX_WINDOWS];
    private static long[] openPrivateWindowsTime = new long[MAX_WINDOWS];
    private static int[] openWindows = new int[MAX_WINDOWS];
    private static int[] openPrivateWindows = new int[MAX_WINDOWS];
    private static TelemetryHistogram windowsLifetimeHistogram =
            new TelemetryHistogram(HISTOGRAM_SIZE, MULTI_WINDOW_BIN_SIZE_MS, 0);

    private class Category {
        private static final String ACTION = "action";
        private static final String HISTOGRAM = "histogram";
    }

    private class Method {
        private static final String FOREGROUND = "foreground";
        private static final String BACKGROUND = "background";
        private static final String OPEN = "open";
        private static final String TYPE_URL = "type_url";
        private static final String TYPE_QUERY = "type_query";
        // TODO: Support "select_query" after providing search suggestion.
        private static final String VOICE_QUERY = "voice_query";
        private static final String IMMERSIVE_MODE = "immersive_mode";
        private static final String TELEMETRY_STATUS = "status";

        // How long is a window open for / window life
        private static final String WINDOW_LIFETIME = "window_lifetime";
        // Frequency of window moves
        private static final String WINDOWS_MOVES_FREQ = "windows_move_freq";
        // Frequency of window resizes
        private static final String WINDOWS_RESIZE_FREQ = "windows_resize_freq";
        // When a session is multi-window, what time is each position the active window
        private static final String PLACEMENTS_ACTIVE_TIME = "place_active_time";
        // When a session is multi-window, what time are one, two or three windows open
        private static final String OPEN_WINDOWS_TIME = "open_windows_time";
        // The weight of windows are opened at a time, per session
        private static final String WINDOWS_OPEN_W = "windows_open_w";
    }

    private class Object {
        private static final String APP = "app";
        private static final String BROWSER = "browser";
        private static final String SEARCH_BAR = "search_bar";
        private static final String VOICE_INPUT = "voice_input";
        private static final String WINDOW = "window";
    }

    private class Extra {
        private static final String TOTAL_URI_COUNT = "total_uri_count";
        private static final String UNIQUE_DOMAINS_COUNT = "unique_domains_count";
        private static final String WINDOW_MOVES_COUNT = "windows_moves_count";
        private static final String WINDOW_RESIZE_COUNT = "windows_resize_count";
        private static final String LEFT_WINDOW_ACTIVE_TIME = "left_window_active_time";
        private static final String FRONT_WINDOW_ACTIVE_TIME = "front_window_active_time";
        private static final String RIGHT_WINDOW_ACTIVE_TIME = "right_window_active_time";
        private static final String ONE_OPEN_WINDOWS_TIME = "one_w_open_time";
        private static final String TWO_OPEN_WINDOWS_TIME = "two_w_open_time";
        private static final String THREE_OPEN_WINDOWS_TIME = "thr_w_open_time";
        private static final String ONE_WINDOWS_OPENED = "one_w_open";
        private static final String TWO_WINDOWS_OPENED = "two_w_open";
        private static final String THREE_WINDOWS_OPENED = "thr_w_open";
        private static final String ONE_PRIVATE_WINDOWS_OPENED = "one_pri_w_open";
        private static final String TWO_PRIVATE_WINDOWS_OPENED = "two_pri_w_open";
        private static final String THREE_PRIVATE_WINDOWS_OPENED = "thr_pri_w_open";
        private static final String TELEMETRY_STATUS = "telemetry_status";
    }

    // We should call this at the application initial stage. Instead,
    // it would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void init(Context aContext) {
        // When initializing the telemetry library it will make sure that all directories exist and
        // are readable/writable.
        final StrictMode.ThreadPolicy threadPolicy = StrictMode.allowThreadDiskWrites();
        try {
            final Resources resources = aContext.getResources();
            final boolean telemetryEnabled = SettingsStore.getInstance(aContext).isTelemetryEnabled();
            final TelemetryConfiguration configuration = new TelemetryConfiguration(aContext)
                    .setServerEndpoint("https://incoming.telemetry.mozilla.org")
                    .setAppName(APP_NAME + "_" + (DeviceType.isOculusBuild() ? "oculusvr" : BuildConfig.FLAVOR_platform))
                    .setUpdateChannel(BuildConfig.BUILD_TYPE)
                    .setPreferencesImportantForTelemetry(resources.getString(R.string.settings_key_locale))
                    .setCollectionEnabled(telemetryEnabled)
                    .setUploadEnabled(telemetryEnabled)
                    // We need to set this to 1 as we want the telemetry opt-in/out ping always to be sent and the minimum is 3 by default.
                    .setMinimumEventsForUpload(1)
                    .setBuildId(String.valueOf(BuildConfig.VERSION_CODE));

            final JSONPingSerializer serializer = new JSONPingSerializer();
            final FileTelemetryStorage storage = new FileTelemetryStorage(configuration, serializer);
            TelemetryScheduler scheduler;
            if (DeviceType.isOculus6DOFBuild()) {
                scheduler = new FxRTelemetryScheduler();
            } else {
                scheduler = new JobSchedulerTelemetryScheduler();
            }
            final TelemetryClient client = new TelemetryClient(new HttpURLConnectionClient());

            TelemetryHolder.set(new Telemetry(configuration, storage, client, scheduler)
                    .addPingBuilder(new TelemetryCorePingBuilder(configuration))
                    .addPingBuilder(new TelemetryMobileEventPingBuilder(configuration)));

            // Check if the Telemetry status has ever been saved (enabled/disabled)
            boolean saved = SettingsStore.getInstance(aContext).telemetryStatusSaved();
            // Check if we have already sent the previous status event
            boolean sent = SettingsStore.getInstance(aContext).isTelemetryPingUpdateSent();
            // If the Telemetry status has been changed but that ping has not been sent, we send it now
            // This should only been true for versions of the app prior to implementing the Telemetry status ping
            // We only send the status ping if it was disabled
            if (saved && !sent && !telemetryEnabled) {
                telemetryStatus(false);
                SettingsStore.getInstance(aContext).setTelemetryPingUpdateSent(true);
            }

        } finally {
            StrictMode.setThreadPolicy(threadPolicy);
        }

        sessionStartTime = SystemClock.elapsedRealtime();
    }

    @UiThread
    public static void start() {
        // Call Telemetry.scheduleUpload() early.
        // See https://github.com/MozillaReality/FirefoxReality/issues/1353
        TelemetryHolder.get()
                .queuePing(TelemetryCorePingBuilder.TYPE)
                .queuePing(TelemetryMobileEventPingBuilder.TYPE)
                .scheduleUpload();

        TelemetryHolder.get().recordSessionStart();
        TelemetryEvent.create(Category.ACTION, Method.FOREGROUND, Object.APP).queue();
    }

    @UiThread
    public static void stop() {
        queueHistogram();
        queueMultiWindowEvents();

        TelemetryEvent.create(Category.ACTION, Method.BACKGROUND, Object.APP).queue();
        TelemetryHolder.get().recordSessionEnd();

        TelemetryHolder.get()
                .queuePing(TelemetryCorePingBuilder.TYPE)
                .queuePing(TelemetryMobileEventPingBuilder.TYPE)
                .scheduleUpload();
    }

    @UiThread
    public static void urlBarEvent(boolean aIsUrl) {
        if (aIsUrl) {
            TelemetryWrapper.browseEvent();
        } else {
            TelemetryWrapper.searchEnterEvent();
        }
    }

    @UiThread
    public static void voiceInputEvent() {
        Telemetry telemetry = TelemetryHolder.get();
        TelemetryEvent.create(Category.ACTION, Method.VOICE_QUERY, Object.VOICE_INPUT).queue();

        String searchEngine = getDefaultSearchEngineIdentifierForTelemetry(telemetry.getConfiguration().getContext());
        telemetry.recordSearch(SearchesMeasurement.LOCATION_ACTIONBAR, searchEngine);
    }

    private static String getDefaultSearchEngineIdentifierForTelemetry(Context aContext) {
        return SearchEngineWrapper.get(aContext).getResourceURL();
    }

    private static void queueHistogram() {
        // Upload loading time histogram
        TelemetryEvent loadingHistogramEvent = TelemetryEvent.create(Category.HISTOGRAM, Method.FOREGROUND, Object.BROWSER);
        for (int bucketIndex = 0; bucketIndex < loadingTimeHistogram.length; ++bucketIndex) {
            loadingHistogramEvent.extra(Integer.toString(bucketIndex * LOADING_BUCKET_SIZE_MS),
                    Integer.toString(loadingTimeHistogram[bucketIndex]));
        }
        loadingHistogramEvent.queue();

        // Clear loading histogram array after queueing it
        loadingTimeHistogram = new int[HISTOGRAM_SIZE];

        // Upload immersive time histogram
        TelemetryEvent immersiveHistogramEvent = TelemetryEvent.create(Category.HISTOGRAM, Method.IMMERSIVE_MODE, Object.BROWSER);
        for (int bucketIndex = 0; bucketIndex < immersiveHistogram.length; ++bucketIndex) {
            immersiveHistogramEvent.extra(Integer.toString(bucketIndex * IMMERSIVE_BUCKET_SIZE_MS),
                    Integer.toString(immersiveHistogram[bucketIndex]));
        }
        immersiveHistogramEvent.queue();

        // Clear loading histogram array after queueing it
        immersiveHistogram = new int[HISTOGRAM_SIZE];

        // We only upload the domain and URI counts to the probes without including
        // users' URI info.
        TelemetryEvent.create(Category.ACTION, Method.OPEN, Object.BROWSER).extra(
                Extra.UNIQUE_DOMAINS_COUNT,
                Integer.toString(domainMap.size())
        ).queue();
        domainMap.clear();

        TelemetryEvent.create(Category.ACTION, Method.OPEN, Object.BROWSER).extra(
                Extra.TOTAL_URI_COUNT,
                Integer.toString(numUri)
        ).queue();
        numUri = 0;

    }

    private static void searchEnterEvent() {
        Telemetry telemetry = TelemetryHolder.get();
        TelemetryEvent.create(Category.ACTION, Method.TYPE_QUERY, Object.SEARCH_BAR).queue();

        String searchEngine = getDefaultSearchEngineIdentifierForTelemetry(telemetry.getConfiguration().getContext());
        telemetry.recordSearch(SearchesMeasurement.LOCATION_ACTIONBAR, searchEngine);
    }

    private static void browseEvent() {
        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.TYPE_URL, Object.SEARCH_BAR);

        // TODO: Working on autocomplete result.
        event.queue();
    }

    @UiThread
    public static void startPageLoadTime() {
        startLoadPageTime = SystemClock.elapsedRealtime();
    }

    @UiThread
    public static void uploadPageLoadToHistogram(String uri) {
        if (startLoadPageTime == 0) {
            return;
        }

        if (uri == null) {
            return;
        }

        try {
            URI uriLink = URI.create(uri);
            if (uriLink.getHost() == null) {
                return;
            }

            domainMap.add(UrlUtils.stripCommonSubdomains(uriLink.getHost()));
            numUri++;

            long elapsedLoad = SystemClock.elapsedRealtime() - startLoadPageTime;
            if (elapsedLoad < MIN_LOAD_TIME) {
                return;
            }

            int histogramLoadIndex = toIntExact(elapsedLoad / LOADING_BUCKET_SIZE_MS);
            Log.d(LOGTAG, "Sent load to histogram");

            if (histogramLoadIndex > (HISTOGRAM_SIZE - 2)) {
                histogramLoadIndex = HISTOGRAM_SIZE - 1;
                Log.e(LOGTAG, "the loading histogram size is overflow.");
            } else if (histogramLoadIndex < HISTOGRAM_MIN_INDEX) {
                histogramLoadIndex = HISTOGRAM_MIN_INDEX;
            }

            loadingTimeHistogram[histogramLoadIndex]++;

        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "Invalid URL", e);
        }
    }

    @UiThread
    public static void startImmersive() {
        startImmersiveTime = SystemClock.elapsedRealtime();
    }

    @UiThread
    public static void uploadImmersiveToHistogram() {
        if (startImmersiveTime == 0) {
            return;
        }

        long elapsedImmersive = SystemClock.elapsedRealtime() - startImmersiveTime;
        if (elapsedImmersive < MIN_IMMERSIVE_TIME) {
            return;
        }

        int histogramImmersiveIndex = toIntExact(elapsedImmersive / IMMERSIVE_BUCKET_SIZE_MS);
        Log.i(LOGTAG, "Send immersive time spent to histogram.");

        if (histogramImmersiveIndex > (HISTOGRAM_SIZE - 2)) {
            histogramImmersiveIndex = HISTOGRAM_SIZE - 1;
            Log.e(LOGTAG, "the immersive histogram size is overflow.");
        } else if (histogramImmersiveIndex < HISTOGRAM_MIN_INDEX) {
            histogramImmersiveIndex = HISTOGRAM_MIN_INDEX;
        }

        immersiveHistogram[histogramImmersiveIndex]++;
    }

    /**
     * Helper method for queuing histograms. This will transform the raw histogram into
     * a Telemetry historam event and queue it for future delivery.
     * @param histogram The histogram to be queued
     * @param method The TelemetryEvent method String
     * @param object The TelemetryEvent object String
     */
    private static void queueHistogram(@NonNull TelemetryHistogram histogram, @NonNull String method, @NonNull String object) {
        TelemetryEvent event = TelemetryEvent.create(Category.HISTOGRAM, method, object);
        int[] hist = histogram.getHistogram();
        for (int bucketIndex = 0; bucketIndex < hist.length; ++bucketIndex) {
            event.extra(
                    Integer.toString(bucketIndex * histogram.getBinSize()),
                    Integer.toString(hist[bucketIndex]));
            Log.d(LOGTAG, "\tHistogram bucket: [" +
                    "" + bucketIndex * histogram.getBinSize() +
                    ", " + hist[bucketIndex] + "]");
        }
        event.queue();
    }

    // Multi-window related events

    public static void queueMultiWindowEvents() {
        // Queue windows lifetime histogram
        queueWindowsLifetimeHistogram();

        // Queue Windows moves freq during the session
        queueWindowsMovesCountEvent();

        // Queue Windows resizes freq during the session
        queueWindowsResizesCountEvent();

        // Queue Windows active time.
        queueActiveWindowTimeEvent();

        // Queue Windows active weight.
        queueOpenWindowsWeightEvent();

        // Queue open Windows time pct
        queueOpenWindowsTimeEvent();
    }

    public static void openWindowEvent(int windowId) {
        windowLifetime.put(windowId, SystemClock.elapsedRealtime());
    }

    public static void closeWindowEvent(int windowId) {
        windowsLifetimeHistogram.addData(SystemClock.elapsedRealtime() - windowLifetime.get(windowId));
        windowLifetime.remove(windowId);
    }

    public static void windowsMoveEvent() {
        windowsMovesCount++;

        Log.d(LOGTAG, "Windows moves: " + windowsMovesCount);
    }

    public static void windowsResizeEvent() {
        windowsResizesCount++;

        Log.d(LOGTAG, "Windows resizes: " + windowsResizesCount);
    }

    public static void activePlacementEvent(int from, boolean active) {
        if (active) {
            activePlacementStartTime[from] = SystemClock.elapsedRealtime();
        } else {
            if (activePlacementStartTime[from] != 0) {
                activePlacementTime[from] += SystemClock.elapsedRealtime() - activePlacementStartTime[from];
                activePlacementStartTime[from] = 0;
            }
        }

        Log.d(LOGTAG, "Placements times:");
        Log.d(LOGTAG, "\tFRONT: " + activePlacementTime[WindowPlacement.FRONT.getValue()]);
        Log.d(LOGTAG, "\tLEFT: " + activePlacementTime[WindowPlacement.LEFT.getValue()]);
        Log.d(LOGTAG, "\tRIGHT: " + activePlacementTime[WindowPlacement.RIGHT.getValue()]);
    }

    public static void openWindowsEvent(int from, int to, boolean isPrivate) {
        if (isPrivate) {
            if (from > 0) {
                openPrivateWindowsTime[from-1] += SystemClock.elapsedRealtime() - openPrivateWindowsStartTime[from-1];
                openPrivateWindowsStartTime[from-1] = 0;
            }

            if (to > 0) {
                openPrivateWindows[to-1]++;
                openPrivateWindowsStartTime[to-1] = SystemClock.elapsedRealtime();
            }

            Log.d(LOGTAG, "Placements times (private):");
            Log.d(LOGTAG, "\tONE: " + openPrivateWindowsTime[WindowPlacement.FRONT.getValue()]);
            Log.d(LOGTAG, "\tTWO: " + openPrivateWindowsTime[WindowPlacement.LEFT.getValue()]);
            Log.d(LOGTAG, "\tTHREE: " + openPrivateWindowsTime[WindowPlacement.RIGHT.getValue()]);

            Log.d(LOGTAG, "Open Windows Count (private):");
            Log.d(LOGTAG, "\tFRONT: " + openPrivateWindows[WindowPlacement.FRONT.getValue()]);
            Log.d(LOGTAG, "\tLEFT: " + openPrivateWindows[WindowPlacement.LEFT.getValue()]);
            Log.d(LOGTAG, "\tRIGHT: " + openPrivateWindows[WindowPlacement.RIGHT.getValue()]);

        } else {
            if (from > 0) {
                openWindowsTime[from-1] += SystemClock.elapsedRealtime() - openWindowsStartTime[from-1];
                openWindowsStartTime[from-1] = 0;
            }

            if (to > 0) {
                openWindows[to-1]++;
                openWindowsStartTime[to-1] = SystemClock.elapsedRealtime();
            }

            Log.d(LOGTAG, "Placements times:");
            Log.d(LOGTAG, "\tONE: " + openWindowsTime[WindowPlacement.FRONT.getValue()]);
            Log.d(LOGTAG, "\tTWO: " + openWindowsTime[WindowPlacement.LEFT.getValue()]);
            Log.d(LOGTAG, "\tTHREE: " + openWindowsTime[WindowPlacement.RIGHT.getValue()]);

            Log.d(LOGTAG, "Open Windows Count:");
            Log.d(LOGTAG, "\tFRONT: " + openWindows[WindowPlacement.FRONT.getValue()]);
            Log.d(LOGTAG, "\tLEFT: " + openWindows[WindowPlacement.LEFT.getValue()]);
            Log.d(LOGTAG, "\tRIGHT: " + openWindows[WindowPlacement.RIGHT.getValue()]);
        }
    }

    private static void queueWindowsLifetimeHistogram() {
        for (Map.Entry<Integer, Long> entry : windowLifetime.entrySet()) {
            windowsLifetimeHistogram.addData(SystemClock.elapsedRealtime() - entry.getValue());
        }

        Log.d(LOGTAG, "[Queue] Windows Lifetime Histogram:");
        queueHistogram(windowsLifetimeHistogram, Method.WINDOW_LIFETIME, Object.WINDOW);
        windowsLifetimeHistogram = new TelemetryHistogram(HISTOGRAM_SIZE, MULTI_WINDOW_BIN_SIZE_MS, 0);

        for(Map.Entry<Integer, Long> entry : windowLifetime.entrySet()) {
            windowLifetime.put(entry.getKey(), SystemClock.elapsedRealtime());
        }
    }

    private static void queueWindowsMovesCountEvent() {
        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.WINDOWS_MOVES_FREQ, Object.WINDOW);
        event.extra(Extra.WINDOW_MOVES_COUNT, Integer.toString(windowsMovesCount));
        event.queue();

        Log.d(LOGTAG, "[Queue] Windows Moves per session: " + windowsMovesCount);

        windowsMovesCount = 0;
    }

    private static void queueWindowsResizesCountEvent() {
        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.WINDOWS_RESIZE_FREQ, Object.WINDOW);
        event.extra(Extra.WINDOW_RESIZE_COUNT, Integer.toString(windowsResizesCount));
        event.queue();

        Log.d(LOGTAG, "[Queue] Windows Resizes per session: " + windowsResizesCount);

        windowsResizesCount = 0;
    }

    private static void queueActiveWindowTimeEvent() {
        for (int index = 0; index< MAX_WINDOWS; index++) {
            if (activePlacementStartTime[index] != 0) {
                activePlacementTime[index] += SystemClock.elapsedRealtime() - activePlacementStartTime[index];
                activePlacementStartTime[index] = SystemClock.elapsedRealtime();
            }
        }

        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.PLACEMENTS_ACTIVE_TIME, Object.WINDOW);
        event.extra(Extra.LEFT_WINDOW_ACTIVE_TIME, String.valueOf(activePlacementTime[WindowPlacement.LEFT.getValue()]));
        event.extra(Extra.FRONT_WINDOW_ACTIVE_TIME, String.valueOf(activePlacementTime[WindowPlacement.FRONT.getValue()]));
        event.extra(Extra.RIGHT_WINDOW_ACTIVE_TIME, String.valueOf(activePlacementTime[WindowPlacement.RIGHT.getValue()]));
        event.queue();

        Log.d(LOGTAG, "[Queue] Placements Active time total:");
        Log.d(LOGTAG, "\tFRONT: " + activePlacementTime[WindowPlacement.FRONT.getValue()]);
        Log.d(LOGTAG, "\tLEFT: " + activePlacementTime[WindowPlacement.LEFT.getValue()]);
        Log.d(LOGTAG, "\tRIGHT: " + activePlacementTime[WindowPlacement.RIGHT.getValue()]);

        for (int index = 0; index< MAX_WINDOWS; index++) {
            activePlacementTime[index] = 0;
        }
    }

    public static void resetOpenedWindowsCount(int number, boolean isPrivate) {
        if (isPrivate) {
            for (int i=0; i<openPrivateWindows.length; i++) {
                openPrivateWindows[i] = 0;
            }
            if (number > 0) {
                openPrivateWindows[number-1] = 1;
            }

        } else {
            for (int i=0; i<openWindows.length; i++) {
                openWindows[i] = 0;
            }

            if (number > 0) {
                openWindows[number-1] = 1;
            }
        }
    }

    private static void queueOpenWindowsWeightEvent() {
        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.WINDOWS_OPEN_W, Object.WINDOW);
        event.extra(Extra.ONE_WINDOWS_OPENED, String.valueOf(openWindows[0]));
        event.extra(Extra.TWO_WINDOWS_OPENED, String.valueOf(openWindows[1]));
        event.extra(Extra.THREE_WINDOWS_OPENED, String.valueOf(openWindows[2]));
        event.extra(Extra.ONE_PRIVATE_WINDOWS_OPENED, String.valueOf(openPrivateWindows[0]));
        event.extra(Extra.TWO_PRIVATE_WINDOWS_OPENED, String.valueOf(openPrivateWindows[1]));
        event.extra(Extra.THREE_PRIVATE_WINDOWS_OPENED, String.valueOf(openPrivateWindows[2]));

        event.queue();

        Log.d(LOGTAG, "[Queue] Open Windows Number:");
        Log.d(LOGTAG, "\tRegular 1: " + openWindows[0]);
        Log.d(LOGTAG, "\tRegular 2: " + openWindows[1]);
        Log.d(LOGTAG, "\tRegular 3: " + openWindows[2]);

        Log.d(LOGTAG, "\tPrivate 1: " + openPrivateWindows[0]);
        Log.d(LOGTAG, "\tPrivate 2: " + openPrivateWindows[1]);
        Log.d(LOGTAG, "\tPrivate 3: " + openPrivateWindows[2]);

        for (int index = 0; index< MAX_WINDOWS; index++) {
            if (openWindows[index] != 0) {
                openWindows[index] = 1;
            }
            if (openPrivateWindows[index] != 0) {
                openPrivateWindows[index] = 1;
            }
        }
    }

    private static void queueOpenWindowsTimeEvent() {
        for (int index = 0; index< MAX_WINDOWS; index++) {
            if (openWindowsStartTime[index] != 0) {
                openWindowsTime[index] += SystemClock.elapsedRealtime() - openWindowsStartTime[index];
                openWindowsStartTime[index] = SystemClock.elapsedRealtime();
            }

            if (openPrivateWindowsStartTime[index] != 0) {
                openPrivateWindowsTime[index] += SystemClock.elapsedRealtime() - openPrivateWindowsStartTime[index];
                openPrivateWindowsStartTime[index] = SystemClock.elapsedRealtime();
            }
        }

        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.OPEN_WINDOWS_TIME, Object.WINDOW);
        event.extra(Extra.ONE_OPEN_WINDOWS_TIME, String.valueOf(openWindowsTime[0]+openPrivateWindowsTime[0]));
        event.extra(Extra.TWO_OPEN_WINDOWS_TIME, String.valueOf(openWindowsTime[1]+openPrivateWindowsTime[1]));
        event.extra(Extra.THREE_OPEN_WINDOWS_TIME, String.valueOf(openWindowsTime[2]+openPrivateWindowsTime[2]));
        event.queue();

        Log.d(LOGTAG, "[Queue] Open Windows time:");
        Log.d(LOGTAG, "\tONE: " + String.valueOf(openWindowsTime[0] + openPrivateWindowsTime[0]));
        Log.d(LOGTAG, "\tTWO: " + String.valueOf(openWindowsTime[1] + openPrivateWindowsTime[1]));
        Log.d(LOGTAG, "\tTHREE: " + String.valueOf(openWindowsTime[2] + openPrivateWindowsTime[2]));

        for (int index = 0; index< MAX_WINDOWS; index++) {
            openWindowsTime[index] = 0;
            openPrivateWindowsTime[index] = 0;
        }
    }

    public static void telemetryStatus(boolean status) {
        TelemetryEvent event = TelemetryEvent.create(Category.ACTION, Method.TELEMETRY_STATUS, Object.APP);
        event.extra(Extra.TELEMETRY_STATUS, String.valueOf(status));
        event.queue();

        // We flush immediately as the Telemetry is going to be turned off in case of opting-out
        // and we want to make sure that this ping is delivered.
        TelemetryHolder.get()
                .queuePing(TelemetryCorePingBuilder.TYPE)
                .queuePing(TelemetryMobileEventPingBuilder.TYPE)
                .scheduleUpload();
    }

}