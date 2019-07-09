package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.content.res.Resources;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;

import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;
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
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.net.URI;
import java.util.HashSet;

import androidx.annotation.UiThread;

import static java.lang.Math.toIntExact;


public class TelemetryWrapper {
    private final static String APP_NAME = "FirefoxReality";
    private final static String LOGTAG = "VRB";
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
    }

    private class Object {
        private static final String APP = "app";
        private static final String BROWSER = "browser";
        private static final String SEARCH_BAR = "search_bar";
        private static final String VOICE_INPUT = "voice_input";
    }

    private class Extra {
        private static final String TOTAL_URI_COUNT = "total_uri_count";
        private static final String UNIQUE_DOMAINS_COUNT = "unique_domains_count";
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
                    .setAppName(APP_NAME + "_" + BuildConfig.FLAVOR_platform)
                    .setUpdateChannel(BuildConfig.BUILD_TYPE)
                    .setPreferencesImportantForTelemetry(resources.getString(R.string.settings_key_locale))
                    .setCollectionEnabled(telemetryEnabled)
                    .setUploadEnabled(telemetryEnabled)
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
        } finally {
            StrictMode.setThreadPolicy(threadPolicy);
        }
    }

    @UiThread
    public static void start() {
        TelemetryHolder.get().recordSessionStart();
        TelemetryEvent.create(Category.ACTION, Method.FOREGROUND, Object.APP).queue();
    }

    @UiThread
    public static void stop() {
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

        if (uri == null)
            return;

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
}

