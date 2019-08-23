/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.VideoAvailabilityListener;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.views.BookmarksView;
import org.mozilla.vrbrowser.ui.widgets.dialogs.AppDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.ContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.MaxWindowsWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AlertPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.AuthPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ChoicePromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.ConfirmPromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.PromptWidget;
import org.mozilla.vrbrowser.ui.widgets.prompts.TextPromptWidget;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;

import mozilla.components.concept.storage.VisitType;

import static org.mozilla.vrbrowser.utils.ServoUtils.isInstanceOfServoSession;

public class WindowWidget extends UIWidget implements SessionChangeListener,
        GeckoSession.ContentDelegate, GeckoSession.PromptDelegate, GeckoSession.ProgressDelegate,
        GeckoSession.NavigationDelegate, VideoAvailabilityListener {

    private static final String LOGTAG = "VRB";

    private int mSessionId;
    private GeckoDisplay mDisplay;
    private Surface mSurface;
    private int mWidth;
    private int mHeight;
    private int mHandle;
    private WidgetPlacement mWidgetPlacement;
    private TopBarWidget mTopBar;
    private TitleBarWidget mTitleBar;
    private WidgetManagerDelegate mWidgetManager;
    private ChoicePromptWidget mChoicePrompt;
    private AlertPromptWidget mAlertPrompt;
    private MaxWindowsWidget mMaxWindowsDialog;
    private ConfirmPromptWidget mConfirmPrompt;
    private TextPromptWidget mTextPrompt;
    private AuthPromptWidget mAuthPrompt;
    private NoInternetWidget mNoInternetToast;
    private AppDialogWidget mAppDialog;
    private ContextMenuWidget mContextMenu;
    private int mWidthBackup;
    private int mHeightBackup;
    private int mBorderWidth;
    private Runnable mFirstDrawCallback;
    private boolean mIsInVRVideoMode;
    private View mView;
    private Point mLastMouseClickPos;
    private SessionStack mSessionStack;
    private int mWindowId;
    private BookmarksView mBookmarksView;
    private ArrayList<BookmarkListener> mBookmarksListeners;
    private Windows.WindowPlacement mWindowPlacement = Windows.WindowPlacement.FRONT;
    private float mMaxWindowScale = 3;
    private boolean mIsRestored = false;
    private WindowDelegate mWindowDelegate;
    boolean mActive = false;
    boolean mHovered = false;
    boolean mClickedAfterFocus = false;

    public interface WindowDelegate {
        void onFocusRequest(@NonNull WindowWidget aWindow);
        void onBorderChanged(@NonNull WindowWidget aWindow);
    }

    public WindowWidget(Context aContext, int windowId, boolean privateMode) {
        super(aContext);
        mWidgetManager = (WidgetManagerDelegate) aContext;
        mBorderWidth = SettingsStore.getInstance(aContext).getTransparentBorderWidth();

        mWindowId = windowId;
        mSessionStack = SessionStore.get().createSessionStack(mWindowId, privateMode);
        mSessionStack.setPromptDelegate(this);
        mSessionStack.addSessionChangeListener(this);
        mSessionStack.addContentListener(this);
        mSessionStack.addVideoAvailabilityListener(this);
        mSessionStack.addNavigationListener(this);
        mSessionStack.addProgressListener(this);
        mSessionStack.newSession();

        mBookmarksView  = new BookmarksView(aContext);
        mBookmarksListeners = new ArrayList<>();

        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        initializeWidgetPlacement(mWidgetPlacement);

        mTopBar = new TopBarWidget(aContext);
        mTopBar.attachToWindow(this);
        mLastMouseClickPos = new Point(0, 0);

        mTitleBar = new TitleBarWidget(aContext);
        mTitleBar.attachToWindow(this);

        setFocusable(true);

        TelemetryWrapper.openWindowEvent(mWindowId);
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
        // Check Windows.placeWindow method for remaining placement set-up
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

        mSessionStack.setActive(true);
    }

    @Override
    public void hide(@HideFlags int aHideFlag) {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
        }

        mWidgetManager.updateWidget(this);

        clearFocus();

        mSessionStack.setActive(false);
    }

    @Override
    protected void onDismiss() {
        if (isBookmarksVisible()) {
            switchBookmarks();

        } else {
            SessionStack activeStore = SessionStore.get().getSessionStack(mWindowId);
            if (activeStore.canGoBack()) {
                activeStore.goBack();
            }
        }
    }

    public void close() {
        TelemetryWrapper.closeWindowEvent(mWindowId);

        releaseWidget();
        mBookmarksView.onDestroy();
        SessionStore.get().destroySessionStack(mWindowId);
    }

    public void loadHomeIfNotRestored() {
        if (!mIsRestored) {
            loadHome();
        }
    }

    public void loadHome() {
        if (mSessionStack.isPrivateMode()) {
            mSessionStack.loadPrivateBrowsingPage();

        } else {
            mSessionStack.loadUri(SettingsStore.getInstance(getContext()).getHomepage());
        }
    }

    protected void setRestored(boolean restored) {
        mIsRestored = restored;
    }

    private void setView(View view) {
        pauseCompositor();
        mView = view;
        removeView(view);
        mView.setVisibility(VISIBLE);
        addView(mView);
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

    private void unsetView(View view) {
        if (mView != null && mView == view) {
            mView = null;
            removeView(view);
            view.setVisibility(GONE);
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

    public boolean isBookmarksVisible() {
        return (mView != null);
    }

    public void addBookmarksListener(@NonNull BookmarkListener listener) {
        mBookmarksListeners.add(listener);
    }

    public void removeBookmarksListener(@NonNull BookmarkListener listener) {
        mBookmarksListeners.remove(listener);
    }

    public void switchBookmarks() {
        if (mView == null) {
            setView(mBookmarksView);
            for (BookmarkListener listener : mBookmarksListeners)
                listener.onBookmarksShown(this);

        } else {
            unsetView(mBookmarksView);
            for (BookmarkListener listener : mBookmarksListeners)
                listener.onBookmarksHidden(this);
        }

        updateTitleBar();
    }

    public void hideBookmarks() {
        if (mView != null) {
            unsetView(mBookmarksView);
            for (BookmarkListener listener : mBookmarksListeners)
                listener.onBookmarksHidden(this);
        }
    }

    public void pauseCompositor() {
        if (mDisplay == null) {
            return;
        }

        mDisplay.surfaceDestroyed();
    }

    public void resumeCompositor() {
        if (mDisplay == null) {
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
            SessionStore.get().setActiveStore(mWindowId);
            mSessionId = mSessionStack.getCurrentSessionId();
            GeckoSession session = mSessionStack.getSession(mSessionId);
            if (session != null) {
                session.getTextInput().setView(this);
            }
        } else {
            updateTitleBar();
        }

        TelemetryWrapper.activePlacementEvent(mWindowPlacement.getValue(), mActive);
        updateBorder();
    }

    private void updateTitleBar() {
        if (isBookmarksVisible()) {
            updateTitleBarUrl(getResources().getString(R.string.url_bookmarks_title));

        } else {
            updateTitleBarUrl(mSessionStack.getCurrentUri());
        }
    }

    private void updateTitleBarUrl(String url) {
        if (mTitleBar != null && url != null) {
            mTitleBar.setIsInsecure(!mSessionStack.isSecure());
            if (url.startsWith("data") && mSessionStack.isPrivateMode()) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(getResources().getString(R.string.private_browsing_title));

            } else if (url.equals(mSessionStack.getHomeUri())) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(getResources().getString(R.string.url_home_title, getResources().getString(R.string.app_name)));

            } else if (url.equals(getResources().getString(R.string.url_bookmarks_title))) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(url);

            } else {
                mTitleBar.setURL(url);
            }
        }
    }

    public SessionStack getSessionStack() {
        return mSessionStack;
    }

    public TopBarWidget getTopBar() {
        return mTopBar;
    }

    public TitleBarWidget getTitleBar() {
        return mTitleBar;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        if (mView != null) {
            super.setSurfaceTexture(aTexture, aWidth, aHeight);

        } else {
            GeckoSession session = mSessionStack.getSession(mSessionId);
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
            if (mDisplay == null) {
                mDisplay = session.acquireDisplay();
            } else {
                Log.e(LOGTAG, "GeckoDisplay was not null in BrowserWidget.setSurfaceTexture()");
            }
            callSurfaceChanged();
        }
    }

    @Override
    public void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        if (mView != null) {
            super.setSurface(aSurface, aWidth, aHeight, aFirstDrawCallback);

        } else {
            GeckoSession session = mSessionStack.getSession(mSessionId);
            if (session == null) {
                return;
            }
            mWidth = aWidth;
            mHeight = aHeight;
            mSurface = aSurface;
            mFirstDrawCallback = aFirstDrawCallback;
            if (mDisplay == null) {
                mDisplay = session.acquireDisplay();
            } else {
                Log.e(LOGTAG, "GeckoDisplay was not null in BrowserWidget.setSurfaceTexture()");
            }
            if (mSurface != null) {
                callSurfaceChanged();
            } else {
                mDisplay.surfaceDestroyed();
            }
        }
    }

    private void callSurfaceChanged() {
        if (mDisplay != null) {
            mDisplay.surfaceChanged(mSurface, mBorderWidth, mBorderWidth, mWidth - mBorderWidth * 2, mHeight - mBorderWidth * 2);
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
        mLastMouseClickPos = new Point((int)aEvent.getX(), (int)aEvent.getY());
        if (aEvent.getAction() == MotionEvent.ACTION_DOWN) {
            if (!mActive) {
                mClickedAfterFocus = true;
                updateBorder();
                if (mWindowDelegate != null) {
                    // Focus this window
                    mWindowDelegate.onFocusRequest(this);
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
            GeckoSession session = mSessionStack.getSession(mSessionId);
            if (session == null) {
                return;
            }
            session.getPanZoomController().onTouchEvent(aEvent);
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
            SessionStack activeStore = SessionStore.get().getActiveStore();
            GeckoSession session = activeStore.getSession(mSessionId);
            if (session == null) {
                return;
            }
            session.getPanZoomController().onMotionEvent(aEvent);
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
            if (mWindowDelegate != null) {
                mWindowDelegate.onBorderChanged(this);
            }
        }
    }

    public void setWindowDelegate(WindowDelegate aDelegate) {
        mWindowDelegate = aDelegate;
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
        mSessionStack.setPromptDelegate(null);
        mSessionStack.removeSessionChangeListener(this);
        mSessionStack.removeContentListener(this);
        mSessionStack.removeVideoAvailabilityListener(this);
        mSessionStack.removeNavigationListener(this);
        mSessionStack.removeProgressListener(this);
        GeckoSession session = mSessionStack.getSession(mSessionId);
        if (session == null) {
            return;
        }
        if (mDisplay != null) {
            mDisplay.surfaceDestroyed();
            session.releaseDisplay(mDisplay);
            mDisplay = null;
        }
        session.getTextInput().setView(null);
    }


    @Override
    public void setFirstDraw(final boolean aIsFirstDraw) {
        mWidgetPlacement.firstDraw = aIsFirstDraw;
    }

    @Override
    public boolean getFirstDraw() {
        return mWidgetPlacement.firstDraw;
    }

    @Override
    public boolean isVisible() {
        return mWidgetPlacement.visible;
    }

    @Override
    public void setVisible(boolean aVisible) {
        if (mWidgetPlacement.visible == aVisible) {
            return;
        }
        mWidgetPlacement.visible = aVisible;
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

    // SessionStack.GeckoSessionChange

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        Log.d(LOGTAG, "onCurrentSessionChange: " + this.toString());
        if (mSessionId == aId) {
            Log.d(LOGTAG, "BrowserWidget.onCurrentSessionChange session id same, bail: " + aId);
            return;
        }

        GeckoSession oldSession = mSessionStack.getSession(mSessionId);
        if (oldSession != null && mDisplay != null) {
            Log.d(LOGTAG, "Detach from previous session: " + mSessionId);
            oldSession.getTextInput().setView(null);
            mDisplay.surfaceDestroyed();
            oldSession.releaseDisplay(mDisplay);
            mDisplay = null;
        }

        mWidgetManager.setIsServoSession(isInstanceOfServoSession(aSession));

        mSessionId = aId;
        mDisplay = aSession.acquireDisplay();
        Log.d(LOGTAG, "surfaceChanged: " + aId);
        callSurfaceChanged();
        aSession.getTextInput().setView(this);

        boolean isPrivateMode  = aSession.getSettings().getUsePrivateMode();
        if (isPrivateMode) {
            setPrivateBrowsingEnabled(true);
        } else {
            setPrivateBrowsingEnabled(false);
        }
    }

    // View
    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        Log.d(LOGTAG, "BrowserWidget onCreateInputConnection");
        GeckoSession session = mSessionStack.getSession(mSessionId);
        if (session == null) {
            return null;
        }
        return session.getTextInput().onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        SessionStack sessionStack = SessionStore.get().getSessionStack(mWindowId);
        return sessionStack.isInputActive(mSessionId);
    }


    @Override
    public boolean onKeyPreIme(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyPreIme(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyPreIme(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyUp(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyUp(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyUp(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyDown(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyDown(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyDown(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyLongPress(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyLongPress(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyLongPress(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyMultiple(int aKeyCode, int repeatCount, KeyEvent aEvent) {
        if (super.onKeyMultiple(aKeyCode, repeatCount, aEvent)) {
            return true;
        }
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyMultiple(aKeyCode, repeatCount, aEvent);
    }
    
    @Override
    protected void onFocusChanged(boolean aGainFocus, int aDirection, Rect aPreviouslyFocusedRect) {
        super.onFocusChanged(aGainFocus, aDirection, aPreviouslyFocusedRect);
        Log.d(LOGTAG, "BrowserWidget onFocusChanged: " + (aGainFocus ? "true" : "false"));
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        GeckoSession session = mSessionStack.getSession(mSessionId);
        return (session != null) && session.getPanZoomController().onMotionEvent(aEvent);
    }

    private void setPrivateBrowsingEnabled(boolean isEnabled) {
    }

    public void setNoInternetToastVisible(boolean aVisible) {
        if (mNoInternetToast == null) {
            mNoInternetToast = new NoInternetWidget(getContext());
            mNoInternetToast.mWidgetPlacement.parentHandle = getHandle();
        }
        if (aVisible && !mNoInternetToast.isVisible()) {
            mNoInternetToast.show(REQUEST_FOCUS);
        } else if (!aVisible && mNoInternetToast.isVisible()) {
            mNoInternetToast.hide(REMOVE_WIDGET);
        }
    }

    public void showAlert(String title, @NonNull String msg, @NonNull PromptWidget.PromptDelegate callback) {
        mAlertPrompt = new AlertPromptWidget(getContext());
        mAlertPrompt.mWidgetPlacement.parentHandle = getHandle();
        mAlertPrompt.setTitle(title);
        mAlertPrompt.setMessage(msg);
        mAlertPrompt.setPromptDelegate(callback);
        mAlertPrompt.show(REQUEST_FOCUS);
    }

    public void showButtonPrompt(String title, @NonNull String msg, @NonNull String[] btnMsg, @NonNull ConfirmPromptWidget.ConfirmPromptDelegate callback) {
        mConfirmPrompt = new ConfirmPromptWidget(getContext());
        mConfirmPrompt.mWidgetPlacement.parentHandle = getHandle();
        mConfirmPrompt.setTitle(title);
        mConfirmPrompt.setMessage(msg);
        mConfirmPrompt.setButtons(btnMsg);
        mConfirmPrompt.setPromptDelegate(callback);
        mConfirmPrompt.show(REQUEST_FOCUS);
    }

    public void showAppDialog(@NonNull String title, @NonNull @StringRes int  description, @NonNull  @StringRes int [] btnMsg, @NonNull AppDialogWidget.Delegate callback) {
        mAppDialog = new AppDialogWidget(getContext());
        mAppDialog.mWidgetPlacement.parentHandle = getHandle();
        mAppDialog.setTitle(title);
        mAppDialog.setMessage(description);
        mAppDialog.setButtons(btnMsg);
        mAppDialog.setDelegate(callback);
        mAppDialog.show(REQUEST_FOCUS);
    }

    public void showMaxWindowsDialog(int maxDialogs) {
        mMaxWindowsDialog = new MaxWindowsWidget(getContext());
        mMaxWindowsDialog.mWidgetPlacement.parentHandle = getHandle();
        mMaxWindowsDialog.setMessage(getContext().getString(R.string.max_windows_message, String.valueOf(maxDialogs)));
        mMaxWindowsDialog.show(REQUEST_FOCUS);
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

    // PromptDelegate

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession geckoSession, @NonNull AlertPrompt alertPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mAlertPrompt = new AlertPromptWidget(getContext());
        mAlertPrompt.mWidgetPlacement.parentHandle = getHandle();
        mAlertPrompt.setTitle(alertPrompt.title);
        mAlertPrompt.setMessage(alertPrompt.message);
        mAlertPrompt.setPromptDelegate(() -> result.complete(alertPrompt.dismiss()));
        mAlertPrompt.show(REQUEST_FOCUS);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession geckoSession, @NonNull ButtonPrompt buttonPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mConfirmPrompt = new ConfirmPromptWidget(getContext());
        mConfirmPrompt.mWidgetPlacement.parentHandle = getHandle();
        mConfirmPrompt.setTitle(buttonPrompt.title);
        mConfirmPrompt.setMessage(buttonPrompt.message);
        mConfirmPrompt.setButtons(new String[] {
                getResources().getText(R.string.ok_button).toString(),
                getResources().getText(R.string.cancel_button).toString()
        });
        mConfirmPrompt.setPromptDelegate(new ConfirmPromptWidget.ConfirmPromptDelegate() {
            @Override
            public void confirm(int index) {
                result.complete(buttonPrompt.confirm(index));
            }

            @Override
            public void dismiss() {
                result.complete(buttonPrompt.dismiss());
            }
        });
        mConfirmPrompt.show(REQUEST_FOCUS);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession geckoSession, @NonNull TextPrompt textPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mTextPrompt = new TextPromptWidget(getContext());
        mTextPrompt.mWidgetPlacement.parentHandle = getHandle();
        mTextPrompt.setTitle(textPrompt.title);
        mTextPrompt.setMessage(textPrompt.message);
        mTextPrompt.setDefaultText(textPrompt.defaultValue);
        mTextPrompt.setPromptDelegate(new TextPromptWidget.TextPromptDelegate() {
            @Override
            public void confirm(String message) {
                result.complete(textPrompt.confirm(message));
            }

            @Override
            public void dismiss() {
                result.complete(textPrompt.dismiss());
            }
        });
        mTextPrompt.show(REQUEST_FOCUS);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession geckoSession, @NonNull AuthPrompt authPrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mAuthPrompt = new AuthPromptWidget(getContext());
        mAuthPrompt.mWidgetPlacement.parentHandle = getHandle();
        mAuthPrompt.setTitle(authPrompt.title);
        mAuthPrompt.setMessage(authPrompt.message);
        mAuthPrompt.setAuthOptions(authPrompt.authOptions);
        mAuthPrompt.setPromptDelegate(new AuthPromptWidget.AuthPromptDelegate() {
            @Override
            public void dismiss() {
                result.complete(authPrompt.dismiss());
            }

            @Override
            public void confirm(String password) {
                result.complete(authPrompt.confirm(password));
            }

            @Override
            public void confirm(String username, String password) {
                result.complete(authPrompt.confirm(username, password));
            }
        });
        mAuthPrompt.show(REQUEST_FOCUS);

        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession geckoSession, @NonNull ChoicePrompt choicePrompt) {
        final GeckoResult<PromptResponse> result = new GeckoResult<>();

        mChoicePrompt = new ChoicePromptWidget(getContext());
        mChoicePrompt.mWidgetPlacement.parentHandle = getHandle();
        mChoicePrompt.setTitle(choicePrompt.title);
        mChoicePrompt.setMessage(choicePrompt.message);
        mChoicePrompt.setChoices(choicePrompt.choices);
        mChoicePrompt.setMenuType(choicePrompt.type);
        mChoicePrompt.setPromptDelegate(new ChoicePromptWidget.ChoicePromptDelegate() {
            @Override
            public void confirm(String[] choices) {
                result.complete(choicePrompt.confirm(choices));
            }

            @Override
            public void dismiss() {

            }
        });
        mChoicePrompt.show(REQUEST_FOCUS);

        return result;
    }

    // GeckoSession.ContentDelegate

    @Override
    public void onContextMenu(GeckoSession session, int screenX, int screenY, ContextElement element) {
        TelemetryWrapper.longPressContextMenuEvent();

        if (mContextMenu != null) {
            mContextMenu.hide(REMOVE_WIDGET);
        }

        mContextMenu = new ContextMenuWidget(getContext());
        mContextMenu.mWidgetPlacement.parentHandle = getHandle();
        mContextMenu.setContextElement(mLastMouseClickPos, element);
        mContextMenu.show(REQUEST_FOCUS);
    }

    @Override
    public void onFirstComposite(GeckoSession session) {
        if (mFirstDrawCallback != null) {
            // Post this call because running it synchronously can cause a deadlock if the runnable
            // resizes the widget and calls surfaceChanged. See https://github.com/MozillaReality/FirefoxReality/issues/1459.
            ThreadUtils.postToUiThread(mFirstDrawCallback);
            mFirstDrawCallback = null;
        }
    }

    // VideoAvailabilityListener

    @Override
    public void onVideoAvailabilityChanged(boolean aVideosAvailable) {
        mWidgetManager.setCPULevel(aVideosAvailable ?
                WidgetManagerDelegate.CPU_LEVEL_HIGH :
                WidgetManagerDelegate.CPU_LEVEL_NORMAL);
    }

    // GeckoSession.NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession session, @Nullable String url) {
        if (isBookmarksVisible()) {
            switchBookmarks();
        }

        if (mTitleBar != null && url != null) {
            if (url.startsWith("data") && mSessionStack.isPrivateMode()) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(getResources().getString(R.string.private_browsing_title));

            } else if (url.equals(mSessionStack.getHomeUri())) {
                mTitleBar.setInsecureVisibility(VISIBLE);
                mTitleBar.setURL(getResources().getString(R.string.url_home_title, getResources().getString(R.string.app_name)));

            } else if (url.equals(getResources().getString(R.string.url_bookmarks_title))) {
                mTitleBar.setInsecureVisibility(GONE);
                mTitleBar.setURL(url);

            } else {
                mTitleBar.setInsecureVisibility(View.VISIBLE);
                mTitleBar.setURL(url);
            }
        }
    }

    @Nullable
    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession session, @NonNull LoadRequest request) {
        if (request.isRedirect) {
            SessionStore.get().getHistoryStore().addHistory(request.uri, VisitType.EMBED);
        } else if (request.triggerUri != null) {
            SessionStore.get().getHistoryStore().addHistory(request.uri, VisitType.LINK);
        }

        return GeckoResult.ALLOW;
    }

    // GeckoSession.ProgressDelegate

    @Override
    public void onSecurityChange(GeckoSession geckoSession, SecurityInformation securityInformation) {
        if (mTitleBar != null) {
            mTitleBar.setIsInsecure(!securityInformation.isSecure);
        }
    }

}
