package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;

public class DisplayImpl implements WDisplay {
    @NonNull private final BrowserDisplay mBrowserDisplay;
    @NonNull SessionImpl mSession;
    private int mWidth = 1;
    private Surface mSurface;

    public DisplayImpl(@NonNull BrowserDisplay browserDisplay, @NonNull SessionImpl session) {
        mBrowserDisplay = browserDisplay;
        mSession = session;
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        mSurface = surface;
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
        mSurface = null;
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
