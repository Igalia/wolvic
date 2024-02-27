/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import androidx.activity.ComponentActivity;
import android.content.Intent;
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

import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends ComponentActivity {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);
    static final float ROTATION = 0.098174770424681f;

    @SuppressWarnings("unused")
    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }

    public static boolean isPositionTrackingSupported() {
        // Dummy implementation.
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

    private final Runnable activityResumedRunnable = this::activityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.noapi_layout);
        mFrameRate = findViewById(R.id.frame_rate_text);
        mView = findViewById(R.id.gl_view);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        //mView.setPreserveEGLContextOnPause(true);

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
                        updateViewport(width, height);
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
        setupUI();
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

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

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

    private float mScale = 0.3f;
    private void setupUI() {
        findViewById(R.id.up_button).setOnClickListener((View view) -> dispatchMoveAxis(0, mScale, 0));
        findViewById(R.id.down_button).setOnClickListener((View view) -> dispatchMoveAxis(0, -mScale, 0));
        findViewById(R.id.forward_button).setOnClickListener((View view) -> dispatchMoveAxis(0, 0, -mScale));
        findViewById(R.id.backward_button).setOnClickListener((View view) -> dispatchMoveAxis(0, 0, mScale));
        findViewById(R.id.left_button).setOnClickListener((View view) -> dispatchMoveAxis(-mScale, 0, 0));
        findViewById(R.id.right_button).setOnClickListener((View view) -> dispatchMoveAxis(mScale, 0, 0));
        findViewById(R.id.home_button).setOnClickListener((View view) -> dispatchMoveAxis(0,0,0));
        findViewById(R.id.right_turn_button).setOnClickListener((View view) -> dispatchRotateHeading(-ROTATION * mScale));
        findViewById(R.id.left_turn_button).setOnClickListener((View view) -> dispatchRotateHeading(ROTATION * mScale));
        findViewById(R.id.pitch_up_button).setOnClickListener((View view) -> dispatchRotatePitch(ROTATION * mScale));
        findViewById(R.id.pitch_down_button).setOnClickListener((View view) -> dispatchRotatePitch(-ROTATION * mScale));
        findViewById(R.id.back_button).setOnClickListener((View view) -> {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                getOnBackPressedDispatcher().onBackPressed();
            } else {
                onBackPressed();
            }
        });
        findViewById(R.id.click_button).setOnTouchListener((View view, MotionEvent event) -> {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.performClick();
                    buttonClicked(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    buttonClicked(false);
                    break;
            }
            return false;
        });
        setImmersiveSticky();
    }

    private void updateUI(final int aMode) {
        if (aMode == 0) {
            Log.d(LOGTAG, "Got render mode of Stand Alone");
            findViewById(R.id.click_button).setVisibility(View.GONE);
        } else {
            Log.d(LOGTAG, "Got render mode of Immersive");
            findViewById(R.id.click_button).setVisibility(View.VISIBLE);
        }
        setImmersiveSticky();
    }

    private void dispatchMoveAxis(final float aX, final float aY, final float aZ) {
        queueRunnable(() -> moveAxis(aX, aY, aZ));
    }

    private void dispatchRotateHeading(final float aHeading) {
        queueRunnable(() -> rotateHeading(aHeading));
    }

    private void dispatchRotatePitch(final float aPitch) {
        queueRunnable(() -> rotatePitch(aPitch));
    }

    private void buttonClicked(final boolean aPressed) {
        queueRunnable(() -> controllerButtonPressed(aPressed));
    }

    @Keep
    @SuppressWarnings("unused")
    private void setRenderMode(final int aMode) {
        runOnUiThread(() -> updateUI(aMode));
    }

    private native void activityCreated(Object aAssetManager);
    private native void updateViewport(int width, int height);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
    private native void moveAxis(float aX, float aY, float aZ);
    private native void rotateHeading(float aHeading);
    private native void rotatePitch(float aPitch);
    private native void touchEvent(boolean aDown, float aX, float aY);
    private native void controllerButtonPressed(boolean aDown);
}
