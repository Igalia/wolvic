package org.mozilla.vrbrowser.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.LocaleUtils;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

public class SettingsStore {

    private static final String LOGTAG = SystemUtils.createLogtag(SettingsStore.class);

    private static SettingsStore mSettingsInstance;

    public static synchronized @NonNull
    SettingsStore getInstance(final @NonNull Context aContext) {
        if (mSettingsInstance == null) {
            mSettingsInstance = new SettingsStore(aContext);
        }

        return mSettingsInstance;
    }

    private Context mContext;
    private SharedPreferences mPrefs;

    // Developer options default values
    public final static boolean REMOTE_DEBUGGING_DEFAULT = false;
    public final static boolean CONSOLE_LOGS_DEFAULT = false;
    public final static boolean ENV_OVERRIDE_DEFAULT = false;
    public final static boolean MULTIPROCESS_DEFAULT = false;
    public final static boolean PERFORMANCE_MONITOR_DEFAULT = true;
    public final static boolean DRM_PLAYBACK_DEFAULT = false;
    public final static boolean TRACKING_DEFAULT = true;
    public final static boolean NOTIFICATIONS_DEFAULT = true;
    public final static boolean SPEECH_DATA_COLLECTION_DEFAULT = false;
    public final static boolean SPEECH_DATA_COLLECTION_REVIEWED_DEFAULT = false;
    public final static boolean SERVO_DEFAULT = false;
    public final static int UA_MODE_DEFAULT = GeckoSessionSettings.USER_AGENT_MODE_VR;
    public final static int INPUT_MODE_DEFAULT = 1;
    public final static float DISPLAY_DENSITY_DEFAULT = 1.0f;
    public final static int WINDOW_WIDTH_DEFAULT = 800;
    public final static int WINDOW_HEIGHT_DEFAULT = 450;
    public final static int DISPLAY_DPI_DEFAULT = 96;
    public final static int MAX_WINDOW_WIDTH_DEFAULT = 1200;
    public final static int MAX_WINDOW_HEIGHT_DEFAULT = 1200;
    public final static int POINTER_COLOR_DEFAULT_DEFAULT = Color.parseColor("#FFFFFF");
    public final static int SCROLL_DIRECTION_DEFAULT = 0;
    public final static String ENV_DEFAULT = "offworld";
    public final static int MSAA_DEFAULT_LEVEL = 1;
    public final static boolean AUDIO_ENABLED = false;
    public final static float CYLINDER_DENSITY_ENABLED_DEFAULT = 4680.0f;
    public final static int FOVEATED_APP_DEFAULT_LEVEL = 0;
    public final static int FOVEATED_WEBVR_DEFAULT_LEVEL = 0;
    private final static long CRASH_RESTART_DELTA = 2000;
    public final static boolean AUTOPLAY_ENABLED = false;
    public final static boolean DEBUG_LOGGING_DEFAULT = false;
    public final static boolean POP_UPS_BLOCKING_DEFAULT = true;

    // Enable telemetry by default (opt-out).
    public final static boolean CRASH_REPORTING_DEFAULT = false;
    public final static boolean TELEMETRY_DEFAULT = true;

    private int mCachedScrollDirection = -1;

