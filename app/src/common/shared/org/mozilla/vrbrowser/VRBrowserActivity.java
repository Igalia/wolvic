/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.util.Log;
import android.view.View;

import org.mozilla.gecko.GeckoSession;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"In onCreate");
        super.onCreate(savedInstanceState);

        mWidgets = new HashMap<>();
        loadFromIntent(getIntent());
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
        }

        if (widget != null) {
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            mWidgets.put(aHandle, widget);
        }

        if (aType != Widget.Browser) {
            // Add hidden UI widget to the platform window for invalidation
            addWidget((View) widget, aWidth, aHeight);
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
}
