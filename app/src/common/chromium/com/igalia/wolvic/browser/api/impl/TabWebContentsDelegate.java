package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.blink.mojom.DisplayMode;
import org.chromium.components.embedder_support.delegate.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.WebContents;

public class TabWebContentsDelegate extends WebContentsDelegateAndroid {
    private @NonNull SessionImpl mSession;

    private boolean mIsFullscreen;

    public TabWebContentsDelegate(@NonNull SessionImpl session) {
        mSession = session;
    }

   @Override
   public boolean takeFocus(boolean reverse) {
       @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
       if (delegate != null && !reverse) {
           delegate.onFocusRequest(mSession);
           return true;
       }
       return false;
   }

    @Override
    public void enterFullscreenModeForTab(boolean prefersNavigationBar, boolean prefersStatusBar) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate == null) return;

        mIsFullscreen = true;
        delegate.onFullScreen(mSession, true);
    }

    @Override
    public void exitFullscreenModeForTab() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate == null) return;

        mIsFullscreen = false;
        delegate.onFullScreen(mSession, false);
    }

    @Override
    public boolean isFullscreenForTabOrPending() {
        return mIsFullscreen;
    }

    @Override
    public int getDisplayMode() {
        return !mIsFullscreen ? DisplayMode.BROWSER : DisplayMode.FULLSCREEN;
    }
}
