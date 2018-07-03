package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.content.res.Resources;
import android.os.StrictMode;
import android.support.annotation.UiThread;
import org.mozilla.telemetry.Telemetry;
import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.telemetry.config.TelemetryConfiguration;
import org.mozilla.telemetry.event.TelemetryEvent;
import org.mozilla.telemetry.net.HttpURLConnectionTelemetryClient;
import org.mozilla.telemetry.ping.TelemetryCorePingBuilder;
import org.mozilla.telemetry.ping.TelemetryMobileEventPingBuilder;
import org.mozilla.telemetry.schedule.jobscheduler.JobSchedulerTelemetryScheduler;
import org.mozilla.telemetry.serialize.JSONPingSerializer;
import org.mozilla.telemetry.storage.FileTelemetryStorage;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.SettingsStore;


public class TelemetryWrapper {
    private final static String APP_NAME = "FirefoxReality";

    private class Category {
        private static final String ACTION = "action";
    }

    private class Method {
        private static final String FOREGROUND = "foreground";
        private static final String BACKGROUND = "background";
    }

    private class Object {
        private static final String APP = "app";
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
    public static void startSession(Context aContext) {
        if (!SettingsStore.getInstance(aContext).isTelemetryEnabled()) {
            return;
        }

        TelemetryHolder.get().recordSessionStart();
        TelemetryEvent.create(Category.ACTION, Method.FOREGROUND, Object.APP).queue();
    }

    @UiThread
    public static void stopSession(Context aContext) {
        if (!SettingsStore.getInstance(aContext).isTelemetryEnabled()) {
            return;
        }

        TelemetryHolder.get().recordSessionEnd();
        TelemetryEvent.create(Category.ACTION, Method.BACKGROUND, Object.APP).queue();
    }

    public static void stopMainActivity(Context aContext) {
        if (!SettingsStore.getInstance(aContext).isTelemetryEnabled()) {
            return;
        }

        TelemetryHolder.get()
                .queuePing(TelemetryCorePingBuilder.TYPE)
                .queuePing(TelemetryMobileEventPingBuilder.TYPE)
                .scheduleUpload();
    }
}

