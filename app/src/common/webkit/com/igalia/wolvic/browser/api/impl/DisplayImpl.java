package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.wpe.wpeview.SurfaceClient;
import com.wpe.wpeview.WPEView;

import kotlin.NotImplementedError;

public class DisplayImpl implements WDisplay, SurfaceHolder {
    @NonNull ViewGroup mContainer;
    @NonNull SessionImpl mSession;
    Surface mSurface;
    private int mWidth = 1;
    private int mHeight = 1;

    public DisplayImpl(@NonNull ViewGroup container, @NonNull SessionImpl session) {
        mContainer = container;
        mSession = session;
    }

    public void acquire() {
        assert mSession.mWPEView.getParent() == null;
        SettingsStore settings = SettingsStore.getInstance(mSession.mWPEView.getContext());
        mContainer.addView(mSession.mWPEView, new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
        mSession.mSurfaceClient.setProxy(new SurfaceClient() {
            @Override
            public void addCallback(WPEView wpeView, Callback2 callback) {
                if (mSurface != null) {
                    mSession.mWPEView.postDelayed(() -> {
                        callback.surfaceCreated(DisplayImpl.this);
                        callback.surfaceChanged(DisplayImpl.this, PixelFormat.RGBA_8888, mWidth, mHeight);
                    }, 0);
                }
            }

            @Override
            public void removeCallback(WPEView wpeView, Callback2 callback2) {

            }
        });
    }

    public void release() {
        mContainer.removeView(mSession.mWPEView);
        mSession.mSurfaceClient.setProxy(null);
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        mSurface = surface;
        mWidth = width;
        mHeight = height;
        for (SurfaceHolder.Callback2 callback: mSession.mSurfaceClient.mCallbacks) {
            callback.surfaceCreated(this);
            callback.surfaceChanged(this, PixelFormat.RGBA_8888, width, height);
        }
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int left, int top, int width, int height) {
        surfaceChanged(surface, width, height);
    }

    @Override
    public void surfaceDestroyed() {
        mSurface = null;
        for (SurfaceHolder.Callback2 callback: mSession.mSurfaceClient.mCallbacks) {
            callback.surfaceDestroyed(this);
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
        return WResult.fromException(new NotImplementedError());
    }

    // SurfaceHolder interface, only getSurface() is used in WPE so far.
    @Override
    public void addCallback(Callback callback) {

    }

    @Override
    public void removeCallback(Callback callback) {

    }

    @Override
    public boolean isCreating() {
        return false;
    }

    @Override
    public void setType(int type) {

    }

    @Override
    public void setFixedSize(int width, int height) {

    }

    @Override
    public void setSizeFromLayout() {

    }

    @Override
    public void setFormat(int format) {

    }

    @Override
    public void setKeepScreenOn(boolean screenOn) {

    }

    @Override
    public Canvas lockCanvas() {
        return null;
    }

    @Override
    public Canvas lockCanvas(Rect dirty) {
        return null;
    }

    @Override
    public void unlockCanvasAndPost(Canvas canvas) {

    }

    @Override
    public Rect getSurfaceFrame() {
        return null;
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }
}
