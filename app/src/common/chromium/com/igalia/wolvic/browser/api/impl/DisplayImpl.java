package com.igalia.wolvic.browser.api.impl;

import static org.chromium.content.browser.LauncherThread.postDelayed;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.components.embedder_support.view.WolvicContentRenderView;

import java.lang.reflect.Field;

public class DisplayImpl implements WDisplay {
    @NonNull private final BrowserDisplay mBrowserDisplay;
    @NonNull SessionImpl mSession;
    private int mWidth = 1;
    private Surface mSurface;
    private WolvicContentRenderView mRenderView;

    public DisplayImpl(@NonNull BrowserDisplay browserDisplay, WolvicContentRenderView renderView, @NonNull SessionImpl session) {
        mBrowserDisplay = browserDisplay;
        mRenderView = renderView;
        mSession = session;
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        Log.e("WolvicLifecycle", "onSurfaceChanged surface=" + "width=" + width + " height=" + height);
        // Make sure we update the current active tab. We have an empty SessionImpl.setActive implementation
        // because in weblayer the browser window is selected from a pool. We can use the hint that
        // the session want's to be rendered into this surface to set it active too.

        mWidth = width;
        mSurface = surface;

        try {
            // Dispatch onSurfaceCreated
            mRenderView.surfaceCreated(surface);

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
        }
        catch (Exception ex) {
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
        return new ResultImpl<>();
    }
}
