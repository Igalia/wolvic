package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.content_public.browser.LifecycleState;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.url.GURL;

public class TabWebContentsObserver extends WebContentsObserver {
    private @NonNull SessionImpl mSession;

    public TabWebContentsObserver(WebContents webContents, @NonNull SessionImpl session) {
        super(webContents);
        mSession = session;
    }

    @Override
    public void didStartLoading(GURL url) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStart(mSession, url.toString());
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void didRedirectNavigation(NavigationHandle navigationHandle) {
        dispatchCanGoBackOrForward();
    }

    @Override
    public void didStopLoading(GURL url, boolean isKnownValid) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStop(mSession, true);
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void didFailLoad(boolean isInPrimaryMainFrame, int errorCode, GURL failingUrl, @LifecycleState int rfhLifecycleState) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null && isInPrimaryMainFrame) {
            delegate.onPageStop(mSession, false);
        }
        dispatchCanGoBackOrForward();
    }

    private void dispatchCanGoBackOrForward() {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            WebContents webContents = mWebContents.get();
            if (webContents == null)
                return;

            delegate.onCanGoBack(mSession, webContents.getNavigationController().canGoBack());
            delegate.onCanGoForward(mSession, webContents.getNavigationController().canGoForward());
        }
    }

    @Override
    public void loadProgressChanged(float progress) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onProgressChange(mSession, (int)(progress * 100));
        }
    }

    @Override
    public void didFirstVisuallyNonEmptyPaint() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFirstContentfulPaint(mSession);
        }
    }
}
