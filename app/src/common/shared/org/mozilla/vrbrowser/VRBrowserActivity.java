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
import android.support.annotation.Keep;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import org.mozilla.gecko.GeckoSession;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;

import java.util.HashMap;

public class VRBrowserActivity extends PlatformActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    static final String DEFAULT_URL = "https://www.polygon.com/"; // https://vr.mozilla.org";
    static final String LOGTAG = "VRB";
    String mTargetUrl;
    BrowserWidget mCurrentBrowser;
    HashMap<Integer, Widget> mWidgets;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"In onCreate");
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
    protected void onNewIntent(final Intent intent) {
        Log.e(LOGTAG,"In onNewIntent");
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
        Log.e(LOGTAG, "Load URI from intent: " + (uri != null ? uri.toString() : DEFAULT_URL));
        mTargetUrl = (uri != null ? uri.toString() : DEFAULT_URL);
        if (mCurrentBrowser != null) {
            mCurrentBrowser.getSession().loadUri(mTargetUrl);
            mTargetUrl = "";
        }
    }

    void createWidget(final int aType, final int aHandle, SurfaceTexture aTexture, int aWidth, int aHeight) {
        Widget widget = null;
        if (aType == Widget.Browser) {
            mCurrentBrowser = new BrowserWidget(this, new GeckoSession());
            if (mTargetUrl != "") {
                mCurrentBrowser.getSession().loadUri(mTargetUrl);
                mTargetUrl = "";
            }
            widget = mCurrentBrowser;
        } else if (aType == Widget.URLBar) {
            widget = (Widget) getLayoutInflater().inflate(R.layout.url, null);
        }

        if (widget != null) {
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            mWidgets.put(aHandle, widget);
        }

        if (aType != Widget.Browser) {
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
                mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 16, 16);
                mOffscreenDisplay.setContentView(mWidgetContainer);
            }
        });
    }
}
