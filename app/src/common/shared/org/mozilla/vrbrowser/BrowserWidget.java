/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.Surface;

import org.mozilla.gecko.GeckoSessionSettings;
import org.mozilla.gecko.gfx.NativePanZoomController;
import org.mozilla.gecko.GeckoSession;

class BrowserWidget implements Widget {
    Context mContext;
    GeckoSession mSession;
    Surface mSurface;

    BrowserWidget(Context aContext, GeckoSession aSession) {
        mContext = aContext;
        mSession = aSession;
        // Remove once e10s issues have been resolved.
        mSession.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, false);
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(aTexture);
        mSession.acquireDisplay().surfaceChanged(mSurface, aWidth, aHeight);
        mSession.openWindow(mContext);
    }

    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
      mSession.getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        mSession.getPanZoomController().onMotionEvent(aEvent);
    }

    GeckoSession getSession() {
        return mSession;
    }
}
