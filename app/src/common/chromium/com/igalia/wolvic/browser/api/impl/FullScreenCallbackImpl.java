package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.weblayer.FullscreenCallback;

class FullScreenCallbackImpl extends FullscreenCallback {
    @NonNull SessionImpl mSession;
    @Nullable Runnable mExitFullscreenRunner;

    public FullScreenCallbackImpl(@NonNull SessionImpl session) {
        mSession = session;
    }

    public void exitFullscreen() {
        if (mExitFullscreenRunner != null) {
            mExitFullscreenRunner.run();
        }
    }

    @Override
    public void onEnterFullscreen(@NonNull Runnable exitFullscreenRunner) {
        mExitFullscreenRunner = exitFullscreenRunner;
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFullScreen(mSession, true);
        }
    }

    @Override
    public void onExitFullscreen() {
        mExitFullscreenRunner = null;
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFullScreen(mSession, false);
        }
    }
}
