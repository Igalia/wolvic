/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import org.mozilla.vrbrowser.Widget;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

import java.lang.reflect.Constructor;
import java.util.HashMap;

public abstract class UIWidget extends FrameLayout implements Widget {
    UISurfaceTextureRenderer mRenderer;
    SurfaceTexture mTexture;
    protected int mHandle;
    protected WidgetPlacement mWidgetPlacement;
    protected WidgetManagerDelegate mWidgetManager;
    static final String LOGTAG = "VRB";
    protected int mInitialWidth;
    protected int mInitialHeight;
    protected Runnable mBackHandler;
    protected HashMap<Integer, UIWidget> mChildren;
    protected UIWidgetDelegate mDelegate;

    public interface UIWidgetDelegate {
        void onWidgetClosed(int aHandle);
    }

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

        mChildren = new HashMap<>();
        mBackHandler = new Runnable() {
            @Override
            public void run() {
                onBackButton();
            }
        };
    }

    protected abstract void initializeWidgetPlacement(WidgetPlacement aPlacement);

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
    public void setFirstDraw(final boolean aIsFirstDraw) {
        mWidgetPlacement.firstDraw = aIsFirstDraw;
    }

    @Override
    public boolean getFirstDraw() {
        return mWidgetPlacement.firstDraw;
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

    public void setDelegate(UIWidgetDelegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void toggle() {
        if (mWidgetPlacement.visible) {
            hide();

        } else {
            show();
        }
    }

    public void show() {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
            mWidgetManager.pushBackHandler(mBackHandler);
        }
    }

    public void hide() {
        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            mWidgetManager.removeWidget(this);
            mWidgetManager.popBackHandler(mBackHandler);
        }
    }

    public boolean isOpened() {
        for (UIWidget child : mChildren.values()) {
            if (child.mWidgetPlacement.visible)
                return true;
        }

        return mWidgetPlacement.visible;
    }

    protected <T extends UIWidget> T createChild(@NonNull Class<T> aChildClassName) {
        return createChild(aChildClassName, true);
    }

    protected <T extends UIWidget> T createChild(@NonNull Class<T> aChildClassName, boolean inheritPlacement) {
        try {
            Constructor<?> constructor = aChildClassName.getConstructor(new Class[] { Context.class });
            UIWidget child = (UIWidget) constructor.newInstance(new Object[] { getContext() });
            if (inheritPlacement)
                child.getPlacement().parentHandle = getHandle();
            child.setDelegate(new UIWidgetDelegate() {

                @Override
                public void onWidgetClosed(int aHandle) {
                    onChildClosed(aHandle);
                }
            });
            mChildren.put(child.mHandle, child);

            return aChildClassName.cast(child);

        } catch (Exception e) {
            Log.e(LOGTAG, "Error creating child widget: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        return null;
    }

    protected <T extends UIWidget> T getChild(int aChildId) {
        return (T) mChildren.get(aChildId);
    }

    protected void removeChild(int aChildId) {
        UIWidget child = mChildren.get(aChildId);
        if (child != null) {
            child.hide();
            mChildren.remove(aChildId);
            child.releaseWidget();
        }
    }

    protected void removeAllChildren() {
        for (UIWidget child : mChildren.values()) {
            child.hide();
            child.releaseWidget();
        }
        mChildren.clear();
    }

    protected void onChildClosed(int aHandle) {
        show();
    }

    protected void onBackButton() {
        hide();
        if (mDelegate != null)
            mDelegate.onWidgetClosed(getHandle());
    }
}
