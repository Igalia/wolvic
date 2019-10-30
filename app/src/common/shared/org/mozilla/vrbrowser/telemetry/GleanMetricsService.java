package org.mozilla.vrbrowser.telemetry;

import android.content.Context;
import android.util.Log;

import androidx.annotation.UiThread;

import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.GleanMetrics.Distribution;

import mozilla.components.service.glean.Glean;
import mozilla.components.service.glean.config.Configuration;


public class GleanMetricsService {

    private final static String APP_NAME = "FirefoxReality";
    private static boolean initialized = false;
    private final static String LOGTAG = SystemUtils.createLogtag(GleanMetricsService.class);
    private static Context context = null;

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
        Glean.INSTANCE.initialize(aContext, config);
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

    private static void setStartupMetrics() {
        Distribution.INSTANCE.getChannelName().set(DeviceType.isOculusBuild() ? "oculusvr" : BuildConfig.FLAVOR_platform);
    }
}
