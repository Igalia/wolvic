/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.Manifest;
import android.app.NativeActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

public class PlatformActivity extends NativeActivity {
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

    protected String getEyeTrackingPermissionString() { return "com.yvr.permission.EYE_TRACKING"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG,"in onCreate");
        super.onCreate(savedInstanceState);
        //getWindow().takeInputQueue(null);
        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Set up full screen
        addFullScreenListener();
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                platformExit();
            }
        });
    }

    @Override
    protected void onResume() {
        setFullScreen();
        super.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setFullScreen();
        }
    }

    protected void setFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void addFullScreenListener() {
        View decorView = getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView,
            new androidx.core.view.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(
                        View view, WindowInsetsCompat windowInsets) {
                    if (windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())
                            || windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())) {
                        setFullScreen();
                    }
                    return ViewCompat.onApplyWindowInsets(view, windowInsets);
                }
            }
        );
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
