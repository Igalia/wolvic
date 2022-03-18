package com.igalia.wolvic.browser.api.impl;

import static android.util.Patterns.WEB_URL;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.icu.number.UnlocalizedNumberFormatter;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WPanZoomController;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.browser.api.WTextInput;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import org.chromium.weblayer.CaptureScreenShotCallback;
import org.chromium.weblayer.NavigateParams;
import org.chromium.weblayer.Tab;

import java.util.logging.Handler;

public class SessionImpl implements WSession {
    RuntimeImpl mRuntime;
    Tab mTab;
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
    NavigationCallbackImpl mNavigationCallback;
    TabCallbackImpl mTabCallback;
    FullScreenCallbackImpl mFullScreenCallback;
    NewTabCallbackImpl mNewTabCallback;

    public SessionImpl(@Nullable WSessionSettings settings) {
        mSettings = settings != null ? (SettingsImpl) settings : new SettingsImpl(false);
        init();
    }


    public SessionImpl(@NonNull RuntimeImpl runtime, @NonNull Tab tab) {
        mRuntime = runtime;
        mTab = tab;
        mSettings = new SettingsImpl(mTab.getBrowser().getProfile().isIncognito());
        mSettings.setTab(tab);
        init();
        registerCallbacks();
    }

    private void init() {
        mTextInput = new TextInputImpl(this);
        mPanZoomCrontroller = new PanZoomCrontrollerImpl(this);
    }

    @Nullable DisplayImpl getDisplay() {
        return mDisplay;
    }

    @Nullable RuntimeImpl getRuntime() {
        return mRuntime;
    }

    @Override
    public void loadUri(@NonNull String uri, int flags) {
        assertSessionOpened();
        NavigateParams.Builder params = new NavigateParams.Builder()
                .disableIntentProcessing()
                .setShouldReplaceCurrentEntry((flags & WSession.LOAD_FLAGS_REPLACE_HISTORY) != 0);
        if (isAutoplayEnabled()) {
            params.enableAutoPlay();
        }

        mTab.getNavigationController().navigate(getUriFromString(uri), params.build());
    }

    @Override
    public void loadData(@NonNull byte[] bytes, String mimeType) {
        assertSessionOpened();

        NavigateParams.Builder params = new NavigateParams.Builder()
                .disableIntentProcessing();

        String dataUri = String.format("data:%s;base64,%s",
                mimeType != null ? mimeType : "", Base64.encodeToString(bytes, Base64.NO_WRAP));

        mTab.getNavigationController().navigate(Uri.parse(dataUri), params.build());
    }

    @Override
    public void reload(int flags) {
        assertSessionOpened();
        mTab.getNavigationController().reload();
    }

    @Override
    public void stop() {
        assertSessionOpened();
        mTab.getNavigationController().stop();
    }

    @Override
    public void setActive(boolean active) {
        assertSessionOpened();
        // No op. Browser active tab is set in acquireDisplay.
    }

    @Override
    public void setFocused(boolean focused) {
        assertSessionOpened();
    }

    @Override
    public void open(@NonNull WRuntime runtime) {
        assert mTab == null;
        mRuntime = (RuntimeImpl) runtime;
        mTab = mRuntime.createTab(mSettings.getUsePrivateMode());
        registerCallbacks();
    }

    private void registerCallbacks() {
        mSettings.setTab(mTab);
        mNavigationCallback = new NavigationCallbackImpl(this);
        mTabCallback = new TabCallbackImpl(this);
        mFullScreenCallback = new FullScreenCallbackImpl(this);

        mTab.getNavigationController().registerNavigationCallback(mNavigationCallback);
        mTab.registerTabCallback(mTabCallback);
        mTab.setFullscreenCallback(mFullScreenCallback);
        mTab.setNewTabCallback(mNewTabCallback);
    }

    @Override
    public boolean isOpen() {
        return mTab != null;
    }

    @Override
    public void close() {
        assertSessionOpened();

        mTab.getNavigationController().unregisterNavigationCallback(mNavigationCallback);
        mTab.unregisterTabCallback(mTabCallback);
        mTab.setFullscreenCallback(null);
        mTab.setNewTabCallback(null);

        mTab.getBrowser().destroyTab(mTab);
        mTab = null;
        mNavigationCallback = null;
        mTabCallback = null;
        mFullScreenCallback = null;
        mNewTabCallback = null;
    }

    @Override
    public void goBack(boolean userInteraction) {
        mTab.getNavigationController().goBack();
    }

    @Override
    public void goForward(boolean userInteraction) {
        mTab.getNavigationController().goForward();
    }

    @Override
    public void gotoHistoryIndex(int index) {
        mTab.getNavigationController().goToIndex(index);
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
        if (mFullScreenCallback != null) {
            mFullScreenCallback.exitFullscreen();
        }
    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        assert mDisplay == null;
        assert mTab != null;
        mDisplay = new DisplayImpl(mRuntime.acquireDisplay(mSettings.getUsePrivateMode()), this);
        mDisplay.getBrowserDisplay().getBrowser().addTab(mTab);
        mDisplay.getBrowserDisplay().getBrowser().setActiveTab(mTab);
        return mDisplay;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        assert mDisplay != null;
        mRuntime.releaseDisplay(mDisplay.getBrowserDisplay());
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
        assert mTab != null;
    }

    private boolean isAutoplayEnabled() {
        return SettingsStore.getInstance(mRuntime.getContext()).isAutoplayEnabled();
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
