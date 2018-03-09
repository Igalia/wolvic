/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Keep;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.URLBarWidget;

import java.util.HashMap;

public class VRBrowserActivity extends PlatformActivity {

    class SwipeRunnable implements Runnable {
        boolean mCanceled = false;
        @Override
        public void run() {
            if (!mCanceled) {
                mLastGesture = NoGesture;
            }
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    static final int NoGesture = -1;
    static final int GestureSwipeLeft = 0;
    static final int GestureSwipeRight = 1;
    static final int SwipeDelay = 1000; // milliseconds

    static final String LOGTAG = "VRB";
    HashMap<Integer, Widget> mWidgets;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "VRBrowserActivity onCreate");
        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgets = new HashMap<>();
        mWidgetContainer = new FrameLayout(this);
        loadFromIntent(getIntent());
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                createOffscreenDisplay();
            }
        });
    }

    @Override
    public void onDestroy() {
        mOffscreenDisplay.release();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.e(LOGTAG,"VRBrowserActivity onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        }
    }

    void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        if (SessionStore.get().getCurrentSession() == null) {
            String url = (uri != null ? uri.toString() : SessionStore.DEFAULT_URL);
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id, this);
            SessionStore.get().loadUri(url);
            Log.e(LOGTAG, "Load creating session and loading URI:" + url);
        } else if (uri != null) {
            SessionStore.get().loadUri(uri.toString());
        }

    }

    @Override
    public void onBackPressed() {
        if (SessionStore.get().canGoBack()) {
            SessionStore.get().goBack();
        } else {
            super.onBackPressed();
        }
    }

    void createWidget(final int aType, final int aHandle, SurfaceTexture aTexture, int aWidth, int aHeight) {
        Widget widget = mWidgets.get(aHandle);
        if (widget != null) {
            Log.e(LOGTAG, "Widget of type: " + aType + " already created");
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            return;
        } else {
            Log.e(LOGTAG, "CREATE WIDGET TYPE: " + aType);
        }
        if (aType == Widget.Browser) {
            if (SessionStore.get().getCurrentSession() == null) {
                int id = SessionStore.get().createSession();
                SessionStore.get().setCurrentSession(id, this);
            }
            int currentSession = SessionStore.get().getCurrentSessionId();
            widget = new BrowserWidget(this, currentSession);
        } else if (aType == Widget.URLBar) {
            widget = new URLBarWidget(this);
        }

        if (widget != null) {
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            mWidgets.put(aHandle, widget);
        }

        if (aType != Widget.Browser) {
            Log.e(LOGTAG, "********** ADDING WIDGET TO VIEW ***************");
            // Add hidden UI widget to a virtual display for invalidation
            mWidgetContainer.addView((View) widget, new FrameLayout.LayoutParams(aWidth, aHeight));
        }
    }

    @Keep
    void dispatchCreateWidget(final int aType, final int aHandle, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        runOnUiThread(new Runnable() {
            public void run() {
                createWidget(aType, aHandle, aTexture, aWidth, aHeight);
            }
        });
    }

    @Keep
    void updateMotionEvent(final int aHandle, final int aDevice, final boolean aPressed, final int aX, final int aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    MotionEventGenerator.dispatch(widget, aDevice, aPressed, aX, aY);
                } else {
                    Log.e(LOGTAG, "Failed to find widget: " + aHandle);
                }
            }
        });
    }

    @Keep
    void dispatchGesture(final int aType) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean consumed = false;
                if ((aType == GestureSwipeLeft) && (mLastGesture == GestureSwipeLeft)) {
                    Log.e(LOGTAG, "Go BACK!");
                    SessionStore.get().goBack();
                    consumed = true;
                } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                    Log.e(LOGTAG, "Go FORWARD!");
                    SessionStore.get().goForward();
                    consumed = true;
                }
                if (mLastRunnable != null) {
                    mLastRunnable.mCanceled = true;
                    mLastRunnable = null;
                }
                if (consumed) {
                    mLastGesture = NoGesture;

                } else {
                    mLastGesture = aType;
                    mLastRunnable = new SwipeRunnable();
                    mHandler.postDelayed(mLastRunnable, SwipeDelay);
                }
            }
        });
    }

    void createOffscreenDisplay() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0]);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(LOGTAG, "OpenGL Error creating OffscreenDisplay: " + error);
        }

        final SurfaceTexture texture = new SurfaceTexture(ids[0]);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 1920, 200);
                mOffscreenDisplay.setContentView(mWidgetContainer);
            }
        });
    }
}
