/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.View;
import android.util.Log;
import android.widget.FrameLayout;

import org.mozilla.vrbrowser.Widget;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

public abstract class UIWidget extends FrameLayout implements Widget {
    UISurfaceTextureRenderer mRenderer;
    SurfaceTexture mTexture;
    protected int mHandle;
    protected WidgetPlacement mWidgetPlacement;
    protected WidgetManagerDelegate mWidgetManager;
    static final String LOGTAG = "VRB";
    protected int mInitialWidth;
    protected int mInitialHeight;

    public UIWidget(Context aContext) {
        super(aContext);
        initialize();
    }

    public UIWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public UIWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    private void initialize() {
        mWidgetManager = (WidgetManagerDelegate) getContext();
        mWidgetPlacement = new WidgetPlacement(getContext());
        mHandle = mWidgetManager.newWidgetHandle();
        initializeWidgetPlacement(mWidgetPlacement);
        mInitialWidth = mWidgetPlacement.width;
        mInitialHeight = mWidgetPlacement.height;
    }

    abstract void initializeWidgetPlacement(WidgetPlacement aPlacement);

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        if (mTexture!= null && (mTexture.equals(aTexture))) {
            Log.d(LOGTAG, "Texture already set");
            return;
        }
        mTexture = aTexture;
        if (mRenderer != null) {
            mRenderer.release();
        }
        if (aTexture != null) {
            mRenderer = new UISurfaceTextureRenderer(aTexture, aWidth, aHeight);
        }
        setWillNotDraw(mRenderer == null);
    }

    @Override
    public void resizeSurfaceTexture(final int aWidth, final int aHeight) {
        if (mRenderer != null){
            mRenderer.resize(aWidth, aHeight);
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        params.width = aWidth;
        params.height = aHeight;
        setLayoutParams(params);
    }

    @Override
    public int getHandle() {
        return mHandle;
    }

    @Override
    public WidgetPlacement getPlacement() {
        return mWidgetPlacement;
    }


    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
        this.dispatchTouchEvent(aEvent);
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        this.dispatchGenericMotionEvent(aEvent);
    }

    @Override
    public void handleResizeEvent(float aWorldWidth, float aWorldHeight) {
        int defaultWidth = mInitialWidth;
        int defaultHeight = mInitialHeight;
        float defaultAspect = (float) defaultWidth / (float) defaultHeight;
        float worldAspect = aWorldWidth / aWorldHeight;

        if (worldAspect > defaultAspect) {
            mWidgetPlacement.height = (int) Math.ceil(defaultWidth / worldAspect);
            mWidgetPlacement.width = defaultWidth;
        } else {
            mWidgetPlacement.width = (int) Math.ceil(defaultHeight * worldAspect);
            mWidgetPlacement.height = defaultHeight;
        }
        mWidgetPlacement.worldWidth = aWorldWidth;
        mWidgetManager.updateWidget(this);
    }

    @Override
    public void releaseWidget() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        mTexture = null;
        mWidgetManager = null;
    }

    @Override
    public void draw(Canvas aCanvas) {
        if (mRenderer == null) {
            super.draw(aCanvas);
            return;
        }
        Canvas textureCanvas = mRenderer.drawBegin();
        if(textureCanvas != null) {
            // set the proper scale
            float xScale = textureCanvas.getWidth() / (float)aCanvas.getWidth();
            textureCanvas.scale(xScale, xScale);
            // draw the view to SurfaceTexture
            super.draw(textureCanvas);
        }
        mRenderer.drawEnd();
    }

    @Override
    public void onDescendantInvalidated (View child, View target) {
        super.onDescendantInvalidated(child, target);
        if (mRenderer != null) {
            // TODO: transform rect and use invalidate(dirty)
            postInvalidate();
        }
    }

    // Need to keep this deprecated function to work on N versions of Android.
    @SuppressWarnings("deprecation")
    @Override
    public ViewParent invalidateChildInParent(int[] aLocation, Rect aDirty) {
        ViewParent parent =  super.invalidateChildInParent(aLocation, aDirty);
        if (parent != null && mRenderer != null) {
            // TODO: transform rect and use invalidate(dirty)
            postInvalidate();
        }
        return parent;
    }
}
