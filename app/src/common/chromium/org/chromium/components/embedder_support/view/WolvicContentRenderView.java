package org.chromium.components.embedder_support.view;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Surface;

import androidx.annotation.Nullable;

import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

public class WolvicContentRenderView extends ContentViewRenderView {
    public WolvicContentRenderView(Context context) {
        super(context);
    }

    @Override
    public void onNativeLibraryLoaded(WindowAndroid rootWindow) {
        assert rootWindow != null;
        mNativeContentViewRenderView = ContentViewRenderViewJni.get().init(this, rootWindow);
        assert mNativeContentViewRenderView != 0;
        mWindowAndroid = rootWindow;
    }

    public void surfaceChanged(Surface surface, int width, int height) {
        assert mNativeContentViewRenderView != 0;
        ContentViewRenderViewJni.get()
                .surfaceChanged(mNativeContentViewRenderView, this, PixelFormat.OPAQUE, width, height, surface);
        if (mWebContents != null) {
            ContentViewRenderViewJni.get()
                    .onPhysicalBackingSizeChanged(
                            mNativeContentViewRenderView, this, mWebContents, width, height);
        }
    }

    public void surfaceCreated(Surface surface) {
        assert mNativeContentViewRenderView != 0;
        ContentViewRenderViewJni.get().surfaceCreated(mNativeContentViewRenderView, this);
    }

    public void surfaceDestroyed() {
        assert mNativeContentViewRenderView != 0;
        ContentViewRenderViewJni.get().surfaceDestroyed(mNativeContentViewRenderView, this);
    }

    @Nullable
    public WebContents getCurrentWebContents() { return mWebContents; }
}
