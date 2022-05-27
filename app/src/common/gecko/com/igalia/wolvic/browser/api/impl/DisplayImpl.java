package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;

import org.mozilla.geckoview.GeckoDisplay;

/* package */ class DisplayImpl implements WDisplay {
    GeckoDisplay mDisplay;

    public DisplayImpl(GeckoDisplay display) {
        this.mDisplay = display;
    }

    /* package */ GeckoDisplay getGeckoDisplay() {
        return  mDisplay;
    }


    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        mDisplay.surfaceChanged(new GeckoDisplay.SurfaceInfo.Builder(surface).size(width, height).build());
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int left, int top, int width, int height) {
        mDisplay.surfaceChanged(new GeckoDisplay.SurfaceInfo.Builder(surface).size(width, height).offset(left, top).build());
    }

    @Override
    public void surfaceDestroyed() {
        mDisplay.surfaceDestroyed();
    }

    @NonNull
    @Override
    public WResult<Bitmap> capturePixels() {
        return new ResultImpl<>(mDisplay.capturePixels());
    }

    @NonNull
    @Override
    public WResult<Bitmap> capturePixelsWithAspectPreservingSize(int width) {
        return new ResultImpl<>(mDisplay.screenshot().aspectPreservingSize(width).capture());
    }
}
