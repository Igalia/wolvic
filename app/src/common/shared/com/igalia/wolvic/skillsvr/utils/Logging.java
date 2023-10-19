package com.igalia.wolvic.skillsvr.utils;

import android.util.Log;

public class Logging {
    public static final String TAG = "svrlog";

    public static void d(String msg)
    {
        Log.d(TAG, msg);
    }

    public static void e(String msg)
    {
        Log.e(TAG, msg);
    }

    public static void eWithException(String msg, Exception exception)
    {
        Log.e(TAG, msg);
        stackTrace(exception);
    }

    public static void stackTrace()
    {
        try {
            throw new IntentionalException();
        } catch (Exception e)
        {
            Log.d(TAG, Log.getStackTraceString(e));
        }
    }

    public static void stackTrace(Exception toTrace)
    {
        Log.d(TAG, Log.getStackTraceString(toTrace));
    }
}
