package com.igalia.wolvic.browser.api;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

@UiThread
public interface WPanZoomController {
    /**
     * Process a touch event through the pan-zoom controller. Treat any mouse events as "touch" rather
     * than as "mouse". Pointer coordinates should be relative to the display surface.
     *
     * @param event MotionEvent to process.
     */
    void onTouchEvent(final @NonNull MotionEvent event);

    /**
     * Process a non-touch motion event through the pan-zoom controller. Currently, hover and scroll
     * events are supported. Pointer coordinates should be relative to the display surface.
     *
     * @param event MotionEvent to process.
     */
    void onMotionEvent(final @NonNull MotionEvent event);
}
