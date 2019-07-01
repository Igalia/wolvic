/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.Surface;

public interface Widget {

    int NO_WINDOW_ID = -1;

    void onPause();
    void onResume();
    void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight);
    void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback);
    void resizeSurface(final int aWidth, final int aHeight);
    int getHandle();
    WidgetPlacement getPlacement();
    void handleTouchEvent(MotionEvent aEvent);
    void handleHoverEvent(MotionEvent aEvent);
    void handleResizeEvent(float aWorldWidth, float aWorldHeight);
    void releaseWidget();
    void setFirstDraw(boolean aIsFirstDraw);
    boolean getFirstDraw();
    boolean isVisible();
    boolean isDialog();
    void setVisible(boolean aVisible);
    void resizeByMultiplier(float aspect, float multiplier);
    default void detachFromWindow(WindowWidget window) {}
    default void attachToWindow(WindowWidget window) {}
}
