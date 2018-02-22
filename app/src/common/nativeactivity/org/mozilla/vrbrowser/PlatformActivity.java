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

public class PlatformActivity extends NativeActivity {
    static String LOGTAG = "VRBrowser";
    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG,"in onCreate");
        super.onCreate(savedInstanceState);

        getWindow().takeSurface(null);
        getWindow().takeInputQueue(null);
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setZOrderOnTop(true);
        surfaceView.setBackgroundColor(Color.BLUE);

        setContentView(surfaceView);
    }
}
