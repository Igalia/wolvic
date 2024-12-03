package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WSession;

import org.chromium.base.Log;

import java.util.concurrent.Executor;

public class OverlayContentWidget extends UIWidget implements WidgetManagerDelegate.WorldClickListener {
    private Surface mSurface;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private Runnable mFirstDrawCallback;
    private WSession mSession;
    private WDisplay mDisplay;
    private WSession.ContentDelegate.OnPaymentHandlerCallback mCallback;
    private Executor mUIThreadExecutor;
    private Handler mHandler;

    public OverlayContentWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public OverlayContentWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public OverlayContentWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    public void setDelegates(@NonNull WSession session, @NonNull WDisplay display,
                             @NonNull WSession.ContentDelegate.OnPaymentHandlerCallback callback) {
        Log.e("MYSH", "OverlayContentWidget setDelegates");
        mSession = session;
        mDisplay = display;
        mCallback = callback;
    }

    @Override
    public void releaseWidget() {
        Log.e("MYSH", "OverlayContentWidget releaseWidget");
        mWidgetManager.removeWorldClickListener(this);
        mCallback.onDismiss();

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.tabs_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.tabs_height);

        // aPlacement.width = SettingsStore.getInstance(getContext()).getWindowWidth() / 2;
        // aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight() / 2;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                                  WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                                      WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    private void initialize(Context aContext) {
        mUIThreadExecutor = ((VRBrowserApplication)aContext.getApplicationContext()).getExecutors().mainThread();
        mHandler = new Handler(Looper.getMainLooper());

        mWidgetManager.addWorldClickListener(this);
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget window) {
        mWidgetPlacement.parentHandle = window.getHandle();
    }

    // View
    @Override
    public boolean onKeyPreIme(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyPreIme(aKeyCode, aEvent)) {
            return true;
        }
        return mSession.getTextInput().onKeyPreIme(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyUp(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyUp(aKeyCode, aEvent)) {
            return true;
        }
        return mSession.getTextInput().onKeyUp(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyDown(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyDown(aKeyCode, aEvent)) {
            return true;
        }
        return mSession.getTextInput().onKeyDown(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyLongPress(int aKeyCode, KeyEvent aEvent) {
        if (super.onKeyLongPress(aKeyCode, aEvent)) {
            return true;
        }
        return mSession.getTextInput().onKeyLongPress(aKeyCode, aEvent);
    }

    @Override
    public boolean onKeyMultiple(int aKeyCode, int repeatCount, KeyEvent aEvent) {
        if (super.onKeyMultiple(aKeyCode, repeatCount, aEvent)) {
            return true;
        }
        return mSession.getTextInput().onKeyMultiple(aKeyCode, repeatCount, aEvent);
    }

    @Override
    public void handleHoverEvent(MotionEvent aEvent) {
        mSession.getPanZoomController().onMotionEvent(aEvent);
    }

    @Override
    public void handleTouchEvent(MotionEvent aEvent) {
        if (aEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestFocus();
            requestFocusFromTouch();
        }

        mSession.getPanZoomController().onTouchEvent(aEvent);
    }

    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        mSession.getPanZoomController().onTouchEvent(aEvent);
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        mSession.getPanZoomController().onMotionEvent(aEvent);
        return true;
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture aTexture, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        Log.e("MYSH", "OverlayContentWidget setSurfaceTexture aTexture=" + aTexture + "aWidth=" + aWidth + " aHeight=" + aHeight);
        mFirstDrawCallback = aFirstDrawCallback;
         if (aTexture == null) {
            setWillNotDraw(true);
            return;
        }
        mSurfaceWidth = aWidth;
        mSurfaceHeight = aHeight;
        mTexture = aTexture;
        aTexture.setDefaultBufferSize(aWidth, aHeight);
        mSurface = new Surface(aTexture);
        callSurfaceChanged();
        mHandler.postDelayed(() -> {
            if (mFirstDrawCallback != null) {
                mUIThreadExecutor.execute(mFirstDrawCallback);
                mFirstDrawCallback = null;
            }
        }, 100);
    }

    @Override
    public void setSurface(Surface aSurface, final int aWidth, final int aHeight, Runnable aFirstDrawCallback) {
        Log.e("MYSH", "OverlayContentWidget setSurface aSurface=" + aSurface + "aWidth=" + aWidth + " aHeight=" + aHeight);
        mSurfaceWidth = aWidth;
        mSurfaceHeight = aHeight;
        mSurface = aSurface;
        mFirstDrawCallback = aFirstDrawCallback;
        if (mSurface != null) {
            callSurfaceChanged();
        } else {
            mDisplay.surfaceDestroyed();
        }
        mHandler.postDelayed(() -> {
            if (mFirstDrawCallback != null) {
                mUIThreadExecutor.execute(mFirstDrawCallback);
                mFirstDrawCallback = null;
            }
        }, 100);
    }

    private void callSurfaceChanged() {
        Log.e("MYSH", "OverlayContentWidget callSurfaceChanged mSurface=" + mSurface);
        if (mSurface != null) {
            mDisplay.surfaceChanged(mSurface, 0, 0, mSurfaceWidth, mSurfaceHeight);
        }
    }

    @Override
    public void resizeSurface(final int aWidth, final int aHeight) {
        Log.e("MYSH", "OverlayContentWidget resizeSurface aWidth=" + aWidth + " aHeight=" + aHeight);
        mSurfaceWidth = aWidth;
        mSurfaceHeight = aHeight;
        if (mTexture != null) {
            mTexture.setDefaultBufferSize(aWidth, aHeight);
        }

        if (mTexture != null && mSurface != null) {
            callSurfaceChanged();
        }
    }

    @Override
    public void onWorldClick() {
        post(this::onDismiss);
        mCallback.onDismiss();
    }

    @Override
    public boolean isDialog() {
        return true;
    }
}