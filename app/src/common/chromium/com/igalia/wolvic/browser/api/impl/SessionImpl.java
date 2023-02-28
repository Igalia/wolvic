package com.igalia.wolvic.browser.api.impl;

import android.graphics.Matrix;
import android.util.Log;

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
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.utils.SystemUtils;

import org.chromium.components.embedder_support.view.WolvicContentRenderView;
import org.chromium.content_public.browser.WebContents;

public class SessionImpl implements WSession {
    private final SettingsImpl mSettings;
    ContentDelegate mContentDelegate;
    ProgressDelegate mProgressDelegate;
    PermissionDelegate mPermissionDelegate;
    NavigationDelegate mNavigationDelegate;
    ScrollDelegate mScrollDelegate;
    HistoryDelegate mHistoryDelegate;
    WContentBlocking.Delegate mContentBlockingDelegate;
    PromptDelegate mPromptDelegate;
    SelectionActionDelegate mSelectionActionDelegate;
    WMediaSession.Delegate mMediaSessionDelegate;
    private WTextInput mTextInput;
    private WPanZoomController mPanZoomCrontroller;
    private DisplayImpl mDisplay;
    private RuntimeImpl mRuntime = null;
    public Tab mTab = null;
    private TabWebContentsObserver mTabWebContentsObserver;

    public SessionImpl(WSessionSettings settings) {
        Log.e("WolvicLifecycle", "SessionImpl()");
        mSettings = settings != null ? (SettingsImpl) settings : new SettingsImpl(false);
        init();
    }
    private void init() {
        // TODO: Init controllers
        mTextInput = new TextInputImpl(this);
        mPanZoomCrontroller = new PanZoomCrontrollerImpl(this);
    }

    private void registerCallbacks() {
        mTabWebContentsObserver = new TabWebContentsObserver(mRuntime.GetCurrentWebContents(), this);
    }

    private void unRegisterCallbacks() {
        mTabWebContentsObserver = null;
    }

    @Override
    public void loadUri(@NonNull String uri, int flags) {

    }

    @Override
    public void loadData(@NonNull byte[] data, String mymeType) {

    }

    @Override
    public void reload(int flags) {

    }

    @Override
    public void stop() {

    }

    @Override
    public void setActive(boolean active) {

    }

    @Override
    public void setFocused(boolean focused) {

    }

    @Override
    public void open(@NonNull WRuntime runtime) {
        Log.e("WolvicLifecycle", "SessionImpl::open");
        mRuntime = (RuntimeImpl) runtime;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public void goBack(boolean userInteraction) {

    }

    @Override
    public void goForward(boolean userInteraction) {

    }

    @Override
    public void gotoHistoryIndex(int index) {

    }

    @Override
    public void purgeHistory() {

    }

    @NonNull
    @Override
    public WSessionSettings getSettings() {
        return mSettings;
    }

    @NonNull
    @Override
    public String getDefaultUserAgent(int mode) {
        // TODO: implement
        return "";
    }

    @Override
    public void exitFullScreen() {

    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        Log.e("WolvicLifecycle", "acquire display called");
        mDisplay = new DisplayImpl(mRuntime.createBrowserDisplay(), mRuntime.getRenderView(), this);
        registerCallbacks();
        return mDisplay;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        unRegisterCallbacks();
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
        return mPanZoomCrontroller;
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

    }

    @Nullable
    @Override
    public PermissionDelegate getPermissionDelegate() {
        return null;
    }

    @Override
    public void setProgressDelegate(@Nullable ProgressDelegate delegate) {
        mProgressDelegate = delegate;
    }

    @Nullable
    @Override
    public ProgressDelegate getProgressDelegate() {
        return mProgressDelegate;
    }

    @Override
    public void setNavigationDelegate(@Nullable NavigationDelegate delegate) {
        mNavigationDelegate = delegate;
    }

    @Nullable
    @Override
    public NavigationDelegate getNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public void setScrollDelegate(@Nullable ScrollDelegate delegate) {
        mScrollDelegate = delegate;
    }

    @Nullable
    @Override
    public ScrollDelegate getScrollDelegate() {
        return mScrollDelegate;
    }

    @Override
    public void setHistoryDelegate(@Nullable HistoryDelegate delegate) {
        mHistoryDelegate = delegate;
    }

    @Nullable
    @Override
    public HistoryDelegate getHistoryDelegate() {
        return mHistoryDelegate;
    }

    @Override
    public void setContentBlockingDelegate(@Nullable WContentBlocking.Delegate delegate) {
        mContentBlockingDelegate = delegate;
    }

    @Nullable
    @Override
    public WContentBlocking.Delegate getContentBlockingDelegate() {
        return mContentBlockingDelegate;
    }

    @Override
    public void setPromptDelegate(@Nullable PromptDelegate delegate) {
        mPromptDelegate = delegate;
    }

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate;
    }

    @Override
    public void setSelectionActionDelegate(@Nullable SelectionActionDelegate delegate) {
        mSelectionActionDelegate = delegate;
    }

    @Nullable
    @Override
    public SelectionActionDelegate getSelectionActionDelegate() {
        return mSelectionActionDelegate;
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
}
