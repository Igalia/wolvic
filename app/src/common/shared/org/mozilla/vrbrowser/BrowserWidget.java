/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.Surface;
import android.util.Log;

import org.mozilla.gecko.gfx.GeckoDisplay;
import org.mozilla.geckoview.GeckoSession;

class BrowserWidget implements Widget, SessionStore.SessionChangeListener{
    private static final String LOGTAG = "VRB";
    private Context mContext;
    private int mSessionId;
    private GeckoDisplay mDisplay;
    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int mWidth;
    private int mHeight;

    BrowserWidget(Context aContext, int aSessionId) {
        mContext = aContext;
        mSessionId = aSessionId;
        SessionStore.get().addSessionChangeListener(this);
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
    public void handleTouchEvent(MotionEvent aEvent) {
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
        if (mSessionId == aId) {
            return;
        }

        GeckoSession oldSession = SessionStore.get().getSession(mSessionId);
        if (oldSession != null && mDisplay != null) {
            mDisplay.surfaceDestroyed();
            oldSession.releaseDisplay(mDisplay);
        }

        mSessionId = aId;
        mDisplay = aSession.acquireDisplay();
        mDisplay.surfaceChanged(mSurface, mWidth, mHeight);
    }
}
