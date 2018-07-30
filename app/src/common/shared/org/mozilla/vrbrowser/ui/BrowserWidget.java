/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.mozilla.gecko.gfx.GeckoDisplay;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.Widget;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

public class BrowserWidget extends View implements Widget, SessionStore.SessionChangeListener {
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
    private PointF mLastWorldSize;

    public BrowserWidget(Context aContext, int aSessionId) {
        super(aContext);
        mSessionId = aSessionId;
        mWidgetManager = (WidgetManagerDelegate) aContext;
        SessionStore.get().addSessionChangeListener(this);
        setFocusableInTouchMode(true);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session != null) {
            session.getTextInput().setView(this);
        }
        mHandle = ((WidgetManagerDelegate)aContext).newWidgetHandle();
        mWidgetPlacement = new WidgetPlacement(aContext);
        initializeWidgetPlacement(mWidgetPlacement);
    }

    private void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.worldWidth =  WidgetPlacement.floatDimension(context, R.dimen.browser_world_width);
        aPlacement.width = WidgetPlacement.pixelDimension(context, R.dimen.browser_width_pixels);
        aPlacement.height = WidgetPlacement.pixelDimension(context, R.dimen.browser_height_pixels);
        aPlacement.density = 1.0f;
        aPlacement.translationX = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.browser_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.browser_world_z);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
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
        mDisplay = session.acquireDisplay();
        mDisplay.surfaceChanged(mSurface, aWidth, aHeight);
    }

    @Override
    public void resizeSurfaceTexture(final int aWidth, final int aHeight) {
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
        int defaultWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels);
        int defaultHeight = WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_height_pixels);
        float defaultAspect = (float) defaultWidth / (float) defaultHeight;
        float worldAspect = aWorldWidth / aWorldHeight;

        if (worldAspect > defaultAspect) {
            mWidgetPlacement.height = (int) Math.ceil(defaultWidth / worldAspect);
            mWidgetPlacement.width = defaultWidth;
        } else {
            mWidgetPlacement.width = (int) Math.ceil(defaultHeight * worldAspect);
            mWidgetPlacement.height = defaultHeight;
        }
        mWidgetPlacement.worldWidth = aWorldWidth;
        mWidgetManager.updateWidget(this);

        mLastWorldSize = new PointF(aWorldWidth, aWorldHeight);
    }

    @Override
    public void releaseWidget() {
        SessionStore.get().removeSessionChangeListener(this);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return;
        }
        session.getTextInput().setView(null);
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

    protected  PointF getLastWorldSize() {
        return mLastWorldSize;
    }
}