    public SettingsStore(Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    public boolean isCrashReportingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_crash), CRASH_REPORTING_DEFAULT);
    }

    public void setCrashReportingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_crash), isEnabled);
        editor.commit();
    }

    public boolean isTelemetryEnabled() {
        // The first access to shared preferences will require a disk read.
        final StrictMode.ThreadPolicy threadPolicy = StrictMode.allowThreadDiskReads();
        try {
            return mPrefs.getBoolean(
                    mContext.getString(R.string.settings_key_telemetry), TELEMETRY_DEFAULT);
        } finally {
            StrictMode.setThreadPolicy(threadPolicy);
        }
    }

    public void setTelemetryEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_telemetry), isEnabled);
        editor.commit();

        // If the state of Telemetry is not the same, we reinitialize it.
        final boolean hasEnabled = isTelemetryEnabled();
        if (hasEnabled != isEnabled) {
            TelemetryWrapper.init(mContext);
        }

        TelemetryHolder.get().getConfiguration().setUploadEnabled(isEnabled);
        TelemetryHolder.get().getConfiguration().setCollectionEnabled(isEnabled);
    }

    public void setGeolocationData(String aGeolocationData) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_geolocation_data), aGeolocationData);
        editor.commit();
    }

    public String getGeolocationData() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_geolocation_data), "");
    }

    public boolean isRemoteDebuggingEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_remote_debugging), REMOTE_DEBUGGING_DEFAULT);
    }

    public void setRemoteDebuggingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_remote_debugging), isEnabled);
        editor.commit();
    }

    public boolean isConsoleLogsEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_console_logs), CONSOLE_LOGS_DEFAULT);
    }

    public void setConsoleLogsEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_console_logs), isEnabled);
        editor.commit();
    }

    public boolean isDrmContentPlaybackEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_drm_playback), DRM_PLAYBACK_DEFAULT);
    }

    public void setDrmContentPlaybackEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_drm_playback), isEnabled);
        editor.commit();
    }

    public boolean isTrackingProtectionEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_tracking_protection), TRACKING_DEFAULT);
    }

    public void setTrackingProtectionEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_tracking_protection), isEnabled);
        editor.commit();
    }

    public boolean isEnvironmentOverrideEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_environment_override), ENV_OVERRIDE_DEFAULT);
    }

    public void setEnvironmentOverrideEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_environment_override), isEnabled);
        editor.commit();
    }


    public boolean isMultiprocessEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_multiprocess), MULTIPROCESS_DEFAULT);
    }

    public void setMultiprocessEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_multiprocess), isEnabled);
        editor.commit();
    }

    public boolean isPerformanceMonitorEnabled() {
        // Disabling Performance Monitor until it can properly handle multi-window
        return false; // mPrefs.getBoolean(mContext.getString(R.string.settings_key_performance_monitor), PERFORMANCE_MONITOR_DEFAULT);
    }

    public void setPerformanceMonitorEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_performance_monitor), isEnabled);
        editor.commit();
    }

    public boolean isServoEnabled() {
        return isServoAvailable() && mPrefs.getBoolean(mContext.getString(R.string.settings_key_servo), SERVO_DEFAULT);
    }

    public void setServoEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_servo), isEnabled);
        editor.commit();
    }

    public int getUaMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_user_agent_version), UA_MODE_DEFAULT);
    }

    public void setUaMode(int mode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_user_agent_version), mode);
        editor.commit();
    }

    public int getInputMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_input_mode), INPUT_MODE_DEFAULT);
    }

    public void setInputMode(int aTouchMode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_input_mode), aTouchMode);
        editor.commit();
    }

    public String getHomepage() {
        return mPrefs.getString(
                mContext.getString(R.string.settings_key_homepage),
                mContext.getString(R.string.homepage_url));
    }

    public void setHomepage(String aHomepage) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_homepage), aHomepage);
        editor.commit();
    }

    public float getDisplayDensity() {
        return mPrefs.getFloat(
                mContext.getString(R.string.settings_key_display_density), DISPLAY_DENSITY_DEFAULT);
    }

    public void setDisplayDensity(float aDensity) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_display_density), aDensity);
        editor.commit();
    }

    public int getWindowWidth() {
        return WINDOW_WIDTH_DEFAULT;
    }

    public int getWindowHeight() {
        return WINDOW_HEIGHT_DEFAULT;
    }

    public float getWindowAspect() {
        return (float)getWindowWidth() / (float)getWindowHeight();
    }

    public int getDisplayDpi() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_display_dpi), DISPLAY_DPI_DEFAULT);
    }

    public void setDisplayDpi(int aDpi) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_display_dpi), aDpi);
        editor.commit();
    }

    public int getMaxWindowWidth() {
        return MAX_WINDOW_WIDTH_DEFAULT;
    }

    public int getMaxWindowHeight() {
        return MAX_WINDOW_HEIGHT_DEFAULT;
    }

    public String getEnvironment() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_env), ENV_DEFAULT);
    }

    public void setEnvironment(String aEnv) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_env), aEnv);
        editor.commit();
    }

    public int getPointerColor() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_pointer_color), POINTER_COLOR_DEFAULT_DEFAULT);
    }

    public void setPointerColor(int color) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_pointer_color), color);
        editor.commit();
    }

    public int getScrollDirection() {
        if (mCachedScrollDirection < 0) {
            mCachedScrollDirection = mPrefs.getInt(mContext.getString(R.string.settings_key_scroll_direction), SCROLL_DIRECTION_DEFAULT);
        }
        return mCachedScrollDirection;
    }

    public void setScrollDirection(int aScrollDirection) {
        mCachedScrollDirection = aScrollDirection;
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_scroll_direction), aScrollDirection);
        editor.commit();
    }


    public int getMSAALevel() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_msaa), MSAA_DEFAULT_LEVEL);
    }

    public void setMSAALevel(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_msaa), level);
        editor.commit();
    }

    public boolean getLayersEnabled() {
        if (DeviceType.isOculusBuild()) {
            return true;
        }
        return false;
    }

    public int getTransparentBorderWidth() {
        return 1;
    }

    public boolean isAudioEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_audio), AUDIO_ENABLED);
    }

    public void setAudioEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_audio), isEnabled);
        editor.commit();
    }

    public String getVoiceSearchLocale() {
        String language = mPrefs.getString(
                mContext.getString(R.string.settings_key_voice_search_language), null);
        if (language == null) {
            return LocaleUtils.getDefaultVoiceSearchLocale(mContext);
        }
        return language;
    }

    public void setVoiceSearchLocale(String language) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_voice_search_language), language);
        editor.commit();
    }

    public String getDisplayLocale() {
        String language = mPrefs.getString(
                mContext.getString(R.string.settings_key_display_language), null);
        if (language == null) {
            return LocaleUtils.getDefaultDisplayLocale(mContext);
        }
        return language;
    }

    public void setDisplayLocale(String language) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_display_language), language);
        editor.commit();
    }

    public ArrayList<String> getContentLocales() {
        ArrayList<String> result = new ArrayList<>();

        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_content_languages),
                null);

        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i=0; i<jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }

            return result;

        } catch (Exception e) {
            return null;
        }
    }

    public void setContentLocales(List<String> languages) {
        JSONArray json = new JSONArray(languages);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_content_languages), json.toString());
        editor.commit();
    }

    public float getCylinderDensity() {
        return mPrefs.getFloat(mContext.getString(R.string.settings_key_cylinder_density),  0);
    }

    public void setCylinderDensity(float aDensity) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_cylinder_density), aDensity);
        editor.commit();
    }

    public int getFoveatedLevelApp() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_foveated_app), FOVEATED_APP_DEFAULT_LEVEL);
    }

    public int getFoveatedLevelWebVR() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_foveated_webvr), FOVEATED_WEBVR_DEFAULT_LEVEL);
    }

    public void setFoveatedLevelApp(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_foveated_app), level);
        editor.commit();
    }

    public void setFoveatedLevelWebVR(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_foveated_webvr), level);
        editor.commit();
    }

    public void setSelectedKeyboard(Locale aLocale) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_keyboard_locale), aLocale.toLanguageTag());
        editor.commit();
    }

    public Locale getKeyboardLocale() {
        String value = mPrefs.getString(mContext.getString(R.string.settings_key_keyboard_locale), null);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return Locale.forLanguageTag(value);
    }

    public synchronized long getCrashRestartCount() {
        long count = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        if (count > 0) {
            final long timestamp = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), -1);
            if (System.currentTimeMillis() - timestamp > CRASH_RESTART_DELTA) {
                count = 0;
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), count);
                editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), -1);
                editor.commit();
            }
        }
        return count;
    }

    public synchronized void incrementCrashRestartCount() {
        SharedPreferences.Editor editor = mPrefs.edit();
        long count = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        count++;
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), count);
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), System.currentTimeMillis());
        editor.commit();
    }

    public synchronized void resetCrashRestartCount() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        editor.commit();
    }

    public boolean isSpeechDataCollectionEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_speech_data_collection), SPEECH_DATA_COLLECTION_DEFAULT);
    }

    public void setSpeechDataCollectionEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_speech_data_collection), isEnabled);
        editor.commit();
    }

    public boolean isNotificationsEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_notifications), NOTIFICATIONS_DEFAULT);
    }

    public void setNotificationsEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_notifications), isEnabled);
        editor.commit();
    }

    public boolean isSpeechDataCollectionReviewed() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_speech_data_collection_reviewed), SPEECH_DATA_COLLECTION_REVIEWED_DEFAULT);
    }

    public void setSpeechDataCollectionReviewed(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_speech_data_collection_reviewed), isEnabled);
        editor.commit();
    }

    public boolean isDebugLogginEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_debug_logging), DEBUG_LOGGING_DEFAULT);
    }

    public void setDebugLoggingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_debug_logging), isEnabled);
        editor.commit();
    }

    public boolean isAutoplayEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autoplay), AUTOPLAY_ENABLED);
    }

    public void setAutoplayEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autoplay), isEnabled);
        editor.commit();
    }

    public void setPid(int aPid) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_pid), aPid);
        editor.commit();
    }

    public int getPid() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_pid), 0);
    }

    public boolean isPopUpsBlockingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_pop_up_blocking), POP_UPS_BLOCKING_DEFAULT);
    }

    public void setPopUpsBlockingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_pop_up_blocking), isEnabled);
        editor.commit();
    }
}

