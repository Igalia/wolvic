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

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.mozilla.vrbrowser.BrowserActivity;

public class PlatformActivity extends Activity {
    static String LOGTAG = "VRB";
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private GvrLayout mLayout;
    private GLSurfaceView mView;

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

        if (AndroidCompat.setVrModeEnabled(this, true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mView = new GLSurfaceView(this);
        mLayout = new GvrLayout(this);

        mView.setEGLContextClientVersion(3);
        mView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        //mView.setPreserveEGLContextOnPause(true);

        mView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        Log.e(LOGTAG, "In onSurfaceCreated");
                        activityCreated(getAssets(), mLayout.getGvrApi().getNativeGvrContext());
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
        mLayout.getUiLayout().setCloseButtonListener(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(PlatformActivity.this, BrowserActivity.class));
            }
        });

        setImmersiveSticky();
        setContentView(mLayout);
    }

    @Override
    protected void onPause() {
        Log.e(LOGTAG, "PlatformActivity onPause");
        synchronized (activityPausedRunnable) {
            mView.queueEvent(activityPausedRunnable);
            try {
                activityPausedRunnable.wait();
            } catch(InterruptedException e) {

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
        mView.queueEvent(activityResumedRunnable);
        setImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        Log.e(LOGTAG, "PlatformActivity onDestroy");
        super.onDestroy();
        synchronized (activityDestroyedRunnable) {
            mView.queueEvent(activityDestroyedRunnable);
            try {
                activityDestroyedRunnable.wait();
            } catch(InterruptedException e) {

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
        mView.queueEvent(aRunnable);
    }

    private native void activityCreated(Object aAssetManager, final long aContext);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
}
