package com.igalia.wolvic.browser.api.impl;

import android.graphics.Matrix;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WPanZoomController;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.browser.api.WTextInput;

public class SessionImpl implements WSession {
    RuntimeImpl mRuntime;
    SettingsImpl mSettings;
    ContentDelegate mContentDelegate;
    ProgressDelegate mProgressDelegate;
    PermissionDelegate mPermissionDelegate;
    NavigationDelegate mNavigationDelegate;
    ScrollDelegate mScrollDelegate;
    HistoryDelegate mHistoryDelegate;
    WContentBlocking.Delegate mContentBlockingDelegate;
    PromptDelegateImpl mPromptDelegate;
    SelectionActionDelegate mSelectionActionDelegate;
    WMediaSession.Delegate mMediaSessionDelegate;
    DisplayImpl mDisplay;
    TextInputImpl mTextInput;
    PanZoomControllerImpl mPanZoomController;
    private String mInitialUri;
    private TabImpl mTab;
    private ReadyCallback mReadyCallback = new ReadyCallback();

    private class ReadyCallback implements BrowserInitializer.Callback {
        @Override
        public void onReady() {
            assert mTab == null;
            mTab = new TabImpl(
                    mRuntime.getContainerView().getContext(), SessionImpl.this);
            if (mInitialUri != null) {
                mTab.loadUrl(mInitialUri);
                mInitialUri = null;
            }
        }
    }

    public SessionImpl(@Nullable WSessionSettings settings) {
        mSettings = settings != null ? (SettingsImpl) settings : new SettingsImpl(false);
        init();
    }

    private void init() {
        mTextInput = new TextInputImpl(this);
        mPanZoomController = new PanZoomControllerImpl(this);
    }

    @Override
    public void loadUri(@NonNull String uri, int flags) {
        if (!isOpen()) {
            // If the session isn't open yet, save the uri and load when the session is ready.
            mInitialUri = uri;
        } else {
            mTab.loadUrl(uri);
        }
    }

    @Override
    public void loadData(@NonNull byte[] data, String mymeType) {
        // TODO: Implement
    }

    @Override
    public void reload(int flags) {
        if (isOpen())
            mTab.reload();
    }

    @Override
    public void stop() {
        // TODO: Implement
    }

    @Override
    public void setActive(boolean active) {
        if (mTab == null)
            return;

        assert mTab.getContentView() != null;
        if (active)
            mTab.getContentView().getWebContents().onShow();
        else
            mTab.getContentView().getWebContents().onHide();
    }

    @Override
    public void setFocused(boolean focused) {
        if (mTab == null)
            return;
        assert mTab.getContentView() != null;
        mTab.getContentView().getWebContents().setFocus(focused);
    }

    @Override
    public void open(@NonNull WRuntime runtime) {
        mRuntime = (RuntimeImpl) runtime;
        mRuntime.getBrowserInitializer().registerCallback(mReadyCallback);
    }

    @Override
    public boolean isOpen() {
        return mTab != null ? true : false;
    }

    @Override
    public void close() {
        mRuntime.getBrowserInitializer().unregisterCallback(mReadyCallback);
        mTab = null;
    }

    @Override
    public void goBack(boolean userInteraction) {
        if (isOpen())
            mTab.goBack();
    }

    @Override
    public void goForward(boolean userInteraction) {
        if (isOpen())
            mTab.goForward();
    }

    @Override
    public void gotoHistoryIndex(int index) {
        // TODO: Implement
    }

    @Override
    public void purgeHistory() {
        // TODO: Implement
    }

    @NonNull
    @Override
    public WSessionSettings getSettings() {
        return mSettings;
    }

    @NonNull
    @Override
    public String getDefaultUserAgent(int mode) {
        return mSettings.getDefaultUserAgent(mode);
    }

    @Override
    public void exitFullScreen() {
        getTab().exitFullScreen();
    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        assert mDisplay == null;
        mDisplay = new DisplayImpl(this, mTab.getCompositorView());
        mRuntime.addViewToBrowserContainer(mTab.getCompositorView());
        mRuntime.addViewToBrowserContainer(getContentView());
        getTextInput().setView(getContentView());
        return mDisplay;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        assert mDisplay != null;
        mDisplay = null;
        getTextInput().setView(null);
    }

