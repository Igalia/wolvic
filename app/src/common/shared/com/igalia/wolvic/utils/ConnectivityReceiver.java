package com.igalia.wolvic.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Delegate {
        void OnConnectivityChanged(boolean connected);
    }

    private static class NetworkWatcher extends LiveData<Boolean> {
        public final ConnectivityManager connectivityManager;
        public final List<Delegate> listeners;
        public ConnectivityManager.NetworkCallback networkCallback;
        private final Context context;
        public NetworkWatcher(@NonNull Context context){
            this.context = context;
            this.listeners = new ArrayList<>();
            this.connectivityManager = context.getSystemService(ConnectivityManager.class);
            this.networkCallback = new ConnectivityManager.NetworkCallback(){
                @Override
                public void onLost(@NonNull Network network) {
                    NetworkWatcher.super.postValue(false);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    NetworkWatcher.super.postValue(
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    );
                }
            };
        }

        public void onReceive() {
            listeners.forEach(listener -> listener.OnConnectivityChanged(isNetworkAvailable(context)));
        }

        @Override
        protected void onActive() {
            this.connectivityManager.registerDefaultNetworkCallback(this.networkCallback);

            onReceive();
            NetworkWatcher.super.observe((LifecycleOwner) context, Observer -> onReceive());
        }

        @Override
        protected void onInactive() {
            this.connectivityManager.unregisterNetworkCallback(this.networkCallback);

            onReceive();
            NetworkWatcher.super.removeObservers((LifecycleOwner) context);
        }
    }

    private final NetworkWatcher mNetworkWatcher;

    public ConnectivityReceiver(@NonNull Context context) {
        mNetworkWatcher = new NetworkWatcher(context);
    }

    public void init() {
        mNetworkWatcher.onActive();
    }

    public void end() {
        mNetworkWatcher.onInactive();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mNetworkWatcher.onReceive();
    }

    public void addListener(@NonNull Delegate aDelegate) {
        mNetworkWatcher.listeners.add(aDelegate);
    }

    public void removeListener(@NonNull Delegate aDelegate) {
        mNetworkWatcher.listeners.remove(aDelegate);
    }

    public static boolean isNetworkAvailable(Context aContext) {
        ConnectivityManager connectivityManager = (ConnectivityManager) aContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);

        return networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
