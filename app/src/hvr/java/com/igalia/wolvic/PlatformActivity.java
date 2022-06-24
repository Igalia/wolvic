/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.hvr.LibUpdateClient;
import com.igalia.wolvic.browser.PermissionDelegate;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.utils.StringUtils;

public class PlatformActivity extends Activity implements SurfaceHolder.Callback {
    public static final String TAG = "PlatformActivity";
    private SurfaceView mView;
    private Context mContext = null;
    private HVRLocationManager mLocationManager;
    private Dialog mActiveDialog;

    static {
        Log.i(TAG, "LoadLibrary");
    }

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);
        mContext = this;
        mLocationManager = new HVRLocationManager(this);
        PermissionDelegate.sPlatformLocationOverride = new PermissionDelegate.PlatformLocationOverride() {
            @Override
            public void onLocationGranted(Session session) {
                mLocationManager.start(session);
            }
        };

        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (manager.getDisplays().length < 2) {
            showPhoneUI();
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            initializeVR();
        }
    }

    private void showPhoneUI() {
        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        manager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // create the activity again, so the theme is set up properly
                recreate();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
            }
        }, null);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTheme(R.style.Theme_WolvicPhone);
        setContentView(R.layout.activity_main);
    }

    private void initializeVR() {
        if (mActiveDialog != null) {
            mActiveDialog.dismiss();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setTheme(R.style.FxR_Dark);
        mView = new SurfaceView(this);
        setContentView(mView);

        mView.getHolder().addCallback(this);
        new LibUpdateClient(this).runUpdate();
        nativeOnCreate();

        initializeAGConnect();
    }

    private void initializeAGConnect() {
        try {
            if (StringUtils.isEmpty(BuildConfig.HVR_ML_API_KEY)) {
                return;
            }
            MLApplication.getInstance().setApiKey(BuildConfig.HVR_ML_API_KEY);
            TelemetryService.setService(new HVRTelemetry(this));
            if (BuildConfig.FLAVOR_country == "cn")
                ((VRBrowserApplication)getApplicationContext()).setSpeechRecognizer(new HVRSpeechRecognizer(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "PlatformActivity onDestroy");
        super.onDestroy();
        nativeOnDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "PlatformActivity onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "PlatformActivity onStop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "PlatformActivity onPause");
        queueRunnable(this::nativeOnPause);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "PlatformActivity onResume");
        queueRunnable(this::nativeOnResume);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "makelele life surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "PlatformActivity surfaceChanged");
        queueRunnable(() -> nativeOnSurfaceChanged(holder.getSurface()));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        Log.i(TAG, "PlatformActivity surfaceDestroyed");
        queueRunnable(this::nativeOnSurfaceDestroyed);
    }

    @Override
    public void onBackPressed() {
        // HVR SDK does a pretty bad job handling presses on the Menu button of the R controller.
        // Instead of letting applications handle their own termination it just kills the activities.
        // That's why we need to handle this event to let the render thread know that it should stop
        // because the activity is about to be destroyed (we cannot rely on things like onDestroy().
        queueRunnable(this::onBackPressedNative);
    }

    protected boolean platformExit() {
        return false;
    }
    protected native void queueRunnable(Runnable aRunnable);
    protected native void nativeOnCreate();
    protected native void nativeOnDestroy();
    protected native void nativeOnPause();
    protected native void nativeOnResume();
    protected native void nativeOnSurfaceChanged(Surface surface);
    protected native void nativeOnSurfaceDestroyed();
    protected native void onBackPressedNative();
}



