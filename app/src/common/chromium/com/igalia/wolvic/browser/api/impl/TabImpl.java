package com.igalia.wolvic.browser.api.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import org.chromium.content_public.browser.MediaSessionObserver;
import org.chromium.wolvic.Tab;

/**
 * Controlls a single tab content in a browser for chromium backend.
 */
public class TabImpl extends Tab {
    private TabMediaSessionObserver mTabMediaSessionObserver;
    private TabWebContentsDelegate mTabWebContentsDelegate;
    private TabWebContentsObserver mWebContentsObserver;

    public TabImpl(@NonNull Context context, @NonNull SessionImpl session) {
        super(context);
        registerCallbacks(session);
    }

    private void registerCallbacks(@NonNull SessionImpl session) {
        mTabMediaSessionObserver = new TabMediaSessionObserver(mWebContents, session);
        mTabWebContentsDelegate = new TabWebContentsDelegate(session, mWebContents);
        setWebContentsDelegate(mWebContents, mTabWebContentsDelegate);

        mWebContentsObserver = new TabWebContentsObserver(mWebContents, session);
    }

    public void exitFullScreen() {
        mWebContents.exitFullscreen();
    }

    public void onMediaFullscreen(boolean isFullscreen) {
        mTabMediaSessionObserver.onMediaFullscreen(isFullscreen);
    }
}
