package com.igalia.wolvic.browser.api.impl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.utils.SystemUtils;

import org.chromium.base.ContextUtils;
import org.chromium.content_public.browser.RenderWidgetHostView;
import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.TabCompositorView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DisplayImpl implements WDisplay {
    private static final String LOGTAG = SystemUtils.createLogtag(DisplayImpl.class);
    private static final String CAPTURE_DIR = "captures";
    private static final ExecutorService sDecodeExecutor = Executors.newSingleThreadExecutor();

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
        ResultImpl<Bitmap> result = new ResultImpl<>();

        TabImpl tab = mSession.getTab();
        WebContents webContents = tab != null ? tab.getActiveWebContents() : null;
        RenderWidgetHostView view =
                webContents != null ? webContents.getRenderWidgetHostView() : null;
        if (view == null) {
            result.completeExceptionally(new IllegalStateException("No render widget host view"));
            return result;
        }

        int contentWidth = webContents.getWidth();
        int contentHeight = webContents.getHeight();
        if (width <= 0 || contentWidth <= 0 || contentHeight <= 0) {
            result.completeExceptionally(new IllegalStateException("Invalid capture size"));
            return result;
        }

        // CopyFromSurface() scales to exactly the requested size, so compute the height that
        // preserves the aspect ratio of the content.
        int height = Math.max(1, Math.round((float) width * contentHeight / contentWidth));
        File dir = new File(ContextUtils.getApplicationContext().getCacheDir(), CAPTURE_DIR);

        // Chromium writes the snapshot as a JPEG into a new temporary file inside |dir| and
        // reports its path, or an empty string if the readback failed.
        view.writeContentBitmapToDiskAsync(width, height, dir.getAbsolutePath(), path -> {
            if (path == null || path.isEmpty()) {
                result.completeExceptionally(new IllegalStateException("Content capture failed"));
                return;
            }
            sDecodeExecutor.execute(() -> decodeAndComplete(path, result));
        });
        return result;
    }

    private static void decodeAndComplete(String path, ResultImpl<Bitmap> result) {
        File file = new File(path);
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                result.complete(bitmap);
            } else {
                result.completeExceptionally(new IOException("Could not decode " + path));
            }
        } finally {
            if (!file.delete()) {
                Log.w(LOGTAG, "Could not delete capture file " + path);
            }
        }
    }
}
