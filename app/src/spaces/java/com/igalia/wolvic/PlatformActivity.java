/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.NativeActivity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

public class PlatformActivity extends NativeActivity {

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

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    protected String getEyeTrackingPermissionString() { return null; }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    @Override
    public void onBackPressed() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                platformExit();
            }
        });
    }

    protected void handleBluetoothHidDeviceConnected(BluetoothDevice device) {
    }

    protected void handleBluetoothHidDeviceDisconnected(BluetoothDevice device) {
    }

    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
