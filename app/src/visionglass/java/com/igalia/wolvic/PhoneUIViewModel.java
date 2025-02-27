package com.igalia.wolvic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

public class PhoneUIViewModel extends ViewModel {

    public enum ConnectionState {
        DISCONNECTED, CONNECTING, REQUESTING_PERMISSIONS, CONNECTED, ACTIVE, PERMISSIONS_UNAVAILABLE, DISPLAY_UNAVAILABLE
    }

    private final MutableLiveData<ConnectionState> mConnectionState = new MutableLiveData<>(ConnectionState.DISCONNECTED);
    private final MutableLiveData<Boolean> mIsPlayingMedia = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> mIsPresentingImmersive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> mIsFullscreen = new MutableLiveData<>(false);



    public LiveData<ConnectionState> getConnectionState() {
        return mConnectionState;
    }

    public void updateConnectionState(ConnectionState newState) {
        if (newState.equals(mConnectionState.getValue()))
            return;

        mConnectionState.postValue(newState);

        if (newState != ConnectionState.ACTIVE) {
            updateIsPlayingMedia(false);
        }
    }

    public LiveData<Boolean> getIsActive() {
        return Transformations.map(mConnectionState,
                connectionState -> connectionState == ConnectionState.ACTIVE);
    }

    public LiveData<Boolean> getIsDisconnected() {
        return Transformations.map(mConnectionState,
                connectionState -> connectionState == ConnectionState.DISCONNECTED);
    }

    public LiveData<Boolean> getIsConnecting() {
        return Transformations.map(mConnectionState, connectionState ->
                connectionState != ConnectionState.DISCONNECTED && connectionState != ConnectionState.ACTIVE);
    }

    public LiveData<Boolean> getIsError() {
        return Transformations.map(mConnectionState, connectionState ->
                connectionState == ConnectionState.PERMISSIONS_UNAVAILABLE || connectionState == ConnectionState.DISPLAY_UNAVAILABLE);
    }

    public LiveData<Boolean> getIsPlayingMedia() {
        return mIsPlayingMedia;
    }

    public LiveData<Boolean> getIsPresentingImmersive() {
        return mIsPresentingImmersive;
    }

    public LiveData<Boolean> getIsFullscreen() {
        return mIsFullscreen;
    }

    public void updateIsPresentingImmersive(boolean isPresentingImmersive) {
        mIsPresentingImmersive.postValue(isPresentingImmersive);
    }

    public void updateIsFullscreen(boolean isFullscreen) {
        mIsFullscreen.postValue(isFullscreen);
    }

    public void updateIsPlayingMedia(boolean isPlayingMedia) {
        mIsPlayingMedia.postValue(isPlayingMedia);
    }
}
