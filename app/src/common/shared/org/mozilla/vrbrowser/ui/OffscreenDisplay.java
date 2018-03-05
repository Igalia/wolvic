/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;

public class OffscreenDisplay {
    private Context mContext;
    private VirtualDisplay mVirtualDisplay;
    private SurfaceTexture mTexture;
    private Surface mSurface;
    private OffscreenPresentation mPresentation;

    private DisplayMetrics mDefaultMetrics;

    public OffscreenDisplay(Context aContext, SurfaceTexture aTexture, int aWidth, int aHeight) {
        mContext = aContext;
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(aTexture);

        DisplayManager manager = (DisplayManager) aContext.getSystemService(Context.DISPLAY_SERVICE);
        Display defaultDisplay = manager.getDisplay(Display.DEFAULT_DISPLAY);

        mDefaultMetrics = new DisplayMetrics();
        defaultDisplay.getMetrics(mDefaultMetrics);

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

        mVirtualDisplay = manager.createVirtualDisplay("OffscreenViews", aWidth, aHeight,
                                                       mDefaultMetrics.densityDpi, mSurface, flags);

        mPresentation = new OffscreenPresentation(mContext, mVirtualDisplay.getDisplay());
        mPresentation.show();
    }

    public void setContentView(View aView) {
        if (mPresentation == null) {
            throw new IllegalStateException("No presentation!");
        }

        mPresentation.setContentView(aView);
    }

    public void resize(int aWidth, int aHeight) {
        if (mVirtualDisplay == null) {
            throw new IllegalStateException("No virtual display!");
        }

        mVirtualDisplay.resize(aWidth, aHeight, mDefaultMetrics.densityDpi);
    }

    public void release() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }

        if (mVirtualDisplay != null) {
            //mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if (mSurface != null) {
            //mSurface.release();
        }

        if (mTexture != null) {
            //mTexture.release();
        }
    }

    class OffscreenPresentation extends Presentation {
        OffscreenPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);
            /*
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                                    */
        }
    }
}
