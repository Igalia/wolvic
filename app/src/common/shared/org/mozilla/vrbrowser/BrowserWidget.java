/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.Surface;

class BrowserWidget implements Widget {
    Context mContext;
    BrowserSession mSession;
    Surface mSurface;

    BrowserWidget(Context aContext, BrowserSession aSession) {
        mContext = aContext;
        mSession = aSession;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(aTexture);
        mSession.getGeckoSession().acquireDisplay().surfaceChanged(mSurface, aWidth, aHeight);
        mSession.getGeckoSession().openWindow(mContext);
    }

    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
      mSession.getGeckoSession().getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        mSession.getGeckoSession().getPanZoomController().onMotionEvent(aEvent);
    }

    @Override
    public void releaseWidget() {
        mSession.getGeckoSession().closeWindow();
    }

    BrowserSession getSession() {
        return mSession;
    }
}
