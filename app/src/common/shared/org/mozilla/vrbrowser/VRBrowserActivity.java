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
import android.os.Looper;
import android.support.annotation.Keep;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.gecko.GeckoVRManager;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.audio.VRAudioTheme;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.BrowserWidget;
import org.mozilla.vrbrowser.ui.KeyboardWidget;
import org.mozilla.vrbrowser.ui.NavigationBarWidget;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.SettingsStore;
import org.mozilla.vrbrowser.ui.SettingsWidget;
import org.mozilla.vrbrowser.ui.TopBarWidget;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate, TopBarWidget.TopBarDelegate {

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

    static final int TrayEventHelp = 0;
    static final int TrayEventSettings = 1;
    static final int TrayEventPrivate = 2;

    static final String LOGTAG = "VRB";
    HashMap<Integer, Widget> mWidgets;
    private int mWidgetHandleIndex = 1;
    AudioEngine mAudioEngine;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler();
    Runnable mAudioUpdateRunnable;
    BrowserWidget mBrowserWidget;
    KeyboardWidget mKeyboard;
    NavigationBarWidget mNavigationBar;
    TopBarWidget mTopBar;
    PermissionDelegate mPermissionDelegate;
    LinkedList<WidgetManagerDelegate.Listener> mWidgetEventListeners;
    LinkedList<Runnable> mBackHandlers;
    SettingsWidget mSettingsWidget;
    private boolean mIsPresentingImmersive = false;
    int mPreviousSessionId = SessionStore.NO_SESSION_ID;
    private Thread mUiThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(LOGTAG, "VRBrowserActivity onCreate");
        mUiThread = Thread.currentThread();
        SessionStore.get().setContext(this);

        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgets = new HashMap<>();
        mWidgetContainer = new FrameLayout(this);
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (mKeyboard != null) {
                    mKeyboard.updateFocusedView(newFocus);
                }
            }
        });

        mPermissionDelegate = new PermissionDelegate(this, this);
        mWidgetEventListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();

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
        final String tempPath = getCacheDir().getAbsolutePath();
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                setTemporaryFilePath(tempPath);
            }
        });
        initializeWorld();

        int sessionId = SettingsStore.getInstance(this).getCurrentSessionId();
        if (sessionId != SessionStore.NO_SESSION_ID) {
            GeckoSession session = SessionStore.get().getSession(sessionId);
            if (session != null) {
                SessionStore.get().setCurrentSession(sessionId);
                mPreviousSessionId = SettingsStore.getInstance(this).getPreviousSessionId();
            }
        }
    }

    protected void initializeWorld() {
        // Create browser widget
        if (SessionStore.get().getCurrentSession() == null) {
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id);
        }
        int currentSession = SessionStore.get().getCurrentSessionId();
        mBrowserWidget = new BrowserWidget(this, currentSession);
        mPermissionDelegate.setParentWidgetHandle(mBrowserWidget.getHandle());

        // Create Browser navigation widget
        mNavigationBar = new NavigationBarWidget(this);
        mNavigationBar.setBrowserWidget(mBrowserWidget);

        // Create keyboard widget
        mKeyboard = new KeyboardWidget(this);
        mKeyboard.setBrowserWidget(mBrowserWidget);

        // Create the top bar
        mTopBar = new TopBarWidget(this);
        mTopBar.setBrowserWidget(mBrowserWidget);
        mTopBar.setDelegate(this);

        addWidgets(Arrays.<Widget>asList(mBrowserWidget, mNavigationBar, mKeyboard, mTopBar));
    }

    @Override
    protected void onStart() {
        super.onStart();
        TelemetryWrapper.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        TelemetryWrapper.stop();
    }

    @Override
    protected void onPause() {
        mAudioEngine.pauseEngine();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mAudioEngine.resumeEngine();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        for (Widget widget: mWidgets.values()) {
            widget.releaseWidget();
        }

        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.release();
        }
        if (mAudioEngine != null) {
            mAudioEngine.release();
        }
        if (mPermissionDelegate != null) {
            mPermissionDelegate.release();
        }

        SettingsStore.getInstance(this).setCurrentSessionId(SessionStore.get().getCurrentSessionId());
        SettingsStore.getInstance(this).setPreviousSessionId(mPreviousSessionId);

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
        if (mIsPresentingImmersive) {
            queueRunnable(new Runnable() {
                @Override
                public void run() {
                    exitImmersiveNative();
                }
            });
            return;
        }
        if (mBackHandlers.size() > 0) {
            mBackHandlers.getLast().run();
            return;
        }
        if (SessionStore.get().canGoBack()) {
            SessionStore.get().goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Keep
    void dispatchCreateWidget(final int aHandle, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        runOnUiThread(new Runnable() {
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget == null) {
                    Log.e(LOGTAG, "Widget " + aHandle + " not found");
                    return;
                }
                widget.setSurfaceTexture(aTexture, aWidth, aHeight);
                // Add widget to a virtual display for invalidation
                if (((View)widget).getParent() == null) {
                    mWidgetContainer.addView((View) widget, new FrameLayout.LayoutParams(aWidth, aHeight));
                }
            }
        });
    }

    @Keep
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aPressed, final float aX, final float aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                MotionEventGenerator.dispatch(widget, aDevice, aPressed, aX, aY);
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
    void handleTrayEvent(final int aType) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (aType) {
                    case TrayEventHelp: {

                    }
                    break;
                    case TrayEventSettings: {
                        if (mSettingsWidget == null) {
                            mSettingsWidget = new SettingsWidget(VRBrowserActivity.this);
                        }
                        mSettingsWidget.toggle();
                    }
                    break;
                    case TrayEventPrivate: {
                        onPrivateBrowsingClicked();
                    }
                    break;
                }
                mAudioEngine.playSound(AudioEngine.Sound.CLICK);
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
    void handleResize(final int aHandle, final float aWorldWidth, final float aWorldHeight) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    widget.handleResizeEvent(aWorldWidth, aWorldHeight);
                } else {
                    Log.e(LOGTAG, "Failed to find widget: " + aHandle);
                }
            }
        });
    }

    @Keep
    void registerExternalContext(long aContext) {
        GeckoVRManager.setExternalContext(aContext);
    }

    class PauseCompositorRunnable implements Runnable {
        public boolean done;
        @Override
        public void run() {
            synchronized (VRBrowserActivity.this) {
                if (mBrowserWidget != null) {
                    Log.e(LOGTAG, "About to pause Compositor");
                    mBrowserWidget.pauseCompositor();
                    Log.e(LOGTAG, "Compositor Paused");
                }
                done = true;
                VRBrowserActivity.this.notify();
            }
        }
    }

    @Keep
    void pauseGeckoViewCompositor() {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = true;
        PauseCompositorRunnable runnable = new PauseCompositorRunnable();

        synchronized (this) {
            runOnUiThread(runnable);
            while (!runnable.done) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "Waiting for compositor pause interrupted");
                }
            }
        }
    }

    @Keep
    void resumeGeckoViewCompositor() {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = false;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBrowserWidget != null) {
                    mBrowserWidget.resumeCompositor();
                    Log.e(LOGTAG, "Compositor Resumed");
                }
            }
        }, 20);
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

    @Override
    public int newWidgetHandle() {
        return mWidgetHandleIndex++;
    }


    public void addWidgets(final Iterable<Widget> aWidgets) {
        for (Widget widget: aWidgets) {
            mWidgets.put(widget.getHandle(), widget);
            ((View)widget).setVisibility(widget.getPlacement().visible ? View.VISIBLE : View.GONE);
        }
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                for (Widget widget: aWidgets) {
                    addWidgetNative(widget.getHandle(), widget.getPlacement());
                }
            }
        });
    }

    // WidgetManagerDelegate
    @Override
    public void addWidget(final Widget aWidget) {
        mWidgets.put(aWidget.getHandle(), aWidget);
        ((View)aWidget).setVisibility(aWidget.getPlacement().visible ? View.VISIBLE : View.GONE);

        queueRunnable(new Runnable() {
            @Override
            public void run() {
                addWidgetNative(aWidget.getHandle(), aWidget.getPlacement());
            }
        });
    }

    @Override
    public void updateWidget(final Widget aWidget) {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                updateWidgetNative(aWidget.getHandle(), aWidget.getPlacement());
            }
        });

        final int textureWidth = aWidget.getPlacement().textureWidth();
        final int textureHeight = aWidget.getPlacement().textureHeight();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)((View)aWidget).getLayoutParams();
        if (params == null) {
            // Widget not added yet
            return;
        }
        if (params.width != textureWidth || params.height != textureHeight) {
            params.width = textureWidth;
            params.height = textureHeight;
            ((View)aWidget).setLayoutParams(params);
            aWidget.resizeSurfaceTexture(textureWidth, textureHeight);
        }

        boolean visible = aWidget.getPlacement().visible;
        View view = (View)aWidget;
        if (visible != (view.getVisibility() == View.VISIBLE)) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        for (WidgetManagerDelegate.Listener listener: mWidgetEventListeners) {
            listener.onWidgetUpdate(aWidget);
        }

    }

    @Override
    public void removeWidget(final Widget aWidget) {
        mWidgets.remove(aWidget.getHandle());
        mWidgetContainer.removeView((View) aWidget);
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                removeWidgetNative(aWidget.getHandle());
            }
        });
    }

    @Override
    public void startWidgetResize(final Widget aWidget) {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                startWidgetResizeNative(aWidget.getHandle());
            }
        });
    }

    @Override
    public void finishWidgetResize(final Widget aWidget) {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                finishWidgetResizeNative(aWidget.getHandle());
            }
        });
    }

    @Override
    public void addListener(WidgetManagerDelegate.Listener aListener) {
        if (!mWidgetEventListeners.contains(aListener)) {
            mWidgetEventListeners.add(aListener);
        }
    }

    @Override
    public void removeListener(WidgetManagerDelegate.Listener aListener) {
        mWidgetEventListeners.remove(aListener);
    }

    @Override
    public void pushBackHandler(Runnable aRunnable) {
        mBackHandlers.addLast(aRunnable);
    }

    @Override
    public void popBackHandler(Runnable aRunnable) {
        mBackHandlers.removeLastOccurrence(aRunnable);
    }

    @Override
    public void fadeOutWorld() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                fadeOutWorldNative();
            }
        });
    }

    @Override
    public void fadeInWorld() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                fadeInWorldNative();
            }
        });
    }

    @Override
    public void setTrayVisible(boolean visible) {
        setTrayVisibleNative(visible);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void onPrivateBrowsingClicked() {
        GeckoSession currentSession = SessionStore.get().getCurrentSession();
        if (currentSession == null)
            return;

        boolean isPrivateMode  = currentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);

        if (!isPrivateMode) {
            fadeOutWorld();
            // TODO: Fade out the browser window. Waiting for https://github.com/MozillaReality/FirefoxReality/issues/77

            if (mPreviousSessionId == SessionStore.NO_SESSION_ID) {
                mPreviousSessionId = SessionStore.get().getCurrentSessionId();

                SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
                settings.privateMode = true;
                int id = SessionStore.get().createSession(settings);
                SessionStore.get().setCurrentSession(id);
                SessionStore.get().loadUri(SessionStore.DEFAULT_URL);

            } else {
                int sessionId = SessionStore.get().getCurrentSessionId();
                SessionStore.get().setCurrentSession(mPreviousSessionId);
                mPreviousSessionId = sessionId;
            }

        } else {
            fadeInWorld();
            // TODO: Fade in the browser window. Waiting for https://github.com/MozillaReality/FirefoxReality/issues/77

            int sessionId = SessionStore.get().getCurrentSessionId();
            SessionStore.get().setCurrentSession(mPreviousSessionId);
            mPreviousSessionId = sessionId;
        }
    }

    // TopBarDelegate
    @Override
    public void onCloseClicked() {
        GeckoSession currentSession = SessionStore.get().getCurrentSession();
        if (currentSession == null)
            return;

        boolean isPrivateMode  = currentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);

        if (isPrivateMode) {
            fadeInWorld();
            // TODO: Fade in the browser window. Waiting for https://github.com/MozillaReality/FirefoxReality/issues/77

            int privateSessionId = SessionStore.get().getCurrentSessionId();
            SessionStore.get().setCurrentSession(mPreviousSessionId);
            mPreviousSessionId = SessionStore.NO_SESSION_ID;

            SessionStore.get().removeSession(privateSessionId);
        }
    }

    private native void addWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void removeWidgetNative(int aHandle);
    private native void startWidgetResizeNative(int aHandle);
    private native void finishWidgetResizeNative(int aHandle);
    private native void fadeOutWorldNative();
    private native void fadeInWorldNative();
    private native void setTemporaryFilePath(String aPath);
    private native void exitImmersiveNative();
    private native void setTrayVisibleNative(boolean visible);
}
