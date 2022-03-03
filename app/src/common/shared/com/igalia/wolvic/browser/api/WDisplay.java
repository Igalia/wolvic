package com.igalia.wolvic.browser.api;

import android.graphics.Bitmap;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

public interface WDisplay {

    /**
     * Sets a surface for the compositor render a surface.
     *
     * <p>Required call. The display's Surface has been created or changed. Must be called on the
     * application main thread. WSession may block this call to ensure the Surface is valid while
     * resuming drawing.
     *
     * @param surface The new Surface.
     * @param width New width of the Surface. Can not be negative.
     * @param height New height of the Surface. Can not be negative.
     */
    @UiThread
    void surfaceChanged(@NonNull final Surface surface, final int width, final int height);

    /**
     * Sets a surface for the compositor render a surface.
     *
     * <p>Required call. The display's Surface has been created or changed. Must be called on the
     * application main thread. WSession may block this call to ensure the Surface is valid while
     * resuming drawing. The origin of the content window (0, 0) is the top left corner of the screen.
     *
     * @param surface The new Surface.
     * @param left The compositor origin offset in the X axis. Can not be negative.
     * @param top The compositor origin offset in the Y axis. Can not be negative.
     * @param width New width of the Surface. Can not be negative.
     * @param height New height of the Surface. Can not be negative.
     * @throws IllegalArgumentException if left or top are negative.
     */
    @UiThread
    void surfaceChanged(@NonNull final Surface surface, final int left, final int top, final int width, final int height);

    /**
     * Removes the current surface registered with the compositor.
     *
     * <p>Required call. The display's Surface has been destroyed. Must be called on the application
     * main thread. WSession may block this call to ensure the Surface is valid while pausing
     * drawing.
     */
    @UiThread
    void surfaceDestroyed();

    /**
     * Request a {@link Bitmap} of the visible portion of the web page currently being rendered.
     *
     * <p>Returned {@link Bitmap} will have the same dimensions as the {@link Surface} the {@link
     * WDisplay} is currently using.
     *
     * <p>If the {@link WSession#isCompositorReady} is false the {@link WResult} will complete
     * with an {@link IllegalStateException}.
     *
     * <p>This function must be called on the UI thread.
     *
     * @return A {@link WResult} that completes with a {@link Bitmap} containing the pixels and
     *     size information of the currently visible rendered web page.
     */
    @UiThread
    @NonNull
    WResult<Bitmap> capturePixels();

    /**
     * Request a {@link Bitmap} of the visible portion of the web page currently being rendered.
     *
     * <p>Returned {@link Bitmap} will have the same dimensions as the {@link Surface} the {@link
     * WDisplay} is currently using.
     *
     * <p>If the {@link WSession#isCompositorReady} is false the {@link WResult} will complete
     * with an {@link IllegalStateException}.
     *
     * <p>This function must be called on the UI thread.
     *
     * @param width The width of the bitmap to create when taking the screenshot.
     *              The height will be calculated to match the aspect ratio of the source as closely
     *              as possible. The source screenshot will be scaled into the resulting Bitmap
     *
     * @return A {@link WResult} that completes with a {@link Bitmap} containing the pixels and
     *     size information of the currently visible rendered web page.
     */
    @UiThread
    @NonNull
    WResult<Bitmap> capturePixelsWithAspectPreservingSize(final int width);


}
