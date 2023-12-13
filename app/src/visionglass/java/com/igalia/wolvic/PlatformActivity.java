/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import androidx.annotation.Keep;

import com.huawei.usblib.DisplayMode;
import com.huawei.usblib.DisplayModeCallback;
import com.huawei.usblib.VisionGlass;

import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends ComponentActivity {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);

    @SuppressWarnings("unused")
    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }

    public static boolean isPositionTrackingSupported() {
        // Vision Glass is a 3DoF device.
        return false;
    }

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    private GLSurfaceView mView;
    private TextView mFrameRate;
    private final ArrayList<Runnable> mPendingEvents = new ArrayList<>();
    private boolean mSurfaceCreated = false;
    private int mFrameCount;
    private long mLastFrameTime = System.currentTimeMillis();

    final Object mRenderLock = new Object();

    private final Runnable activityDestroyedRunnable = () -> {
        synchronized (mRenderLock) {
            activityDestroyed();
            mRenderLock.notifyAll();
        }
    };

    private final Runnable activityPausedRunnable = () -> {
        synchronized (mRenderLock) {
            activityPaused();
            mRenderLock.notifyAll();
        }
    };
    private int count = 0;
    private final Runnable activityResumedRunnable = this::activityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        VisionGlass.getInstance().init(getApplication());

        boolean wasImuStarted = false;
        boolean isAskingForPermission = false;
        do {
            if (VisionGlass.getInstance().isConnected()) {
                if (isAskingForPermission) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (VisionGlass.getInstance().hasUsbPermission()) {
                    Log.d(LOGTAG, "Device has USB permission -> registering callback for startImu");
                    wasImuStarted = true;
                    VisionGlass.getInstance().startImu((w, x, y, z) -> queueRunnable(() -> setHead(x, y, z, w)));
                } else {
                    Log.w(LOGTAG, "Device does not have USB permission -> asking");
                    VisionGlass.getInstance().requestUsbPermission();
                    isAskingForPermission = true;
                }
            } else {
                // TODO: show a dialog asking the user to put on the glasses
                Log.e(LOGTAG, "Device not connected" + (VisionGlass.getInstance().hasUsbPermission() ? " and does NOT have USB permissions" : ""));
            }
        } while (!wasImuStarted);

        VisionGlass.getInstance().setDisplayMode(DisplayMode.vr2d, new DisplayModeCallback() {
            @Override
            public void onSuccess(DisplayMode displayMode) { Log.d(LOGTAG, "Successfully switched to 2D mode"); }

            @Override
            public void onError(String s, int i) { Log.d(LOGTAG, "Error " + i + " failed to switch to 2D mode " + s); }
        });

        setContentView(R.layout.visionglass_layout);
        mFrameRate = findViewById(R.id.frame_rate_text);
        mView = findViewById(R.id.gl_view);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        mView.setPreserveEGLContextOnPause(true);

        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.d(LOGTAG, "In onSurfaceCreated");
                        activityCreated(getAssets());
                        mSurfaceCreated = true;
                        notifyPendingEvents();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        Log.d(LOGTAG, "In onSurfaceChanged");
                        // There is no way to actually get these values from the device so we have
                        // to hardcode them. The width and height we get here are from device's
                        // screen (the phone display), not the glasses' screen.
                        final int VISION_GLASS_WIDTH = 3840;
                        final int VISION_GLASS_HEIGHT = 1080;
                        updateViewport(VISION_GLASS_WIDTH, VISION_GLASS_HEIGHT);
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        mFrameCount++;
                        long ctime = System.currentTimeMillis();
                        if ((ctime - mLastFrameTime) >= 1000) {
                            final int value =  Math.round(mFrameCount / ((ctime - mLastFrameTime) / 1000.0f));
                            mLastFrameTime = ctime;
                            mFrameCount = 0;
                            runOnUiThread(() -> mFrameRate.setText(String.valueOf(value)));
                        }
                        drawGL();
                    }
                });
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG,"aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        int action = aEvent.getAction();
        boolean down;
        if (action == MotionEvent.ACTION_DOWN) {
            down = true;
        } else if (action == MotionEvent.ACTION_UP) {
            down = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            down = true;
        } else {
            return false;
        }

        final boolean isDown = down;

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(isDown, xx, yy));
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG,"aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        if (aEvent.getAction() != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(false, xx, yy));
        return true;
    }

    @Override
    protected void onPause() {
        Log.d(LOGTAG, "PlatformActivity onPause");
        synchronized (mRenderLock) {
            queueRunnable(activityPausedRunnable);
            try {
                mRenderLock.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e.toString());
            }
        }
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        mView.onResume();
        queueRunnable(activityResumedRunnable);
        setImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "PlatformActivity onDestroy");
        super.onDestroy();
        synchronized (mRenderLock) {
            queueRunnable(activityDestroyedRunnable);
            try {
                mRenderLock.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityDestroyedRunnable interrupted: " + e.toString());
            }
        }
    }

    void setImmersiveSticky() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    void queueRunnable(Runnable aRunnable) {
        if (mSurfaceCreated) {
            mView.queueEvent(aRunnable);
        } else {
            synchronized (mPendingEvents) {
                mPendingEvents.add(aRunnable);
            }
            if (mSurfaceCreated) {
                notifyPendingEvents();
            }
        }
    }

    private void notifyPendingEvents() {
        synchronized (mPendingEvents) {
            for (Runnable runnable: mPendingEvents) {
                mView.queueEvent(runnable);
            }
            mPendingEvents.clear();
        }
    }

    private native void activityCreated(Object aAssetManager);
    private native void updateViewport(int width, int height);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
    private native void touchEvent(boolean aDown, float aX, float aY);
    private native void setHead(double x, double y, double z, double w);
}
