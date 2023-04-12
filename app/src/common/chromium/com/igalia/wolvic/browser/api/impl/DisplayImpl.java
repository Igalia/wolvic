package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.wolvic.TabCompositorView;

public class DisplayImpl implements WDisplay {
    @NonNull SessionImpl mSession;
    private int mWidth = 1;
    private TabCompositorView mTabCompositorView;

    public DisplayImpl(@NonNull SessionImpl session,  @NonNull TabCompositorView TabCompositorView) {
        mSession = session;
        mTabCompositorView = TabCompositorView;
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int width, int height) {
        mWidth = width;
        mTabCompositorView.surfaceChanged(surface, width, height);
        mTabCompositorView.insertVisualStateCallback(updated -> {
            if (updated) {
                @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
                if (delegate != null) {
                    delegate.onFirstComposite(mSession);
                }
            }
        });
    }

    @Override
    public void surfaceChanged(@NonNull Surface surface, int left, int top, int width, int height) {
        surfaceChanged(surface, width, height);
    }

    @Override
    public void surfaceDestroyed() {
        mTabCompositorView.surfaceDestroyed();
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
