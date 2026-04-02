package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.wpe.wpeview.WPEView;
import com.wpe.wpeview.WPEViewClient;

class WPEWebViewClientImpl implements WPEViewClient {
    @NonNull SessionImpl mSession;

    public WPEWebViewClientImpl(@NonNull SessionImpl session) {
        mSession = session;
    }


    @Override
    public void onPageStarted(WPEView view, String url) {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onLocationChange(mSession, url);
        }

        @Nullable WSession.ProgressDelegate progress = mSession.getProgressDelegate();
        if (progress != null) {
            progress.onPageStart(mSession, url);
        }
        dispatchCanGoBackForward();
    }

    @Override
    public void onPageFinished(WPEView view, String url) {
        @Nullable WSession.ProgressDelegate progress = mSession.getProgressDelegate();
        if (progress != null) {
            progress.onPageStop(mSession, true);
        }

        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFirstContentfulPaint(mSession);
        }
        dispatchCanGoBackForward();
    }

    private void dispatchCanGoBackForward() {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onCanGoBack(mSession, mSession.mWPEView.canGoBack());
            delegate.onCanGoForward(mSession, mSession.mWPEView.canGoForward());
        }
    }

    @Override
    public void onViewReady(WPEView view) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFirstComposite(mSession);
        }
    }
}
