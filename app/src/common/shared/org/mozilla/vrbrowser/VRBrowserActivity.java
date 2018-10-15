/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.mozilla.gecko.GeckoVRManager;
import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.CrashReporter;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.audio.VRAudioTheme;
import org.mozilla.vrbrowser.search.SearchEngine;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.BrowserWidget;
import org.mozilla.vrbrowser.ui.CrashDialogWidget;
import org.mozilla.vrbrowser.ui.KeyboardWidget;
import org.mozilla.vrbrowser.ui.NavigationBarWidget;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.RootWidget;
import org.mozilla.vrbrowser.ui.TopBarWidget;
import org.mozilla.vrbrowser.ui.TrayWidget;
import org.mozilla.vrbrowser.ui.UIWidget;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate {

    private BroadcastReceiver mCrashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(CrashReporterService.CRASH_ACTION)) {
                Intent crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG);
                handleCrashIntent(crashIntent);
            }
        }
    };

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
    private int mWidgetHandleIndex = 1;
    AudioEngine mAudioEngine;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler();
    Runnable mAudioUpdateRunnable;
    BrowserWidget mBrowserWidget;
    RootWidget mRootWidget;
    KeyboardWidget mKeyboard;
    NavigationBarWidget mNavigationBar;
    CrashDialogWidget mCrashDialog;
    TopBarWidget mTopBar;
    TrayWidget mTray;
    PermissionDelegate mPermissionDelegate;
    LinkedList<UpdateListener> mWidgetUpdateListeners;
    LinkedList<PermissionListener> mPermissionListeners;
    LinkedList<FocusChangeListener> mFocusChangeListeners;
    LinkedList<Runnable> mBackHandlers;
    private boolean mIsPresentingImmersive = false;
    private Thread mUiThread;
    private boolean isDimmed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set a global exception handler as soon as possible
        GlobalExceptionHandler.register();

        if (BuildConfig.FLAVOR_platform == "oculusvr") {
            workaroundGeckoSigAction();
        }
        mUiThread = Thread.currentThread();

        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        SessionStore.get().setContext(this, extras);

        // Create broadcast receiver for getting crash messages from crash process
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CrashReporterService.CRASH_ACTION);
        registerReceiver(mCrashReceiver, intentFilter, getString(R.string.app_permission_name), null);

        isDimmed = false;
        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgetUpdateListeners = new LinkedList<>();
        mPermissionListeners = new LinkedList<>();
        mFocusChangeListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();

        mWidgets = new HashMap<>();
        mWidgetContainer = new FrameLayout(this);
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                Log.d(LOGTAG, "======> OnGlobalFocusChangeListener: old(" + oldFocus + ") new(" + newFocus + ")");
                for (FocusChangeListener listener: mFocusChangeListeners) {
                    listener.onGlobalFocusChanged(oldFocus, newFocus);
                }
            }
        });

        mPermissionDelegate = new PermissionDelegate(this, this);

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

        SearchEngine.get(this).update();
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

        // Empty widget just for handling focus on empty space
        mRootWidget = new RootWidget(this);

        // Create Tray
        mTray = new TrayWidget(this);

        addWidgets(Arrays.<Widget>asList(mRootWidget, mBrowserWidget, mNavigationBar, mKeyboard, mTray));
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
        if (mIsPresentingImmersive) {
            // This needs to be sync to ensure that WebVR is correctly paused.
            // Also prevents a deadlock in onDestroy when the BrowserWidget is released.
            exitImmersiveSync();
        }
        mAudioEngine.pauseEngine();
        SessionStore.get().setActive(false);
        super.onPause();
    }

    @Override
    protected void onResume() {
        SessionStore.get().setActive(true);
        mAudioEngine.resumeEngine();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        // Unregister the crash service broadcast receiver
        unregisterReceiver(mCrashReceiver);

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

        SessionStore.get().clearListeners();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.d(LOGTAG,"VRBrowserActivity onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        } else if (GeckoRuntime.ACTION_CRASHED.equals(intent.getAction())) {
            handleCrashIntent(intent);
        }
    }

    void loadFromIntent(final Intent intent) {
        if (GeckoRuntime.ACTION_CRASHED.equals(intent.getAction())) {
            handleCrashIntent(intent);
        }

        Uri uri = intent.getData();
        if (uri == null && intent.getExtras() != null && intent.getExtras().containsKey("url")) {
            uri = Uri.parse(intent.getExtras().getString("url"));
        }

        if (SessionStore.get().getCurrentSession() == null) {
            String url = (uri != null ? uri.toString() : null);
            int id = SessionStore.get().createSession();
            SessionStore.get().setCurrentSession(id);
            SessionStore.get().loadUri(url);
            Log.d(LOGTAG, "Creating session and loading URI from intent: " + url);
        } else if (uri != null) {
            Log.d(LOGTAG, "Loading URI from intent: " + uri.toString());
            SessionStore.get().loadUri(uri.toString());
        }
    }

    private void handleCrashIntent(final Intent intent) {
        Log.e(LOGTAG, "======> Got crashed intent");
        Log.d(LOGTAG, "======> Dump File: " +
                intent.getStringExtra(GeckoRuntime.EXTRA_MINIDUMP_PATH));
        Log.d(LOGTAG, "======> Extras File: " +
                intent.getStringExtra(GeckoRuntime.EXTRA_EXTRAS_PATH));
        Log.d(LOGTAG, "======> Dump Success: " +
                intent.getBooleanExtra(GeckoRuntime.EXTRA_MINIDUMP_SUCCESS, false));
        Log.d(LOGTAG, "======> Fatal: " +
                intent.getBooleanExtra(GeckoRuntime.EXTRA_CRASH_FATAL, false));

        boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        if (isCrashReportingEnabled) {
            sendCrashData(intent);

        } else {
            if (mCrashDialog == null) {
                mCrashDialog = new CrashDialogWidget(this);
                mCrashDialog.setCrashDialogDelegate(new CrashDialogWidget.CrashDialogDelegate() {
                    @Override
                    public void onSendData() {
                        sendCrashData(intent);
                    }
                });
            }

            mCrashDialog.show();
        }
    }

    private void sendCrashData(final Intent intent) {
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                try {
                    CrashReporter.sendCrashReport(VRBrowserActivity.this, intent, getString(R.string.crash_app_name));

                } catch (IOException | URISyntaxException e) {
                    Log.e(LOGTAG, "Failed to send crash report: " + e.toString());
                }
            }
        });
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

        } else if (SessionStore.get().canUnstackSession()){
            SessionStore.get().unstackSession();

        } else if (SessionStore.get().isCurrentSessionPrivate()) {
            SessionStore.get().exitPrivateMode();

        } else{
            super.onBackPressed();
        }
    }

    private void exitImmersiveSync() {
        Runnable exitImmersive = new Runnable() {
            @Override
            public void run() {
                exitImmersiveNative();
                synchronized(this) {
                    this.notifyAll();
                }
            }
        };
        synchronized (exitImmersive) {
            queueRunnable(exitImmersive);
            try {
                exitImmersive.wait();
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "Waiting for exit immersive onPause interrupted");
            }
        }
    }

    @Keep
    @SuppressWarnings("unused")
    void dispatchCreateWidget(final int aHandle, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        runOnUiThread(new Runnable() {
            public void run() {
                final Widget widget = mWidgets.get(aHandle);
                if (widget == null) {
                    Log.e(LOGTAG, "Widget " + aHandle + " not found");
                    return;
                }
                if (aTexture == null) {
                    Log.d(LOGTAG, "Widget: " + aHandle + " (" + aWidth + "x" + aHeight + ") received a null surface texture.");
                } else {
                    aTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                        @Override
                        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                            surfaceTexture.setOnFrameAvailableListener(null);
                            if (!widget.getFirstDraw()) {
                                widget.setFirstDraw(true);
                                updateWidget(widget);
                            }
                        }

                    }, mHandler);
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
    @SuppressWarnings("unused")
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aPressed, final float aX, final float aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget == null) {
                    MotionEventGenerator.dispatch(mRootWidget, aDevice, aPressed, aX, aY);

                } else {
                    MotionEventGenerator.dispatch(widget, aDevice, aPressed, aX, aY);
                }
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleScrollEvent(final int aHandle, final int aDevice, final float aX, final float aY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    MotionEventGenerator.dispatchScroll(widget, aDevice, aX, aY);
                } else {
                    Log.e(LOGTAG, "Failed to find widget for scroll event: " + aHandle);
                }
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleGesture(final int aType) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean consumed = false;
                if ((aType == GestureSwipeLeft) && (mLastGesture == GestureSwipeLeft)) {
                    Log.d(LOGTAG, "Go back!");
                    SessionStore.get().goBack();
                    consumed = true;
                } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                    Log.d(LOGTAG, "Go forward!");
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

    @SuppressWarnings({"UnusedDeclaration"})
    @Keep
    void handleBack() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                dispatchKeyEvent(new KeyEvent (KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            }
        });
    }

    @Keep
    @SuppressWarnings({"UnusedDeclaration"})
    void handleAudioPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        mAudioEngine.setPose(qx, qy, qz, qw, px, py, pz);

        // https://developers.google.com/vr/reference/android/com/google/vr/sdk/audio/GvrAudioEngine.html#resume()
        // The update method must be called from the main thread at a regular rate.
        runOnUiThread(mAudioUpdateRunnable);
    }

    @Keep
    @SuppressWarnings("unused")
    void handleResize(final int aHandle, final float aWorldWidth, final float aWorldHeight) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Widget widget = mWidgets.get(aHandle);
                if (widget != null) {
                    widget.handleResizeEvent(aWorldWidth, aWorldHeight);
                } else {
                    Log.e(LOGTAG, "Failed to find widget for resize: " + aHandle);
                }
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void registerExternalContext(long aContext) {
        GeckoVRManager.setExternalContext(aContext);
    }

    class PauseCompositorRunnable implements Runnable {
        public boolean done;
        @Override
        public void run() {
            synchronized (VRBrowserActivity.this) {
                if (mBrowserWidget != null) {
                    Log.d(LOGTAG, "About to pause Compositor");
                    mBrowserWidget.pauseCompositor();
                    Log.d(LOGTAG, "Compositor Paused");
                }
                done = true;
                VRBrowserActivity.this.notify();
            }
        }
    }

    @Keep
    @SuppressWarnings("unused")
    void pauseGeckoViewCompositor() {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = true;
        TelemetryWrapper.startImmersive();
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
    @SuppressWarnings("unused")
    void resumeGeckoViewCompositor() {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = false;
        TelemetryWrapper.uploadImmersiveToHistogram();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBrowserWidget != null) {
                    mBrowserWidget.resumeCompositor();
                    Log.d(LOGTAG, "Compositor Resumed");
                }
            }
        }, 20);
    }

    @Keep
    @SuppressWarnings("unused")
    String getStorageAbsolutePath() {
        return getExternalFilesDir(null).getAbsolutePath();
    }

    @Keep
    @SuppressWarnings("unused")
    public boolean isOverrideEnvPathEnabled() {
        return SettingsStore.getInstance(this).isEnvironmentOverrideEnabled();
    }

    @Keep
    @SuppressWarnings("unused")
    public String getActiveEnvironment() {
        return SettingsStore.getInstance(this).getEnvironment();
    }

    @Keep
    @SuppressWarnings("unused")
    public int getPointerColor() {
        return SettingsStore.getInstance(this).getPointerColor();
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

        for (UpdateListener listener: mWidgetUpdateListeners) {
            listener.onWidgetUpdate(aWidget);
        }

    }

    @Override
    public void removeWidget(final Widget aWidget) {
        mWidgets.remove(aWidget.getHandle());
        mWidgetContainer.removeView((View) aWidget);
        aWidget.setFirstDraw(false);
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
    public void addUpdateListener(UpdateListener aUpdateListener) {
        if (!mWidgetUpdateListeners.contains(aUpdateListener)) {
            mWidgetUpdateListeners.add(aUpdateListener);
        }
    }

    @Override
    public void removeUpdateListener(UpdateListener aUpdateListener) {
        mWidgetUpdateListeners.remove(aUpdateListener);
    }

    @Override
    public void addPermissionListener(PermissionListener aListener) {
        if (!mPermissionListeners.contains(aListener)) {
            mPermissionListeners.add(aListener);
        }
    }

    @Override
    public void removePermissionListener(PermissionListener aListener) {
        mPermissionListeners.remove(aListener);
    }

    @Override
    public void addFocusChangeListener(FocusChangeListener aListener) {
        if (!mFocusChangeListeners.contains(aListener)) {
            mFocusChangeListeners.add(aListener);
        }
    }

    @Override
    public void removeFocusChangeListener(FocusChangeListener aListener) {
        mFocusChangeListeners.remove(aListener);
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
        if (!isDimmed && (SessionStore.get().isCurrentSessionPrivate() ^
                mNavigationBar.isInFocusMode() ^
                mTray.isSettingsDialogOpened() ^
                mPermissionDelegate.isPermissionDialogVisible() ^
                (mCrashDialog != null && mCrashDialog.isVisible()))) {
            isDimmed = true;
            queueRunnable(new Runnable() {
                @Override
                public void run() {
                    fadeOutWorldNative();
                }
            });
        }
    }

    @Override
    public void fadeInWorld() {
        if (isDimmed && (!SessionStore.get().isCurrentSessionPrivate() &&
                !mNavigationBar.isInFocusMode() &&
                !mTray.isSettingsDialogOpened() &&
                !mPermissionDelegate.isPermissionDialogVisible() &&
                !(mCrashDialog != null && mCrashDialog.isVisible()))) {
            isDimmed = false;
            queueRunnable(new Runnable() {
                @Override
                public void run() {
                    fadeInWorldNative();
                }
            });
        }
    }

    @Override
    public void setTrayVisible(boolean visible) {
        if (visible) {
            mTray.show();

        } else {
            mTray.hide();
        }
    }

    @Override
    public void setBrowserSize(float targetWidth, float targetHeight) {
        mBrowserWidget.setBrowserSize(targetWidth, targetHeight, 1.0f);
    }

    @Override
    public void keyboardDismissed() {
        mNavigationBar.showVoiceSearch();
    }

    @Override
    public void updateEnvironment() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                updateEnvironmentNative();
            }
        });
    }

    @Override
    public void updatePointerColor() {
        queueRunnable(new Runnable() {
            @Override
            public void run() {
                updatePointerColorNative();
            }
        });
    }

    @Override
    public boolean isPermissionGranted(@NonNull String permission) {
        return mPermissionDelegate.isPermissionGranted(permission);
    }

    @Override
    public void requestPermission(@NonNull String uri, @NonNull String permission, GeckoSession.PermissionDelegate.Callback aCallback) {
        mPermissionDelegate.onAppPermissionRequest(
                SessionStore.get().getCurrentSession(),
                uri,
                permission,
                aCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (PermissionListener listener : mPermissionListeners) {
            listener.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
    private native void workaroundGeckoSigAction();
    private native void updateEnvironmentNative();
    private native void updatePointerColorNative();
}
