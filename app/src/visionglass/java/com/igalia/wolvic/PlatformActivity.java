/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import androidx.activity.ComponentActivity;

import androidx.annotation.Keep;

import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.huawei.usblib.DisplayMode;
import com.huawei.usblib.DisplayModeCallback;
import com.huawei.usblib.VisionGlass;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends ComponentActivity implements SensorEventListener {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    private DisplayManager mDisplayManager;
    private Display mPresentationDisplay;
    private VisionGlassPresentation mActivePresentation;

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

    private final ArrayList<Runnable> mPendingEvents = new ArrayList<>();
    private SensorManager mSensorManager;

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
    private final Runnable activityResumedRunnable = this::activityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        VisionGlass.getInstance().init(getApplication());

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        boolean wasImuStarted = false;
        boolean isAskingForPermission = false;
        do {
            if (VisionGlass.getInstance().isConnected()) {
                if (VisionGlass.getInstance().hasUsbPermission()) {
                    Log.d(LOGTAG, "Device has USB permission -> registering callback for startImu");
                    wasImuStarted = true;
                    VisionGlass.getInstance().startImu((w, x, y, z) -> queueRunnable(() -> setHead(x, y, z, w)));
                } else {
                    if (isAskingForPermission) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.w(LOGTAG, "Device does not have USB permission -> asking");
                        VisionGlass.getInstance().requestUsbPermission();
                        isAskingForPermission = true;
                    }
                }
            } else {
                // TODO: show a dialog asking the user to put on the glasses
                Log.e(LOGTAG, "Device not connected" + (VisionGlass.getInstance().hasUsbPermission() ? " and does NOT have USB permissions" : ""));
            }
        } while (!wasImuStarted);


        VisionGlass.getInstance().setDisplayMode(DisplayMode.vr3d, new DisplayModeCallback() {
            @Override
            public void onSuccess(DisplayMode displayMode) { Log.d(LOGTAG, "Successfully switched to 3D mode"); }

            @Override
            public void onError(String s, int i) {
                Log.d(LOGTAG, "Error " + i + " failed to switch to 3D mode " + s);
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        setContentView(R.layout.visionglass_layout);

        View touchpad = findViewById(R.id.touchpad);
        // Make touchpad square.
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) touchpad.getLayoutParams();
        layoutParams.height = getResources().getDisplayMetrics().widthPixels;
        touchpad.setLayoutParams(layoutParams);

        touchpad.setOnClickListener((View.OnClickListener) v -> {
            // We don't really need the coordinates of the click because we use the position
            // of the aim in the 3D environment.
            queueRunnable(() -> touchEvent(false, 0, 0));
        });

        touchpad.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // We don't really need the coordinates of the click because we use the position
                    // of the aim in the 3D environment.
                    queueRunnable(() -> touchEvent(true, 0, 0));
                    break;
                case MotionEvent.ACTION_UP:
                    // We'd emit the touchEvent in the onClick listener of the view. This way both
                    // user and system activated clicks (e.g. a11y) will work.
                    view.performClick();
                    break;
                default:
                    return false;
            }
            return true;
        });

        // Find the buttons by their id
        Button backButton = findViewById(R.id.back_button);
        Button homeButton = findViewById(R.id.home_button);

        // Set click listeners for the buttons
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle home button click
                Log.d(LOGTAG, "Home button clicked");
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // SensorEventListener overrides
    @Override
    public void onSensorChanged(SensorEvent event) {
        // retrieve the device orientation from sensorevent in the form of quaternion
        if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR)
            return;

        float[] quaternion = new float[4];
        SensorManager.getQuaternionFromVector(quaternion, event.values);
        // The quaternion is returned in the form [w, x, z, y] but we use it as [x, y, z, w].
        // See https://developer.android.com/reference/android/hardware/Sensor#TYPE_ROTATION_VECTOR
        queueRunnable(() -> setControllerOrientation(quaternion[1], quaternion[3], quaternion[2], quaternion[0]));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
        super.onPause();
        synchronized (mRenderLock) {
            queueRunnable(activityPausedRunnable);
            try {
                mRenderLock.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e.toString());
            }
        }
        if (mActivePresentation != null)
            mActivePresentation.mGLView.onPause();

        // Unregister from the display manager.
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "PlatformActivity onResume");
        super.onResume();

        updateDisplays();
        showPresentation();

        if (mActivePresentation != null && mActivePresentation.mGLView != null)
            mActivePresentation.mGLView.onResume();

        queueRunnable(activityResumedRunnable);
        setImmersiveSticky();

        // Register to receive events from the display manager.
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
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
        if (mActivePresentation != null) {
            mActivePresentation.mGLView.queueEvent(aRunnable);
        } else {
            synchronized (mPendingEvents) {
                mPendingEvents.add(aRunnable);
            }
            if (mActivePresentation != null) {
                notifyPendingEvents();
            }
        }
    }

    private void notifyPendingEvents() {
        synchronized (mPendingEvents) {
            for (Runnable runnable: mPendingEvents) {
                mActivePresentation.mGLView.queueEvent(runnable);
            }
            mPendingEvents.clear();
        }
    }

    private void updateDisplays() {
        Display[] displays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (displays.length == 0) {
            mPresentationDisplay = null;
            return;
        }

        mPresentationDisplay = displays[0];
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    updateDisplays();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    updateDisplays();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    updateDisplays();
                }
            };

    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mActivePresentation = null;
                }
            };

    private void showPresentation() {
        if (mActivePresentation != null) {
            return;
        }
        if (mPresentationDisplay == null) {
            Log.e(LOGTAG, "No suitable displays found");
            return;
        }
        VisionGlassPresentation presentation = new VisionGlassPresentation(this, mPresentationDisplay);
        Display.Mode [] modes = mPresentationDisplay.getSupportedModes();
        presentation.setPreferredDisplayMode(modes[0].getModeId());
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);
        mActivePresentation = presentation;
    }

    private final class VisionGlassPresentation extends Presentation {

        private GLSurfaceView mGLView;

        public VisionGlassPresentation(Context context, Display display) {
            super(context, display);
        }

        /**
         * Sets the preferred display mode id for the presentation.
         */
        public void setPreferredDisplayMode(int modeId) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = modeId;
            getWindow().setAttributes(params);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // Be sure to call the super class.
            super.onCreate(savedInstanceState);

            // Get the resources for the context of the presentation.
            // Notice that we are getting the resources from the context of the presentation.
            Resources r = getContext().getResources();

            // Inflate the layout.
            setContentView(R.layout.visionglass_presentation_layout);

            mGLView = findViewById(R.id.gl_presentation_view);
            mGLView.setEGLContextClientVersion(3);
            mGLView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
            mGLView.setPreserveEGLContextOnPause(true);

            mGLView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                    activityCreated(getAssets());
                    notifyPendingEvents();
                }

                @Override
                public void onSurfaceChanged(GL10 gl, int width, int height) {
                    updateViewport(width, height);
                }

                @Override
                public void onDrawFrame(GL10 gl) {
                    drawGL();
                }
            });
        }
    }

    @Keep
    @SuppressWarnings("unused")
    private void setRenderMode(final int aMode) {
        runOnUiThread(() -> setImmersiveSticky());
    }

    private native void activityCreated(Object aAssetManager);
    private native void updateViewport(int width, int height);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
    private native void touchEvent(boolean aDown, float aX, float aY);
    private native void setHead(double x, double y, double z, double w);
    private native void setControllerOrientation(double x, double y, double z, double w);
}
