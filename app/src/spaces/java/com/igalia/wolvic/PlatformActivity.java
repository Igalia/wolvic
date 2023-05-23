/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.ActivityOptions;
import android.app.NativeActivity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.igalia.wolvic.utils.SystemUtils;

public class PlatformActivity extends NativeActivity {

    private static final String controllerActivity = "com.qualcomm.snapdragon.spaces.hostcontroller.ControllerActivity";
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);

    private boolean getMetaDataFlag(String key) {
        boolean flag = false;
        try {
            ApplicationInfo app = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = app.metaData;

            flag = bundle.getBoolean("com.qualcomm.snapdragon.spaces." + key, false);
        } catch (Exception e) {
            Log.e(LOGTAG, "Can't get meta data info to retrieve flag for: " + key);
            e.printStackTrace();
        }

        return flag;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v(LOGTAG, "Application was started with intent flags: " + getIntent().getFlags());
        try {
            Class.forName(controllerActivity);
            tryStartControllerActivity();
        } catch(ClassNotFoundException e) {
            Log.e(LOGTAG, "There was no Spaces Controller included in the project! No controller to launch therefore.");
        }
    }
    private void tryStartControllerActivity() {
          Intent controllerIntent = new Intent(getIntent());
          Bundle startupBundle = null;
          controllerIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
          controllerIntent.setClassName(this, controllerActivity);
          startupBundle = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();
          controllerIntent.putExtras(startupBundle);

          startActivity(controllerIntent, startupBundle);
    }

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
