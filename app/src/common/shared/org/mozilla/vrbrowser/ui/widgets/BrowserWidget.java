/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.widgets.prompts.ChoicePromptWidget;


public class BrowserWidget extends UIWidget implements SessionStore.SessionChangeListener,
        GeckoSession.ContentDelegate, GeckoSession.PromptDelegate, WidgetManagerDelegate.UpdateListener,
        BookmarkListener {

    private static final String LOGTAG = "VRB";

    private int mSessionId;
    private GeckoDisplay mDisplay;
    private Surface mSurface;
    private int mWidth;
    private int mHeight;
    private int mHandle;
    private WidgetPlacement mWidgetPlacement;
    private WidgetManagerDelegate mWidgetManager;
    private ChoicePromptWidget mChoicePrompt;
    private int mWidthBackup;
    private int mHeightBackup;
    private int mBorderWidth;
    private BookmarksWidget mBookmarksWidget;
    private float mMultiplier;
    Runnable mFirstDrawCallback;

    public BrowserWidget(Context aContext, int aSessionId) {
        super(aContext);
        mSessionId = aSessionId;
        mWidgetManager = (WidgetManagerDelegate) aContext;
        mBorderWidth = SettingsStore.getInstance(aContext).getLayersEnabled() ? 1 : 0;
        SessionStore.get().addSessionChangeListener(this);
        SessionStore.get().addPromptListener(this);
        SessionStore.get().addContentListener(this);
        mWidgetManager.addUpdateListener(this);
        setFocusable(true);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session != null) {
            session.getTextInput().setView(this);
        }
        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        initializeWidgetPlacement(mWidgetPlacement);

        mMultiplier = 1.0f;

        handleResizeEvent(SettingsStore.getInstance(getContext()).getBrowserWorldWidth(),
                SettingsStore.getInstance(getContext()).getBrowserWorldHeight());
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = SettingsStore.getInstance(getContext()).getWindowWidth() + mBorderWidth * 2;
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight() + mBorderWidth * 2;
        aPlacement.density = 1.0f;
        aPlacement.translationX = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.window_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.visible = true;
    }

    @Override
    public void show(boolean focus) {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
        }

        mWidgetManager.updateWidget(this);

        if (focus) {
            setFocusableInTouchMode(true);
            requestFocusFromTouch();
        }
    }

    @Override
    public void hide(@HideFlags int aHideFlag) {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
        }

        mWidgetManager.updateWidget(this);

        clearFocus();
    }

    public void setBookmarksWidget(BookmarksWidget aWidget) {
        mBookmarksWidget = aWidget;
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
        mWidthBackup = mWidth;
        mHeightBackup = mHeight;
        boolean borderChanged = aResetBorder && mBorderWidth > 0;
        if (aVideoWidth == mWidth && aVideoHeight == mHeight && !borderChanged) {
            return;
        }
        if (aResetBorder) {
            mBorderWidth = 0;
        }
        mWidgetPlacement.width = aVideoWidth + mBorderWidth * 2;
        mWidgetPlacement.height = aVideoHeight + mBorderWidth * 2;
        resizeSurface(aVideoWidth, aVideoHeight);
        Log.e(LOGTAG, "onMetadataChange resize browser " + aVideoWidth + " " + aVideoHeight);
    }

    public void disableVRVideoMode() {
        if (mWidthBackup == 0 || mHeightBackup == 0) {
            return;
        }
        int border = SettingsStore.getInstance(getContext()).getLayersEnabled() ? 1 : 0;
        if (mWidthBackup == mWidth && mHeightBackup == mHeight && border == mBorderWidth) {
            return;
        }
        mBorderWidth = border;
        mWidgetPlacement.width = mWidthBackup;
        mWidgetPlacement.height = mHeightBackup;
        resizeSurface(mWidthBackup, mWidthBackup);
    }

    @Override
    public void setSize(float windowWidth, float windowHeight, float multiplier) {
        mMultiplier = multiplier;

        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        float aspect = windowWidth / windowHeight;
        float worldHeight = worldWidth / aspect;
        float area = worldWidth * worldHeight * multiplier;

        float targetWidth = (float) Math.sqrt(area * aspect);
        float targetHeight = (float) Math.sqrt(area / aspect);

        handleResizeEvent(targetWidth, targetHeight);
    }

    public int getBorderWidth() {
        return mBorderWidth;
    }

    public int getTextureWidth() {
        return mWidth;
    }

    public int getTextureHeight() {
        return mHeight;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
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

    @Override
    public void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
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

    private void callSurfaceChanged() {
        mDisplay.surfaceChanged(mSurface, mBorderWidth, mBorderWidth, mWidth - mBorderWidth * 2, mHeight - mBorderWidth * 2);
    }

    @Override
    public void resizeSurface(final int aWidth, final int aHeight) {
        mWidth = aWidth;
        mHeight = aHeight;
        if (mTexture != null) {
            mTexture.setDefaultBufferSize(aWidth, aHeight);
        }
        callSurfaceChanged();
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
        if (aEvent.getActionMasked() == MotionEvent.ACTION_UP) {
            requestFocus();
            requestFocusFromTouch();
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return;
        }
        session.getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return;
        }
        session.getPanZoomController().onMotionEvent(aEvent);
    }

    @Override
    public void handleResizeEvent(float aWorldWidth, float aWorldHeight) {
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        int defaultWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        int defaultHeight = SettingsStore.getInstance(getContext()).getWindowHeight();

        float aspect = (float)defaultWidth / (float)defaultHeight;
        float worldHeight = worldWidth / aspect;
        mWidgetPlacement.width = (int)((aWorldWidth * defaultWidth) / worldWidth) + mBorderWidth * 2;
        mWidgetPlacement.height = (int)((aWorldHeight * defaultHeight) /worldHeight) + mBorderWidth * 2;
        mWidgetPlacement.worldWidth = aWorldWidth;
        mWidgetManager.updateWidget(this);

        SettingsStore.getInstance(getContext()).setBrowserWorldWidth(aWorldWidth);
        SettingsStore.getInstance(getContext()).setBrowserWorldHeight(aWorldHeight);
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);
        SessionStore.get().removePromptListener(this);
        SessionStore.get().removeContentListener(this);
        mWidgetManager.removeUpdateListener(this);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
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

    @SuppressLint("MissingSuperCall")
    @Override
    public void draw(Canvas aCanvas) {
        // Nothing to do

    }

    // SessionStore.GeckoSessionChange
    @Override
    public void onNewSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        Log.d(LOGTAG, "onCurrentSessionChange: " + this.toString());
        if (mSessionId == aId) {
            Log.d(LOGTAG, "BrowserWidget.onCurrentSessionChange session id same, bail: " + aId);
            return;
        }

        GeckoSession oldSession = SessionStore.get().getSession(mSessionId);
        if (oldSession != null && mDisplay != null) {
            Log.d(LOGTAG, "Detach from previous session: " + mSessionId);
            oldSession.getTextInput().setView(null);
            mDisplay.surfaceDestroyed();
            oldSession.releaseDisplay(mDisplay);
            mDisplay = null;
        }

        mSessionId = aId;
        mDisplay = aSession.acquireDisplay();
        Log.d(LOGTAG, "surfaceChanged: " + aId);
        callSurfaceChanged();
        aSession.getTextInput().setView(this);

        boolean isPrivateMode  = aSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode)
            setPrivateBrowsingEnabled(true);
        else
            setPrivateBrowsingEnabled(false);
    }

    // View
    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        Log.d(LOGTAG, "BrowserWidget onCreateInputConnection");
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return null;
        }
        return session.getTextInput().onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return SessionStore.get().isInputActive(mSessionId);
    }


    @Override
    public boolean onKeyPreIme(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyPreIme(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyPreIme(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyUp(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyUp(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyUp(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyDown(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyDown(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyDown(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyLongPress(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyLongPress(aKeyCode, aEvent)) {
            return true;
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyLongPress(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyMultiple(int aKeyCode, int repeatCount, KeyEvent aEvent) {
        if (super.onKeyMultiple(aKeyCode, repeatCount, aEvent)) {
            return true;
        }
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getTextInput().onKeyMultiple(aKeyCode, repeatCount, aEvent);
    }
    
    @Override
    protected void onFocusChanged(boolean aGainFocus, int aDirection, Rect aPreviouslyFocusedRect) {
        super.onFocusChanged(aGainFocus, aDirection, aPreviouslyFocusedRect);
        Log.d(LOGTAG, "BrowserWidget onFocusChanged: " + (aGainFocus ? "true" : "false"));
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        return (session != null) && session.getPanZoomController().onMotionEvent(aEvent);
    }

    private void setPrivateBrowsingEnabled(boolean isEnabled) {
        // TODO: Fade in/out the browser window. Waiting for https://github.com/MozillaReality/FirefoxReality/issues/77
    }

    @Override
    public void onAlert(GeckoSession session, String title, String msg, AlertCallback callback) {

    }

    @Override
    public void onButtonPrompt(GeckoSession session, String title, String msg, String[] btnMsg, ButtonCallback callback) {

    }

    @Override
    public void onTextPrompt(GeckoSession session, String title, String msg, String value, TextCallback callback) {

    }

    @Override
    public void onAuthPrompt(GeckoSession session, String title, String msg, AuthOptions options, AuthCallback callback) {

    }

    @Override
    public void onChoicePrompt(GeckoSession session, String title, String msg, int type, final Choice[] choices, final ChoiceCallback callback) {
        if (mChoicePrompt == null) {
            mChoicePrompt = new ChoicePromptWidget(getContext());
            mChoicePrompt.mWidgetPlacement.parentHandle = getHandle();
        }

        mChoicePrompt.setTitle(title);
        mChoicePrompt.setMessage(msg);
        mChoicePrompt.setChoices(choices);
        mChoicePrompt.setMenuType(type);
        mChoicePrompt.setDelegate(ids -> {
            callback.confirm(ids);
            mChoicePrompt.hide(UIWidget.REMOVE_WIDGET);
        });

        mChoicePrompt.show();
    }

    @Override
    public void onColorPrompt(GeckoSession session, String title, String value, TextCallback callback) {

    }

    @Override
    public void onDateTimePrompt(GeckoSession session, String title, int type, String value, String min, String max, TextCallback callback) {

    }

    @Override
    public void onFilePrompt(GeckoSession session, String title, int type, String[] mimeTypes, FileCallback callback) {

    }

    @Override
    public GeckoResult<AllowOrDeny> onPopupRequest(final GeckoSession session, final String targetUri) {
        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
    }

    // BookmarkListener

    @Override
    public void onBookmarksShown() {
        pauseCompositor();
        hide(UIWidget.REMOVE_WIDGET);
    }

    @Override
    public void onBookmarksHidden() {
        resumeCompositor();
        show();
    }

    // UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget != mBookmarksWidget || !mBookmarksWidget.isVisible()) {
            return;
        }

        mWidgetPlacement.worldWidth = aWidget.getPlacement().worldWidth;
        mWidgetPlacement.width = aWidget.getPlacement().width;
        mWidgetPlacement.height = aWidget.getPlacement().height;
        mWidgetManager.updateWidget(this);
    }

    // GeckoSession.ContentDelegate
    @Override
    public void onTitleChange(GeckoSession session, String title) {

    }

    @Override
    public void onFocusRequest(GeckoSession session) {

    }

    @Override
    public void onCloseRequest(GeckoSession session) {

    }

    @Override
    public void onFullScreen(GeckoSession session, boolean fullScreen) {

    }

    @Override
    public void onContextMenu(GeckoSession session, int screenX, int screenY, String uri, int elementType, String elementSrc) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }

    @Override
    public void onCrash(GeckoSession session) {

    }

    @Override
    public void onFirstComposite(GeckoSession session) {
        if (mFirstDrawCallback != null) {
            mFirstDrawCallback.run();
            mFirstDrawCallback = null;
        }
    }
}
