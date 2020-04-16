/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.NativeActivity;
import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.utils.SystemUtils;

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
