package org.mozilla.vrbrowser.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.viewmodel.SettingsViewModel;
import org.mozilla.vrbrowser.ui.widgets.menus.library.SortingContextMenuWidget;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.RemoteProperties;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mozilla.components.concept.fetch.Request;
import mozilla.components.concept.fetch.Response;

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

    @IntDef(value = { INTERNAL, EXTERNAL})
    public @interface Storage {}
    public static final int INTERNAL = 0;
    public static final int EXTERNAL = 1;

    private Context mContext;
    private SharedPreferences mPrefs;
    private SettingsViewModel mSettingsViewModel;

    // Developer options default values
    public final static boolean REMOTE_DEBUGGING_DEFAULT = false;
    public final static boolean ENV_OVERRIDE_DEFAULT = false;
    public final static boolean UI_HARDWARE_ACCELERATION_DEFAULT = true;
    public final static boolean UI_HARDWARE_ACCELERATION_DEFAULT_WAVEVR = false;
    public final static boolean PERFORMANCE_MONITOR_DEFAULT = true;
    public final static boolean DRM_PLAYBACK_DEFAULT = false;
    public final static int TRACKING_DEFAULT = ContentBlocking.EtpLevel.DEFAULT;
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
    public final static int MSAA_DEFAULT_LEVEL = 0;
    public final static boolean AUDIO_ENABLED = false;
    public final static float CYLINDER_DENSITY_ENABLED_DEFAULT = 4680.0f;
    private final static long CRASH_RESTART_DELTA = 2000;
    public final static boolean AUTOPLAY_ENABLED = false;
    public final static boolean DEBUG_LOGGING_DEFAULT = BuildConfig.DEBUG;
    public final static boolean POP_UPS_BLOCKING_DEFAULT = true;
    public final static boolean WEBXR_ENABLED_DEFAULT = true;
    public final static boolean TELEMETRY_STATUS_UPDATE_SENT_DEFAULT = false;
    public final static boolean BOOKMARKS_SYNC_DEFAULT = true;
    public final static boolean LOGIN_SYNC_DEFAULT = true;
    public final static boolean HISTORY_SYNC_DEFAULT = true;
    public final static boolean WHATS_NEW_DISPLAYED = false;
    public final static long FXA_LAST_SYNC_NEVER = 0;
    public final static boolean RESTORE_TABS_ENABLED = true;
    public final static boolean BYPASS_CACHE_ON_RELOAD = false;
    public final static int DOWNLOADS_STORAGE_DEFAULT = INTERNAL;
    public final static int DOWNLOADS_SORTING_ORDER_DEFAULT = SortingContextMenuWidget.SORT_DATE_ASC;
    public final static boolean AUTOCOMPLETE_ENABLED = true;
    public final static boolean WEBGL_OUT_OF_PROCESS = false;
    public final static int PREFS_LAST_RESET_VERSION_CODE = 0;
    public final static boolean PASSWORDS_ENCRYPTION_KEY_GENERATED = false;
    public final static boolean AUTOFILL_ENABLED = true;
    public final static boolean LOGIN_AUTOCOMPLETE_ENABLED = true;

    // Enable telemetry by default (opt-out).
    public final static boolean CRASH_REPORTING_DEFAULT = false;
    public final static boolean TELEMETRY_DEFAULT = true;

    private int mCachedScrollDirection = -1;

    private boolean mDisableLayers = false;
    public void setDisableLayers(final boolean aDisableLayers) {
        mDisableLayers = aDisableLayers;
    }

    public SettingsStore(Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    public void initModel(@NonNull Context context) {
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)context,
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) context).getApplication()))
                .get(SettingsViewModel.class);

        // Setup the stored properties until we get updated ones
        String json = mPrefs.getString(mContext.getString(R.string.settings_key_remote_props), null);
        mSettingsViewModel.setProps(json);

        mSettingsViewModel.refresh();

        update();
    }

    /**
     * Synchronizes the remote properties with the settings storage and notifies the model.
     * Any consumer listening to the SettingsViewModel will get notified of the properties updates.
     */
    private void update() {
        ((VRBrowserApplication) mContext.getApplicationContext()).getExecutors().backgroundThread().post(() -> {
            Request request = new Request(
                    BuildConfig.PROPS_ENDPOINT,
                    Request.Method.GET,
                    null,
                    null,
                    null,
                    null,
                    Request.Redirect.FOLLOW,
                    Request.CookiePolicy.INCLUDE,
                    false
            );

            try {
                Response response = EngineProvider.INSTANCE.getDefaultClient(mContext).fetch(request);
                if (response.getStatus() == 200) {
                    String json = response.getBody().string(StandardCharsets.UTF_8);
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString(mContext.getString(R.string.settings_key_remote_props), json);
                    editor.commit();

                    mSettingsViewModel.setProps(json);
                }

            } catch (IOException e) {
                Log.d(LOGTAG, "Remote properties error: " + e.getLocalizedMessage());
            }
        });
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

        // We send after enabling in case of opting-in
        if (isEnabled) {
            GleanMetricsService.start();
        } else {
            GleanMetricsService.stop();
        }

        // Update the status sent flag
        setTelemetryPingUpdateSent(true);
    }

    public boolean telemetryStatusSaved() {
        return mPrefs.contains(mContext.getString(R.string.settings_key_telemetry));
    }

    public boolean isTelemetryPingUpdateSent() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_telemetry_status_update_sent), TELEMETRY_STATUS_UPDATE_SENT_DEFAULT);
    }

    public void setTelemetryPingUpdateSent(boolean isSent) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_telemetry_status_update_sent), isSent);
        editor.commit();
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


    public boolean isDrmContentPlaybackEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_drm_playback), DRM_PLAYBACK_DEFAULT);
    }

    public boolean isDrmContentPlaybackSet() {
        return mPrefs.contains(mContext.getString(R.string.settings_key_drm_playback));
    }

    public void setDrmContentPlaybackEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_drm_playback), isEnabled);
        editor.commit();

        mSettingsViewModel.setIsDrmEnabled(isEnabled);
    }

    public int getTrackingProtectionLevel() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_tracking_protection_level), TRACKING_DEFAULT);
    }

    public void setTrackingProtectionLevel(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_tracking_protection_level), level);
        editor.commit();

        mSettingsViewModel.setIsTrackingProtectionEnabled(level != ContentBlocking.EtpLevel.NONE);
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

    public boolean isUIHardwareAccelerationEnabled() {
        boolean defaultValue = UI_HARDWARE_ACCELERATION_DEFAULT;
        if (DeviceType.isWaveBuild()) {
            defaultValue = UI_HARDWARE_ACCELERATION_DEFAULT_WAVEVR;
        }
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_ui_hardware_acceleration), defaultValue);
    }

    public void setUIHardwareAccelerationEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_ui_hardware_acceleration), isEnabled);
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
        int checkedMode = mode;
        if ((mode != GeckoSessionSettings.USER_AGENT_MODE_VR) && (mode != GeckoSessionSettings.USER_AGENT_MODE_MOBILE)) {
            Log.e(LOGTAG, "User agent mode: " + mode + " is not supported.");
            checkedMode = UA_MODE_DEFAULT;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_user_agent_version), checkedMode);
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
        if (DeviceType.isOculusBuild() && !mDisableLayers) {
            Log.i(LOGTAG, "Layers are enabled");
            return true;
        }
        Log.i(LOGTAG, "Layers are not supported");
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

    public boolean isCurvedModeEnabled() {
        return getCylinderDensity() > 0;
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

    public boolean isDebugLoggingEnabled() {
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

        mSettingsViewModel.setIsPopUpBlockingEnabled(isEnabled);
    }

    public boolean isWebXREnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_webxr), WEBXR_ENABLED_DEFAULT);
    }

    public void setWebXREnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_webxr), isEnabled);
        editor.commit();

        mSettingsViewModel.setIsWebXREnabled(isEnabled);
    }

    public void setWhatsNewDisplayed(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_whats_new_displayed), isEnabled);
        editor.commit();
    }

    public boolean isWhatsNewDisplayed() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_whats_new_displayed), WHATS_NEW_DISPLAYED);
    }

    public void setFxALastSync(@NonNull String email, long timestamp) {
        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_fxa_last_sync),
                new JSONObject().toString());

        try {
            JSONObject jsonObject = new JSONObject(json);
            jsonObject.put(email, timestamp);

            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putString(mContext.getString(R.string.settings_key_fxa_last_sync), jsonObject.toString());
            editor.commit();

        } catch (Exception e) {
            Log.d(LOGTAG, e.getMessage());
        }
    }

    public long getFxALastSync(@NonNull String email) {
        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_fxa_last_sync),
                null);

        try {
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.equals(email)) {
                    return jsonObject.getLong(key);
                }
            }

            return FXA_LAST_SYNC_NEVER;

        } catch (Exception e) {
            return FXA_LAST_SYNC_NEVER;
        }
    }

    public void setRestoreTabsEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_restore_tabs), isEnabled);
        editor.commit();
    }

    public boolean isRestoreTabsEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_restore_tabs), RESTORE_TABS_ENABLED);
    }

    public void setBypassCacheOnReload(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_bypass_cache_on_reload), isEnabled);
        editor.commit();
    }

    public boolean isBypassCacheOnReloadEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_bypass_cache_on_reload), BYPASS_CACHE_ON_RELOAD);
    }

    public void setDownloadsStorage(@Storage int storage) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_downloads_external), storage);
        editor.commit();
    }

    public @Storage int getDownloadsStorage() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_downloads_external), DOWNLOADS_STORAGE_DEFAULT);
    }

    public void setDownloadsSortingOrder(@SortingContextMenuWidget.Order int order) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_downloads_sorting_order), order);
        editor.commit();
    }

    public @Storage int getDownloadsSortingOrder() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_downloads_sorting_order), DOWNLOADS_SORTING_ORDER_DEFAULT);
    }

    public void setRemotePropsVersionName(String versionName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_remote_props_version_name), versionName);
        editor.commit();

        mSettingsViewModel.setPropsVersionName(versionName);
    }

    public String getRemotePropsVersionName() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_remote_props_version_name), "0");
    }

    public void setAutocompleteEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autocomplete), isEnabled);
        editor.commit();
    }

    public boolean isAutocompleteEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autocomplete), AUTOCOMPLETE_ENABLED);
    }

    public void setWebGLOutOfProcess(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_webgl_out_of_process), isEnabled);
        editor.commit();
    }

    public boolean isWebGLOutOfProcess() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_webgl_out_of_process), WEBGL_OUT_OF_PROCESS);
    }

    public int getPrefsLastResetVersionCode() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_prefs_last_reset_version_code), PREFS_LAST_RESET_VERSION_CODE);
    }

    public void setPrefsLastResetVersionCode(int versionCode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_prefs_last_reset_version_code), versionCode);
        editor.commit();
    }

    @Nullable
    public Map<String, RemoteProperties> getRemoteProperties() {
        String json = mPrefs.getString(mContext.getString(R.string.settings_key_remote_props), null);

        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<Map<String, RemoteProperties>>() {}.getType();

        Map<String, RemoteProperties> propertiesMap = null;
        try {
            propertiesMap = gson.fromJson(json, type);

        } catch (Exception ignored) { }

        return propertiesMap;
    }
    
    public void setRemoteProperties(@Nullable String json) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_remote_props), json);
        editor.commit();
    }

    public void recordPasswordsEncryptionKeyGenerated() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_passwords_encryption_key_generated), true);
        editor.commit();
    }

    public boolean isPasswordsEncryptionKeyGenerated() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_passwords_encryption_key_generated), PASSWORDS_ENCRYPTION_KEY_GENERATED);
    }

    public void setAutoFillEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autofill_enabled), isEnabled);
        editor.commit();
    }

    public boolean isAutoFillEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autofill_enabled), AUTOFILL_ENABLED);
    }

    public void setLoginAutocompleteEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_login_autocomplete_enabled), isEnabled);
        editor.commit();
    }

    public boolean isLoginAutocompleteEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_login_autocomplete_enabled), LOGIN_AUTOCOMPLETE_ENABLED);
    }

    public void setLoginSyncEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_login_sync_enabled), isEnabled);
        editor.commit();
    }

    public boolean isLoginSyncEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_login_sync_enabled), LOGIN_SYNC_DEFAULT);
    }

    public void setTabAfterRestore(@Nullable String uri) {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (!StringUtils.isEmpty(uri)) {
            editor.putString(mContext.getString(R.string.settings_key_tab_after_restore), uri);
        } else {
            editor.remove(mContext.getString(R.string.settings_key_tab_after_restore));
        }
        editor.commit();
    }

    public String getTabAfterRestore() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_tab_after_restore), null);
    }

}
