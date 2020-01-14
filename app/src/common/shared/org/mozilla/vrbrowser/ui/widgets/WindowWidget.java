/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.PanZoomController;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.PromptDelegate;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.VideoAvailabilityListener;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.adapters.Bookmark;
import org.mozilla.vrbrowser.ui.callbacks.BookmarksCallback;
import org.mozilla.vrbrowser.ui.callbacks.HistoryCallback;
import org.mozilla.vrbrowser.ui.callbacks.LibraryItemContextMenuClickCallback;
import org.mozilla.vrbrowser.ui.views.BookmarksView;
import org.mozilla.vrbrowser.ui.views.HistoryView;
import org.mozilla.vrbrowser.ui.widgets.dialogs.ClearHistoryDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.SelectionActionWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.ContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.LibraryMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsWidget;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mozilla.components.concept.storage.PageObservation;
import mozilla.components.concept.storage.PageVisit;
import mozilla.components.concept.storage.RedirectSource;
import mozilla.components.concept.storage.VisitInfo;
import mozilla.components.concept.storage.VisitType;

import static org.mozilla.vrbrowser.utils.ServoUtils.isInstanceOfServoSession;

public class WindowWidget extends UIWidget implements SessionChangeListener,
        GeckoSession.ContentDelegate, GeckoSession.NavigationDelegate, VideoAvailabilityListener,
        GeckoSession.HistoryDelegate, GeckoSession.ProgressDelegate, GeckoSession.SelectionActionDelegate {

    public interface HistoryViewDelegate {
        default void onHistoryViewShown(WindowWidget aWindow) {}
        default void onHistoryViewHidden(WindowWidget aWindow) {}
    }

    public interface BookmarksViewDelegate {
        default void onBookmarksShown(WindowWidget aWindow) {}
        default void onBookmarksHidden(WindowWidget aWindow) {}
    }

    @IntDef(value = { SESSION_RELEASE_DISPLAY, SESSION_DO_NOT_RELEASE_DISPLAY})
    public @interface OldSessionDisplayAction {}
    public static final int SESSION_RELEASE_DISPLAY = 0;
    public static final int SESSION_DO_NOT_RELEASE_DISPLAY = 1;


    private Surface mSurface;
    private int mWidth;
    private int mHeight;
    private int mHandle;
    private WidgetPlacement mWidgetPlacement;
    private TopBarWidget mTopBar;
    private TitleBarWidget mTitleBar;
    private WidgetManagerDelegate mWidgetManager;
    private PromptDialogWidget mAlertDialog;
    private PromptDialogWidget mConfirmDialog;
    private PromptDialogWidget mAppDialog;
    private ClearHistoryDialogWidget mClearHistoryDialog;
    private ContextMenuWidget mContextMenu;
    private SelectionActionWidget mSelectionMenu;
    private LibraryMenuWidget mLibraryItemContextMenu;
    private int mWidthBackup;
    private int mHeightBackup;
    private int mBorderWidth;
    private Runnable mFirstDrawCallback;
    private boolean mIsInVRVideoMode;
    private View mView;
    private Session mSession;
    private int mWindowId;
    private BookmarksView mBookmarksView;
    private HistoryView mHistoryView;
    private ArrayList<BookmarksViewDelegate> mBookmarksViewListeners;
    private ArrayList<HistoryViewDelegate> mHistoryViewListeners;
    private Windows.WindowPlacement mWindowPlacement = Windows.WindowPlacement.FRONT;
    private Windows.WindowPlacement mWindowPlacementBeforeFullscreen = Windows.WindowPlacement.FRONT;
    private float mMaxWindowScale = 3;
    private boolean mIsRestored = false;
    private CopyOnWriteArrayList<WindowListener> mListeners;
    boolean mActive = false;
    boolean mHovered = false;
    boolean mClickedAfterFocus = false;
    boolean mIsBookmarksVisible = false;
    boolean mIsHistoryVisible = false;
    private WidgetPlacement mPlacementBeforeFullscreen;
    private WidgetPlacement mPlacementBeforeResize;
    private boolean mIsResizing;
    private boolean mIsFullScreen;
    private boolean mAfterFirstPaint;
    private boolean mCaptureOnPageStop;
    private PromptDelegate mPromptDelegate;
    private Executor mUIThreadExecutor;

    public interface WindowListener {
        default void onFocusRequest(@NonNull WindowWidget aWindow) {}
        default void onBorderChanged(@NonNull WindowWidget aWindow) {}
        default void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {}
        default void onFullScreen(@NonNull WindowWidget aWindow, boolean aFullScreen) {}
        default void onVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {}
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
        mWidgetManager = (WidgetManagerDelegate) aContext;
        mBorderWidth = SettingsStore.getInstance(aContext).getTransparentBorderWidth();

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mListeners = new CopyOnWriteArrayList<>();
        setupListeners(mSession);

        mBookmarksView = new BookmarksView(aContext);
        mBookmarksView.addBookmarksListener(mBookmarksListener);
        mBookmarksViewListeners = new ArrayList<>();

        mHistoryView = new HistoryView(aContext);
        mHistoryView.addHistoryListener(mHistoryListener);
        mHistoryViewListeners = new ArrayList<>();

        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        mPlacementBeforeFullscreen = new WidgetPlacement(aContext);
        mPlacementBeforeResize = new WidgetPlacement(aContext);
        mIsResizing = false;
        mIsFullScreen = false;
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

        TelemetryWrapper.openWindowEvent(mWindowId);
        GleanMetricsService.openWindowEvent(mWindowId);

        if (mSession.getGeckoSession() != null) {
            onCurrentSessionChange(null, mSession.getGeckoSession());
        }
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        int windowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.width = windowWidth + mBorderWidth * 2;
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight() + mBorderWidth * 2;
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) *
                                (float)windowWidth / (float)SettingsStore.WINDOW_WIDTH_DEFAULT;
        aPlacement.density = 1.0f;
        aPlacement.visible = true;
        aPlacement.cylinder = true;
        aPlacement.textureScale = 1.0f;
        aPlacement.name = "Window";
        // Check Windows.placeWindow method for remaining placement set-up
    }

    public void setPopUpDelegate(@Nullable PromptDelegate.PopUpDelegate delegate) {
        mPromptDelegate.setPopupDelegate(delegate);
    }

    void setupListeners(Session aSession) {
        aSession.addSessionChangeListener(this);
        aSession.addContentListener(this);
        aSession.addVideoAvailabilityListener(this);
        aSession.addNavigationListener(this);
        aSession.addProgressListener(this);
        aSession.setHistoryDelegate(this);
        aSession.addSelectionActionListener(this);
    }

    void cleanListeners(Session aSession) {
        aSession.removeSessionChangeListener(this);
        aSession.removeContentListener(this);
        aSession.removeVideoAvailabilityListener(this);
        aSession.removeNavigationListener(this);
        aSession.removeProgressListener(this);
        aSession.setHistoryDelegate(null);
        aSession.removeSelectionActionListener(this);
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
        if (isBookmarksVisible()) {
            hideBookmarks();

        } else if (isHistoryVisible()) {
            hideHistory();

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
        }
    }

    public void close() {
        TelemetryWrapper.closeWindowEvent(mWindowId);
        GleanMetricsService.closeWindowEvent(mWindowId);
        hideContextMenus();
        releaseWidget();
        mBookmarksView.onDestroy();
        mHistoryView.onDestroy();
        SessionStore.get().destroySession(mSession);
        if (mTopBar != null) {
            mWidgetManager.removeWidget(mTopBar);
        }
        if (mTitleBar != null) {
            mWidgetManager.removeWidget(mTitleBar);
        }
        mListeners.clear();
    }

    public void loadHomeIfNotRestored() {
        if (!mIsRestored) {
            loadHome();
        }
    }

    public void loadHome() {
        if (mSession.isPrivateMode()) {
            mSession.loadPrivateBrowsingPage();

        } else {
            mSession.loadUri(SettingsStore.getInstance(getContext()).getHomepage());
        }
    }

    protected void setRestored(boolean restored) {
        mIsRestored = restored;
    }

    private void setView(View view, boolean switchSurface) {
        if (switchSurface) {
            pauseCompositor();
        }

        mView = view;
        removeView(view);
        mView.setVisibility(VISIBLE);
        addView(mView);

        if (switchSurface) {
            mWidgetPlacement.density = getContext().getResources().getDisplayMetrics().density;
            if (mTexture != null && mSurface != null && mRenderer == null) {
                // Create the UI Renderer for the current surface.
                // Surface must be released when switching back to WebView surface or the browser
                // will not render it correctly. See release code in unsetView().
                mRenderer = new UISurfaceTextureRenderer(mSurface, mWidgetPlacement.textureWidth(), mWidgetPlacement.textureHeight());
            }
            mWidgetManager.updateWidget(this);
            mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
            mWidgetManager.pushBackHandler(mBackHandler);
            setWillNotDraw(false);
            postInvalidate();
        }
    }

    private void unsetView(View view, boolean switchSurface) {
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
                mWidgetPlacement.density = 1.0f;
                mWidgetManager.updateWidget(this);
                mWidgetManager.popWorldBrightness(this);
                mWidgetManager.popBackHandler(mBackHandler);
            }
        }
    }

    public boolean isBookmarksVisible() {
        return (mView != null && mView == mBookmarksView);
    }

    public boolean isHistoryVisible() {
        return (mView != null && mView == mHistoryView);
    }

    public int getWindowWidth() {
        return mWidgetPlacement.width;
    }

    public int getWindowHeight() {
        return mWidgetPlacement.height;
    }

    public void addBookmarksViewListener(@NonNull BookmarksViewDelegate listener) {
        mBookmarksViewListeners.add(listener);
    }

    public void removeBookmarksViewListener(@NonNull BookmarksViewDelegate listener) {
        mBookmarksViewListeners.remove(listener);
    }

    public void addHistoryViewListener(@NonNull HistoryViewDelegate listener) {
        mHistoryViewListeners.add(listener);
    }

    public void removeHistoryViewListener(@NonNull HistoryViewDelegate listener) {
        mHistoryViewListeners.remove(listener);
    }

    public void switchBookmarks() {
        if (isHistoryVisible()) {
            hideHistory(false);
            showBookmarks(false);

        } else if (isBookmarksVisible()) {
            hideBookmarks();

        } else {
            showBookmarks();
        }
    }

    public void showBookmarks() {
        showBookmarks(true);
    }

    public void showBookmarks(boolean switchSurface) {
        if (mView == null) {
            setView(mBookmarksView, switchSurface);
            mBookmarksView.onShow();
            for (BookmarksViewDelegate listener : mBookmarksViewListeners) {
                listener.onBookmarksShown(this);
            }
            mIsBookmarksVisible = true;
        }

        updateTitleBar();
    }

    public void hideBookmarks() {
        hideBookmarks(true);
    }

    public void hideBookmarks(boolean switchSurface) {
        if (mView != null) {
            unsetView(mBookmarksView, switchSurface);
            for (BookmarksViewDelegate listener : mBookmarksViewListeners) {
                listener.onBookmarksHidden(this);
            }
            mIsBookmarksVisible = false;
        }
    }

    public void switchHistory() {
        if (isBookmarksVisible()) {
            hideBookmarks(false);
            showHistory(false);

        } else if (isHistoryVisible()) {
            hideHistory();

        } else {
            showHistory();
        }
    }

    private void hideLibraryPanels() {
        if (isBookmarksVisible()) {
            hideBookmarks();
        } else if (isHistoryVisible()) {
            hideHistory();
        }
    }

    public void showHistory() {
        showHistory(true);
    }

    public void showHistory(boolean switchSurface) {
        if (mView == null) {
            setView(mHistoryView, switchSurface);
            mHistoryView.onShow();
            for (HistoryViewDelegate listener : mHistoryViewListeners) {
                listener.onHistoryViewShown(this);
            }
            mIsHistoryVisible = true;
        }
    }

    public void hideHistory() {
        hideHistory(true);
    }

    public void hideHistory(boolean switchSurface) {
        if (mView != null) {
            unsetView(mHistoryView, switchSurface);
            for (HistoryViewDelegate listener : mHistoryViewListeners) {
                listener.onHistoryViewHidden(this);
            }
            mIsHistoryVisible = false;
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
    }

    public void disableVRVideoMode() {
        if (!mIsInVRVideoMode || mWidthBackup == 0 || mHeightBackup == 0) {
            return;
        }
        mIsInVRVideoMode = false;
        int border = SettingsStore.getInstance(getContext()).getTransparentBorderWidth();
        if (mWidthBackup == mWidth && mHeightBackup == mHeight && border == mBorderWidth) {
            return;
        }
        mBorderWidth = border;
        mWidgetPlacement.width = mWidthBackup;
        mWidgetPlacement.height = mHeightBackup;
        mWidgetManager.updateWidget(this);
    }

    public void setWindowPlacement(@NonNull Windows.WindowPlacement aPlacement) {
        if (mActive) {
            TelemetryWrapper.activePlacementEvent(mWindowPlacement.getValue(), false);
        }

        mWindowPlacement = aPlacement;

        if (mActive) {
            TelemetryWrapper.activePlacementEvent(mWindowPlacement.getValue(), true);
        }
    }

    public @NonNull Windows.WindowPlacement getmWindowPlacementBeforeFullscreen() {
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
            GeckoSession session = mSession.getGeckoSession();
            if (session != null) {
                session.getTextInput().setView(this);
            }
            mSession.updateLastUse();
        } else {
            updateTitleBar();
        }

        updateTitleBarMediaStatus();
        hideContextMenus();

        TelemetryWrapper.activePlacementEvent(mWindowPlacement.getValue(), mActive);
        updateBorder();
    }

    private void updateTitleBar() {
        if (isBookmarksVisible()) {
            updateTitleBarUrl(getResources().getString(R.string.url_bookmarks_title));

        } else if (isHistoryVisible()) {
                updateTitleBarUrl(getResources().getString(R.string.url_history_title));

        } else {
            updateTitleBarUrl(mSession.getCurrentUri());
        }
    }

    private void updateTitleBarUrl(String url) {
        if (mTitleBar != null && url != null) {
            mTitleBar.setIsInsecure(!mSession.isSecure());
            if (url.startsWith("data") && mSession.isPrivateMode()) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(getResources().getString(R.string.private_browsing_title));

            } else if (url.equals(mSession.getHomeUri())) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(getResources().getString(R.string.url_home_title, getResources().getString(R.string.app_name)));

            } else if (url.equals(getResources().getString(R.string.url_bookmarks_title)) ||
                    url.equals(getResources().getString(R.string.url_history_title))) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(url);

            } else if (url.equals(getResources().getString(R.string.about_blank))) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL("");

            } else {
                mTitleBar.setURL(url);
            }
        }
    }

    public void updateTitleBarMediaStatus() {
        if (mTitleBar != null) {
            mTitleBar.updateMediaStatus();
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

    public TitleBarWidget getTitleBar() {
        return mTitleBar;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        mFirstDrawCallback = aFirstDrawCallback;
        if (mView != null) {
            super.setSurfaceTexture(aTexture, aWidth, aHeight, aFirstDrawCallback);

        } else {
            GeckoSession session = mSession.getGeckoSession();
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
        if (mSession != null) {
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

        if (mSurface != null && mView == null) {
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
            GeckoSession session = mSession.getGeckoSession();
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

        if (mView != null) {
            super.handleHoverEvent(aEvent);

        } else {
            GeckoSession session = mSession.getGeckoSession();
            if (session != null) {
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
        mWindowPlacement = mWindowPlacementBeforeFullscreen;
        mWidgetPlacement.copyFrom(mPlacementBeforeFullscreen);
    }

    public WidgetPlacement getBeforeFullscreenPlacement() {
        return mPlacementBeforeFullscreen;
    }

    public void saveBeforeResizePlacement() {
        mPlacementBeforeResize.copyFrom(mWidgetPlacement);
    }

    public void restoreBeforeResizePlacement() {
        mWidgetPlacement.copyFrom(mPlacementBeforeResize);
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
        if (isFullScreen != mIsFullScreen) {
            mIsFullScreen = isFullScreen;
            for (WindowListener listener: mListeners) {
                listener.onFullScreen(this, isFullScreen);
            }
        }
    }

    public boolean isFullScreen() {
        return mIsFullScreen;
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
    }

    @Override
    public void releaseWidget() {
        cleanListeners(mSession);
        GeckoSession session = mSession.getGeckoSession();

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
            // Custom SurfaceTexture used for GeckoView
            mTexture.release();
            mTexture = null;
        }
        mBookmarksView.removeBookmarksListener(mBookmarksListener);
        mHistoryView.removeHistoryListener(mHistoryListener);
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
            if (mIsBookmarksVisible || mIsHistoryVisible) {
                mWidgetManager.popWorldBrightness(this);
            }

        } else {
            if (mIsBookmarksVisible || mIsHistoryVisible) {
                mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
            }
        }
        mIsBookmarksVisible = isBookmarksVisible();
        mIsHistoryVisible = isHistoryVisible();
        mWidgetManager.updateWidget(this);
        if (!aVisible) {
            clearFocus();
        }
    }

    @Override
    public void draw(Canvas aCanvas) {
        if (mView != null) {
            super.draw(aCanvas);
        }
    }

    public void setSession(@NonNull Session aSession) {
        setSession(aSession, SESSION_RELEASE_DISPLAY);
    }

    public void setSession(@NonNull Session aSession, @OldSessionDisplayAction int aDisplayAction) {
        if (mSession != aSession) {
            Session oldSession = mSession;
            if (oldSession != null) {
                cleanListeners(oldSession);
                if (aDisplayAction == SESSION_RELEASE_DISPLAY) {
                    oldSession.releaseDisplay();
                }
            }

            mSession = aSession;
            if (oldSession != null) {
                onCurrentSessionChange(oldSession.getGeckoSession(), aSession.getGeckoSession());
            } else {
                onCurrentSessionChange(null, aSession.getGeckoSession());
            }
            setupListeners(mSession);
            for (WindowListener listener: mListeners) {
                listener.onSessionChanged(oldSession, aSession);
            }
        }
        mCaptureOnPageStop = false;
        hideLibraryPanels();
    }


    public void showPopUps() {
        if (mPromptDelegate != null) {
            mPromptDelegate.showPopUps(getSession().getGeckoSession());
        }
    }

    public boolean hasPendingPopUps() {
        if (mPromptDelegate != null) {
            return mPromptDelegate.hasPendingPopUps(getSession().getGeckoSession());
        }

        return false;
    }

    // Session.GeckoSessionChange
    @Override
    public void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {
        Log.d(LOGTAG, "onCurrentSessionChange: " + this.hashCode());

        mWidgetManager.setIsServoSession(isInstanceOfServoSession(aSession));
        Log.d(LOGTAG, "surfaceChanged: " + aSession.hashCode());
        callSurfaceChanged();
        aSession.getTextInput().setView(this);

        boolean isPrivateMode  = aSession.getSettings().getUsePrivateMode();
        if (isPrivateMode) {
            setPrivateBrowsingEnabled(true);
        } else {
            setPrivateBrowsingEnabled(false);
        }
        waitForFirstPaint();
    }

    @Override
    public void onStackSession(Session aSession) {
        // e.g. tab opened via window.open()
        aSession.updateLastUse();
        Session current = mSession;
        setSession(aSession);
        SessionStore.get().setActiveSession(aSession);
        current.captureBackgroundBitmap(getWindowWidth(), getWindowHeight()).thenAccept(aVoid -> current.setActive(false));
        mWidgetManager.getTray().showTabAddedNotification();

        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.BROWSER);
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        if (mSession == aSession) {
            aParent.setActive(true);
            setSession(aParent);
            SessionStore.get().setActiveSession(aParent);
            SessionStore.get().destroySession(aSession);
        }
    }

    // View
    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        Log.d(LOGTAG, "BrowserWidget onCreateInputConnection");
        GeckoSession session = mSession.getGeckoSession();
        if (session == null) {
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
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getTextInput().onKeyPreIme(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyUp(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyUp(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getTextInput().onKeyUp(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyDown(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyDown(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getTextInput().onKeyDown(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyLongPress(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyLongPress(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getTextInput().onKeyLongPress(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyMultiple(int aKeyCode, int repeatCount, KeyEvent aEvent) {
        if (super.onKeyMultiple(aKeyCode, repeatCount, aEvent)) {
            return true;
        }
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getTextInput().onKeyMultiple(aKeyCode, repeatCount, aEvent);
    }
    
    @Override
    protected void onFocusChanged(boolean aGainFocus, int aDirection, Rect aPreviouslyFocusedRect) {
        super.onFocusChanged(aGainFocus, aDirection, aPreviouslyFocusedRect);
        Log.d(LOGTAG, "BrowserWidget onFocusChanged: " + (aGainFocus ? "true" : "false"));
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        GeckoSession session = mSession.getGeckoSession();
        return (session != null) && session.getPanZoomController().onTouchEvent(aEvent) == PanZoomController.INPUT_RESULT_HANDLED;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (mView != null) {
            return super.onGenericMotionEvent(aEvent);
        } else {
            GeckoSession session = mSession.getGeckoSession();
            return (session != null) && session.getPanZoomController().onMotionEvent(aEvent) == PanZoomController.INPUT_RESULT_HANDLED;
        }
    }

    private void setPrivateBrowsingEnabled(boolean isEnabled) {
    }

    public void showAlert(String title, @NonNull String msg, @Nullable PromptDialogWidget.Delegate callback) {
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
        mAlertDialog.setButtonsDelegate(index -> {
            mAlertDialog.hide(REMOVE_WIDGET);
            if (callback != null) {
                callback.onButtonClicked(index);
            }
        });
        mAlertDialog.show(REQUEST_FOCUS);
    }

    public void showConfirmPrompt(String title, @NonNull String msg, @NonNull String[] btnMsg, @Nullable PromptDialogWidget.Delegate callback) {
        if (mConfirmDialog == null) {
            mConfirmDialog = new PromptDialogWidget(getContext());
            mAlertDialog.setButtons(new int[] {
                    R.string.cancel_button,
                    R.string.ok_button
            });
            mConfirmDialog.setCheckboxVisible(false);
            mConfirmDialog.setDescriptionVisible(false);
        }
        mConfirmDialog.setTitle(title);
        mConfirmDialog.setBody(msg);
        mConfirmDialog.setButtons(btnMsg);
        mAlertDialog.setButtonsDelegate(index -> {
            mAlertDialog.hide(REMOVE_WIDGET);
            if (callback != null) {
                callback.onButtonClicked(index);
            }
        });
        mConfirmDialog.show(REQUEST_FOCUS);
    }

    public void showDialog(@NonNull String title, @NonNull @StringRes int  description, @NonNull  @StringRes int [] btnMsg,
                           @Nullable PromptDialogWidget.Delegate buttonsCallback, @Nullable Runnable linkCallback) {
        mAppDialog = new PromptDialogWidget(getContext());
        mAppDialog.setIconVisible(false);
        mAppDialog.setCheckboxVisible(false);
        mAppDialog.setDescriptionVisible(false);
        mAppDialog.setTitle(title);
        mAppDialog.setBody(description);
        mAppDialog.setButtons(btnMsg);
        mAppDialog.setButtonsDelegate(index -> {
            mAppDialog.hide(REMOVE_WIDGET);
            if (buttonsCallback != null) {
                buttonsCallback.onButtonClicked(index);
            }
        });
        mAppDialog.setLinkDelegate(() -> {
            mAppDialog.hide(REMOVE_WIDGET);
            if (linkCallback != null) {
                linkCallback.run();
            }
        });
        mAppDialog.show(REQUEST_FOCUS);
    }

    public void showClearCacheDialog() {
        if (mClearHistoryDialog == null) {
            mClearHistoryDialog = new ClearHistoryDialogWidget(getContext());
        }
        mClearHistoryDialog.show(REQUEST_FOCUS);
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
            }
        }
    }

    public float getMaxWindowScale() {
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

    private void showLibraryItemContextMenu(@NonNull View view, LibraryMenuWidget.LibraryContextMenuItem item, boolean isLastVisibleItem) {
        view.requestFocusFromTouch();

        hideContextMenus();

        float ratio = WidgetPlacement.viewToWidgetRatio(getContext(), WindowWidget.this);

        Rect offsetViewBounds = new Rect();
        getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(view, offsetViewBounds);

        SessionStore.get().getBookmarkStore().isBookmarked(item.getUrl()).thenAcceptAsync((isBookmarked -> {
            mLibraryItemContextMenu = new LibraryMenuWidget(getContext(), item, mWidgetManager.canOpenNewWindow(), isBookmarked);
            mLibraryItemContextMenu.getPlacement().parentHandle = getHandle();

            PointF position;
            if (isLastVisibleItem) {
                mLibraryItemContextMenu.mWidgetPlacement.anchorY = 0.0f;
                position = new PointF(
                        (offsetViewBounds.left + view.getWidth()) * ratio,
                        -(offsetViewBounds.top) * ratio);

            } else {
                mLibraryItemContextMenu.mWidgetPlacement.anchorY = 1.0f;
                position = new PointF(
                        (offsetViewBounds.left + view.getWidth()) * ratio,
                        -(offsetViewBounds.top + view.getHeight()) * ratio);
            }
            mLibraryItemContextMenu.mWidgetPlacement.translationX = position.x - (mLibraryItemContextMenu.getWidth()/mLibraryItemContextMenu.mWidgetPlacement.density);
            mLibraryItemContextMenu.mWidgetPlacement.translationY = position.y + getResources().getDimension(R.dimen.library_menu_top_margin)/mLibraryItemContextMenu.mWidgetPlacement.density;

            mLibraryItemContextMenu.setItemDelegate((new LibraryItemContextMenuClickCallback() {
                @Override
                public void onOpenInNewWindowClick(LibraryMenuWidget.LibraryContextMenuItem item) {
                    mWidgetManager.openNewWindow(item.getUrl());
                    hideContextMenus();
                }

                @Override
                public void onOpenInNewTabClick(LibraryMenuWidget.LibraryContextMenuItem item) {
                    mWidgetManager.openNewTabForeground(item.getUrl());
                    if (item.getType() == LibraryMenuWidget.LibraryItemType.HISTORY) {
                        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.HISTORY);
                    } else if (item.getType() == LibraryMenuWidget.LibraryItemType.BOOKMARKS) {
                        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.BOOKMARKS);
                    }
                    hideContextMenus();
                }

                @Override
                public void onAddToBookmarks(LibraryMenuWidget.LibraryContextMenuItem item) {
                    SessionStore.get().getBookmarkStore().addBookmark(item.getUrl(), item.getTitle());
                    hideContextMenus();
                }

                @Override
                public void onRemoveFromBookmarks(LibraryMenuWidget.LibraryContextMenuItem item) {
                    SessionStore.get().getBookmarkStore().deleteBookmarkByURL(item.getUrl());
                    hideContextMenus();
                }
            }));
            mLibraryItemContextMenu.show(REQUEST_FOCUS);

        }), mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting the bookmarked status: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    private BookmarksCallback mBookmarksListener = new BookmarksCallback() {
        @Override
        public void onShowContextMenu(@NonNull View view, @NonNull Bookmark item, boolean isLastVisibleItem) {
            showLibraryItemContextMenu(
                    view,
                    new LibraryMenuWidget.LibraryContextMenuItem(
                            item.getUrl(),
                            item.getTitle(),
                            LibraryMenuWidget.LibraryItemType.BOOKMARKS),
                    isLastVisibleItem);
        }

        @Override
        public void onFxASynSettings(@NonNull View view) {
            mWidgetManager.getTray().showSettingsDialog(SettingsWidget.SettingDialog.FXA);
        }

        @Override
        public void onHideContextMenu(@NonNull View view) {
            hideContextMenus();
        }

        @Override
        public void onFxALogin(@NonNull View view) {
            hideBookmarks();
        }

        @Override
        public void onClickItem(@NonNull View view, Bookmark item) {
            hideBookmarks();
        }
    };

    private HistoryCallback mHistoryListener = new HistoryCallback() {
        @Override
        public void onClearHistory(@NonNull View view) {
            view.requestFocusFromTouch();
            showClearCacheDialog();
        }

        @Override
        public void onShowContextMenu(@NonNull View view, @NonNull VisitInfo item, boolean isLastVisibleItem) {
            showLibraryItemContextMenu(
                    view,
                    new LibraryMenuWidget.LibraryContextMenuItem(
                            item.getUrl(),
                            item.getTitle(),
                            LibraryMenuWidget.LibraryItemType.HISTORY),
                    isLastVisibleItem);
        }

        @Override
        public void onFxASynSettings(@NonNull View view) {
            mWidgetManager.getTray().showSettingsDialog(SettingsWidget.SettingDialog.FXA);
        }

        @Override
        public void onHideContextMenu(@NonNull View view) {
            hideContextMenus();
        }

        @Override
        public void onFxALogin(@NonNull View view) {
            hideHistory();
        }

        @Override
        public void onClickItem(@NonNull View view, @NonNull VisitInfo item) {
            hideHistory();
        }
    };

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

        if (mWidgetPlacement.tintColor != 0xFFFFFFFF) {
            mWidgetPlacement.tintColor = 0xFFFFFFFF;
            mWidgetManager.updateWidget(this);
        }

        if (mLibraryItemContextMenu != null && !mLibraryItemContextMenu.isReleased()
            && mLibraryItemContextMenu.isVisible()) {
            mLibraryItemContextMenu.hide(REMOVE_WIDGET);
        }
    }

    // GeckoSession.ContentDelegate

    @Override
    public void onContextMenu(GeckoSession session, int screenX, int screenY, ContextElement element) {
        if (element.type == ContextElement.TYPE_VIDEO) {
            return;
        }

        hideContextMenus();

        mContextMenu = new ContextMenuWidget(getContext());
        mContextMenu.mWidgetPlacement.parentHandle = getHandle();
        mContextMenu.setDismissCallback(this::hideContextMenus);
        mContextMenu.setContextElement(element);
        mContextMenu.show(REQUEST_FOCUS);

        mWidgetPlacement.tintColor = 0x555555FF;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void onFirstComposite(@NonNull GeckoSession session) {
        if (!mAfterFirstPaint) {
            return;
        }
        if (mFirstDrawCallback != null) {
            mUIThreadExecutor.execute(mFirstDrawCallback);
            mFirstDrawCallback = null;
        }
    }

    @Override
    public void onFirstContentfulPaint(@NonNull GeckoSession session) {
        if (mAfterFirstPaint) {
            return;
        }
        if (mFirstDrawCallback != null) {
            mUIThreadExecutor.execute(mFirstDrawCallback);
            mFirstDrawCallback = null;
            mAfterFirstPaint = true;
        }
    }

    // VideoAvailabilityListener

    @Override
    public void onVideoAvailabilityChanged(boolean aVideosAvailable) {
        if (mTitleBar != null) {
            mTitleBar.mediaAvailabilityChanged(aVideosAvailable);
        }

        for (WindowListener listener: mListeners) {
            listener.onVideoAvailabilityChanged(this);
        }
    }

    // GeckoSession.NavigationDelegate


    @Override
    public void onPageStart(@NonNull GeckoSession geckoSession, @NonNull String s) {
        mCaptureOnPageStop = true;

        if (isHistoryVisible()) {
            hideHistory();
        }

        if (isBookmarksVisible()) {
            hideBookmarks();
        }
    }

    @Override
    public void onPageStop(@NonNull GeckoSession aSession, boolean b) {
        if (mCaptureOnPageStop || !mSession.hasCapturedBitmap()) {
            mCaptureOnPageStop = false;
            captureImage();
        }
    }

    public void captureImage() {
        mSession.captureBitmap();
    }

    @Override
    public void onLocationChange(@NonNull GeckoSession session, @Nullable String url) {
        updateTitleBarUrl(url);
    }

    // GeckoSession.HistoryDelegate

    @Override
    public void onHistoryStateChange(@NonNull GeckoSession geckoSession, @NonNull HistoryList historyList) {
        if (!mSession.isPrivateMode()) {
            for (HistoryItem item : historyList) {
                SessionStore.get().getHistoryStore().recordObservation(item.getUri(), new PageObservation(item.getTitle()));
            }
        }
    }

    @Nullable
    @Override
    public GeckoResult<Boolean> onVisited(@NonNull GeckoSession geckoSession, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        if (mSession.isPrivateMode() ||
                (flags & VISIT_TOP_LEVEL) == 0 ||
                (flags & VISIT_UNRECOVERABLE_ERROR) != 0) {
            return GeckoResult.fromValue(false);
        }

        // Check if we want this type of url.
        if (!shouldStoreUri(url)) {
            return GeckoResult.fromValue(false);
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
        if ((flags & GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0) {
            redirectSource = RedirectSource.PERMANENT;

        } else if ((flags & GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE) != 0) {
            redirectSource = RedirectSource.TEMPORARY;

        } else {
            redirectSource = RedirectSource.NOT_A_SOURCE;
        }

        SessionStore.get().getHistoryStore().recordVisit(url, new PageVisit(visitType, redirectSource));
        SessionStore.get().getHistoryStore().recordObservation(url, new PageObservation(url));

        return GeckoResult.fromValue(true);
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
    public GeckoResult<boolean[]> getVisited(@NonNull GeckoSession geckoSession, @NonNull String[] urls) {
        if (mSession.isPrivateMode()) {
            return GeckoResult.fromValue(new boolean[]{});
        }

        GeckoResult<boolean[]> result = new GeckoResult<>();

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

    // GeckoSession.ProgressDelegate

    @Override
    public void onSecurityChange(GeckoSession geckoSession, SecurityInformation securityInformation) {
        if (mTitleBar != null) {
            mTitleBar.setIsInsecure(!securityInformation.isSecure);
        }
    }

    // GeckoSession.SelectionActionDelegate

    @Override
    public void onShowActionRequest(@NonNull GeckoSession aSession, @NonNull Selection aSelection) {
        if (aSelection.availableActions.size() == 1 && (aSelection.availableActions.contains(GeckoSession.SelectionActionDelegate.ACTION_HIDE))) {
            // See: https://github.com/MozillaReality/FirefoxReality/issues/2214
            aSelection.hide();
            return;
        }

        hideContextMenus();
        mSelectionMenu = new SelectionActionWidget(getContext());
        mSelectionMenu.mWidgetPlacement.parentHandle = getHandle();
        mSelectionMenu.setActions(aSelection.availableActions);
        Matrix matrix = new Matrix();
        aSession.getClientToSurfaceMatrix(matrix);
        matrix.mapRect(aSelection.clientRect);
        RectF selectionRect = null;
        if (aSelection.clientRect != null) {
            float ratio = WidgetPlacement.worldToWindowRatio(getContext());
            selectionRect = new RectF(
                    aSelection.clientRect.left * ratio,
                    aSelection.clientRect.top* ratio,
                    aSelection.clientRect.right * ratio,
                    aSelection.clientRect.bottom * ratio
            );
        }
        mSelectionMenu.setSelectionRect(selectionRect);
        mSelectionMenu.setDelegate(new SelectionActionWidget.Delegate() {
            @Override
            public void onAction(String action) {
                hideContextMenus();
                if (aSelection.isActionAvailable(action)) {
                    aSelection.execute(action);
                }
                if (GeckoSession.SelectionActionDelegate.ACTION_COPY.equals(action) &&
                        aSelection.isActionAvailable(GeckoSession.SelectionActionDelegate.ACTION_UNSELECT)) {
                    // Don't keep the text selected after it's copied.
                    aSelection.execute(GeckoSession.SelectionActionDelegate.ACTION_UNSELECT);
                }
            }

            @Override
            public void onDismiss() {
                hideContextMenus();
                if (aSelection.isActionAvailable(GeckoSession.SelectionActionDelegate.ACTION_UNSELECT)) {
                    aSelection.execute(GeckoSession.SelectionActionDelegate.ACTION_UNSELECT);
                }
            }
        });
        mSelectionMenu.show(KEEP_FOCUS);
    }

    @Override
    public void onHideAction(@NonNull GeckoSession aSession, int aHideReason) {
        hideContextMenus();
    }

}
