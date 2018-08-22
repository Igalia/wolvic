package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.content.res.Resources;
import android.os.StrictMode;
import android.support.annotation.UiThread;
import org.mozilla.telemetry.Telemetry;
import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.telemetry.config.TelemetryConfiguration;
import org.mozilla.telemetry.event.TelemetryEvent;
import org.mozilla.telemetry.measurement.SearchesMeasurement;
import org.mozilla.telemetry.net.HttpURLConnectionTelemetryClient;
import org.mozilla.telemetry.ping.TelemetryCorePingBuilder;
import org.mozilla.telemetry.ping.TelemetryMobileEventPingBuilder;
import org.mozilla.telemetry.schedule.jobscheduler.JobSchedulerTelemetryScheduler;
import org.mozilla.telemetry.serialize.JSONPingSerializer;
import org.mozilla.telemetry.storage.FileTelemetryStorage;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngine;


public class TelemetryWrapper {
    private final static String APP_NAME = "FirefoxReality";

    private class Category {
        private static final String ACTION = "action";
    }

    private class Method {
        private static final String FOREGROUND = "foreground";
        private static final String BACKGROUND = "background";
        private static final String TYPE_URL = "type_url";
        private static final String TYPE_QUERY = "type_query";
        // TODO: Support "select_query" after providing search suggestion.
        private static final String VOICE_QUERY = "voice_query";

    }

    private class Object {
        private static final String APP = "app";
        private static final String SEARCH_BAR = "search_bar";
        private static final String VOICE_INPUT = "voice_input";
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
                    .setAppName(APP_NAME + "_" + BuildConfig.FLAVOR)
                    .setUpdateChannel(BuildConfig.BUILD_TYPE)
                    .setPreferencesImportantForTelemetry(resources.getString(R.string.settings_key_locale))
                    .setCollectionEnabled(telemetryEnabled)
                    .setUploadEnabled(telemetryEnabled)
                    .setBuildId(String.valueOf(BuildConfig.VERSION_CODE));
            
            final JSONPingSerializer serializer = new JSONPingSerializer();
            final FileTelemetryStorage storage = new FileTelemetryStorage(configuration, serializer);
            final HttpURLConnectionTelemetryClient client = new HttpURLConnectionTelemetryClient();
            final JobSchedulerTelemetryScheduler scheduler = new JobSchedulerTelemetryScheduler();

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
        return SearchEngine.get(aContext).getURLResource();
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
}

