/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.audio.VRAudioTheme;
import org.mozilla.vrbrowser.ui.BrowserHeaderWidget;
import org.mozilla.vrbrowser.ui.KeyboardWidget;
import org.mozilla.vrbrowser.ui.MoreMenuWidget;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.NavigationBar;
import org.mozilla.vrbrowser.ui.TabOverflowWidget;
import org.mozilla.vrbrowser.ui.UIWidget;

import java.util.HashMap;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate {
    class SwipeRunnable implements Runnable {
        boolean mCanceled = false;
        @Override
        public void run() {
            if (!mCanceled) {
                mLastGesture = NoGesture;
            }
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    static final int NoGesture = -1;
    static final int GestureSwipeLeft = 0;
    static final int GestureSwipeRight = 1;
    static final int SwipeDelay = 1000; // milliseconds

    static final String LOGTAG = "VRB";
    HashMap<Integer, Widget> mWidgets;
    SparseArray<WidgetAddCallback> mWidgetAddCallbacks;
    private int mWidgetAddCallbackIndex;
    AudioEngine mAudioEngine;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler();
    Runnable mAudioUpdateRunnable;
    BrowserWidget mBrowserWidget;
    KeyboardWidget mKeyboard;
    private boolean mWasBrowserPressed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "VRBrowserActivity onCreate");

        SessionStore.get().setContext(this);

        if (SessionStore.get().getCurrentSession() == null) {
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                   SessionStore.get().getCurrentSession().loadUri(SessionStore.DEFAULT_URL);
                }
            }, 2000);
        }

        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgets = new HashMap<>();
        mWidgetAddCallbacks = new SparseArray<>();
        mWidgetContainer = new FrameLayout(this);
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                checkKeyboardFocus(newFocus);
            }
        });

        mAudioEngine = new AudioEngine(this, new VRAudioTheme());
        mAudioEngine.preloadAsync(new Runnable() {
            @Override
            public void run() {
                Log.i(LOGTAG, "AudioEngine sounds preloaded!");
                // mAudioEngine.playSound(AudioEngine.Sound.AMBIENT, true);
            }
        });
        mAudioUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                mAudioEngine.update();
            }
        };

        loadFromIntent(getIntent());
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                createOffscreenDisplay();
            }
        });
    }

    @Override
    protected void onPause() {
        SessionStore.get().setShowSoftInputOnFocus(true);
        mAudioEngine.pauseEngine();
        super.onPause();
    }

    @Override
    protected void onResume() {
        SessionStore.get().setShowSoftInputOnFocus(false);
        mAudioEngine.resumeEngine();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.release();
        }
        if (mAudioEngine != null) {
            mAudioEngine.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.e(LOGTAG,"VRBrowserActivity onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        }
    }

    void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        if (SessionStore.get().getCurrentSession() == null) {
            String url = (uri != null ? uri.toString() : SessionStore.DEFAULT_URL);
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id);
            SessionStore.get().loadUri(url);
            Log.e(LOGTAG, "Load creating session and loading URI:" + url);
        } else if (uri != null) {
            Log.e(LOGTAG, "Got URI: " + uri.toString());
            SessionStore.get().loadUri(uri.toString());
        }
    }

    @Override
    public void onBackPressed() {
        if (SessionStore.get().canGoBack()) {
            SessionStore.get().goBack();
        } else {
            super.onBackPressed();
        }
    }

    void createWidget(final int aType, final int aHandle, SurfaceTexture aTexture, int aWidth, int aHeight, int aCallbackId) {
        Widget widget = mWidgets.get(aHandle);
        if (widget != null) {
            Log.e(LOGTAG, "Widget of type: " + aType + " already created");
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            return;
        } else {
            Log.e(LOGTAG, "CREATE WIDGET TYPE: " + aType);
        }
        if (aType == Widget.Browser) {
            if (SessionStore.get().getCurrentSession() == null) {
                int id = SessionStore.get().createSession();
                SessionStore.get().setCurrentSession(id);
            }
            int currentSession = SessionStore.get().getCurrentSessionId();
            mBrowserWidget = new BrowserWidget(this, currentSession);
            widget = mBrowserWidget;
            createKeyboard();

        } else if (aType == Widget.URLBar) {
            widget = new BrowserHeaderWidget(this);
        } else if (aType == Widget.MoreMenu) {
            widget = new MoreMenuWidget(this);
        } else if (aType == Widget.TabOverflowMenu) {
            widget = new TabOverflowWidget(this);
        } else if (aType == Widget.KeyboardWidget) {
            widget = new KeyboardWidget(this);
        }

        if (widget != null) {
            widget.setSurfaceTexture(aTexture, aWidth, aHeight);
            mWidgets.put(aHandle, widget);
        }

        widget.setHandle(aHandle);
        widget.setWidgetManager(this);

        // Add hidden UI widget to a virtual display for invalidation
        mWidgetContainer.addView((View) widget, new FrameLayout.LayoutParams(aWidth, aHeight));

        WidgetAddCallback callback = mWidgetAddCallbacks.get(aCallbackId);
        if (callback != null) {
            mWidgetAddCallbacks.remove(aCallbackId);
            callback.onWidgetAdd(widget);
        }
    }

    void createKeyboard() {
        if (mKeyboard != null) {
            return;
        }

        final WidgetPlacement placement = new WidgetPlacement();
        placement.widgetType = Widget.KeyboardWidget;
        placement.parentHandle = mBrowserWidget.getHandle();
        placement.width = 640;
        placement.height = 192;
        placement.parentAnchorX = 0.5f;
        placement.parentAnchorY = 0.5f;
        placement.anchorX = 0.5f;
        placement.anchorY = 0.5f;
        placement.translationZ = 660.0f;
        placement.rotationAxisX = 1.0f;
        placement.rotation = (float)Math.toRadians(-15.0f);
        placement.worldScale = 0.11f;

        addWidget(placement, false, new WidgetAddCallback() {
            @Override
            public void onWidgetAdd(Widget aWidget) {
                mKeyboard = (KeyboardWidget) aWidget;
                mKeyboard.setPlacement(placement);
            }
        });

    }

    void checkKeyboardFocus(View focusedView) {
        if (mKeyboard == null) {
            return;
        }

        boolean showKeyboard = focusedView.onCheckIsTextEditor();
        WidgetPlacement updatedPlacement = null;
        if (showKeyboard) {
            mKeyboard.setFocusedView(focusedView);
            // Fixme: Improve keyboard placement once GeckoView API lands a way to detect the TextView position on a webpage
            // For now we just use different placements for navigation bar &  GeckoView
            float translationY = focusedView == mBrowserWidget ? -30.0f : -20.0f;
            if (translationY != mKeyboard.getPlacement().translationY) {
                updatedPlacement = mKeyboard.getPlacement();
                updatedPlacement.translationY = translationY;
            }

        }
        boolean keyboardIsVisible = mKeyboard.getVisibility() == View.VISIBLE;
        if (showKeyboard != keyboardIsVisible || (updatedPlacement != null)) {
            updateWidget(mKeyboard.getHandle(), showKeyboard, updatedPlacement);
        }
    }

    @Keep
    void dispatchCreateWidget(final int aType, final int aHandle, final SurfaceTexture aTexture, final int aWidth, final int aHeight, final int aCallbackId) {
        runOnUiThread(new Runnable() {
            public void run() {
                createWidget(aType, aHandle, aTexture, aWidth, aHeight, aCallbackId);
            }
        });
    }

    @Keep
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aPressed, final float aX, final float aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    MotionEventGenerator.dispatch(widget, aDevice, aPressed, aX, aY);
                } else {
                    Log.e(LOGTAG, "Failed to find widget: " + aHandle);
                }
                // Fixme: Remove this once the new Keyboard delegate lands in GeckoView
                if (widget == mBrowserWidget) {
                    if (mWasBrowserPressed != aPressed) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                checkKeyboardFocus(mBrowserWidget);
                            }
                        }, 150);
                    }
                    mWasBrowserPressed = aPressed;
                }
            }
        });
    }

    @Keep
    void handleScrollEvent(final int aHandle, final int aDevice, final float aX, final float aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    MotionEventGenerator.dispatchScroll(widget, aDevice, aX, aY);
                } else {
                    Log.e(LOGTAG, "Failed to find widget: " + aHandle);
                }
            }
        });
    }

    @Keep
    void handleGesture(final int aType) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean consumed = false;
                if ((aType == GestureSwipeLeft) && (mLastGesture == GestureSwipeLeft)) {
                    Log.e(LOGTAG, "Go BACK!");
                    SessionStore.get().goBack();
                    consumed = true;
                } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                    Log.e(LOGTAG, "Go FORWARD!");
                    SessionStore.get().goForward();
                    consumed = true;
                }
                if (mLastRunnable != null) {
                    mLastRunnable.mCanceled = true;
                    mLastRunnable = null;
                }
                if (consumed) {
                    mLastGesture = NoGesture;

                } else {
                    mLastGesture = aType;
                    mLastRunnable = new SwipeRunnable();
                    mHandler.postDelayed(mLastRunnable, SwipeDelay);
                }
            }
        });
    }

    @Keep
    void handleAudioPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        mAudioEngine.setPose(qx, qy, qz, qw, px, py, pz);

        // https://developers.google.com/vr/reference/android/com/google/vr/sdk/audio/GvrAudioEngine.html#resume()
        // The update method must be called from the main thread at a regular rate.
        runOnUiThread(mAudioUpdateRunnable);
    }

    @Keep
    float getDisplayDensity() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.density;
    }

    void createOffscreenDisplay() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0]);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(LOGTAG, "OpenGL Error creating OffscreenDisplay: " + error);
        }

        final SurfaceTexture texture = new SurfaceTexture(ids[0]);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 16, 16);
                mOffscreenDisplay.setContentView(mWidgetContainer);
            }
        });
    }

    // WidgetManagerDelegate
    @Override
    public void addWidget(final WidgetPlacement aPlacement, final boolean aVisible, final WidgetAddCallback aCallback) {
        int id = 0;
        if (aCallback != null) {
            id = ++mWidgetAddCallbackIndex;
            mWidgetAddCallbacks.put(id, aCallback);
        }
        final int callbackId = id;
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                addWidgetNative(aPlacement, aVisible, callbackId);
            }
        });
    }

    @Override
    public void updateWidget(final int aHandle, final boolean aVisible, @Nullable final WidgetPlacement aPlacement) {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                if (aPlacement != null) {
                    updateWidgetPlacementNative(aHandle, aPlacement);
                }
                setWidgetVisibleNative(aHandle, aVisible);
            }
        });

        Widget widget = mWidgets.get(aHandle);
        if (widget != null) {
            ((UIWidget) widget).setVisibility(aVisible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void removeWidget(final int aHandle) {
        Widget widget = mWidgets.remove(aHandle);
        if (widget instanceof View) {
            mWidgetContainer.removeView((View) widget);
        }
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                removeWidgetNative(aHandle);
            }
        });
    }


    private native void addWidgetNative(WidgetPlacement aWidget, boolean aVisible, int aCallbackId);
    private native void setWidgetVisibleNative(int aHandle, boolean aVisible);
    private native void updateWidgetPlacementNative(int aHandle, WidgetPlacement aPlacement);
    private native void removeWidgetNative(int aHandle);
}
