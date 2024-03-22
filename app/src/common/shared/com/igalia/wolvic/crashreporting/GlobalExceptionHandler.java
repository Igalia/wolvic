package com.igalia.wolvic.crashreporting;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.engine.EngineProvider;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.browser.api.impl.CrashReporterServiceImpl;

public class GlobalExceptionHandler {

    private static final String LOGTAG = SystemUtils.createLogtag(GlobalExceptionHandler.class);

    public static GlobalExceptionHandler mInstance;

    public static synchronized @NonNull
    GlobalExceptionHandler register(Context aContext) {
        if (mInstance == null) {
            mInstance = new GlobalExceptionHandler();
            WRuntime runtime = EngineProvider.INSTANCE.getOrCreateRuntime(aContext);
            mInstance.mCrashHandler = runtime.createCrashHandler(aContext, CrashReporterServiceImpl.class);
            Log.d(LOGTAG, "======> GlobalExceptionHandler registered");
        }

        return mInstance;
    }

    public Thread.UncaughtExceptionHandler mCrashHandler;
}
