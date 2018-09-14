package org.mozilla.vrbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.DeveloperOptionsWidget;


public class SettingsStore {

    private static final String LOGTAG = "VRB";

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
    public final static DeveloperOptionsWidget.UaMode UA_MODE_DEFAULT = DeveloperOptionsWidget.UaMode.MOBILE;
    public final static DeveloperOptionsWidget.InputMode INPUT_MODE_DEFAULT = DeveloperOptionsWidget.InputMode.TOUCH;
    public final static float DISPLAY_DENSITY_DEFAULT = 1.0f;
    public final static int WINDOW_WIDTH_DEFAULT = 800;
    public final static int WINDOW_HEIGHT_DEFAULT = 450;
    public final static int DISPLAY_DPI_DEFAULT = 96;
    public final static int MAX_WINDOW_WIDTH_DEFAULT = 800;
    public final static int MAX_WINDOW_HEIGHT_DEFAULT = 450;
    public final static int POINTER_COLOR_DEFAULT_DEFAULT = Color.parseColor("#FFFFFF");
    public final static String ENV_DEFAULT = "cave";
    public final static float BROWSER_WORLD_WIDTH_DEFAULT = 4.0f;
    public final static float BROWSER_WORLD_HEIGHT_DEFAULT = 2.25f;

    // Enable telemetry by default (opt-out).
    private final static boolean enableCrashReportingByDefault = false;
    private final static boolean enableTelemetryByDefault = true;

    public SettingsStore(Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    public boolean isCrashReportingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_crash), enableCrashReportingByDefault);
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
                    mContext.getString(R.string.settings_key_telemetry), enableTelemetryByDefault);
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

    public int getUaMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_desktop_version), UA_MODE_DEFAULT.ordinal());
    }

    public void setUaMode(int mode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_desktop_version), mode);
        editor.commit();
    }

    public int getInputMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_input_mode), INPUT_MODE_DEFAULT.ordinal());
    }

    public void setInputMode(int aTouchMode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_input_mode), aTouchMode);
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
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_window_width), WINDOW_WIDTH_DEFAULT);
    }

    public void setWindowWidth(int aWindowWidth) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_window_width), aWindowWidth);
        editor.commit();
    }

    public int getWindowHeight() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_window_height), WINDOW_HEIGHT_DEFAULT);
    }

    public void setWindowHeight(int aWindowHeight) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_window_height), aWindowHeight);
        editor.commit();
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
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_max_window_width), MAX_WINDOW_WIDTH_DEFAULT);
    }

    public void setMaxWindowWidth(int aMaxWindowWidth) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_max_window_width), aMaxWindowWidth);
        editor.commit();
    }

    public int getMaxWindowHeight() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_max_window_height), MAX_WINDOW_HEIGHT_DEFAULT);
    }

    public void setMaxWindowHeight(int aMaxWindowHeight) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_max_window_height), aMaxWindowHeight);
        editor.commit();
    }

    public String getEnvironment() {
        return mPrefs.getString(
                mContext.getString(R.string.settings_key_env), ENV_DEFAULT);
    }

    public void setEnvironment(String aEnv) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_env), aEnv);
    }

    public float getBrowserWorldWidth() {
        return mPrefs.getFloat(
                mContext.getString(R.string.settings_key_browser_world_width), BROWSER_WORLD_WIDTH_DEFAULT);
    }

    public void setBrowserWorldWidth(float aBrowserWorldWidth) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_browser_world_width), aBrowserWorldWidth);
        editor.commit();
    }

    public float getBrowserWorldHeight() {
        return mPrefs.getFloat(
                mContext.getString(R.string.settings_key_browser_world_height), BROWSER_WORLD_HEIGHT_DEFAULT);
    }

    public void setBrowserWorldHeight(float aBrowserWorldHeight) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_browser_world_height), aBrowserWorldHeight);
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

}
