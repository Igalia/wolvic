package com.igalia.wolvic.browser.api.impl;

import android.graphics.Color;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WPanZoomController;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.browser.api.WTextInput;

import org.mozilla.geckoview.GeckoSession;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class SessionImpl implements WSession {
    private @NonNull GeckoSession mSession;
    private WSessionSettings mSettings;
    private WSession.ContentDelegate mContentDelegate;
    private WSession.SelectionActionDelegate mSelectionActionDelegate;
    private WSession.PermissionDelegate mPermissionDelegate;
    private WSession.ProgressDelegate mProgressDelegate;
    private WSession.NavigationDelegate mNavigationDelegate;
    private WSession.ScrollDelegate mScrollDelegate;
    private WSession.HistoryDelegate mHistoryDelegate;
    private WSession.PromptDelegate mPromptDelegate;
    private WContentBlocking.Delegate mContentBlockingDelegate;
    private WMediaSession.Delegate mMediaSessionDelegate;
    private TextInputImpl mTextInput;
    private PanZoomControllerImpl mPanZoomController;
    private Method mGeckoLocationMethod;
    private UrlUtilsVisitor mUrlUtilsVisitor;
    private int mClearColor = Color.WHITE;

    // The difference between "Mobile" and "VR" matches GeckoViewSettings.jsm
    private static final String WOLVIC_USER_AGENT_MOBILE = GeckoSession.getDefaultUserAgent() + " Wolvic/" + BuildConfig.VERSION_NAME;
    private static final String WOLVIC_USER_AGENT_VR = WOLVIC_USER_AGENT_MOBILE.replace("Mobile", "Mobile VR");
    private static final String WOLVIC_USER_AGENT_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";

    public SessionImpl(@Nullable WSessionSettings settings) {
        if (settings == null) {
            mSession = new GeckoSession();
        } else {
            mSession = new GeckoSession(((SettingsImpl)settings).getGeckoSettings());
        }
        mSettings = new SettingsImpl(mSession.getSettings());
        mTextInput = new TextInputImpl(this);
        mPanZoomController = new PanZoomControllerImpl(mSession);
    }

    public @NonNull GeckoSession getGeckoSession() {
        return mSession;
    }

    @Override
    public void loadUri(@NonNull String uri, int flags) {
        mSession.load(new GeckoSession.Loader()
                .uri(uri)
                .flags(toGeckoFlags(flags)));
    }

    @Override
    public void loadData(@NonNull byte[] data, String mymeType) {
        mSession.load(new GeckoSession.Loader()
                .data(data, mymeType));
    }

    @Override
    public void reload(int flags) {
        mSession.reload(toGeckoFlags(flags));
    }

    @Override
    public void stop() {
        mSession.stop();
    }

    @Override
    public void setActive(boolean active) {
        mSession.setActive(active);
    }

    @Override
    public void setFocused(boolean focused) {
        mSession.setFocused(focused);
    }

    @Override
    public void open(@NonNull WRuntime runtime) {
        mSession.open(((RuntimeImpl)runtime).getGeckoRuntime());
    }

    @Override
    public boolean isOpen() {
        return mSession.isOpen();
    }

    @Override
    public void close() {
        mSession.close();
    }

    @Override
    public void goBack(boolean userInteraction) {
        mSession.goBack();
    }

    @Override
    public void goForward(boolean userInteraction) {
        mSession.goForward();
    }

    @Override
    public void gotoHistoryIndex(int index) {
        mSession.gotoHistoryIndex(index);
    }

    @Override
    public void purgeHistory() {
        mSession.purgeHistory();
    }

    @NonNull
    @Override
    public WSessionSettings getSettings() {
        return mSettings;
    }

    @NonNull
    @Override
    public String getDefaultUserAgent(int mode) {
        switch (mode) {
            case WSessionSettings.USER_AGENT_MODE_DESKTOP:
                return WOLVIC_USER_AGENT_DESKTOP;
            case WSessionSettings.USER_AGENT_MODE_VR:
                return WOLVIC_USER_AGENT_VR;
            case WSessionSettings.USER_AGENT_MODE_MOBILE:
            default:
                return WOLVIC_USER_AGENT_MOBILE;
        }
    }

    @NonNull
    @Override
    public SessionFinder getSessionFinder() {
        return new SessionFinderImpl(mSession.getFinder());
    }

    @Override
    public void exitFullScreen() {
        mSession.exitFullScreen();
    }

    @NonNull
    @Override
    public WDisplay acquireDisplay() {
        return new DisplayImpl(mSession.acquireDisplay());
    }

    @Override
    public void releaseDisplay(@NonNull WDisplay display) {
        mSession.releaseDisplay(((DisplayImpl)display).getGeckoDisplay());
    }

    @Override
    public void restoreState(@NonNull WSessionState state) {
        mSession.restoreState(((SessionStateImpl)state).getGeckoState());
    }

    @Override
    public void getClientToSurfaceMatrix(@NonNull Matrix matrix) {
        mSession.getClientToSurfaceMatrix(matrix);
    }

    @Override
    public void getClientToScreenMatrix(@NonNull Matrix matrix) {
        mSession.getClientToScreenMatrix(matrix);
    }

    @Override
    public void getPageToScreenMatrix(@NonNull Matrix matrix) {
        mSession.getPageToScreenMatrix(matrix);
    }

    @Override
    public void getPageToSurfaceMatrix(@NonNull Matrix matrix) {
        mSession.getPageToSurfaceMatrix(matrix);
    }

    @Override
    public void dispatchLocation(double latitude, double longitude, double altitude, float accuracy, float altitudeAccuracy, float heading, float speed, float time) {
        if (mGeckoLocationMethod == null) {
            initGeckoLocationReflection();
        }
        if (mGeckoLocationMethod != null) {
            try {
                mGeckoLocationMethod.invoke(null, latitude, longitude, altitude, accuracy, altitudeAccuracy, heading, speed, time);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initGeckoLocationReflection() {
        try {
            mGeckoLocationMethod = Class.forName("org.mozilla.gecko.GeckoAppShell").getDeclaredMethod("onLocationChanged", double.class, double.class, double.class, float.class, float.class, float.class, float.class, long.class);
            mGeckoLocationMethod.setAccessible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @NonNull
    @Override
    public WTextInput getTextInput() {
        return mTextInput;
    }

    @Override
    public void pageZoomIn() {}

    @Override
    public void pageZoomOut() {}

    @Override
    public int getCurrentZoomLevel() { return 0; }

    @NonNull
    @Override
    public WPanZoomController getPanZoomController() {
        return mPanZoomController;
    }

    @Override
    public void setClearColor(int color) {
        mClearColor = color;
        mSession.getCompositorController().setClearColor(mClearColor);
    }

    @Override
    public int getClearColor() {
        return mClearColor;
    }

    @Override
    public void setContentDelegate(@Nullable ContentDelegate delegate) {
        if (mContentDelegate == delegate) {
            return;
        }
        mContentDelegate = delegate;
        mSession.setContentDelegate(delegate != null ? new ContentDelegateImpl(delegate, this ) : null);
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
        mSession.setPermissionDelegate(delegate != null ? new PermissionDelegateImpl(delegate, this ) : null);
    }

    @Nullable
    @Override
    public PermissionDelegate getPermissionDelegate() {
        return mPermissionDelegate;
    }

    @Override
    public void setProgressDelegate(@Nullable ProgressDelegate delegate) {
        if (mProgressDelegate == delegate) {
            return;
        }
        mProgressDelegate = delegate;
        mSession.setProgressDelegate(delegate != null ? new ProgressDelegateImpl(delegate, this ) : null);
    }

    @Nullable
    @Override
    public ProgressDelegate getProgressDelegate() {
        return mProgressDelegate;
    }

    @Override
    public void setNavigationDelegate(@Nullable NavigationDelegate delegate) {
        if (mNavigationDelegate == delegate) {
            return;
        }
        mNavigationDelegate = delegate;
        mSession.setNavigationDelegate(delegate != null ? new NavigationDelegateImpl(delegate, this ) : null);
    }

    @Nullable
    @Override
    public NavigationDelegate getNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    public void setScrollDelegate(@Nullable ScrollDelegate delegate) {
        if (mScrollDelegate == delegate) {
            return;
        }
        mScrollDelegate = delegate;
        mSession.setScrollDelegate(delegate != null ? new ScrollDelegateImpl(delegate, this ) : null);
    }

    @Nullable
    @Override
    public ScrollDelegate getScrollDelegate() {
        return mScrollDelegate;
    }

    @Override
    public void setHistoryDelegate(@Nullable HistoryDelegate delegate) {
        if (mHistoryDelegate == delegate) {
            return;
        }
        mHistoryDelegate = delegate;
        mSession.setHistoryDelegate(delegate != null ? new HistoryDelegateImpl(delegate, this ) : null);
    }

    @Nullable
    @Override
    public HistoryDelegate getHistoryDelegate() {
        return mHistoryDelegate;
    }

    @Override
    public void setContentBlockingDelegate(@Nullable WContentBlocking.Delegate delegate) {
        if (mContentBlockingDelegate == delegate) {
            return;
        }
        mContentBlockingDelegate = delegate;
        mSession.setContentBlockingDelegate(delegate != null ? new ContentBlockingDelegateImpl(delegate, this) : null);
    }

    @Nullable
    @Override
    public WContentBlocking.Delegate getContentBlockingDelegate() {
        return mContentBlockingDelegate;
    }

    @Override
    public void setPromptDelegate(@Nullable PromptDelegate delegate) {
        if (mPromptDelegate == delegate) {
            return;
        }
        mPromptDelegate = delegate;
        mSession.setPromptDelegate(delegate != null ? new PromptDelegateImpl(delegate, this) : null);
    }

    @Nullable
    @Override
    public PromptDelegate getPromptDelegate() {
        return mPromptDelegate;
    }

    @Override
    public void setSelectionActionDelegate(@Nullable WSession.SelectionActionDelegate delegate) {
        if (mSelectionActionDelegate == delegate) {
            return;
        }
        mSelectionActionDelegate = delegate;
        mSession.setSelectionActionDelegate(delegate != null ? new SelectionActionDelegateImpl(delegate, this) : null);
    }

    @Override
    public void setMediaSessionDelegate(@Nullable WMediaSession.Delegate delegate) {
        if (mMediaSessionDelegate == delegate) {
            return;
        }
        mMediaSessionDelegate = delegate;
        mSession.setMediaSessionDelegate(delegate != null ? new MediaSessionDelegateImpl(this, delegate) : null);
    }

    @Nullable
    @Override
    public WMediaSession.Delegate getMediaSessionDelegate() {
        return mMediaSessionDelegate;
    }

    @Nullable
    @Override
    public WSession.SelectionActionDelegate getSelectionActionDelegate() {
        return mSelectionActionDelegate;
    }


    private int toGeckoFlags(@LoadFlags int flags) {
        int result = 0;
        if ((flags & WSession.LOAD_FLAGS_NONE) != 0) {
            result |= GeckoSession.LOAD_FLAGS_NONE;
        }
        if ((flags & WSession.LOAD_FLAGS_BYPASS_CACHE) != 0) {
            result |= GeckoSession.LOAD_FLAGS_BYPASS_CACHE;
        }
        if ((flags & WSession.LOAD_FLAGS_BYPASS_PROXY) != 0) {
            result |= GeckoSession.LOAD_FLAGS_BYPASS_PROXY;
        }
        if ((flags & WSession.LOAD_FLAGS_EXTERNAL) != 0) {
            result |= GeckoSession.LOAD_FLAGS_EXTERNAL;
        }
        if ((flags & WSession.LOAD_FLAGS_ALLOW_POPUPS) != 0) {
            result |= GeckoSession.LOAD_FLAGS_ALLOW_POPUPS;
        }
        if ((flags & WSession.LOAD_FLAGS_BYPASS_CLASSIFIER) != 0) {
            result |= GeckoSession.LOAD_FLAGS_BYPASS_CLASSIFIER;
        }
        return result;
    }

    @Override
    public UrlUtilsVisitor getUrlUtilsVisitor() {
        if (mUrlUtilsVisitor == null) {
            mUrlUtilsVisitor = new UrlUtilsVisitor() {
                private final List<String> ENGINE_SUPPORTED_SCHEMES = Arrays.asList("about", "data", "file", "ftp", "http", "https", "moz-extension", "moz-safe-about", "resource", "view-source", "ws", "wss", "blob");
                @Override
                public boolean isSupportedScheme(@NonNull String scheme) {
                    return ENGINE_SUPPORTED_SCHEMES.contains(scheme);
                }
            };
        }
        return mUrlUtilsVisitor;
    }
}
