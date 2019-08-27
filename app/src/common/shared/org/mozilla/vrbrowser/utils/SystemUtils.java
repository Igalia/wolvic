package org.mozilla.vrbrowser.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.VRBrowserActivity;

public class SystemUtils {

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
                PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + delay, mPendingIntent);

        System.exit(0);
    }

}
