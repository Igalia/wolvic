package org.mozilla.vrbrowser.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;

public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Delegate {
        void OnConnectivityChanged(boolean connected);
    }

    private Context mContext;
    private List<Delegate> mListeners;

    public ConnectivityReceiver(@NonNull Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
    }

    public void init() {
        mContext.registerReceiver(this, new IntentFilter(CONNECTIVITY_ACTION));
    }

    public void end() {
        mContext.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mListeners.forEach(listener -> listener.OnConnectivityChanged(isNetworkAvailable(mContext)));
    }

    public void addListener(@NonNull Delegate aDelegate) {
        mListeners.add(aDelegate);
    }

    public void removeListener(@NonNull Delegate aDelegate) {
        mListeners.remove(aDelegate);
    }

    public static boolean isNetworkAvailable(Context aContext) {
        ConnectivityManager manager = (ConnectivityManager) aContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = manager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
