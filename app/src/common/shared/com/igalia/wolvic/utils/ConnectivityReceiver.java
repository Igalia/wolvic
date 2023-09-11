package com.igalia.wolvic.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

public class ConnectivityReceiver extends LiveData<Boolean> {

    public interface Delegate {
        void OnConnectivityChanged(boolean connected);
    }

    private final Context mContext;
    private final List<Delegate> mListeners;
    private final ConnectivityManager mConnectivityManager;
    private final ConnectivityManager.NetworkCallback mNetworkCallback;

    public ConnectivityReceiver(@NonNull Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mNetworkCallback = new ConnectivityManager.NetworkCallback(){
            @Override
            public void onLost(@NonNull Network network) {
                ConnectivityReceiver.super.postValue(false);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                ConnectivityReceiver.super.postValue(
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                );
            }
        };
    }

    public void init() {
        mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
        ConnectivityReceiver.super.observe((LifecycleOwner) mContext,
                Observer -> onReceive(Boolean.TRUE.equals(ConnectivityReceiver.super.getValue())));
    }

    public void end() {
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        ConnectivityReceiver.super.removeObservers((LifecycleOwner) mContext);
    }

    private void onReceive(boolean status) {
        mListeners.forEach(listener -> listener.OnConnectivityChanged(status));
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
