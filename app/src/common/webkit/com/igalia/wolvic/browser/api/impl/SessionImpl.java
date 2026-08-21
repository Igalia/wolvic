package com.igalia.wolvic.browser.api.impl;

import static android.util.Patterns.WEB_URL;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.icu.number.UnlocalizedNumberFormatter;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WPanZoomController;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.browser.api.WTextInput;
import com.wpe.wpeview.WPEView;

public class SessionImpl implements WSession {
    WPEView mWPEView;
    RuntimeImpl mRuntime;
    SettingsImpl mSettings;
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
    DisplayImpl mDisplay;
    TextInputImpl mTextInput;
    PanZoomCrontrollerImpl mPanZoomCrontroller;
    SurfaceClientImpl mSurfaceClient;

    public SessionImpl(@Nullable WSessionSettings settings) {
        mSettings = settings != null ? (SettingsImpl) settings : new SettingsImpl(false);
        init();
    }

    private void init() {
        mTextInput = new TextInputImpl(this);
        mPanZoomCrontroller = new PanZoomCrontrollerImpl(this);
        mSurfaceClient = new SurfaceClientImpl();
    }

    @UiThread
    @Override
    public void loadUri(@NonNull String uri, int flags) {
        assertSessionOpened();
        mWPEView.loadUrl(getUriFromString(uri).toString());
    }

    @UiThread
    @Override
    public void loadData(@NonNull byte[] bytes, String mimeType) {
        assertSessionOpened();

        String dataUri = String.format("data:%s;base64,%s",
                mimeType != null ? mimeType : "", Base64.encodeToString(bytes, Base64.NO_WRAP));

        mWPEView.loadUrl(dataUri);
    }

    @UiThread
    @Override
    public void reload(int flags) {
        assertSessionOpened();
        mWPEView.reload();
    }

    @UiThread
    @Override
    public void stop() {
        assertSessionOpened();
        mWPEView.stopLoading();
    }

    @Override
    public void setActive(boolean active) {
        assertSessionOpened();
        mWPEView.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setFocused(boolean focused) {
        assertSessionOpened();
        mWPEView.requestFocus();
    }

    @Override
    public void open(@NonNull WRuntime runtime) {
        assert mWPEView == null;
        mRuntime = (RuntimeImpl)runtime;
        mWPEView = new WPEView(((RuntimeImpl)runtime).getContext());
        registerCallbacks();
    }

    private void registerCallbacks() {
        mWPEView.setSurfaceClient(mSurfaceClient);
        mWPEView.setWPEViewClient(new WPEWebViewClientImpl(this));
        mWPEView.setWebChromeClient(new WebChromeClientImpl(this));
    }

    @Override
    public boolean isOpen() {
        return mWPEView != null;
    }

    @Override
    public void close() {
        assertSessionOpened();
        mWPEView.setSurfaceClient(null);
        mWPEView.setWPEViewClient(null);
        mWPEView.setWebChromeClient(null);
        mWPEView.stopLoading();
    }

    @UiThread
    @Override
    public void goBack(boolean userInteraction) {
        mWPEView.goBack();
    }

    @UiThread
    @Override
    public void goForward(boolean userInteraction) {
        mWPEView.goForward();
    }

    @UiThread
    @Override
    public void gotoHistoryIndex(int index) {
        mWPEView.goBack();
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

    @Override
    public void exitFullScreen() {

    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        assert mDisplay == null;
        assert mWPEView != null;

        mDisplay = new DisplayImpl(mRuntime.getContainer(), this);
        mDisplay.acquire();
        return mDisplay;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        assert mDisplay != null;
        mDisplay.release();
        mDisplay = null;
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
        // TODO: Implement bridge
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
        // TODO: Implement bridge
        mPromptDelegate = delegate;
    }

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate;
    }

    @Override
    public void setSelectionActionDelegate(@Nullable SelectionActionDelegate delegate) {
        // TODO: Implement bridge
        mSelectionActionDelegate = delegate;
    }

    @Override
    public void setMediaSessionDelegate(@Nullable WMediaSession.Delegate delegate) {
        // TODO: Implement bridge
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

    private void assertSessionOpened() {
        assert mWPEView != null;
    }

    private Uri getUriFromString(@NonNull String str) {
        // WEB_URL doesn't match port numbers. Special case "localhost:" to aid
        // testing where a port is remapped.
        // Use WEB_URL first to ensure this matches urls such as 'https.'
        if (WEB_URL.matcher(str).matches() || str.startsWith("http://localhost:")) {
            // WEB_URL matches relative urls (relative meaning no scheme), but this branch is only
            // interested in absolute urls. Fall through if no scheme is supplied.
            Uri uri = Uri.parse(str);
            if (!uri.isRelative()) return uri;
        }

        if (str.startsWith("www.") || str.indexOf(":") == -1) {
            String url = "http://" + str;
            if (WEB_URL.matcher(url).matches()) {
                return Uri.parse(url);
            }
        }

        if (str.startsWith("chrome://")) return Uri.parse(str);

        return Uri.parse("https://google.com/search")
                .buildUpon()
                .appendQueryParameter("q", str)
                .build();
    }
}
