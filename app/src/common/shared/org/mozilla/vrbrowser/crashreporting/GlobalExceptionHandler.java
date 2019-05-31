package org.mozilla.vrbrowser.crashreporting;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.mozilla.gecko.CrashHandler;

import androidx.annotation.NonNull;

public class GlobalExceptionHandler {

    private static final String LOGTAG = "VRB";

    private static GlobalExceptionHandler mInstance;

    public static synchronized @NonNull
    GlobalExceptionHandler register(Context aContext) {
        if (mInstance == null) {
            mInstance = new GlobalExceptionHandler();
            mInstance.mCrashHandler = new CrashHandler(aContext, CrashReporterService.class) {
                @Override
                protected Bundle getCrashExtras(final Thread thread, final Throwable exc) {
                    final Bundle extras = super.getCrashExtras(thread, exc);
                    if (extras == null) {
                        return null;
                    }
                    extras.putString("Version", org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION);
                    extras.putString("BuildID", org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID);
                    extras.putString("Vendor", org.mozilla.geckoview.BuildConfig.MOZ_APP_VENDOR);
                    extras.putString("ReleaseChannel", org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL);
                    return extras;
                }
            };
            Log.d(LOGTAG, "======> GlobalExceptionHandler registered");
        }

        return mInstance;
    }

    private CrashHandler mCrashHandler;
}
