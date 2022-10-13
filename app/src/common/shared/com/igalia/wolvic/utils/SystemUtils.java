package com.igalia.wolvic.utils;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.engine.EngineProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class SystemUtils {

    private static final String LOGTAG = SystemUtils.createLogtag(SystemUtils.class);

    public static final long ONE_DAY_MILLIS = 86400000;
    public static final long TWO_DAYS_MILLIS = 172800000;
    public static final long ONE_WEEK_MILLIS = 604800000;

    public static final void restart(@NonNull Context context) {
        Intent i = new Intent(context, VRBrowserActivity.class);
        i.setPackage(BuildConfig.APPLICATION_ID);
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static final void scheduleRestart(@NonNull Context context, long delay) {
        Intent i = new Intent(context, VRBrowserActivity.class);
        i.setPackage(BuildConfig.APPLICATION_ID);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent mPendingIntent = PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + delay, mPendingIntent);

        System.exit(0);
    }

    @NonNull
    public static String createLogtag(@NonNull Class aClass) {
        return "VRB[" + aClass.getSimpleName() + "]";
    }

    private static final String CRASH_STATS_URL = "https://crash-stats.mozilla.com/report/index/";

    private static void sendCrashFiles(@NonNull Context context, @NonNull final String aDumpFile, @NonNull final String aExtraFile) {
        ((VRBrowserApplication)context.getApplicationContext()).getExecutors().backgroundThread().post(() -> {
            try {
                WRuntime runtime = EngineProvider.INSTANCE.getOrCreateRuntime(context);
                WResult<String> result = runtime.sendCrashReport(context, new File(aDumpFile), new File(aExtraFile), context.getString(R.string.crash_app_name));

                result.then(crashID -> {
                    Log.e(LOGTAG, "Submitted crash report id: " + crashID);
                    Log.e(LOGTAG, "Report available at: " + CRASH_STATS_URL + crashID);
                    return null;
                }).exceptionally(ex -> {
                    Log.e(LOGTAG, "Failed to submit crash report: " + (ex != null ? ex.getMessage() : "Exception is NULL"));
                    return null;
                });
            } catch (IOException | URISyntaxException e) {
                Log.e(LOGTAG, "Failed to send crash report: " + e.toString());
            }
        });
    }

    public static void postCrashFiles(@NonNull Context context, @NonNull final String aDumpFile, @NonNull final String aExtraFile) {
        sendCrashFiles(context, aDumpFile, aExtraFile);
    }

    public static void postCrashFiles(@NonNull Context context, final ArrayList<String> aFiles) {
        for (String file: aFiles) {
            try {
                ArrayList<String> list = new ArrayList<>(2);
                try (FileInputStream in = context.openFileInput(file)) {
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while((line = br.readLine()) != null) {
                            list.add(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (list.size() < 2) {
                    Log.e(LOGTAG, "Failed read crash dump file names from: " + file);
                    return;
                }
                sendCrashFiles(context, list.get(0), list.get(1));
            } finally {
                Log.d(LOGTAG,"Removing crash file: " + file);
                context.deleteFile(file);
            }
        }
    }

    public static void clearCrashFiles(@NonNull Context context, final ArrayList<String> aFiles) {
        for (String file : aFiles) {
            Log.e(LOGTAG, "Deleting crash file: " + file);
            context.deleteFile(file);
        }
    }

    public static boolean isMainProcess(@NonNull Context context) {
        int pid = Process.myPid();

        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }

        return activityManager.getRunningAppProcesses().stream().anyMatch(processInfo ->
                processInfo.pid == pid && processInfo.processName.equals(context.getPackageName()));
    }

}
