package com.igalia.wolvic.browser.api.impl;

import static android.util.Patterns.WEB_URL;

import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;
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

import org.chromium.components.embedder_support.view.WolvicContentRenderView;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationController;
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
    private NavigationController mNavigationController = null;
    private boolean mIsDisplayAcquired = false;
    private String mInitialUri = null;

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
        mTabWebContentsObserver = new TabWebContentsObserver(getCurrentWebContents(), this);
    }

    private void unRegisterCallbacks() {
        mTabWebContentsObserver = null;
    }

    @Override
    public void loadUri(@NonNull String uri, int flags) {
        if (!mIsDisplayAcquired) {
            mInitialUri = uri;
            return;
        }

        LoadUrlParams params = new LoadUrlParams(getUriFromString(uri).toString());
        mNavigationController.loadUrl(params);
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

        if (str.startsWith("www.") || !str.contains(":")) {
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

    private void loadInitialUri() {
        if (mInitialUri == null) {
            return;
        }
        loadUri(mInitialUri);
        mInitialUri = null;
    }

    @Override
    public void loadData(@NonNull byte[] data, String mymeType) {

    }

    @Override
    public void reload(int flags) {
        mNavigationController.reload(true);
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
        mNavigationController.goBack();
    }

    @Override
    public void goForward(boolean userInteraction) {
        mNavigationController.goForward();
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
        ContentShellController controller = mRuntime.getContentShellController();
        WebContents webContents = controller.createWebContents();
        ContentView cv = ContentView.createContentView(
                mContext, null /* eventOffsetHandler */, webContents);
        mCurrentContentView = cv;
        WolvicContentRenderView renderView = new WolvicContentRenderView(mRuntime.getContext());
        renderView.onNativeLibraryLoaded(controller.getWindowAndroid());
        renderView.setCurrentWebContents(webContents);
        mRuntime.addViewToBrowserContainer(renderView);
        controller.getWindowAndroid().setAnimationPlaceholderView(renderView);
        mDisplay = new DisplayImpl(renderView, this);
        mNavigationController = webContents.getNavigationController();
        mIsDisplayAcquired = true;
        registerCallbacks();
        loadInitialUri();
        getTextInput().setView(getContentView());
        return mDisplay;
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        unRegisterCallbacks();
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

    @NonNull
    public WebContents getCurrentWebContents() {
        return mRuntime.getRenderView().getCurrentWebContents();
    }

    @NonNull
    public ViewGroup getContentView() {
        return mCurrentContentView;
    }
}
