/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.Manifest;
import android.app.NativeActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

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
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.oculus.vrshell");
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("systemux://store"));
        intent.putExtra("uri", "/item/5917120145021341");
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"in onCreate");
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
        // TODO: Deprecated setSystemUiVisibility(int) and related flags,
        //  see https://github.com/Igalia/wolvic/issues/800
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void addFullScreenListener() {
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            setFullScreen();
                        }
                    }
                });
    }


    protected native void queueRunnable(Runnable aRunnable);
    protected native boolean platformExit();
}
