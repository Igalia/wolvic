/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.vrbrowser.ui.widgets;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.view.Surface;

class UISurfaceTextureRenderer {
    private int mTextureWidth;
    private int mTextureHeight;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Canvas mSurfaceCanvas;

    UISurfaceTextureRenderer(SurfaceTexture aTexture, int aWidth, int aHeight) {
        mTextureWidth = aWidth;
        mTextureHeight = aHeight;
        mSurfaceTexture = aTexture;
        mSurfaceTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(mSurfaceTexture);
    }

    UISurfaceTextureRenderer(Surface aSurface, int aWidth, int aHeight) {
        mTextureWidth = aWidth;
        mTextureHeight = aHeight;
        mSurface = aSurface;
    }

    void resize(int aWidth, int aHeight) {
        if (aWidth == mTextureWidth && aHeight == mTextureHeight) {
            return;
        }
        mTextureWidth = aWidth;
        mTextureHeight = aHeight;
        if (mSurfaceTexture != null) {
            mSurfaceTexture.setDefaultBufferSize(aWidth, aHeight);
        }
    }

    void release() {
        if(mSurface != null){
            mSurface.release();
        }
        if(mSurfaceTexture != null){
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;
    }

    Canvas drawBegin() {
        mSurfaceCanvas = null;
        if (mSurface != null) {
            try {
                mSurfaceCanvas = mSurface.lockHardwareCanvas();
                mSurfaceCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        return mSurfaceCanvas;
    }

    void drawEnd() {
        if(mSurfaceCanvas != null) {
            mSurface.unlockCanvasAndPost(mSurfaceCanvas);
        }
        mSurfaceCanvas = null;
    }

    int width() {
        return mTextureWidth;
    }

    int height() {
        return mTextureHeight;
    }

}
