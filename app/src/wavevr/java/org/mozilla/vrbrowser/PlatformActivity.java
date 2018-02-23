/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import com.htc.vr.sdk.VRActivity;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;

public class PlatformActivity extends VRActivity {
    static final String LOGTAG = "VRB";
    private FrameLayout mFrameLayout;

    public PlatformActivity() {
        super.setUsingRenderBaseActivity(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Render to our SurfaceView instead of letting VRActivity take ownership of the window.
        SurfaceHolder.Callback2 callback = getVRActivitySurfaceCallback();
        if (callback != null) {
            getWindow().takeSurface(null);
            SurfaceView surfaceView = new SurfaceView(this);
            surfaceView.setClickable(true);
            surfaceView.getHolder().addCallback(callback);
            surfaceView.setZOrderOnTop(true);
            mFrameLayout = new FrameLayout(this);
            mFrameLayout.addView(surfaceView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(mFrameLayout);
        }

        queueRunnable(new Runnable() {
            @Override
            public void run() {
                initializeJava(getAssets());
            }
        });
    }

    protected void addWidget(View aView, int aWidth, int aHeight) {
        if (mFrameLayout != null) {
            mFrameLayout.addView(aView, 0, new FrameLayout.LayoutParams(aWidth, aHeight));
        }
    }

    private SurfaceHolder.Callback2 getVRActivitySurfaceCallback() {
        try {
            Field fPlatform = VRActivity.class.getDeclaredField("mVRPlatform");
            fPlatform.setAccessible(true);
            Object platform = fPlatform.get(this);
            Field fRenderer = platform.getClass().getDeclaredField("mSVRRenderBase");
            fRenderer.setAccessible(true);
            Object renderer = fRenderer.get(platform);
            if (renderer != null && renderer instanceof SurfaceHolder.Callback) {
                return (SurfaceHolder.Callback2) renderer;
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Error getting SurfaceHolder.Callback from VRActivity:" + e.toString());
        }
        return  null;
    }

    protected native void queueRunnable(Runnable aRunnable);
    protected native void initializeJava(AssetManager aAssets);
}
