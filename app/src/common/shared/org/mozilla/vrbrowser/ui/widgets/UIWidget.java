/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;

import java.lang.reflect.Constructor;
import java.util.HashMap;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

public abstract class UIWidget extends FrameLayout implements Widget {

    private static final String LOGTAG = "VRB";

    public interface Delegate {
        void onDismiss();
    }

    protected UISurfaceTextureRenderer mRenderer;
    protected SurfaceTexture mTexture;
    protected float mWorldWidth;
    protected int mHandle;
    protected WidgetPlacement mWidgetPlacement;
    protected WidgetManagerDelegate mWidgetManager;
    protected int mInitialWidth;
    protected int mInitialHeight;
    protected Runnable mBackHandler;
    protected HashMap<Integer, UIWidget> mChildren;
    protected Delegate mDelegate;
    protected int mBorderWidth;
    private Runnable mFirstDrawCallback;

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
        mBorderWidth = SettingsStore.getInstance(getContext()).getTransparentBorderWidth();
        mWidgetManager = (WidgetManagerDelegate) getContext();
        mWidgetPlacement = new WidgetPlacement(getContext());
        mHandle = mWidgetManager.newWidgetHandle();
        mWorldWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.world_width);
        initializeWidgetPlacement(mWidgetPlacement);
        mInitialWidth = mWidgetPlacement.width;
        mInitialHeight = mWidgetPlacement.height;
        // Transparent border useful for TimeWarp Layers and better aliasing.
        final float scale = getResources().getDisplayMetrics().density;
        int padding_px = (int) (mBorderWidth * scale + 0.5f);
        this.setPadding(padding_px, padding_px, padding_px, padding_px);

        mChildren = new HashMap<>();
        mBackHandler = () -> onDismiss();
    }

    @Override
    public void resizeByMultiplier(float aspect, float multiplier) {
        // To be implemented by inheriting widgets
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
            mRenderer = null;
        }
        if (aTexture != null) {
            mRenderer = new UISurfaceTextureRenderer(aTexture, aWidth, aHeight);
        }
        setWillNotDraw(mRenderer == null);
    }

    @Override
    public void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        mFirstDrawCallback = aFirstDrawCallback;
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        if (aSurface != null) {
            mRenderer = new UISurfaceTextureRenderer(aSurface, aWidth, aHeight);
        }
        setWillNotDraw(mRenderer == null);
    }

    @Override
    public void resizeSurface(final int aWidth, final int aHeight) {
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
        if (mFirstDrawCallback != null) {
            mFirstDrawCallback.run();
            mFirstDrawCallback = null;
        }
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

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void toggle() {
        if (isVisible()) {
            hide(REMOVE_WIDGET);

        } else {
            show();
        }
    }

    public void show() {
        show(true);
    }

    public void show(boolean focus) {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
            mWidgetManager.pushBackHandler(mBackHandler);
        }

        if (focus) {
            setFocusableInTouchMode(true);
            requestFocusFromTouch();
        }
    }

    @IntDef(value = { REMOVE_WIDGET, KEEP_WIDGET })
    public @interface HideFlags {}
    public static final int REMOVE_WIDGET = 0;
    public static final int KEEP_WIDGET = 1;

    public void hide(@HideFlags int aHideFlags) {
        for (UIWidget child : mChildren.values()) {
            if (child.isVisible()) {
                child.hide(aHideFlags);
            }
        }

        if (mWidgetPlacement.visible) {
            mWidgetPlacement.visible = false;
            if (aHideFlags == REMOVE_WIDGET) {
                mWidgetManager.removeWidget(this);

            } else {
                mWidgetManager.updateWidget(this);
            }
            mWidgetManager.popBackHandler(mBackHandler);
        }

        clearFocus();
    }

    @Override
    public boolean isVisible() {
        for (UIWidget child : mChildren.values()) {
            if (child.isVisible())
                return true;
        }

        return mWidgetPlacement.visible;
    }

    @Override
    public void setVisible(boolean aVisible) {
        if (mWidgetPlacement.visible == aVisible) {
            return;
        }
        mWidgetPlacement.visible = aVisible;
        mWidgetManager.updateWidget(this);
        if (!aVisible) {
            clearFocus();
        }
    }

    protected <T extends UIWidget> T createChild(@NonNull Class<T> aChildClassName) {
        return createChild(aChildClassName, true);
    }

    protected <T extends UIWidget> T createChild(@NonNull Class<T> aChildClassName, boolean inheritPlacement) {
        try {
            Constructor<?> constructor = aChildClassName.getConstructor(new Class[] { Context.class });
            UIWidget child = (UIWidget) constructor.newInstance(new Object[] { getContext() });
            if (inheritPlacement) {
                child.getPlacement().parentHandle = getHandle();
            }
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

    protected boolean isChild(View aView) {
        return findViewById(aView.getId()) != null;
    }

    protected void onDismiss() {
        hide(REMOVE_WIDGET);

        if (mDelegate != null) {
            mDelegate.onDismiss();
        }
    }

    protected float getWorldWidth() {
        return mWorldWidth;
    }
}
