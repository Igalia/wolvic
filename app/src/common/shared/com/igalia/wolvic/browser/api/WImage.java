package com.igalia.wolvic.browser.api;


import android.graphics.Bitmap;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/** Represents an Web API image resource as used in web app manifests and media session metadata. */
@AnyThread
public interface WImage {
    /**
     * Get the best version of this image for size <code>size</code>. Embedders are encouraged to
     * cache the result of this method keyed with this instance.
     *
     * @param size pixel size at which this image will be displayed at.
     * @return A {@link WResult} that resolves to the bitmap when ready. Will resolve
     *     exceptionally to {@link com.igalia.wolvic.browser.api.Image.ImageProcessingException} if the image cannot be processed.
     */
    @NonNull
    WResult<Bitmap> getBitmap(final int size);
}