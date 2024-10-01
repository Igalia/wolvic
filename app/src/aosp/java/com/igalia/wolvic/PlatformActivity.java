/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.content.Intent;
import android.view.KeyEvent;

import com.google.androidgamesdk.GameActivity;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;

public class PlatformActivity extends GameActivity {

    public static boolean filterPermission(final String aPermission) {
        // Dummy implementation.
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        // Dummy implementation.
        return true;
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

    protected String getEyeTrackingPermissionString() {
        // FIXME: currently returning MagicLeap2 string, should be generalized to other devices.
        return "com.magicleap.permission.EYE_TRACKING";
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // GameActivity does not invoke onBackPressed() on KEYCODE_BACK
            onBackPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
