/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.utils.SystemUtils;

public class OffscreenDisplay {
    final String LOGTAG = SystemUtils.createLogtag(OffscreenDisplay.class);
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private VirtualDisplay mVirtualDisplay;
    private SurfaceTexture mTexture;
    private Surface mSurface;
    private OffscreenPresentation mPresentation;
    private View mContentView;

    private DisplayMetrics mDefaultMetrics;

    public OffscreenDisplay(Context aContext, SurfaceTexture aTexture, int aWidth, int aHeight) {
        mContext = aContext;
        mWidth = aWidth;
        mHeight = aHeight;
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mTexture = aTexture;
        mSurface = new Surface(aTexture);

        mDefaultMetrics = new DisplayMetrics();

        onResume();
    }

    public void setContentView(View aView) {
        mContentView = aView;
        if (mPresentation != null) {
            mPresentation.setContentView(aView);
        }
    }

    public void resize(int aWidth, int aHeight) {
        if (mVirtualDisplay == null) {
            throw new IllegalStateException("No virtual display!");
        }

        mVirtualDisplay.resize(aWidth, aHeight, mDefaultMetrics.densityDpi);
    }

    public void onPause() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }


        if (mContentView != null && mContentView.getParent() != null) {
            ((ViewGroup)mContentView.getParent()).removeView(mContentView);
        }
    }

    public void onResume() {
        if (mVirtualDisplay == null) {
            DisplayManager manager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            Display defaultDisplay = manager.getDisplay(Display.DEFAULT_DISPLAY);

            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            // TODO: Deprecated getMetrics(DisplayMetrics), see https://github.com/Igalia/wolvic/issues/799
            defaultDisplay.getMetrics(mDefaultMetrics);
            WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            mDefaultMetrics.widthPixels = bounds.width();
            mDefaultMetrics.heightPixels = bounds.height();
            mDefaultMetrics.density = metrics.getDensity();

            mVirtualDisplay = manager.createVirtualDisplay("OffscreenViews Overlay", mWidth, mHeight,
                    mDefaultMetrics.densityDpi, mSurface, flags);
        }

        if (mPresentation == null) {
            mPresentation = new OffscreenPresentation(mContext, mVirtualDisplay.getDisplay());
            mPresentation.show();
            if (mContentView != null) {
                mPresentation.setContentView(mContentView);
            }
        }
    }

    public void release() {
        onPause();

        if (mSurface != null) {
            mSurface.release();
        }

        if (mTexture != null) {
            mTexture.release();
        }
    }

    class OffscreenPresentation extends Presentation {
        OffscreenPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getWindow().setDecorFitsSystemWindows(false);
                    WindowInsetsController controller = getWindow().getInsetsController();
                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                } else {
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
            } catch (Exception e) {
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return ((VRBrowserActivity)mContext).dispatchKeyEvent(event);
        }
    }
}
