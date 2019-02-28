/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends Activity {
    static String LOGTAG = "VRB";

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    private GvrLayout mLayout;
    private GLSurfaceView mView;
    private static final String FLAT_ACTIVITY_CLASSNAME = "org.mozilla.vrbrowser.BrowserActivity";
    private ArrayList<Runnable> mPendingEvents;
    private boolean mSurfaceCreated = false;

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

        mPendingEvents = new ArrayList<>();
        AndroidCompat.setVrModeEnabled(this, true);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mView = new GLSurfaceView(this);
        mLayout = new GvrLayout(this);

        if (mLayout.setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 8, 24, 0);
        //mView.setPreserveEGLContextOnPause(true);

        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.e(LOGTAG, "In onSurfaceCreated");
                        activityCreated(getAssets(), mLayout.getGvrApi().getNativeGvrContext());
                        mSurfaceCreated = true;
                        notifyPendingEvents();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height) {
                        Log.e(LOGTAG, "In onSurfaceChanged");
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        drawGL();
                    }
                });

        mLayout.setAsyncReprojectionEnabled(true);
        mLayout.setPresentationView(mView);

        try {
            final Class flatActivityClass = Class.forName(FLAT_ACTIVITY_CLASSNAME);
            mLayout.getUiLayout().setCloseButtonListener(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(PlatformActivity.this, flatActivityClass));
                }
            });
        }
        catch (ClassNotFoundException e) {
            Log.e(LOGTAG,"Class not found: " + e.toString());
        }

        setImmersiveSticky();
        setContentView(mLayout);
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "PlatformActivity onPause");
        synchronized (activityPausedRunnable) {
            queueRunnable(activityPausedRunnable);
            try {
                activityPausedRunnable.wait();
            } catch(InterruptedException e) {
                Log.e(LOGTAG,"activityPausedRunnable interrupted: " + e.toString());
            }
        }
        mLayout.onPause();
        mView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        mLayout.onResume();
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
                Log.e(LOGTAG,"activityDestroyedRunnable interrupted: " + e.toString());
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

    private native void activityCreated(Object aAssetManager, final long aContext);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
}
