/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import com.htc.vr.sdk.VRActivity;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.util.Log;
import android.view.Surface;

import org.mozilla.gecko.GeckoSession;

public class VRBrowserActivity extends VRActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static GeckoSession mSession;
    private static Surface mBrowserSurface;
    static final String DEFAULT_URL = "https://www.polygon.com/"; // https://vr.mozilla.org";
    static String LOGTAG = "VRB";

    public VRBrowserActivity() {
        super.setUsingRenderBaseActivity(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"In onCreate");
        super.onCreate(savedInstanceState);
        if (mSession == null) {
            mSession = new GeckoSession();
        }
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                initializeJava(getAssets());
            }
        });

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

    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        Log.e(LOGTAG, "Load URI from intent: " + (uri != null ? uri.toString() : DEFAULT_URL));
        String uriValue = (uri != null ? uri.toString() : DEFAULT_URL);
        mSession.loadUri(uriValue);
    }

    @Keep
    private void setSurfaceTexture(String aName, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        runOnUiThread(new Runnable() {
            public void run() {
                createBrowser(aTexture, aWidth, aHeight);
            }
        });
    }

    private void createBrowser(SurfaceTexture aTexture, int aWidth, int aHeight) {
        if (aTexture != null) {
            //Log.e(LOGTAG,"In createBrowser");
            aTexture.setDefaultBufferSize(aWidth, aHeight);
            mBrowserSurface = new Surface(aTexture);
            mSession.acquireDisplay().surfaceChanged(mBrowserSurface, aWidth, aHeight);
            mSession.openWindow(this);
        }
    }

    @Keep
    private void updateMotionEvent(final int aTarget, final int aDevice, final boolean aPressed, final int aX, final int aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MotionEventGenerator.dispatch(mSession.getPanZoomController(), aDevice, aPressed, aX, aY);
            }
        });
    }

    private native void queueRunnable(Runnable aRunnable);
    private native void initializeJava(AssetManager aAssets);
}
