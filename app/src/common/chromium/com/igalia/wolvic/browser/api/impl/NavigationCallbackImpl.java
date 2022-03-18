package com.igalia.wolvic.browser.api.impl;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.weblayer.Navigation;
import org.chromium.weblayer.NavigationCallback;
import org.chromium.weblayer.Page;

public class NavigationCallbackImpl extends NavigationCallback {
    @NonNull SessionImpl mSession;

    public NavigationCallbackImpl(@NonNull SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onNavigationStarted(@NonNull Navigation navigation) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStart(mSession, navigation.getUri().toString());
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void onNavigationRedirected(@NonNull Navigation navigation) {
        dispatchCanGoBackOrForward();
    }

    @Override
    public void onNavigationCompleted(@NonNull Navigation navigation) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStop(mSession, true);
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void onNavigationFailed(@NonNull Navigation navigation) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStop(mSession, false);
        }
        dispatchCanGoBackOrForward();
    }

    private void dispatchCanGoBackOrForward() {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onCanGoBack(mSession, mSession.mTab.getNavigationController().canGoBack());
            delegate.onCanGoForward(mSession, mSession.mTab.getNavigationController().canGoForward());
        }
    }

    @Override
    public void onLoadStateChanged(boolean isLoading, boolean shouldShowLoadingUi) {
        dispatchCanGoBackOrForward();
    }

    @Override
    public void onLoadProgressChanged(double progress) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onProgressChange(mSession, (int)(progress * 100));
        }
    }

    @Override
    public void onFirstContentfulPaint() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFirstContentfulPaint(mSession);
        }
    }

    @Override
    public void onFirstContentfulPaint(long navigationStartMs, long firstContentfulPaintDurationMs) {

    }

    @Override
    public void onLargestContentfulPaint(long navigationStartMs, long largestContentfulPaintDurationMs) {

    }

    @Override
    public void onOldPageNoLongerRendered(@NonNull Uri newNavigationUri) {

    }

    @Override
    public void onPageDestroyed(@NonNull Page page) {

    }

    @Override
    public void onPageLanguageDetermined(@NonNull Page page, @NonNull String language) {

    }
}
