package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.components.embedder_support.view.WolvicContentRenderView;

public class DisplayImpl implements WDisplay {
    @NonNull BrowserDisplay mDisplay;
    @NonNull SessionImpl mSession;
    private int mWidth = 1;
    private Surface mSurface;
    private WolvicContentRenderView mRenderView;

    public DisplayImpl(@NonNull BrowserDisplay display, @NonNull SessionImpl session, WolvicContentRenderView renderView) {
        mDisplay = display;
        mSession = session;
        mRenderView = renderView;
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        mWidth = width;
        if (mSurface == null) {
            // Dispatch onSurfaceCreated
            mRenderView.surfaceCreated(surface);
        }
        mSurface = surface;

        try {
            // Dispatch onSurfaceChanged
            mRenderView.surfaceChanged(surface, width, height);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int left, int top, int width, int height) {
        surfaceChanged(surface, width, height);
    }

    @Override
    public void surfaceDestroyed() {
        if (mSurface == null) {
            return;
        }
        try {
            mRenderView.surfaceDestroyed();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            mSurface = null;
        }
    }

    @NonNull
    @Override
    public WResult<Bitmap> capturePixels() {
        return capturePixelsWithAspectPreservingSize(mWidth);
    }

    @NonNull
    @Override
    public WResult<Bitmap> capturePixelsWithAspectPreservingSize(int width) {
        // TODO: Implement
        return null;
    }
}
