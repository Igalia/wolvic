package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.blink.mojom.DisplayMode;

import org.chromium.components.embedder_support.delegate.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.WebContents;

public class TabWebContentsDelegate extends WebContentsDelegateAndroid {
    private @NonNull WebContents mWebContents;
    private @NonNull SessionImpl mSession;

    private boolean mIsFullscreen;

    public TabWebContentsDelegate(WebContents webContents, @NonNull SessionImpl session) {
        mWebContents = mWebContents;
        mSession = session;
        mSession.getTab().setWebContentsDelegate(webContents, this);
    }

   @Override
   public boolean takeFocus(boolean reverse) {
       @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
       if (delegate != null && !reverse) {
           delegate.onFocusRequest(mSession);
       }
       return false;
   }

    @Override
    public void enterFullscreenModeForTab(boolean prefersNavigationBar, boolean prefersStatusBar) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            mIsFullscreen = true;
            delegate.onFullScreen(mSession, true);
        }
    }

    @Override
    public void exitFullscreenModeForTab() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            mIsFullscreen = false;
            delegate.onFullScreen(mSession, false);
        }
    }

    @Override
    public boolean isFullscreenForTabOrPending() {
        return mIsFullscreen;
    }

    @Override
    public int getDisplayMode() {
        return !mIsFullscreen ? DisplayMode.BROWSER : DisplayMode.FULLSCREEN ;
    }
}
