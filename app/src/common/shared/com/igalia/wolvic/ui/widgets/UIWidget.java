/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.utils.SystemUtils;

import java.lang.reflect.Constructor;
import java.util.HashMap;

public abstract class UIWidget extends FrameLayout implements Widget {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    public interface Delegate {
        void onDismiss();
    }

    protected UISurfaceTextureRenderer mRenderer;
    protected UISurfaceTextureRenderer mProxyRenderer;
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
    protected boolean mResizing = false;
    protected boolean mReleased = false;
    private Boolean mIsHardwareAccelerationEnabled;

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
        mWidgetPlacement.name = getClass().getSimpleName();
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
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void resizeByMultiplier(float aspect, float multiplier) {
        // To be implemented by inheriting widgets
    }

    protected abstract void initializeWidgetPlacement(WidgetPlacement aPlacement);

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        if (mTexture!= null && (mTexture.equals(aTexture))) {
            Log.d(LOGTAG, "Texture already set");
            return;
        }

        if (mRenderer != null && mRenderer.isLayer()) {
            // Widget is using a layer write-only surface but we also want a proxy.
            if (mProxyRenderer != null) {
                mProxyRenderer.release();
            }
            mProxyRenderer = new UISurfaceTextureRenderer(aTexture, aWidth, aHeight);
            postInvalidate();
            return;
        } else {
            mFirstDrawCallback = aFirstDrawCallback;
        }
        mTexture = aTexture;
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        if (aTexture != null) {
            mRenderer = new UISurfaceTextureRenderer(aTexture, aWidth, aHeight);
            if (mIsHardwareAccelerationEnabled != null) {
                mRenderer.setIsHardwareAccelerationEnabled(mIsHardwareAccelerationEnabled);
            }
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
            if (mIsHardwareAccelerationEnabled != null) {
                mRenderer.setIsHardwareAccelerationEnabled(mIsHardwareAccelerationEnabled);
            }
        }
        setWillNotDraw(mRenderer == null);
    }

    @Override
    public void resizeSurface(final int aSurfaceWidth, final int aSurfaceHeight) {
        if (mRenderer != null){
            mRenderer.resize(aSurfaceWidth, aSurfaceHeight);
        }
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
    public void handleMoveEvent(float aDeltaX, float aDeltaY, float aDeltaZ, float aRotation) {
        mWidgetPlacement.translationX += aDeltaX;
        mWidgetPlacement.translationY += aDeltaY;
        mWidgetPlacement.translationZ += aDeltaZ;
        mWidgetPlacement.rotation = aRotation;
    }

    private void releaseRenderer() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        mTexture = null;
    }

    @Override
    public void releaseWidget() {
        releaseRenderer();
        mWidgetManager = null;
        mReleased = true;
    }

    public boolean isReleased() {
        return mReleased;
    }

    @Override
    public boolean isDialog() {
        return false;
    }

    @Override
    public void setFirstPaintReady(final boolean aFirstPaintReady) {
        mWidgetPlacement.composited = aFirstPaintReady;
    }

    @Override
    public boolean isFirstPaintReady() {
        return mWidgetPlacement.composited;
    }

    @Override
    public void draw(Canvas aCanvas) {
        if (mRenderer == null) {
            super.draw(aCanvas);
            return;
        }
        draw(aCanvas, mRenderer);
        if (mProxyRenderer != null && mWidgetPlacement.proxifyLayer) {
            draw(aCanvas, mProxyRenderer);
        }

        if (mFirstDrawCallback != null) {
            mFirstDrawCallback.run();
            mFirstDrawCallback = null;
        }
    }

    private void draw(Canvas aCanvas, UISurfaceTextureRenderer aRenderer) {
        if (mResizing) {
            return;
        }
        Canvas textureCanvas = aRenderer.drawBegin();
        if(textureCanvas != null) {
            // set the proper scale
            float xScale = textureCanvas.getWidth() / (float)aCanvas.getWidth();
            textureCanvas.scale(xScale, xScale);
            // draw the view to SurfaceTexture
            super.draw(textureCanvas);
        }
        aRenderer.drawEnd();
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
            show(REQUEST_FOCUS);
        }
    }

    public void setResizing(boolean aResizing) {
        mResizing = aResizing;
    }

    public boolean isLayer() {
        return mRenderer != null && mRenderer.isLayer();
    }

    @IntDef(value = { REQUEST_FOCUS, CLEAR_FOCUS, KEEP_FOCUS })
    public @interface ShowFlags {}
    public static final int REQUEST_FOCUS = 0;
    public static final int CLEAR_FOCUS = 1;
    public static final int KEEP_FOCUS = 2;

    public void show(@ShowFlags int aShowFlags) {
        if (!mWidgetPlacement.visible) {
            mWidgetPlacement.visible = true;
            mWidgetManager.addWidget(this);
            mWidgetManager.pushBackHandler(mBackHandler);
        }

        setFocusableInTouchMode(true);
        if (aShowFlags == REQUEST_FOCUS) {
            post(this::requestFocusFromTouch);
        } else if (aShowFlags == CLEAR_FOCUS) {
            clearFocus();
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

        if (mWidgetPlacement.visible && mWidgetManager != null) {
            mWidgetPlacement.visible = false;
            if (aHideFlags == REMOVE_WIDGET) {
                mWidgetManager.removeWidget(this);
                releaseRenderer();
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
            if (child.isVisible()) {
                return true;
            }
        }

        return mWidgetPlacement.visible;
    }

    @Override
    public void setVisible(boolean aVisible) {
        if (mWidgetPlacement.visible == aVisible) {
            return;
        }
        mWidgetPlacement.visible = aVisible;
        if (mWidgetManager != null) {
            mWidgetManager.updateWidget(this);
        }
        if (!aVisible) {
            clearFocus();
        }
    }

    @Override
    public void updatePlacementTranslationZ() {
        // To be implemented by inheriting widgets
    }

    public void updateWidget() {
        if (mWidgetManager != null) {
            mWidgetManager.updateWidget(this);
        }
    }

    @Override
    public int getBorderWidth() {
        return 0;
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

    @SuppressWarnings("unchecked")
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

    public void setIsHardwareAccelerationEnabled(boolean enabled) {
        mIsHardwareAccelerationEnabled = enabled;
    }

}
