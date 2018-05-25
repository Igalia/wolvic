/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.graphics.SurfaceTexture;
import android.view.MotionEvent;

public interface Widget {
    int Browser = 0;
    int URLBar = 1;
    int MoreMenu = 2;
    int TabOverflowMenu = 3;
    int KeyboardWidget = 4;
    int PermissionWidget = 5;
    void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight);
    void setHandle(int aHandle);
    int getHandle();
    void setWidgetManager(WidgetManagerDelegate aWidgetManager);
    void handleTouchEvent(MotionEvent aEvent);
    void handleHoverEvent(MotionEvent aEvent);
    void releaseWidget();
}
