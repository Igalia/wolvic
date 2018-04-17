/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.mozilla.gecko.gfx.GeckoDisplay;
import org.mozilla.geckoview.GeckoSession;

class BrowserWidget extends View implements Widget, SessionStore.SessionChangeListener {
    private static final String LOGTAG = "VRB";
    private int mSessionId;
    private GeckoDisplay mDisplay;
    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int mWidth;
    private int mHeight;
    private int mHandle;

    BrowserWidget(Context aContext, int aSessionId) {
        super(aContext);
        mSessionId = aSessionId;
        SessionStore.get().addSessionChangeListener(this);
        setFocusableInTouchMode(true);
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session != null) {
            session.getTextInput().setView(this);
        }
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
    public void setHandle(int aHandle) {
        mHandle = aHandle;
    }

    @Override
    public int getHandle() {
        return mHandle;
    }

    @Override
    public void setWidgetManager(WidgetManagerDelegate aWidgetManager) {

    }

    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
        if (aEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
            Log.e(LOGTAG, "************** REQUESTING FOCUS");
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
        Log.e(LOGTAG, "onCurrentSessionChange: " + this.toString());
        if (mSessionId == aId) {
            Log.e(LOGTAG, "BrowserWidget.onCurrentSessionChange session id same, bail: " + aId);
            return;
        }

        GeckoSession oldSession = SessionStore.get().getSession(mSessionId);
        if (oldSession != null && mDisplay != null) {
            Log.e(LOGTAG, "Detach from previous session: " + mSessionId);
            oldSession.getTextInput().setView(null);
            mDisplay.surfaceDestroyed();
            oldSession.releaseDisplay(mDisplay);
        }

        mSessionId = aId;
        mDisplay = aSession.acquireDisplay();
        Log.e(LOGTAG, "surfaceChanged: " + aId);
        mDisplay.surfaceChanged(mSurface, mWidth, mHeight);
        aSession.getTextInput().setView(this);
    }

    // View
    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        Log.e(LOGTAG, "BrowserWidget onCreateInputConnection");
        GeckoSession session = SessionStore.get().getSession(mSessionId);
        if (session == null) {
            return null;
        }
        return session.getTextInput().onCreateInputConnection(outAttrs);
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
        Log.e(LOGTAG, "BrowserWidget onFoucusChanged: " + (aGainFocus ? "TRUE" : "FALSE"));
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
}
