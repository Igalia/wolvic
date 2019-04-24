package org.mozilla.vrbrowser.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;

public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Delegate {
        void OnConnectivityChanged();
    }

    private Delegate mDelegate;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mDelegate != null) {
            mDelegate.OnConnectivityChanged();
        }
    }

    public void register(Activity aActivity, Delegate aDelegate) {
        mDelegate = aDelegate;
        aActivity.registerReceiver(this, new IntentFilter(CONNECTIVITY_ACTION));
    }

    public void unregister(Activity aActivity) {
        aActivity.unregisterReceiver(this);
        mDelegate = null;
    }

    public static boolean isNetworkAvailable(Context aContext) {
        ConnectivityManager manager = (ConnectivityManager) aContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = manager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
