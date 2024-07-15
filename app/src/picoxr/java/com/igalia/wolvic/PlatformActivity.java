/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.NativeActivity;
import android.content.Intent;
import android.view.KeyEvent;

import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

public class PlatformActivity extends NativeActivity {

    public static boolean filterPermission(final String aPermission) {
        // Dummy implementation.
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        // Recognize PICO's screenshot button.
        return event.getKeyCode() != KeyEvent.KEYCODE_CAMERA;
    }

    public static boolean isPositionTrackingSupported() {
        // Dummy implementation.
        return true;
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    protected String getEyeTrackingPermissionString() { return "com.picovr.permission.EYE_TRACKING"; }

    @Override
    public void onBackPressed() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                platformExit();
            }
        });
    }    
    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
