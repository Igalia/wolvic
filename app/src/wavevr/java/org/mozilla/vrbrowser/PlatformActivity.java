/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import com.htc.vr.sdk.VRActivity;

import android.Manifest;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.KeyEvent;

import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

public class PlatformActivity extends VRActivity {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);

    public static boolean filterPermission(final String aPermission) {
        if (aPermission.equals(Manifest.permission.CAMERA)) {
            return true;
        }
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
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

    public PlatformActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        queueRunnable(new Runnable() {
            @Override
            public void run() {
                initializeJava(getAssets());
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Discard back button presses that would otherwise exit the app,
        // as the UI standard on this platform is to require the use of
        // the system menu to exit applications.
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    protected native void queueRunnable(Runnable aRunnable);
    protected native void initializeJava(AssetManager aAssets);
}
