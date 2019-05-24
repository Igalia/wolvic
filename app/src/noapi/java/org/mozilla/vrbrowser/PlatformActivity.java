/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.Keep;

public class PlatformActivity extends Activity {
    static String LOGTAG = "VRB";
    static final float ROTATION = 0.098174770424681f;

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    private GLSurfaceView mView;
    private TextView mFrameRate;
    private ArrayList<Runnable> mPendingEvents;
    private boolean mSurfaceCreated = false;
    private int mFrameCount;
    private long mLastFrameTime = System.currentTimeMillis();

    private final Runnable activityDestroyedRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                activityDestroyed();
                notifyAll();
            }
        }
    };

    private final Runnable activityPausedRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                activityPaused();
                notifyAll();
            }
        }
    };

    private final Runnable activityResumedRunnable = new Runnable() {
        @Override
        public void run() {
            activityResumed();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.noapi_layout);
        mPendingEvents = new ArrayList<>();
        mFrameRate = findViewById(R.id.frame_rate_text);
        mView = findViewById(R.id.gl_view);
        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        //mView.setPreserveEGLContextOnPause(true);

        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.e(LOGTAG, "In onSurfaceCreated");
                        activityCreated(getAssets());
                        mSurfaceCreated = true;
                        notifyPendingEvents();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        Log.e(LOGTAG, "In onSurfaceChanged");
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

        Log.e(LOGTAG, "real onTouchEvent: " + aEvent.toString());
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

        // Log.e(LOGTAG, "real onGenericMotionEvent: " + aEvent.toString());

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(false, xx, yy));
        return true;
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "PlatformActivity onPause");
        synchronized (activityPausedRunnable) {
            queueRunnable(activityPausedRunnable);
            try {
                activityPausedRunnable.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e.toString());
            }
        }
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        mView.onResume();
        queueRunnable(activityResumedRunnable);
        setImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        Log.e(LOGTAG, "PlatformActivity onDestroy");
        super.onDestroy();
        synchronized (activityDestroyedRunnable) {
            queueRunnable(activityDestroyedRunnable);
            try {
                activityDestroyedRunnable.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG, "activityDestroyedRunnable interrupted: " + e.toString());
            }
        }
    }

    void setImmersiveSticky() {
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
        findViewById(R.id.back_button).setOnClickListener((View view) -> onBackPressed());
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
    }

    private void updateUI(final int aMode) {
        if (aMode == 0) {
            Log.e(LOGTAG, "Got render mode of Stand Alone");
            findViewById(R.id.click_button).setVisibility(View.GONE);
        } else {
            Log.e(LOGTAG, "Got render mode of Immersive");
            findViewById(R.id.click_button).setVisibility(View.VISIBLE);
        }
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
