/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import com.huawei.agconnect.AGConnectInstance;
import com.huawei.agconnect.AGConnectOptionsBuilder;
import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.hvr.LibUpdateClient;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import org.mozilla.vrbrowser.utils.StringUtils;

public class PlatformActivity extends Activity implements SurfaceHolder.Callback {
    public static final String TAG = "PlatformActivity";
    private SurfaceView mView;
    private Context mContext = null;

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

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mView = new SurfaceView(this);
        setContentView(mView);

        mView.getHolder().addCallback(this);
        //getDir();
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
}



