package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

public class DisplayImpl implements WDisplay {
    @NonNull BrowserDisplay mDisplay;
    @NonNull SessionImpl mSession;
    private int mWidth = 1;
    private Surface mSurface;
    private boolean mIsFirstSurfaceChangedCall = true;

    public DisplayImpl(@NonNull BrowserDisplay display, @NonNull SessionImpl session) {
        mDisplay = display;
        mSession = session;
    }

    @NonNull BrowserDisplay getBrowserDisplay() {
        return mDisplay;
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        // Make sure we update the current active tab. We have an empty SessionImpl.setActive implementation
        // because in weblayer the browser window is selected from a pool. We can use the hint that
        // the session want's to be rendered into this surface to set it active too.
        
        if (mDisplay.getBrowser().getActiveTab() != mSession.mTab) {
            mDisplay.getBrowser().setActiveTab(mSession.mTab);
        }
        mWidth = width;
        mDisplay.surfaceChanged(surface, width, height, mFirstCompositeRunnable);
        if (!mIsFirstSurfaceChangedCall && mSurface != null && mSurface != surface) {
            // Workaround for what it looks like a Chrome bug.
            // When Surface is a different instance from previous one, chrome doesn't render
            // correctly to it until navigation changes.
            mSession.mTab.getNavigationController().reload();
        }
        mIsFirstSurfaceChangedCall = false;
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
        mDisplay.surfaceDestroyed();
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
        WResult<Bitmap> result = WResult.create();
        float scale = (float)width / (float)mWidth;
        mSession.mTab.captureScreenShot(scale, (bitmap, errorCode) -> {
            if (errorCode == 0) {
                result.complete(bitmap);
            } else {
                result.completeExceptionally(new RuntimeException("Error code:" + errorCode));
            }
        });
        return result;
    }

    private final Runnable mFirstCompositeRunnable = new Runnable() {
        @Override
        public void run() {
            @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
            if (delegate != null) {
                delegate.onFirstComposite(mSession);
            }
        }
    };

}
