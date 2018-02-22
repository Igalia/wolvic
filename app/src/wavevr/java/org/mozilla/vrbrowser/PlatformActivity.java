/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import com.htc.vr.sdk.VRActivity;
import android.content.res.AssetManager;
import android.os.Bundle;

public class PlatformActivity extends VRActivity {
    public PlatformActivity() {
        super.setUsingRenderBaseActivity(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                initializeJava(getAssets());
            }
        });
    }

    protected native void queueRunnable(Runnable aRunnable);
    protected native void initializeJava(AssetManager aAssets);
}
