package org.mozilla.vrbrowser.crashreporting;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;

public class CrashReporterService extends JobIntentService {

    private static final String LOGTAG = "VRB";

    public static final String CRASH_ACTION = "org.mozilla.vrbrowser.CRASH_ACTION";
    public static final String DATA_TAG = "intent";

    private static final int PID_CHECK_INTERVAL = 100;
    private static final int JOB_ID = 1000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG, "======> onStartCommand");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enqueueWork(this, CrashReporterService.class, JOB_ID, intent);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();
        if (GeckoRuntime.ACTION_CRASHED.equals(action)) {
            boolean fatal = intent.getBooleanExtra(GeckoRuntime.EXTRA_CRASH_FATAL, false);

            if (fatal) {
                Log.d(LOGTAG, "======> NATIVE CRASH PARENT" + intent);
                final int pid = Process.myPid();
                final ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager == null) {
                    return;
                }

                do {
                    boolean otherProcessesFound = false;
                    for (final ActivityManager.RunningAppProcessInfo info : activityManager.getRunningAppProcesses()) {
                        if (pid != info.pid) {
                            otherProcessesFound = true;
                            Log.e(LOGTAG, "======> Found PID " + info.pid);
                            break;
                        }
                    }

                    if (!otherProcessesFound) {
                        intent.setClass(CrashReporterService.this, VRBrowserActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        break;

                    } else {
                        try {
                            Thread.sleep(PID_CHECK_INTERVAL);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                } while (true);

            } else {
                Log.d(LOGTAG, "======> NATIVE CRASH CONTENT" + intent);
                Intent broadcastIntent = new Intent(CRASH_ACTION);
                broadcastIntent.putExtra(DATA_TAG, intent);
                sendBroadcast(broadcastIntent, getString(R.string.app_permission_name));
            }
        }

        Log.d(LOGTAG, "======> Crash reporter job finished");
    }

}
