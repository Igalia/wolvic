/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.*;
import org.mozilla.vrbrowser.ui.prompts.ChoicePromptWidget;


public class BrowserWidget extends View implements Widget, SessionStore.SessionChangeListener, GeckoSession.PromptDelegate {

    private static final String LOGTAG = "VRB";

    private int mSessionId;
    private GeckoDisplay mDisplay;
    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int mWidth;
    private int mHeight;
    private int mHandle;
    private WidgetPlacement mWidgetPlacement;
    private WidgetManagerDelegate mWidgetManager;
    private ChoicePromptWidget mChoicePrompt;

    public BrowserWidget(Context aContext, int aSessionId) {
        super(aContext);
        mSessionId = aSessionId;
        mWidgetManager = (WidgetManagerDelegate) aContext;
        SessionStore.get().addSessionChangeListener(this);
        SessionStore.get().addPromptListener(this);
        setFocusableInTouchMode(true);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session != null) {
            session.getTextInput().setView(this);
        }
        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        initializeWidgetPlacement(mWidgetPlacement);

        handleResizeEvent(SettingsStore.getInstance(getContext()).getBrowserWorldWidth(),
                SettingsStore.getInstance(getContext()).getBrowserWorldHeight());
    }

    private void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.worldWidth =  WidgetPlacement.floatDimension(context, R.dimen.browser_world_width);
        aPlacement.width = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight();
        aPlacement.density = 1.0f;
        aPlacement.translationX = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.browser_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.browser_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
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

        mDisplay.surfaceChanged(mSurface, mWidth, mHeight);
    }

    public void setBrowserSize(float windowWidth, float windowHeight, float multiplier) {
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.browser_world_width);
        float aspect = windowWidth / windowHeight;
        float worldHeight = worldWidth / aspect;
        float area = worldWidth * worldHeight * multiplier;

        float targetWidth = (float) Math.sqrt(area * aspect);
        float targetHeight = (float) Math.sqrt(area / aspect);

        handleResizeEvent(targetWidth, targetHeight);
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return;
        }
        mWidth = aWidth;
        mHeight = aHeight;
        mSurfaceTexture = aTexture;
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(aTexture);
        if (mDisplay == null) {
            mDisplay = session.acquireDisplay();
        } else {
            Log.e(LOGTAG, "GeckoDisplay was not null in BrowserWidget.setSurfaceTexture()");
        }
        mDisplay.surfaceChanged(mSurface, aWidth, aHeight);
    }

    @Override
    public void resizeSurfaceTexture(final int aWidth, final int aHeight) {
        mWidth = aWidth;
        mHeight = aHeight;
        mSurfaceTexture.setDefaultBufferSize(aWidth, aHeight);
        mDisplay.surfaceChanged(mSurface, aWidth, aHeight);
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
        if (aEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestFocus();
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
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.browser_world_width);
        int defaultWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        int defaultHeight = SettingsStore.getInstance(getContext()).getWindowHeight();

        float aspect = (float)defaultWidth / (float)defaultHeight;
        float worldHeight = worldWidth / aspect;
        mWidgetPlacement.width = (int)((aWorldWidth * defaultWidth) / worldWidth);
        mWidgetPlacement.height = (int)((aWorldHeight * defaultHeight) /worldHeight);
        mWidgetPlacement.worldWidth = aWorldWidth;
        mWidgetManager.updateWidget(this);

        SettingsStore.getInstance(getContext()).setBrowserWorldWidth(aWorldWidth);
        SettingsStore.getInstance(getContext()).setBrowserWorldHeight(aWorldHeight);
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);
        SessionStore.get().removePromptListener(this);
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
        mDisplay.surfaceChanged(mSurface, mWidth, mHeight);
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
        mChoicePrompt.setDelegate(new ChoicePromptWidget.ChoicePromptDelegate() {
            @Override
            public void onDismissed(String[] ids) {
                callback.confirm(ids);
                mChoicePrompt.hide();
            }
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
    public GeckoResult<Boolean> onPopupRequest(final GeckoSession session, final String targetUri) {
        return GeckoResult.fromValue(true);
    }
}
