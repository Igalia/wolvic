package com.igalia.wolvic.browser.api.impl;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import org.chromium.components.embedder_support.view.ContentView;
import org.chromium.components.embedder_support.view.WolvicContentRenderView;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.ImeAdapter;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.Tab;
import org.chromium.wolvic.TabJni;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.IntentRequestTracker;
import org.chromium.ui.base.ViewAndroidDelegate;

/**
 * Controlls a single tab content in a browser for chromium backend.
 *
 * TODO: All chromium objects should be moved under `org.chromium.wolvic.*`, so that wolvic
 * will communicate with chromium through public APIs of it.
 */
public class TabImpl extends Tab {
    private ActivityWindowAndroid mWindowAndroid;
    private ContentView mContentView;
    private NavigationController mNavigationController;
    private TabWebContentsObserver mWebContentsObserver;
    private WebContents mWebContents;
    private WolvicContentRenderView mRenderView;

    public TabImpl(@NonNull Context context, @NonNull SessionImpl session) {
        mWindowAndroid = new ActivityWindowAndroid(
                context, false, IntentRequestTracker.createFromActivity((Activity) context));

        mRenderView = new WolvicContentRenderView(context);
        mRenderView.onNativeLibraryLoaded(mWindowAndroid);

        mWebContents = TabJni.get().createWebContents();
        mContentView = ContentView.createContentView(
                context, null /* eventOffsetHandler */, mWebContents);
        mWebContents.initialize(
                "", ViewAndroidDelegate.createBasicDelegate(mContentView), mContentView,
                mWindowAndroid, WebContents.createDefaultInternalsHolder());

        mWindowAndroid.setAnimationPlaceholderView(mRenderView);

        mRenderView.setCurrentWebContents(mWebContents);
        mWebContents.onShow();

        mNavigationController = mWebContents.getNavigationController();

        registerCallbacks(session);
    }

    private void registerCallbacks(@NonNull SessionImpl session) {
        mWebContentsObserver = new TabWebContentsObserver(mWebContents, session);
    }

    public void goBack() {
        mNavigationController.goBack();
    }

    public void goForward() {
        mNavigationController.goForward();
    }

    public void reload() {
        mNavigationController.reload(true);
    }

    public void loadUrl(@NonNull String uri) {
        LoadUrlParams params = new LoadUrlParams(UrlFormatter.fixupUrl(uri).getPossiblyInvalidSpec());
        mNavigationController.loadUrl(params);
    }

    public WolvicContentRenderView getView() {
        return mRenderView;
    }

    public ContentView getContentView() {
        return mContentView;
    }

    public WebContents getWebContents() {
        return mWebContents;
    }

    public ImeAdapter getImeAdapter() {
        return ImeAdapter.fromWebContents(mWebContents);
    }
}
