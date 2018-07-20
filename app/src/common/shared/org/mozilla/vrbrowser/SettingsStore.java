package org.mozilla.vrbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.mozilla.telemetry.TelemetryHolder;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;


public class SettingsStore {

    private static final String LOGTAG = "SettingsStore";

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
    // Enable telemetry by default (opt-out).
    private final static boolean enableTelemetryByDefault = true;

    public SettingsStore(Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    public boolean isCrashReportingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_crash), false);
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

}
