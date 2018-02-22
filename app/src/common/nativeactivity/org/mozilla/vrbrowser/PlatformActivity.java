/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.NativeActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

public class PlatformActivity extends NativeActivity {
    static String LOGTAG = "VRBrowser";
    private SurfaceView mSurfaceView;
    private FrameLayout mFrameLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"in onCreate");
        super.onCreate(savedInstanceState);

        getWindow().takeSurface(null);
        getWindow().takeInputQueue(null);

        mFrameLayout = new FrameLayout(this);
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setClickable(true);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setZOrderOnTop(true);
        surfaceView.setBackgroundColor(Color.BLUE);
        mFrameLayout.addView(surfaceView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(mFrameLayout);
    }

    protected void addWidget(View aView, int aWidth, int aHeight) {
        mFrameLayout.addView(aView, 0, new FrameLayout.LayoutParams(aWidth, aHeight));
    }
}
