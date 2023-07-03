package com.igalia.wolvic.utils;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

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
        // TODO: CONNECTIVITY_ACTION constant was deprecated in API level 28.
        //  apps should use the more versatile requestNetwork(NetworkRequest, PendingIntent),
        //  registerNetworkCallback(NetworkRequest, PendingIntent) or
        //  registerDefaultNetworkCallback(NetworkCallback) functions instead for faster and more
        //  detailed updates about the network changes they care about.
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
        ConnectivityManager connectivityManager = (ConnectivityManager) aContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);

        return networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