    @Override
    public void restoreState(@NonNull WSessionState state) {

    }

    @Override
    public void getClientToSurfaceMatrix(@NonNull Matrix matrix) {

    }

    @Override
    public void getClientToScreenMatrix(@NonNull Matrix matrix) {

    }

    @Override
    public void getPageToScreenMatrix(@NonNull Matrix matrix) {

    }

    @Override
    public void getPageToSurfaceMatrix(@NonNull Matrix matrix) {

    }

    @Override
    public void dispatchLocation(double latitude, double longitude, double altitude, float accuracy, float altitudeAccuracy, float heading, float speed, float time) {

    }

    @NonNull
    @Override
    public WTextInput getTextInput() {
        return mTextInput;
    }

    @NonNull
    @Override
    public WPanZoomController getPanZoomController() {
        return mPanZoomController;
    }

    @Override
    public void setContentDelegate(@Nullable ContentDelegate delegate) {
        mContentDelegate = delegate;
    }

    @Nullable
    @Override
    public ContentDelegate getContentDelegate() {
        return mContentDelegate;
    }

    @Override
    public void setPermissionDelegate(@Nullable PermissionDelegate delegate) {
        // TODO: Implement bridge
        mPermissionDelegate = delegate;
    }

    @Nullable
    @Override
    public PermissionDelegate getPermissionDelegate() {
        return mPermissionDelegate;
    }

    @Override
    public void setProgressDelegate(@Nullable ProgressDelegate delegate) {
        // TODO: Implement bridge
        mProgressDelegate = delegate;
    }

    @Nullable
    @Override
    public ProgressDelegate getProgressDelegate() {
        return mProgressDelegate;
    }

    @Override
    public void setNavigationDelegate(@Nullable NavigationDelegate delegate) {
        // TODO: Implement bridge
        mNavigationDelegate = delegate;
    }

    @Nullable
    @Override
    public NavigationDelegate getNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public void setScrollDelegate(@Nullable ScrollDelegate delegate) {
        // TODO: Implement bridge
        mScrollDelegate = delegate;
    }

    @Nullable
    @Override
    public ScrollDelegate getScrollDelegate() {
        return mScrollDelegate;
    }

    @Override
    public void setHistoryDelegate(@Nullable HistoryDelegate delegate) {
        // TODO: Implement bridge
        mHistoryDelegate = delegate;
    }

    @Nullable
    @Override
    public HistoryDelegate getHistoryDelegate() {
        return mHistoryDelegate;
    }

    @Override
    public void setContentBlockingDelegate(@Nullable WContentBlocking.Delegate delegate) {
        // TODO: Implement bridge
        mContentBlockingDelegate = delegate;
    }

    @Nullable
    @Override
    public WContentBlocking.Delegate getContentBlockingDelegate() {
        return mContentBlockingDelegate;
    }

    @Override
    public void setPromptDelegate(@Nullable PromptDelegate delegate) {
        if (getPromptDelegate() == delegate) {
            return;
        }
        mPromptDelegate = new PromptDelegateImpl(delegate, this);
    }

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate == null ? null : mPromptDelegate.getDelegate();
    }

    @Override
    public void setSelectionActionDelegate(@Nullable SelectionActionDelegate delegate) {
        // TODO: Implement bridge
        mSelectionActionDelegate = delegate;
    }

    @Override
    public void setMediaSessionDelegate(@Nullable WMediaSession.Delegate delegate) {
        mMediaSessionDelegate = delegate;
    }

    @Nullable
    @Override
    public WMediaSession.Delegate getMediaSessionDelegate() {
        return mMediaSessionDelegate;
    }

    @Nullable
    @Override
    public SelectionActionDelegate getSelectionActionDelegate() {
        return mSelectionActionDelegate;
    }

    public TabImpl getTab() {
        return mTab;
    }

    public ViewGroup getContentView() {
        return mTab != null ? mTab.getContentView() : null;
    }
}
