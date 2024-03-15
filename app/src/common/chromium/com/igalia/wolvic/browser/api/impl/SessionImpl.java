package com.igalia.wolvic.browser.api.impl;

import android.graphics.Matrix;
import android.view.ViewGroup;

import androidx.annotation.AnyThread;
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
import com.igalia.wolvic.browser.api.WWebResponse;
import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.DownloadManagerBridge;
import org.chromium.wolvic.PermissionManagerBridge;
import org.chromium.wolvic.UserDialogManagerBridge;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class SessionImpl implements WSession, DownloadManagerBridge.Delegate {
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
    TextInputImpl mTextInput;
    PanZoomControllerImpl mPanZoomController;
    private PermissionManagerBridge.Delegate mChromiumPermissionDelegate;
    private String mInitialUri;
    private TabImpl mTab;
    private ReadyCallback mReadyCallback = new ReadyCallback();

    private class ReadyCallback implements RuntimeImpl.Callback {
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
        DownloadManagerBridge.get().setDelegate(this);
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
        WebContents webContents = mTab.getContentView().getWebContents();
        if (active) {
            webContents.onShow();
        } else {
            webContents.onHide();
            webContents.suspendAllMediaPlayers();
        }
        webContents.setAudioMuted(!active);
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
        mRuntime.registerCallback(mReadyCallback);
    }

    @Override
    public boolean isOpen() {
        return mTab != null ? true : false;
    }

    @Override
    public void close() {
        mRuntime.unregisterCallback(mReadyCallback);
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

    @Nullable
    @Override
    public SessionFinder getSessionFinder() {
        // TODO: Implement session finder
        return null;
    }

    @Override
    public void exitFullScreen() {
        getTab().exitFullScreen();
    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        SettingsStore settings = SettingsStore.getInstance(mRuntime.getContext());
        WDisplay display = new DisplayImpl(this, mTab.getCompositorView());
        mRuntime.getContainerView().addView(mTab.getCompositorView(),
                new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
        mRuntime.getContainerView().addView(getContentView(),
                new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
        getTextInput().setView(getContentView());
        return display;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        mRuntime.getContainerView().removeView(mTab.getCompositorView());
        mRuntime.getContainerView().removeView(getContentView());
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

    @AnyThread
    @Override
    public void pageZoomIn() {
        mTab.pageZoomIn();
    }

    @AnyThread
    @Override
    public void pageZoomOut() {
        mTab.pageZoomOut();
    }

    @AnyThread
    @Override
    public int getCurrentZoomLevel() {
        return mTab.getCurrentZoomLevel();
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
        if (mPermissionDelegate == delegate) {
            return;
        }

        mPermissionDelegate = delegate;
        mChromiumPermissionDelegate = new ChromiumPermissionDelegate(this, delegate);
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
        UserDialogManagerBridge.get().setDelegate(mPromptDelegate);
    }

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate == null ? null : mPromptDelegate.getDelegate();
    }

    @Nullable
    public PromptDelegateImpl getChromiumPromptDelegate() {
        return mPromptDelegate;
    }

    @Override
    public void setSelectionActionDelegate(@Nullable SelectionActionDelegate delegate) {
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

    @Override
    public void newDownload(String url) {
        // Since we only have the URL, we have to use default values for the rest of the web
        // response data.
        mContentDelegate.onExternalResponse(this, new WWebResponse() {
            @NonNull
            @Override
            public String uri() {
                return url;
            }

            @NonNull
            @Override
            public Map<String, String> headers() {
                return new HashMap<>();
            }

            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public boolean redirected() {
                return false;
            }

            @Override
            public boolean isSecure() {
                return true;
            }

            @Nullable
            @Override
            public X509Certificate certificate() {
                return null;
            }

            @Nullable
            @Override
            public InputStream body() {
                return null;
            }
        });
    }

    public TabImpl getTab() {
        return mTab;
    }

    public ViewGroup getContentView() {
        return mTab != null ? mTab.getContentView() : null;
    }

    // The onReadyCallback() mechanism is really limited because it heavily depends on renderers
    // being created by the client (Wolvic). There are cases in which the renderer is created by the
    // web engine (like target=_blank navigations) so we need to explicitly call onReady ourselves.
    public void invokeOnReady(RuntimeImpl runtime, String uri) {
        assert !isOpen();
        mRuntime = runtime;
        mInitialUri = uri;
        mReadyCallback.onReady();
    }

}
