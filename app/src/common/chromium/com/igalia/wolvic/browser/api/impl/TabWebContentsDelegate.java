package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.blink.mojom.DisplayMode;
import org.chromium.components.embedder_support.delegate.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.InvalidateTypes;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.GURL;

public class TabWebContentsDelegate extends WebContentsDelegateAndroid {
    private @NonNull SessionImpl mSession;
    private @NonNull final WebContents mWebContents;

    private boolean mIsFullscreen;

    public TabWebContentsDelegate(@NonNull SessionImpl session, WebContents webContents) {
        mSession = session;
        mWebContents = webContents;
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

    @Override
    public void navigationStateChanged(int flags) {
        if ((flags & InvalidateTypes.TITLE) != 0) {
            WSession.ContentDelegate delegate = mSession.getContentDelegate();
            if (delegate != null) {
                delegate.onTitleChange(mSession, mWebContents.getTitle());
            }
        }
    }

    @Override
    public void onUpdateUrl(GURL url) {
        WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onLocationChange(mSession, mWebContents.getVisibleUrl().getSpec());
        }
    }
}
