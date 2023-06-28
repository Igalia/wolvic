/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.BookmarksStore;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.PromptDelegate;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.VideoAvailabilityListener;
import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WWebResponse;
import com.igalia.wolvic.browser.engine.EngineProvider;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionState;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.downloads.DownloadJob;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.ui.views.library.LibraryPanel;
import com.igalia.wolvic.ui.widgets.dialogs.PromptDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SelectionActionWidget;
import com.igalia.wolvic.ui.widgets.menus.ContextMenuWidget;
import com.igalia.wolvic.ui.widgets.prompts.PromptData;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.UrlUtils;
import com.igalia.wolvic.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mozilla.components.concept.storage.PageObservation;
import mozilla.components.concept.storage.PageVisit;
import mozilla.components.concept.storage.RedirectSource;
import mozilla.components.concept.storage.VisitType;


public class WindowWidget extends UIWidget implements SessionChangeListener,
        WSession.ContentDelegate, WSession.NavigationDelegate, VideoAvailabilityListener,
        WSession.HistoryDelegate, WSession.ProgressDelegate, WSession.SelectionActionDelegate,
        Session.WebXRStateChangedListener, Session.PopUpStateChangedListener,
        Session.DrmStateChangedListener, Session.ExternalRequestDelegate, SharedPreferences.OnSharedPreferenceChangeListener {

    @IntDef(value = { SESSION_RELEASE_DISPLAY, SESSION_DO_NOT_RELEASE_DISPLAY})
    public @interface OldSessionDisplayAction {}
    public static final int SESSION_RELEASE_DISPLAY = 0;
    public static final int SESSION_DO_NOT_RELEASE_DISPLAY = 1;

    @IntDef(value = { DEACTIVATE_CURRENT_SESSION, LEAVE_CURRENT_SESSION_ACTIVE})
    public @interface SetSessionActiveState {}
    public static final int DEACTIVATE_CURRENT_SESSION = 0;
    public static final int LEAVE_CURRENT_SESSION_ACTIVE = 1;

    private Surface mSurface;
    private int mWidth;
    private int mHeight;
    private int mHandle;
    private TopBarWidget mTopBar;
    private TitleBarWidget mTitleBar;
    private WidgetManagerDelegate mWidgetManager;
    private PromptDialogWidget mAlertDialog;
    private PromptDialogWidget mConfirmDialog;
    private PromptDialogWidget mAppDialog;
    private ContextMenuWidget mContextMenu;
    private SelectionActionWidget mSelectionMenu;
    private int mWidthBackup;
    private int mHeightBackup;
    private int mBorderWidth;
    private Runnable mFirstDrawCallback;
    private boolean mIsInVRVideoMode;
    private View mView;
    private Session mSession;
    private int mWindowId;
    private LibraryPanel mLibrary;
    private Windows.WindowPlacement mWindowPlacement = Windows.WindowPlacement.FRONT;
    private Windows.WindowPlacement mWindowPlacementBeforeFullscreen = Windows.WindowPlacement.FRONT;
    private float mMaxWindowScale = 3;
    private CopyOnWriteArrayList<WindowListener> mListeners;
    boolean mActive = false;
    boolean mHovered = false;
    boolean mClickedAfterFocus = false;
    private WidgetPlacement mPlacementBeforeFullscreen;
    private WidgetPlacement mPlacementBeforeResize;
    private boolean mIsResizing;
    private boolean mAfterFirstPaint;
    private boolean mCaptureOnPageStop;
    private PromptDelegate mPromptDelegate;
    private Executor mUIThreadExecutor;
    private WindowViewModel mViewModel;
    private CopyOnWriteArrayList<Runnable> mSetViewQueuedCalls;
    private SharedPreferences mPrefs;
    private DownloadsManager mDownloadsManager;
    private float mBrowserDensity;

    public interface WindowListener {
        default void onFocusRequest(@NonNull WindowWidget aWindow) {}
        default void onBorderChanged(@NonNull WindowWidget aWindow) {}
        default void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {}
        default void onFullScreen(@NonNull WindowWidget aWindow, boolean aFullScreen) {}
        default void onMediaFullScreen(@NonNull final WMediaSession mediaSession, boolean aFullScreen) {}
        default void onVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {}
        default void onKioskMode(WindowWidget aWindow, boolean isKioskMode) {}
    }

    @Override
    public void onSharedPreferenceChanged(@NonNull SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getString(R.string.settings_key_drm_playback))) {
            if (mViewModel.getIsDrmUsed().getValue().get() && getSession() != null) {
                getSession().reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }
    }

    public WindowWidget(Context aContext, int windowId, boolean privateMode)  {
        super(aContext);
        mWindowId = windowId;
        mSession = SessionStore.get().createSession(privateMode);
        initialize(aContext);
    }

    public WindowWidget(Context aContext, int windowId, Session aSession)  {
        super(aContext);
        mWindowId = windowId;
        mSession = aSession;
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        mSetViewQueuedCalls = new CopyOnWriteArrayList<>();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mWidgetManager = (WidgetManagerDelegate) aContext;
        // TODO: Fix the compositor in the browser engine to support correct border offset
        mBorderWidth = 0; //SettingsStore.getInstance(aContext).getTransparentBorderWidth();

        mDownloadsManager = mWidgetManager.getServicesProvider().getDownloadsManager();

        // ModelView creation and observers setup
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(hashCode()), WindowViewModel.class);
        mViewModel.setIsPrivateSession(mSession.isPrivateMode());
        mViewModel.setUrl(mSession.getCurrentUri());
        mViewModel.setIsDesktopMode(mSession.getUaMode() == WSessionSettings.USER_AGENT_MODE_DESKTOP);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mListeners = new CopyOnWriteArrayList<>();
        setupListeners(mSession);

        mLibrary = new LibraryPanel(aContext);

        SessionStore.get().getBookmarkStore().addListener(mBookmarksListener);

        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        mPlacementBeforeFullscreen = new WidgetPlacement(aContext);
        mPlacementBeforeResize = new WidgetPlacement(aContext);
        mIsResizing = false;
        initializeWidgetPlacement(mWidgetPlacement);
        if (mSession.isPrivateMode()) {
            mWidgetPlacement.clearColor = ViewUtils.ARGBtoRGBA(getContext().getColor(R.color.window_private_clear_color));
        } else {
            mWidgetPlacement.clearColor = ViewUtils.ARGBtoRGBA(getContext().getColor(R.color.window_blank_clear_color));
        }

        mTopBar = new TopBarWidget(aContext);
        mTopBar.attachToWindow(this);

        mTitleBar = new TitleBarWidget(aContext);
        mTitleBar.attachToWindow(this);

        mPromptDelegate = new PromptDelegate(getContext());
        mPromptDelegate.attachToWindow(this);

        setFocusable(true);
        TelemetryService.openWindowEvent(mWindowId);

        if (mSession.getWSession() != null) {
            onCurrentSessionChange(null, mSession.getWSession());
        }

        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        int windowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.width = windowWidth + mBorderWidth * 2;
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight() + mBorderWidth * 2;
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) *
                (float)windowWidth / (float)SettingsStore.WINDOW_WIDTH_DEFAULT;
        aPlacement.density = getBrowserDensity();
        aPlacement.visible = true;
        aPlacement.cylinder = true;
        aPlacement.textureScale = 1.0f;
        aPlacement.name = "Window";
        // Check Windows.placeWindow method for remaining placement set-up
    }

    void setupListeners(Session aSession) {
        aSession.addSessionChangeListener(this);
        aSession.addContentListener(this);
        aSession.addVideoAvailabilityListener(this);
        aSession.addNavigationListener(this);
        aSession.addProgressListener(this);
        aSession.setHistoryDelegate(this);
        aSession.addSelectionActionListener(this);
        aSession.addWebXRStateChangedListener(this);
        aSession.addPopUpStateChangedListener(this);
        aSession.addDrmStateChangedListener(this);
        aSession.setExternalRequestDelegate(this);
        aSession.setVideoAvailabilityDelegate(this);
    }

    void cleanListeners(Session aSession) {
        aSession.removeSessionChangeListener(this);
        aSession.removeContentListener(this);
        aSession.removeVideoAvailabilityListener(this);
        aSession.removeNavigationListener(this);
        aSession.removeProgressListener(this);
        aSession.setHistoryDelegate(null);
        aSession.removeSelectionActionListener(this);
        aSession.removeWebXRStateChangedListener(this);
        aSession.removePopUpStateChangedListener(this);
        aSession.removeDrmStateChangedListener(this);
        aSession.setExternalRequestDelegate(null);
        aSession.setVideoAvailabilityDelegate(null);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
        }

        mWidgetManager.updateWidget(this);

        setFocusableInTouchMode(false);
        if (aShowFlags == REQUEST_FOCUS) {
            requestFocusFromTouch();

        } else {
            clearFocus();
        }

        mSession.setActive(true);
    }

    @Override
    public void hide(@HideFlags int aHideFlag) {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
        }

        mWidgetManager.updateWidget(this);

        clearFocus();

        mSession.setActive(false);
    }

    @Override
    protected void onDismiss() {
        if (mViewModel.getIsLibraryVisible().getValue().get()) {
            if (!mLibrary.onBack()) {
                hidePanel();
            }

        } else {
            if (mSession.canGoBack()) {
                mSession.goBack();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.setActive(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVisible() || mIsInVRVideoMode) {
            mSession.setActive(true);
            if (!SettingsStore.getInstance(getContext()).getLayersEnabled() && !mSession.hasDisplay()) {
                // Ensure the Display is correctly recreated.
                // See: https://github.com/MozillaReality/FirefoxReality/issues/2880
                callSurfaceChanged();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mLibrary.onConfigurationChanged(newConfig);

        mViewModel.refresh();
    }

    public void close() {
        TelemetryService.closeWindowEvent(mWindowId);
        hideContextMenus();
        releaseWidget();
        mLibrary.onDestroy();
        mViewModel.setIsTopBarVisible(false);
        mViewModel.setIsTitleBarVisible(false);
        SessionStore.get().destroySession(mSession);
        if (mTopBar != null) {
            mWidgetManager.removeWidget(mTopBar);
            mTopBar.setDelegate((TopBarWidget.Delegate) null);
        }
        if (mTitleBar != null) {
            mWidgetManager.removeWidget(mTitleBar);
            mTitleBar.setDelegate((TitleBarWidget.Delegate) null);
        }
        mListeners.clear();

        if (mPromptDelegate != null){
            mPromptDelegate.hideAllPrompts();
        }
    }

    public boolean isCurrentUriBlank() {
        final String currentUri = mSession.getCurrentUri();
        return (currentUri == null) || currentUri.isEmpty() || UrlUtils.isBlankUri(getContext(), currentUri);
    }

    public void loadHome() {
        if (mSession.isPrivateMode()) {
            mSession.loadPrivateBrowsingPage();

        } else {
            mSession.loadUri(SettingsStore.getInstance(getContext()).getHomepage());
        }
    }

    private void recreateWidgetSurfaceIfNeeded(float prevDensity) {
        if (prevDensity != mWidgetPlacement.density || !isLayer())
            return;

        // If the densities are the same updateWidget won't generate a new surface as the resulting
        // texture sizes are equal. We need to force a new surface creation when using layers.
        mWidgetManager.recreateWidgetSurface(this);
    }

    private void setView(View view, boolean switchSurface) {
        Runnable setView = () -> {
            if (switchSurface) {
                pauseCompositor();
            }

            mView = view;
            removeView(view);
            mView.setVisibility(VISIBLE);
            addView(mView);

            if (switchSurface) {
                float prevDensity = mWidgetPlacement.density;
                mWidgetPlacement.density = getContext().getResources().getDisplayMetrics().density;
                if (mTexture != null && mSurface != null && mRenderer == null) {
                    // Create the UI Renderer for the current surface.
                    // Surface must be released when switching back to WebView surface or the browser
                    // will not render it correctly. See release code in unsetView().
                    mRenderer = new UISurfaceTextureRenderer(mSurface, mWidgetPlacement.textureWidth(), mWidgetPlacement.textureHeight());
                }
                mWidgetManager.updateWidget(WindowWidget.this);
                mWidgetManager.pushWorldBrightness(WindowWidget.this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
                mWidgetManager.pushBackHandler(mBackHandler);
                setWillNotDraw(false);
                postInvalidate();

                recreateWidgetSurfaceIfNeeded(prevDensity);
            }
        };

        if (mAfterFirstPaint) {
            setView.run();

        } else {
            mSetViewQueuedCalls.add(setView);
        }
    }

    private void unsetView(View view, boolean switchSurface) {
        mSetViewQueuedCalls.clear();
        if (mView != null && mView == view) {
            mView = null;
            removeView(view);
            view.setVisibility(GONE);

            if (switchSurface) {
                setWillNotDraw(true);
                if (mTexture != null) {
                    // Surface must be recreated here when not using layers.
                    // When using layers the new Surface is received via the setSurface() method.
                    if (mRenderer != null) {
                        mRenderer.release();
                        mRenderer = null;
                    }
                    mSurface = new Surface(mTexture);
                }
                float prevDensity = mWidgetPlacement.density;
                mWidgetPlacement.density = getBrowserDensity();
                mWidgetManager.updateWidget(WindowWidget.this);
                mWidgetManager.popWorldBrightness(WindowWidget.this);
                mWidgetManager.popBackHandler(mBackHandler);
                if (mTexture != null) {
                    resumeCompositor();
                }
                recreateWidgetSurfaceIfNeeded(prevDensity);
            }
        }
    }

    public boolean isLibraryVisible() {
        return mViewModel.getIsLibraryVisible().getValue().get();
    }

    public int getWindowWidth() {
        return mWidgetPlacement.width;
    }

    public int getWindowHeight() {
        return mWidgetPlacement.height;
    }

    public @Windows.PanelType
    int getSelectedPanel() {
        return mLibrary.getSelectedPanelType();
    }

    private void hideLibraryPanel() {
        if (mViewModel.getIsLibraryVisible().getValue().get()) {
            hidePanel(true);
        }
    }

    public void switchPanel(@Windows.PanelType int panelType) {
        if (mViewModel.getIsLibraryVisible().getValue().get()) {
            hidePanel(true);

        } else {
            showPanel(panelType, true);
        }
    }

    Runnable mRestoreFirstPaint;

    public void showPanel(@Windows.PanelType int panelType) {
        showPanel(panelType, true);
    }

    private void showPanel(@Windows.PanelType int panelType, boolean switchSurface) {
        if (mLibrary != null) {
            if (mView == null) {
                setView(mLibrary, switchSurface);
                mLibrary.selectPanel(panelType);
                mLibrary.onShow();
                mViewModel.setIsPanelVisible(true);
                if (mRestoreFirstPaint == null && !isFirstPaintReady() && (mFirstDrawCallback != null) && (mSurface != null)) {
                    final Runnable firstDrawCallback = mFirstDrawCallback;
                    onFirstContentfulPaint(mSession.getWSession());
                    mRestoreFirstPaint = () -> {
                        setFirstPaintReady(false);
                        setFirstDrawCallback(firstDrawCallback);
                        if (mWidgetManager != null) {
                            mWidgetManager.updateWidget(WindowWidget.this);
                        }
                    };
                }

            } else if (mView == mLibrary) {
                mLibrary.selectPanel(panelType);
            }
        }
    }

    public void hidePanel() {
        hidePanel(true);
    }

    private void hidePanel(boolean switchSurface) {
        if (mView != null && mLibrary != null) {
            unsetView(mLibrary, switchSurface);
            mLibrary.onHide();
            mViewModel.setIsPanelVisible(false);
        }
        if (switchSurface && mRestoreFirstPaint != null) {
            mRestoreFirstPaint.run();
            mRestoreFirstPaint = null;
        }
    }

    public void pauseCompositor() {
        if (mSession == null) {
            return;
        }

        mSession.surfaceDestroyed();
    }

    public void resumeCompositor() {
        if (mSession == null) {
            return;
        }
        if (mSurface == null) {
            return;
        }

        callSurfaceChanged();
    }

    public void enableVRVideoMode(int aVideoWidth, int aVideoHeight, boolean aResetBorder) {
        if (!mIsInVRVideoMode) {
            mWidthBackup = mWidth;
            mHeightBackup = mHeight;
            mIsInVRVideoMode = true;
        }
        boolean borderChanged = aResetBorder && mBorderWidth > 0;
        if (aVideoWidth == mWidth && aVideoHeight == mHeight && !borderChanged) {
            return;
        }
        if (aResetBorder) {
            mBorderWidth = 0;
        }
        mWidgetPlacement.width = aVideoWidth + mBorderWidth * 2;
        mWidgetPlacement.height = aVideoHeight + mBorderWidth * 2;
        mWidgetManager.updateWidget(this);

        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    public void disableVRVideoMode() {
        if (!mIsInVRVideoMode || mWidthBackup == 0 || mHeightBackup == 0) {
            return;
        }
        mIsInVRVideoMode = false;

        // TODO: Fix the compositor to support correct border offset
        int border = 0; // SettingsStore.getInstance(getContext()).getTransparentBorderWidth();
        if (mWidthBackup == mWidth && mHeightBackup == mHeight && border == mBorderWidth) {
            return;
        }
        mBorderWidth = border;
        mWidgetPlacement.width = mWidthBackup;
        mWidgetPlacement.height = mHeightBackup;
        mWidgetManager.updateWidget(this);

        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    public void setWindowPlacement(@NonNull Windows.WindowPlacement aPlacement) {
        if (mActive) {
            TelemetryService.activePlacementEvent(mWindowPlacement.getValue(), false);
        }
        mWindowPlacement = aPlacement;
        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
        mViewModel.setPlacement(mWindowPlacement);
        if (mActive) {
            TelemetryService.activePlacementEvent(mWindowPlacement.getValue(), true);
        }
    }

    public void setIsOnlyWindow(boolean isOnlyWindow) {
        mViewModel.setIsOnlyWindow(isOnlyWindow);
    }

    public @NonNull Windows.WindowPlacement getWindowPlacementBeforeFullscreen() {
        return mWindowPlacementBeforeFullscreen;
    }

    public @NonNull Windows.WindowPlacement getWindowPlacement() {
        return mWindowPlacement;
    }

    @Override
    public void resizeByMultiplier(float aspect, float multiplier) {
        Pair<Float, Float> targetSize = getSizeForScale(multiplier, aspect);
        handleResizeEvent(targetSize.first, targetSize.second);
    }

    public float getCurrentScale() {
        float currentAspect = getCurrentAspect();
        float currentWorldHeight = mWidgetPlacement.worldWidth / currentAspect;
        float currentArea = mWidgetPlacement.worldWidth * currentWorldHeight;
        float defaultWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        float defaultHeight = defaultWidth / SettingsStore.getInstance(getContext()).getWindowAspect();
        float defaultArea = defaultWidth * defaultHeight;
        return currentArea / defaultArea;
    }

    public float getCurrentAspect() {
        return (float) mWidgetPlacement.width / (float) mWidgetPlacement.height;
    }

    public int getBorderWidth() {
        return mBorderWidth;
    }

    public void setActiveWindow(boolean active) {
        mActive = active;
        if (active) {
            SessionStore.get().setActiveSession(mSession);
            WSession session = mSession.getWSession();
            if (session != null) {
                session.getTextInput().setView(this);
            }
            mSession.updateLastUse();
            mWidgetManager.getNavigationBar().addNavigationBarListener(mNavigationBarListener);
            mViewModel.setIsDesktopMode(mSession.getUaMode() == WSessionSettings.USER_AGENT_MODE_DESKTOP);

        } else {
            mWidgetManager.getNavigationBar().removeNavigationBarListener(mNavigationBarListener);
            updateBookmarked();

        }

        hideContextMenus();

        TelemetryService.activePlacementEvent(mWindowPlacement.getValue(), mActive);
        updateBorder();

        mViewModel.setIsActiveWindow(active);

        // Remove tha back handler in case there is a library view visible, otherwise it gets dismissed
        // when back is clicked even if other window is focused.
        if (mView != null) {
            if (active) {
                mWidgetManager.pushBackHandler(mBackHandler);
            } else {
                mWidgetManager.popBackHandler(mBackHandler);
            }
        }
    }

    @Nullable
    public Session getSession() {
        return mSession;
    }

    public TopBarWidget getTopBar() {
        return mTopBar;
    }

    public void setTopBar(TopBarWidget aWidget) {
        if (mTopBar != aWidget) {
            mTopBar = aWidget;
            mTopBar.attachToWindow(this);
        }
    }

    public void setResizeMode(boolean resizing) {
        mViewModel.setIsResizeMode(resizing);
    }

    public TitleBarWidget getTitleBar() {
        return mTitleBar;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        mFirstDrawCallback = aFirstDrawCallback;
        if (mView != null) {
            super.setSurfaceTexture(aTexture, aWidth, aHeight, aFirstDrawCallback);

        } else {
            WSession session = mSession.getWSession();
            if (session == null) {
                return;
            }
            if (aTexture == null) {
                setWillNotDraw(true);
                return;
            }
            mWidth = aWidth;
            mHeight = aHeight;
            mTexture = aTexture;
            aTexture.setDefaultBufferSize(aWidth, aHeight);
            mSurface = new Surface(aTexture);
            callSurfaceChanged();
        }
    }

    @Override
    public void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        if (mView != null) {
            super.setSurface(aSurface, aWidth, aHeight, aFirstDrawCallback);

        } else {
            mWidth = aWidth;
            mHeight = aHeight;
            mSurface = aSurface;
            mFirstDrawCallback = aFirstDrawCallback;
            if (mSurface != null) {
                callSurfaceChanged();
            } else {
                mSession.surfaceDestroyed();
            }
        }
    }

    private void callSurfaceChanged() {
        if (mSession != null && mSurface != null) {
            mSession.surfaceChanged(mSurface, mBorderWidth, mBorderWidth, mWidth - mBorderWidth * 2, mHeight - mBorderWidth * 2);
            mSession.updateLastUse();
        }
    }

    @Override
    public void resizeSurface(final int aWidth, final int aHeight) {
        if (mView != null) {
            super.resizeSurface(aWidth, aHeight);
        }

        mWidth = aWidth;
        mHeight = aHeight;
        if (mTexture != null) {
            mTexture.setDefaultBufferSize(aWidth, aHeight);
        }

        if (mTexture != null && mSurface != null && mView == null) {
            callSurfaceChanged();
        }
    }

    @Override
    public int getHandle() {
        return mHandle;
    }

    @Override
    public WidgetPlacement getPlacement() {
        return mWidgetPlacement;
    }

    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
        if (aEvent.getAction() == MotionEvent.ACTION_DOWN) {
            if (!mActive) {
                mClickedAfterFocus = true;
                updateBorder();
                // Focus this window
                for (WindowListener listener: mListeners) {
                    listener.onFocusRequest(this);
                }
                // Return to discard first click after focus
                return;
            }
        } else if (aEvent.getAction() == MotionEvent.ACTION_UP || aEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            mClickedAfterFocus = false;
            updateBorder();
        }

        if (!mActive) {
            // Do not send touch events to not focused windows.
            return;
        }

        if (mView != null) {
            super.handleTouchEvent(aEvent);

        } else {
            if (aEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                requestFocus();
                requestFocusFromTouch();
            }
            WSession session = mSession.getWSession();
            if (session != null) {
                session.getPanZoomController().onTouchEvent(aEvent);
            }
        }
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        if (aEvent.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
            mHovered = true;
            updateBorder();
        } else if (aEvent.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            mHovered = false;
            updateBorder();
        }

        if (!mActive) {
            // Do not send touch events to not focused windows.
            return;
        }

        if (aEvent.getAction() == MotionEvent.ACTION_SCROLL) {
            requestFocusFromTouch();
        }

        if (mView != null) {
            super.handleHoverEvent(aEvent);

        } else {
            WSession session = mSession.getWSession();
            if (session != null && !isContextMenuVisible()) {
                session.getPanZoomController().onMotionEvent(aEvent);
            }
        }
    }

    protected void updateBorder() {
        int color = 0;
        if (!mActive && !mClickedAfterFocus && mHovered) {
            color = ViewUtils.ARGBtoRGBA(getContext().getColor(R.color.window_border_hover));
        } else if (mClickedAfterFocus) {
            color = ViewUtils.ARGBtoRGBA(getContext().getColor(R.color.window_border_click));
        }
        if (mWidgetPlacement.borderColor != color) {
            mWidgetPlacement.borderColor = color;
            mWidgetManager.updateWidget(this);
            for (WindowListener listener: mListeners) {
                listener.onBorderChanged(this);
            }
        }
    }

    public void saveBeforeFullscreenPlacement() {
        mWindowPlacementBeforeFullscreen = mWindowPlacement;
        mPlacementBeforeFullscreen.copyFrom(mWidgetPlacement);
    }

    public void restoreBeforeFullscreenPlacement() {
        // We need to process `composited` separately to handle GV content process onCrash/onKill.
        // Composited is false after a content crash but it was true when the placement was saved.
        boolean composited = mWidgetPlacement.composited;
        mWindowPlacement = mWindowPlacementBeforeFullscreen;
        mWidgetPlacement.copyFrom(mPlacementBeforeFullscreen);
        mWidgetPlacement.composited = composited;
        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    public WidgetPlacement getBeforeFullscreenPlacement() {
        return mPlacementBeforeFullscreen;
    }

    public void saveBeforeResizePlacement() {
        mPlacementBeforeResize.copyFrom(mWidgetPlacement);
    }

    public void restoreBeforeResizePlacement() {
        mWidgetPlacement.copyFrom(mPlacementBeforeResize);
        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    public WidgetPlacement getBeforeResizePlacement() {
        return mPlacementBeforeResize;
    }

    public void setIsResizing(boolean isResizing) {
        mIsResizing = isResizing;
    }

    public boolean isResizing() {
        return mIsResizing;
    }

    public void setIsFullScreen(boolean isFullScreen) {
        if (mViewModel.getIsFullscreen().getValue().get() != isFullScreen) {
            mViewModel.setIsFullscreen(isFullScreen);
            for (WindowListener listener: mListeners) {
                listener.onFullScreen(this, isFullScreen);
            }
        }
    }

    public boolean isFullScreen() {
        return mViewModel.getIsFullscreen().getValue().get();
    }

    public void setKioskMode(boolean isKioskMode) {
        if (mViewModel.getIsKioskMode().getValue().get() == isKioskMode)
            return;

        mViewModel.setIsKioskMode(isKioskMode);
        for (WindowListener listener : mListeners) {
            listener.onKioskMode(this, isKioskMode);
        }
    }

    public boolean isKioskMode() {
        return mViewModel.getIsKioskMode().getValue().get();
    }

    public void addWindowListener(WindowListener aListener) {
        if (!mListeners.contains(aListener)) {
            mListeners.add(aListener);
        }
    }

    public void removeWindowListener(WindowListener aListener) {
        mListeners.remove(aListener);
    }

    public void waitForFirstPaint() {
        setFirstPaintReady(false);
        setFirstDrawCallback(() -> {
            if (!isFirstPaintReady()) {
                setFirstPaintReady(true);
                mWidgetManager.updateWidget(WindowWidget.this);
            }
        });
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void handleResizeEvent(float aWorldWidth, float aWorldHeight) {
        int width = getWindowWidth(aWorldWidth);
        float aspect = aWorldWidth / aWorldHeight;
        int height = (int) Math.floor((float)width / aspect);
        mWidgetPlacement.width = width + mBorderWidth * 2;
        mWidgetPlacement.height = height + mBorderWidth * 2;
        mWidgetPlacement.worldWidth = aWorldWidth;
        mWidgetManager.updateWidget(this);
        mWidgetManager.updateVisibleWidgets();

        mViewModel.setWidth(mWidgetPlacement.width);
        mViewModel.setHeight(mWidgetPlacement.height);
    }

    @Override
    public void releaseWidget() {
        cleanListeners(mSession);
        WSession session = mSession.getWSession();

        mPrefs.unregisterOnSharedPreferenceChangeListener(this);

        mSetViewQueuedCalls.clear();
        if (mSession != null) {
            mSession.releaseDisplay();
        }
        if (session != null) {
            session.getTextInput().setView(null);
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mTexture != null && mRenderer == null) {
            // Custom SurfaceTexture used for the browser window
            mTexture.release();
            mTexture = null;
        }
        mWidgetManager.getNavigationBar().removeNavigationBarListener(mNavigationBarListener);
        SessionStore.get().getBookmarkStore().removeListener(mBookmarksListener);
        mPromptDelegate.detachFromWindow();
        super.releaseWidget();
    }


    @Override
    public void setFirstPaintReady(final boolean aFirstPaintReady) {
        mWidgetPlacement.composited = aFirstPaintReady;
        if (!aFirstPaintReady) {
            mAfterFirstPaint = false;
        }
    }

    public void setFirstDrawCallback(Runnable aRunnable) {
        mFirstDrawCallback = aRunnable;
    }

    @Override
    public boolean isFirstPaintReady() {
        return mWidgetPlacement != null && mWidgetPlacement.composited;
    }

    @Override
    public boolean isVisible() {
        return mWidgetPlacement.visible;
    }

    @Override
    public boolean isLayer() {
        return mSurface != null && mTexture == null;
    }


    @Override
    public void setVisible(boolean aVisible) {
        if (mWidgetPlacement.visible == aVisible) {
            return;
        }

        if (!mIsInVRVideoMode) {
            mSession.setActive(aVisible);
            if (aVisible) {
                callSurfaceChanged();
            }
        }
        mWidgetPlacement.visible = aVisible;
        if (!aVisible) {
            if (mViewModel.getIsLibraryVisible().getValue().get()) {
                mWidgetManager.popWorldBrightness(this);
            }

        } else {
            if (mViewModel.getIsLibraryVisible().getValue().get()) {
                mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
            }
        }
        mWidgetManager.updateWidget(this);
        if (!aVisible) {
            clearFocus();
        }

        mViewModel.setIsWindowVisible(aVisible);
    }

    @Override
    public void draw(Canvas aCanvas) {
        if (mView != null) {
            super.draw(aCanvas);
        }
    }

    public void setSession(@NonNull Session aSession, @SetSessionActiveState int previousSessionState) {
        setSession(aSession, SESSION_RELEASE_DISPLAY, previousSessionState);
    }

    public void setSession(@NonNull Session aSession, @SetSessionActiveState int previousSessionState, boolean hidePanel) {
        setSession(aSession, SESSION_RELEASE_DISPLAY, previousSessionState, hidePanel);
    }

    public void setSession(@NonNull Session aSession, @OldSessionDisplayAction int aDisplayAction, @SetSessionActiveState int previousSessionState) {
        setSession(aSession, aDisplayAction, previousSessionState, true);
    }

    public void setSession(@NonNull Session aSession, @OldSessionDisplayAction int aDisplayAction, @SetSessionActiveState int previousSessionState, boolean hidePanel) {
        if (mSession != aSession) {
            Session oldSession = mSession;
            if (oldSession != null) {
                cleanListeners(oldSession);
                if (previousSessionState == DEACTIVATE_CURRENT_SESSION) {
                    oldSession.setActive(false);
                }
                if (aDisplayAction == SESSION_RELEASE_DISPLAY) {
                    oldSession.releaseDisplay();
                }
            }

            mSession = aSession;

            setupListeners(mSession);
            SessionStore.get().setActiveSession(mSession);

            mViewModel.setIsPrivateSession(mSession.isPrivateMode());
            mViewModel.setIsDesktopMode(mSession.getUaMode() == WSessionSettings.USER_AGENT_MODE_DESKTOP);

            if (hidePanel) {
                if (oldSession != null) {
                    onCurrentSessionChange(oldSession.getWSession(), aSession.getWSession());
                } else {
                    onCurrentSessionChange(null, aSession.getWSession());
                }
            }

            for (WindowListener listener: mListeners) {
                listener.onSessionChanged(oldSession, aSession);
            }
        }
        mCaptureOnPageStop = false;

        if (hidePanel) {
            hideLibraryPanel();
        }
    }

    public void setDrmUsed(boolean isEnabled) {
        mViewModel.setIsDrmUsed(isEnabled);
    }

    // Session.ISessionChange
    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        Log.d(LOGTAG, "onCurrentSessionChange: " + this.hashCode());

        Log.d(LOGTAG, "surfaceChanged: " + aSession.hashCode());
        callSurfaceChanged();
        aSession.getTextInput().setView(this);

        mViewModel.setIsPrivateSession(aSession.getSettings().getUsePrivateMode());
        mViewModel.setIsDesktopMode(mSession.getUaMode() == WSessionSettings.USER_AGENT_MODE_DESKTOP);

        // Update the title bar media controls state
        boolean mediaAvailable = mSession.getActiveVideo() != null;
        if (mediaAvailable) {
            if (mSession.getActiveVideo().isPlaying()) {
                mViewModel.setIsMediaPlaying(true);
            }
            mViewModel.setIsMediaAvailable(true);

        } else {
            mViewModel.setIsMediaAvailable(false);
            mViewModel.setIsMediaPlaying(false);
        }

        waitForFirstPaint();
    }

    @Override
    public void onStackSession(Session aSession) {
        if (aSession == mSession) {
            Log.e(LOGTAG, "Attempting to stack same session.");
            return;
        }
        // e.g. tab opened via window.open()
        aSession.updateLastUse();
        Session current = mSession;
        setSession(aSession, WindowWidget.DEACTIVATE_CURRENT_SESSION);
        current.captureBackgroundBitmap(getWindowWidth(), getWindowHeight()).thenAccept(aVoid -> current.setActive(false));

        // Delay the notification so it it's displayed in the tray when a link in
        // full screen ones in a new tab. Otherwise the navigation bar has not the correct size and
        // the notification is misplaced.
        postDelayed(() -> mWidgetManager.getWindows().showTabAddedNotification(), 500);

        TelemetryService.Tabs.openedCounter(TelemetryService.Tabs.TabSource.BROWSER);
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        if (mSession == aSession) {
            setSession(aParent, WindowWidget.DEACTIVATE_CURRENT_SESSION);
        }
    }

    // View
    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        Log.d(LOGTAG, "BrowserWidget onCreateInputConnection");
        WSession session = mSession.getWSession();
        if (session == null || mView != null) {
            return null;
        }
        return session.getTextInput().onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return !mIsResizing && mSession.isInputActive();
    }


    @Override
    public boolean onKeyPreIme(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyPreIme(aKeyCode, aEvent)) {
            return true;
        }
        WSession session = mSession.getWSession();
        return (session != null) && session.getTextInput().onKeyPreIme(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyUp(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyUp(aKeyCode, aEvent)) {
            return true;
        }
        WSession session = mSession.getWSession();
        return (session != null) && session.getTextInput().onKeyUp(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyDown(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyDown(aKeyCode, aEvent)) {
            return true;
        }
        WSession session = mSession.getWSession();
        return (session != null) && session.getTextInput().onKeyDown(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyLongPress(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyLongPress(aKeyCode, aEvent)) {
            return true;
        }
        WSession session = mSession.getWSession();
        return (session != null) && session.getTextInput().onKeyLongPress(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyMultiple(int aKeyCode, int repeatCount, KeyEvent aEvent) {
        if (super.onKeyMultiple(aKeyCode, repeatCount, aEvent)) {
            return true;
        }
        WSession session = mSession.getWSession();
        return (session != null) && session.getTextInput().onKeyMultiple(aKeyCode, repeatCount, aEvent);
    }

    @Override
    protected void onFocusChanged(boolean aGainFocus, int aDirection, Rect aPreviouslyFocusedRect) {
        super.onFocusChanged(aGainFocus, aDirection, aPreviouslyFocusedRect);
        Log.d(LOGTAG, "BrowserWidget onFocusChanged: " + (aGainFocus ? "true" : "false"));
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        WSession session = mSession.getWSession();
        if (session == null) {
            return false;
        }
        session.getPanZoomController().onTouchEvent(aEvent);
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (mView != null) {
            return super.onGenericMotionEvent(aEvent);
        } else {
            WSession session = mSession.getWSession();
            if (session == null) {
                return false;
            }
            session.getPanZoomController().onMotionEvent(aEvent);
            return true;
        }
    }

    public void showAlert(String title, String msg, @Nullable PromptDialogWidget.Delegate callback) {
        if (mAlertDialog == null) {
            mAlertDialog = new PromptDialogWidget(getContext());
            mAlertDialog.setButtons(new int[] {
                    R.string.ok_button
            });
            mAlertDialog.setCheckboxVisible(false);
            mAlertDialog.setDescriptionVisible(false);
        }
        mAlertDialog.setTitle(title);
        mAlertDialog.setBody(msg);
        mAlertDialog.setButtonsDelegate((index, isChecked) -> {
            mAlertDialog.hide(REMOVE_WIDGET);
            if (callback != null) {
                callback.onButtonClicked(index, isChecked);
            }
            mAlertDialog.releaseWidget();
            mAlertDialog = null;
        });
        mAlertDialog.setLinkDelegate((widget, url) ->  {
            mWidgetManager.openNewTabForeground(url);
            mAlertDialog.hide(REMOVE_WIDGET);
            mAlertDialog.releaseWidget();
            mAlertDialog = null;
        });
        mAlertDialog.show(REQUEST_FOCUS);
    }

    public void hideConfirmPrompt() {
        if (mConfirmDialog != null) {
            mConfirmDialog.onDismiss();
        }
    }

    public void showConfirmPrompt(@NonNull String title,
                                  String msg,
                                  @NonNull String[] btnMsg,
                                  @Nullable PromptDialogWidget.Delegate callback) {
        PromptData data = new PromptData.Builder()
                .withTitle(title)
                .withBody(msg)
                .withBtnMsg(btnMsg)
                .withCallback(callback)
                .build();
        showConfirmPrompt(data);
    }

    public void showConfirmPrompt(@DrawableRes int icon,
                                  @NonNull String title,
                                  String msg,
                                  @NonNull String[] btnMsg,
                                  @Nullable PromptDialogWidget.Delegate callback) {
        PromptData data = new PromptData.Builder()
                .withIconRes(icon)
                .withTitle(title)
                .withBody(msg)
                .withBtnMsg(btnMsg)
                .withCallback(callback)
                .build();
        showConfirmPrompt(data);
    }

    public void showConfirmPrompt(@NonNull String title,
                                  String msg,
                                  @NonNull String[] btnMsg,
                                  @NonNull String checkBoxText,
                                  @Nullable PromptDialogWidget.Delegate callback) {
        PromptData data = new PromptData.Builder()
                .withTitle(title)
                .withBody(msg)
                .withBtnMsg(btnMsg)
                .withCheckboxText(checkBoxText)
                .withCallback(callback)
                .build();
        showConfirmPrompt(data);
    }

    public void showConfirmPrompt(@NonNull PromptData promptData) {
        mConfirmDialog = confirmPrompt(promptData);
        mConfirmDialog.show(REQUEST_FOCUS);
    }

    private PromptDialogWidget confirmPrompt(@NonNull PromptData promptData) {
        if (mConfirmDialog == null) {
            mConfirmDialog = new PromptDialogWidget(getContext());
            mConfirmDialog.setButtons(new int[] {
                    R.string.cancel_button,
                    R.string.ok_button
            });
            mConfirmDialog.setCheckboxVisible(false);
            mConfirmDialog.setDescriptionVisible(false);
        }
        mConfirmDialog.setTitle(promptData.getTitle());
        mConfirmDialog.setBody(promptData.getBody());
        if (promptData.getBodyGravity() != Gravity.NO_GRAVITY) {
            mConfirmDialog.setBodyGravity(promptData.getBodyGravity());
        }
        mConfirmDialog.setButtons(promptData.getBtnMsg());
        mConfirmDialog.setButtonsDelegate((index, isChecked) -> {
            mConfirmDialog.hide(REMOVE_WIDGET);
            if (promptData.getCallback() != null) {
                promptData.getCallback().onButtonClicked(index, isChecked);
            }
            mConfirmDialog.releaseWidget();
            mConfirmDialog = null;
        });
        if (promptData.getCheckboxText() != null) {
            mConfirmDialog.setCheckboxVisible(true);
            mConfirmDialog.setCheckboxText(promptData.getCheckboxText());

        } else {
            mConfirmDialog.setCheckboxVisible(false);
        }
        if (promptData.getIconType() == PromptData.RES) {
            mConfirmDialog.setIcon(promptData.getIconRes());

        } else if (promptData.getIconType() == PromptData.URL) {
            mConfirmDialog.setIcon(promptData.getIconUrl());
        }
        mConfirmDialog.setLinkDelegate((widget, url) ->  {
            mWidgetManager.openNewTabForeground(url);
            mConfirmDialog.hide(REMOVE_WIDGET);
            mConfirmDialog.releaseWidget();
            mConfirmDialog = null;
        });

        return mConfirmDialog;
    }

    public void showDialog(@NonNull String title, @StringRes int  description, @NonNull  @StringRes int [] btnMsg,
                           @Nullable PromptDialogWidget.Delegate buttonsCallback, @Nullable Runnable linkCallback) {
        mAppDialog = new PromptDialogWidget(getContext());
        mAppDialog.setIconVisible(false);
        mAppDialog.setCheckboxVisible(false);
        mAppDialog.setDescriptionVisible(false);
        mAppDialog.setTitle(title);
        mAppDialog.setBody(description);
        mAppDialog.setButtons(btnMsg);
        mAppDialog.setButtonsDelegate((index, isChecked) -> {
            mAppDialog.hide(REMOVE_WIDGET);
            if (buttonsCallback != null) {
                buttonsCallback.onButtonClicked(index, isChecked);
            }
            mAppDialog.releaseWidget();
        });
        mAppDialog.setLinkDelegate((widget, url) -> {
            mWidgetManager.openNewTabForeground(url);
            mAppDialog.hide(REMOVE_WIDGET);
            if (linkCallback != null) {
                linkCallback.run();
            }
            mAppDialog.releaseWidget();
            mAppDialog = null;
        });
        mAppDialog.show(REQUEST_FOCUS);
    }

    public void showFirstTimeDrmDialog(@NonNull Runnable callback) {
        showConfirmPrompt(
                R.drawable.ic_icon_drm_allowed,
                getContext().getString(R.string.drm_first_use_title_v1),
                getContext().getString(R.string.drm_first_use_body_v2, getResources().getString(R.string.sumo_drm_url)),
                new String[]{
                        getContext().getString(R.string.drm_first_use_do_not_allow),
                        getContext().getString(R.string.drm_first_use_allow),
                },
                (index, isChecked) -> {
                    // We remove the prefs listener before the first DRM update to avoid reloading the session
                    mPrefs.unregisterOnSharedPreferenceChangeListener(this);
                    SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(index == PromptDialogWidget.POSITIVE);
                    mPrefs.registerOnSharedPreferenceChangeListener(this);
                    callback.run();
                }
        );
    }

    public void setMaxWindowScale(float aScale) {
        if (mMaxWindowScale != aScale) {
            mMaxWindowScale = aScale;

            Pair<Float, Float> maxSize = getSizeForScale(aScale);

            if (mWidgetPlacement.worldWidth > maxSize.first) {
                float currentAspect = (float) mWidgetPlacement.width / (float) mWidgetPlacement.height;
                mWidgetPlacement.worldWidth = maxSize.first;
                mWidgetPlacement.width = getWindowWidth(maxSize.first);
                mWidgetPlacement.height = (int) Math.ceil((float)mWidgetPlacement.width / currentAspect);

                mViewModel.setWidth(mWidgetPlacement.width);
                mViewModel.setHeight(mWidgetPlacement.height);
            }
        }
    }

    public float getMaxWindowScale() {
        Windows windows = mWidgetManager.getWindows();
        if (windows != null) {
            windows.updateMaxWindowScales();
        }
        return mMaxWindowScale;
    }

    public @NonNull Pair<Float, Float> getSizeForScale(float aScale) {
        return getSizeForScale(aScale, SettingsStore.getInstance(getContext()).getWindowAspect());
    }

    public @NonNull Pair<Float, Float> getSizeForScale(float aScale, float aAspect) {
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) *
                (float)SettingsStore.getInstance(getContext()).getWindowWidth() / (float)SettingsStore.WINDOW_WIDTH_DEFAULT;
        float worldHeight = worldWidth / aAspect;
        float area = worldWidth * worldHeight * aScale;
        float targetWidth = (float) Math.sqrt(area * aAspect);
        float targetHeight = targetWidth / aAspect;
        return Pair.create(targetWidth, targetHeight);
    }

    private int getWindowWidth(float aWorldWidth) {
        return (int) Math.floor(SettingsStore.WINDOW_WIDTH_DEFAULT * aWorldWidth / WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width));
    }

    private NavigationBarWidget.NavigationListener mNavigationBarListener = new NavigationBarWidget.NavigationListener() {
        @Override
        public void onBack() {
            hideLibraryPanel();
        }

        @Override
        public void onForward() {
            hideLibraryPanel();
        }

        @Override
        public void onReload() {
            hideLibraryPanel();
        }

        @Override
        public void onStop() {
            // Nothing to do
        }

        @Override
        public void onHome() {
            hideLibraryPanel();
        }
    };

    private BookmarksStore.BookmarkListener mBookmarksListener = new BookmarksStore.BookmarkListener() {
        @Override
        public void onBookmarksUpdated() {
            updateBookmarked();
        }

        @Override
        public void onBookmarkAdded() {
            updateBookmarked();
        }
    };

    private void updateBookmarked() {
        SessionStore.get().getBookmarkStore().isBookmarked(mViewModel.getUrl().getValue().toString()).thenAcceptAsync(bookmarked -> {
            if (bookmarked) {
                mViewModel.setIsBookmarked(true);

            } else {
                mViewModel.setIsBookmarked(false);
            }
        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error checking bookmark: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    private void hideContextMenus() {
        if (mContextMenu != null) {
            if (!mContextMenu.isReleased()) {
                if (mContextMenu.isVisible()) {
                    mContextMenu.hide(REMOVE_WIDGET);
                }
                mContextMenu.releaseWidget();
            }
            mContextMenu = null;
        }
        if (mSelectionMenu != null) {
            mSelectionMenu.setDelegate((SelectionActionWidget.Delegate)null);
            if (!mSelectionMenu.isReleased()) {
                if (mSelectionMenu.isVisible()) {
                    mSelectionMenu.hide(REMOVE_WIDGET);
                }
                mSelectionMenu.releaseWidget();
            }
            mSelectionMenu = null;
        }

        if (mWidgetPlacement.tintColor != Windows.WHITE) {
            mWidgetPlacement.tintColor = Windows.WHITE;
            mWidgetManager.updateWidget(this);
        }
    }

    public void startDownload(@NonNull DownloadJob downloadJob, boolean showConfirmDialog) {
        if (showConfirmDialog) {
            // As of O, the prefixes are used in their standard meanings in the SI system, so kB = 1000 bytes, MB = 1,000,000 bytes, etc.
            // In Build.VERSION_CODES.N and earlier, powers of 1024 are used instead, with KB = 1024 bytes, MB = 1,048,576 bytes, etc.
            String fileSize = downloadJob.getContentLength() > 0 ? "<br>" + Formatter.formatFileSize(getContext(), downloadJob.getContentLength()) : "";
            mWidgetManager.getFocusedWindow().showConfirmPrompt(new PromptData.Builder()
                    .withIconRes(R.drawable.ic_icon_downloads)
                    .withTitle(getResources().getString(R.string.download_confirm_title))
                    .withBody(downloadJob.getFilename() + fileSize)
                    .withBodyGravity(Gravity.START)
                    .withBtnMsg(new String[]{
                            getResources().getString(R.string.download_confirm_cancel),
                            getResources().getString(R.string.download_confirm_download)})
                    .withCallback((index, isChecked) -> {
                            if (index == PromptDialogWidget.POSITIVE) {
                                mDownloadsManager.startDownload(downloadJob);
                            }
                        })
                    .build()
            );
        } else {
            mDownloadsManager.startDownload(downloadJob);
        }
    }

    private boolean isContextMenuVisible() {
        return (mContextMenu != null && mContextMenu.isVisible() ||
                mSelectionMenu != null && mSelectionMenu.isVisible());
    }

    // ISession.ContentDelegate

    @Override
    public void onFullScreen(@NonNull WSession session, boolean aFullScreen) {
        setIsFullScreen(aFullScreen);
    }

    @Override
    public void onCloseRequest(@NonNull WSession aSession) {
        Session session = SessionStore.get().getSession(aSession);
        mWidgetManager.getWindows().onTabsClose(Collections.singletonList(session));
    }

    @Override
    public void onContextMenu(WSession session, int screenX, int screenY, ContextElement element) {
        hideContextMenus();

        // We don't show the menu for blobs
        if (UrlUtils.isBlobUri(element.srcUri)) {
            return;
        }

        // We don't show the menu in kiosk mode
        if (isKioskMode()) {
            return;
        }

        mContextMenu = new ContextMenuWidget(getContext());
        mContextMenu.mWidgetPlacement.parentHandle = getHandle();
        mContextMenu.setDismissCallback(this::hideContextMenus);
        mContextMenu.setContextElement(element);
        if (!mContextMenu.hasActions()) {
            hideContextMenus();
            return;
        }
        mContextMenu.show(REQUEST_FOCUS);

        mWidgetPlacement.tintColor = Windows.GRAY;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void onFirstComposite(@NonNull WSession session) {
        if (!mAfterFirstPaint) {
            return;
        }
        if (mFirstDrawCallback != null) {
            mUIThreadExecutor.execute(mFirstDrawCallback);
            mFirstDrawCallback = null;
        }
    }

    @Override
    public void onFirstContentfulPaint(@NonNull WSession session) {
        if (mAfterFirstPaint) {
            return;
        }
        if (mFirstDrawCallback != null) {
            mUIThreadExecutor.execute(mFirstDrawCallback);
            mFirstDrawCallback = null;
            mAfterFirstPaint = true;
            // view queue calls need to be delayed to avoid a deadlock
            // caused by ISession.syncResumeResizeCompositor()
            // See: https://github.com/MozillaReality/FirefoxReality/issues/2889
            mUIThreadExecutor.execute(() -> {
                mSetViewQueuedCalls.forEach(Runnable::run);
                mSetViewQueuedCalls.clear();
            });

        }
    }

    @Override
    public void onExternalResponse(@NonNull WSession aSession, @NonNull WWebResponse webResponseInfo) {
        // We don't want to trigger downloads of already downloaded files that we can't open
        // so we let the system handle it.
        if (!UrlUtils.isFileUri(webResponseInfo.uri())) {
            DownloadJob job;
            if (UrlUtils.isBlobUri(webResponseInfo.uri())) {
                job = DownloadJob.fromUri(webResponseInfo.uri(), webResponseInfo.headers(), webResponseInfo.body());
            } else {
                job = DownloadJob.fromUri(webResponseInfo.uri(), webResponseInfo.headers());
            }
            // Don't show the confirmation dialog when we are in WebXR, because it will not be visible.
            boolean showConfirmDialog = !mWidgetManager.isWebXRPresenting();
            startDownload(job, showConfirmDialog);

        } else {
            File file = new File(webResponseInfo.uri().substring("file://".length()));
            Uri contentUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getApplicationContext().getPackageName() + ".provider",
                    file);
            Intent newIntent = new Intent(Intent.ACTION_VIEW);
            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
            newIntent.setDataAndType(contentUri, mimeType);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PackageManager packageManager = getContext().getPackageManager();
            if (newIntent.resolveActivity(packageManager) != null) {
                showConfirmPrompt(getResources().getString(R.string.download_open_file_unsupported_title),
                        getResources().getString(R.string.download_open_file_unsupported_body),
                        new String[]{
                                getResources().getString(R.string.download_open_file_unsupported_cancel),
                                getResources().getString(R.string.download_open_file_unsupported_open)
                        }, (index, isChecked) -> {
                            if (index == PromptDialogWidget.POSITIVE) {
                                try {
                                    getContext().startActivity(newIntent);
                                } catch (Exception ignored) {
                                    showAlert(
                                            getResources().getString(R.string.download_open_file_error_title),
                                            getResources().getString(R.string.download_open_file_error_body),
                                            null);
                                }
                            }
                        });
            } else {
                showAlert(
                        getResources().getString(R.string.download_open_file_error_title),
                        getResources().getString(R.string.download_open_file_open_unsupported_body),
                        null);
            }
        }
    }

    // VideoAvailabilityListener

    @Override
    public void onVideoAvailabilityChanged(@NonNull Media aMedia, boolean aVideoAvailable) {
        if (mSession != null) {
            if (aVideoAvailable) {
                aMedia.addMediaListener(mMediaDelegate);
            } else {
                aMedia.removeMediaListener(mMediaDelegate);
            }
        }

        if (aVideoAvailable) {
            if (aMedia.isPlaying()) {
                mViewModel.setIsMediaPlaying(true);
            }
            mViewModel.setIsMediaAvailable(true);

        } else {
            mViewModel.setIsMediaAvailable(false);
            mViewModel.setIsMediaPlaying(false);
        }
        for (WindowListener listener: mListeners)
            listener.onVideoAvailabilityChanged(this);
    }

   WMediaSession.Delegate mMediaDelegate = new WMediaSession.Delegate() {

       @Override
       public void onPlay(@NonNull @NotNull WSession session, @NonNull @NotNull WMediaSession mediaSession) {
           mViewModel.setIsMediaAvailable(true);
           mViewModel.setIsMediaPlaying(true);
       }

       @Override
       public void onPause(@NonNull @NotNull WSession session, @NonNull @NotNull WMediaSession mediaSession) {
           mViewModel.setIsMediaAvailable(true);
           mViewModel.setIsMediaPlaying(false);
       }

       @Override
       public void onStop(@NonNull @NotNull WSession session, @NonNull @NotNull WMediaSession mediaSession) {
           mViewModel.setIsMediaAvailable(true);
           mViewModel.setIsMediaPlaying(false);
       }

       @Override
       public void onFullscreen(@NonNull final WSession session, @NonNull final WMediaSession mediaSession, final boolean enabled, @Nullable final WMediaSession.ElementMetadata meta) {
           for (WindowListener listener: mListeners)
               listener.onMediaFullScreen(mediaSession, enabled);
       }
    };

    // ISession.NavigationDelegate

    @Override
    public void onPageStart(@NonNull WSession aSession, @NonNull String aUri) {
        mCaptureOnPageStop = true;
        mViewModel.setIsLoading(true);
    }

    @Override
    public void onPageStop(@NonNull WSession aSession, boolean b) {
        if (mCaptureOnPageStop || !mSession.hasCapturedBitmap()) {
            mCaptureOnPageStop = false;
            captureImage();
        }

        mViewModel.setIsLoading(false);
    }

    public void captureImage() {
        mSession.captureBitmap();
    }

    @Override
    public void onLocationChange(@NonNull WSession session, @Nullable String url) {
        if (mPromptDelegate != null &&
                mViewModel.getUrl().getValue() != null &&
                UrlUtils.getHost(url) != null &&
                !UrlUtils.getHost(url).equals(UrlUtils.getHost(mViewModel.getUrl().getValue().toString()))){
            mPromptDelegate.hideAllPrompts();
        }

        mViewModel.setUrl(url);
        mViewModel.setIsDrmUsed(false);
        mViewModel.setIsMediaAvailable(false);
        mViewModel.setIsMediaPlaying(false);
        mViewModel.setIsWebApp(false);

        if (StringUtils.isEmpty(url)) {
            mViewModel.setIsBookmarked(false);

        } else {
            SessionStore.get().getBookmarkStore().isBookmarked(url).thenAcceptAsync(aBoolean -> mViewModel.setIsBookmarked(aBoolean), mUIThreadExecutor).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            });
        }
    }

    @Override
    public void onWebAppManifest(@NonNull WSession session, @NonNull WebApp webAppManifest) {
        mViewModel.setIsWebApp(true);
    }

    @Override
    public void onCanGoBack(@NonNull WSession aSession, boolean canGoBack) {
        mViewModel.setCanGoBack(canGoBack);
    }

    @Override
    public void onCanGoForward(@NonNull WSession aSession, boolean canGoForward) {
        mViewModel.setCanGoForward(canGoForward);
    }

    @Override
    public @Nullable
    WResult<WAllowOrDeny> onLoadRequest(WSession aSession, @NonNull LoadRequest aRequest) {
        final WResult<WAllowOrDeny> result = WResult.create();

        Uri uri = Uri.parse(aRequest.uri);
        if (UrlUtils.isAboutPage(uri.toString())) {
            if(UrlUtils.isBookmarksUrl(uri.toString())) {
                showPanel(Windows.BOOKMARKS);

            } else if (UrlUtils.isHistoryUrl(uri.toString())) {
                showPanel(Windows.HISTORY);

            } else if (UrlUtils.isDownloadsUrl(uri.toString())) {
                showPanel(Windows.DOWNLOADS);

            } else if (UrlUtils.isAddonsUrl(uri.toString())) {
                showPanel(Windows.ADDONS);

            } else {
                hideLibraryPanel();
            }

        } else {
            hideLibraryPanel();
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            // Check that we have permission to open the file, otherwise try to request it.
            File file = new File(uri.getPath());
            if (file.exists() && !file.canRead() &&
                    !mWidgetManager.isPermissionGranted(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                mWidgetManager.requestPermission(
                        aRequest.uri,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        new WSession.PermissionDelegate.Callback() {
                            @Override
                            public void grant() {
                                result.complete(WAllowOrDeny.ALLOW);
                            }

                            @Override
                            public void reject() {
                                result.complete(WAllowOrDeny.DENY);
                            }
                        });
                return result;
            }
        }

        mViewModel.setIsDesktopMode(mSession.getUaMode() == WSessionSettings.USER_AGENT_MODE_DESKTOP);

        result.complete(WAllowOrDeny.ALLOW);
        return result;
    }

    // ISession.HistoryDelegate

    @Override
    public void onHistoryStateChange(@NonNull WSession session, @NonNull HistoryList historyList) {
        if (!mSession.isPrivateMode()) {
            for (HistoryItem item : historyList.getItems()) {
                SessionStore.get().getHistoryStore().recordObservation(item.getUri(), new PageObservation(item.getTitle()));
            }
        }
    }

    @Nullable
    @Override
    public WResult<Boolean> onVisited(@NonNull WSession session, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        if (mSession.isPrivateMode() ||
                (flags & VISIT_TOP_LEVEL) == 0 ||
                (flags & VISIT_UNRECOVERABLE_ERROR) != 0) {
            return WResult.fromValue(false);
        }

        // Check if we want this type of url.
        if (!shouldStoreUri(url)) {
            return WResult.fromValue(false);
        }

        boolean isReload = lastVisitedURL != null && lastVisitedURL.equals(url);

        VisitType visitType;
        if (isReload) {
            visitType = VisitType.RELOAD;

        } else {
            // Note the difference between `VISIT_REDIRECT_PERMANENT`,
            // `VISIT_REDIRECT_TEMPORARY`, `VISIT_REDIRECT_SOURCE`, and
            // `VISIT_REDIRECT_SOURCE_PERMANENT`.
            //
            // The former two indicate if the visited page is the *target*
            // of a redirect; that is, another page redirected to it.
            //
            // The latter two indicate if the visited page is the *source*
            // of a redirect: it's redirecting to another page, because the
            // server returned an HTTP 3xy status code.
            if ((flags & VISIT_REDIRECT_PERMANENT) != 0) {
                visitType = VisitType.REDIRECT_PERMANENT;

            } else if ((flags & VISIT_REDIRECT_TEMPORARY) != 0) {
                visitType = VisitType.REDIRECT_TEMPORARY;

            } else {
                visitType = VisitType.LINK;
            }
        }
        RedirectSource redirectSource;
        if ((flags & WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0) {
            redirectSource = RedirectSource.PERMANENT;

        } else if ((flags & WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE) != 0) {
            redirectSource = RedirectSource.TEMPORARY;

        } else {
            redirectSource = RedirectSource.NOT_A_SOURCE;
        }

        SessionStore.get().getHistoryStore().recordVisit(url, new PageVisit(visitType, redirectSource));
        SessionStore.get().getHistoryStore().recordObservation(url, new PageObservation(url));

        return WResult.fromValue(true);
    }

    /**
     * Filter out unwanted URIs, such as "chrome:", "about:", etc.
     * Ported from nsAndroidHistory::CanAddURI
     * See https://dxr.mozilla.org/mozilla-central/source/mobile/android/components/build/nsAndroidHistory.cpp#326
     */
    private boolean shouldStoreUri(@NonNull String uri) {
        Uri parsedUri = Uri.parse(uri);
        String scheme = parsedUri.getScheme();
        if (scheme == null) {
            return false;
        }

        // Short-circuit most common schemes.
        if (scheme.equals("http") || scheme.equals("https")) {
            return true;
        }

        // Allow about about:reader uris. They are of the form:
        // about:reader?url=http://some.interesting.page/to/read.html
        if (uri.startsWith("about:reader")) {
            return true;
        }

        List<String> schemasToIgnore = Stream.of(
                "about", "imap", "news", "mailbox", "moz-anno", "moz-extension",
                "view-source", "chrome", "resource", "data", "javascript", "blob"
        ).collect(Collectors.toList());

        return !schemasToIgnore.contains(scheme);
    }

    @UiThread
    @Nullable
    public WResult<boolean[]> getVisited(@NonNull WSession aSession, @NonNull String[] urls) {
        if (mSession.isPrivateMode()) {
            return WResult.fromValue(new boolean[]{});
        }

        WResult<boolean[]> result = WResult.create();

        SessionStore.get().getHistoryStore().getVisited(Arrays.asList(urls)).thenAcceptAsync(list -> {
            final boolean[] primitives = new boolean[list.size()];
            int index = 0;
            for (Boolean object : list) {
                primitives[index++] = object;
            }
            result.complete(primitives);

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting history: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });

        return result;
    }

    // ISession.ProgressDelegate

    @Override
    public void onSecurityChange(WSession aSession, SecurityInformation securityInformation) {
        mViewModel.setIsInsecure(!securityInformation.isSecure);
    }

    // ISession.SelectionActionDelegate

    @Override
    public void onShowActionRequest(@NonNull WSession aSession, @NonNull Selection aSelection) {
        if (aSelection.availableActions().size() == 1 && (aSelection.availableActions().contains(WSession.SelectionActionDelegate.ACTION_HIDE))) {
            // See: https://github.com/MozillaReality/FirefoxReality/issues/2214
            aSelection.hide();
            return;
        }

        hideContextMenus();
        mSelectionMenu = new SelectionActionWidget(getContext());
        mSelectionMenu.mWidgetPlacement.parentHandle = getHandle();
        mSelectionMenu.setSelectionText(aSelection.text());
        mSelectionMenu.setActions(aSelection.availableActions());
        Matrix matrix = new Matrix();
        aSession.getClientToSurfaceMatrix(matrix);
        matrix.mapRect(aSelection.clientRect());
        RectF selectionRect = null;
        RectF clientRect = aSelection.clientRect();
        if (clientRect != null) {
            float ratio = WidgetPlacement.worldToWindowRatio(getContext());
            selectionRect = new RectF(
                    clientRect.left * ratio,
                    clientRect.top* ratio,
                    clientRect.right * ratio,
                    clientRect.bottom * ratio
            );
        }
        mSelectionMenu.setSelectionRect(selectionRect);
        mSelectionMenu.setDelegate(new SelectionActionWidget.Delegate() {
            @Override
            public void onAction(String action) {
                hideContextMenus();
                if (aSelection.isActionAvailable(action)) {
                    aSelection.execute(action);

                } else if (aSelection.isActionAvailable(WSession.SelectionActionDelegate.ACTION_UNSELECT)) {
                    aSelection.unselect();
                }

                if (WSession.SelectionActionDelegate.ACTION_COPY.equals(action) &&
                        aSelection.isActionAvailable(WSession.SelectionActionDelegate.ACTION_UNSELECT)) {
                    // Don't keep the text selected after it's copied.
                    aSelection.execute(WSession.SelectionActionDelegate.ACTION_UNSELECT);
                }
            }

            @Override
            public void onDismiss() {
                if (aSelection.isActionAvailable(WSession.SelectionActionDelegate.ACTION_UNSELECT)) {
                    aSelection.unselect();
                } else if (aSelection.isActionAvailable(WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END)) {
                    aSelection.collapseToEnd() ;
                }

                aSelection.hide();
            }
        });
        mSelectionMenu.show(KEEP_FOCUS);
    }

    @Override
    public void onHideAction(@NonNull WSession aSession, int aHideReason) {
        hideContextMenus();
    }

    // WebXRStateChangedListener

    @Override
    public void onWebXRStateChanged(Session aSession, @SessionState.WebXRState int aWebXRState) {
        mViewModel.setIsWebXRBlocked(aWebXRState == SessionState.WEBXR_BLOCKED);
        mViewModel.setIsWebXRUsed(aWebXRState != SessionState.WEBXR_UNUSED);
    }

    // PopUpStateChangedListener

    @Override
    public void onPopUpStateChanged(Session aSession, @SessionState.PopupState int aPopUpState) {
        mViewModel.setIsPopUpBlocked(aPopUpState == SessionState.POPUP_BLOCKED);
        mViewModel.setIsPopUpAvailable(aPopUpState != SessionState.POPUP_UNUSED);
    }

    // DrmStateChangedListener

    @Override
    public void onDrmStateChanged(Session aSession, @SessionState.DrmState int aDrmState) {
        mViewModel.setIsDrmUsed(aDrmState == SessionState.DRM_BLOCKED ||
                aDrmState == SessionState.DRM_ALLOWED);
    }


    // ExternalRequestDelegate

    @Override
    public boolean onHandleExternalRequest(@NonNull String url) {
        if (UrlUtils.isEngineSupportedScheme(url)) {
            return false;

        } else {
            Intent intent;
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } catch (Exception ex) {
                mWidgetManager.getFocusedWindow().showAlert(
                        getResources().getString(R.string.external_open_uri_error_title),
                        getResources().getString(R.string.external_open_uri_error_bad_uri_body, url),
                        null);
                return false;
            }

            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            Intent selector = intent.getSelector();
            if (selector != null) {
                selector.addCategory(Intent.CATEGORY_BROWSABLE);
                selector.setComponent(null);
            }

            try {
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                mWidgetManager.getFocusedWindow().showAlert(
                        getResources().getString(R.string.external_open_uri_error_title),
                        getResources().getString(R.string.external_open_uri_error_no_activity_body, url),
                        null);
                return false;
            } catch (SecurityException ex) {
                mWidgetManager.getFocusedWindow().showAlert(
                        getResources().getString(R.string.external_open_uri_error_title),
                        getResources().getString(R.string.external_open_uri_error_security_exception_body, url),
                        null);
                return false;
            }

            return true;
        }
    }

    private float getBrowserDensity() {
        if (mBrowserDensity == 0) {
            mBrowserDensity = EngineProvider.INSTANCE.getOrCreateRuntime(getContext()).getDensity();
        }
        return mBrowserDensity;
    }
}
