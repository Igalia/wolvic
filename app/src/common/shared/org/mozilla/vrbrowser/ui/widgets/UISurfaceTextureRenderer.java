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

import androidx.annotation.Nullable;

public class UISurfaceTextureRenderer {
    private int mTextureWidth;
    private int mTextureHeight;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Canvas mSurfaceCanvas;
    private static boolean sUseHarwareAcceleration;
    private static boolean sRenderActive = true;

    public static void setUseHardwareAcceleration(boolean aEnabled) {
        sUseHarwareAcceleration = aEnabled;
    }

    public static void setRenderActive(boolean aActive) {
        sRenderActive = aActive;
    }

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
    public boolean isLayer() {
        return mSurface != null && mSurfaceTexture == null;
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

    @Nullable
    Canvas drawBegin() {
        mSurfaceCanvas = null;
        if (!sRenderActive) {
            return null;
        }
        if (mSurface != null) {
            try {
                if (sUseHarwareAcceleration) {
                    mSurfaceCanvas = mSurface.lockHardwareCanvas();
                } else {
                    mSurfaceCanvas = mSurface.lockCanvas(null);
                }
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

    void clearSurface() {
        drawBegin();
        drawEnd();
    }

    int width() {
        return mTextureWidth;
    }

    int height() {
        return mTextureHeight;
    }

}
