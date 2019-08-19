/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.CrashReporter;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoVRManager;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;
import org.mozilla.vrbrowser.crashreporting.GlobalExceptionHandler;
import org.mozilla.vrbrowser.geolocation.GeolocationWrapper;
import org.mozilla.vrbrowser.ui.widgets.prompts.ConfirmPromptWidget;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.input.MotionEventGenerator;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.widgets.KeyboardWidget;
import org.mozilla.vrbrowser.ui.widgets.NavigationBarWidget;
import org.mozilla.vrbrowser.ui.widgets.RootWidget;
import org.mozilla.vrbrowser.ui.widgets.TrayWidget;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.VideoProjectionMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.Widget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.ui.widgets.dialogs.CrashDialogWidget;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;
import org.mozilla.vrbrowser.utils.LocaleUtils;
import org.mozilla.vrbrowser.utils.ServoUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;

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
    static final long RESET_CRASH_COUNT_DELAY = 5000;

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
    Windows mWindows;
    RootWidget mRootWidget;
    KeyboardWidget mKeyboard;
    NavigationBarWidget mNavigationBar;
    CrashDialogWidget mCrashDialog;
    TrayWidget mTray;
    PermissionDelegate mPermissionDelegate;
    LinkedList<UpdateListener> mWidgetUpdateListeners;
    LinkedList<PermissionListener> mPermissionListeners;
    LinkedList<FocusChangeListener> mFocusChangeListeners;
    LinkedList<WorldClickListener> mWorldClickListeners;
    LinkedList<Runnable> mBackHandlers;
    private boolean mIsPresentingImmersive = false;
    private Thread mUiThread;
    private LinkedList<Pair<Object, Float>> mBrightnessQueue;
    private Pair<Object, Float> mCurrentBrightness;
    private SearchEngineWrapper mSearchEngineWrapper;
    private SettingsStore mSettings;
    private ConnectivityReceiver mConnectivityReceiver;
    private boolean mConnectionAvailable = true;
    private AudioManager mAudioManager;
    private Widget mActiveDialog;
    private Set<String> mPoorPerformanceWhiteList;

    private boolean callOnAudioManager(Consumer<AudioManager> fn) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            try {
                fn.accept(mAudioManager);
                return true;
            } catch (Exception e) {
                Log.e(LOGTAG, "Caught exception calling AudioManager: " + e.toString());
            }
        }
        return false;
    }

    private ViewTreeObserver.OnGlobalFocusChangeListener globalFocusListener = new ViewTreeObserver.OnGlobalFocusChangeListener() {
        @Override
        public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            Log.d(LOGTAG, "======> OnGlobalFocusChangeListener: old(" + oldFocus + ") new(" + newFocus + ")");
            for (FocusChangeListener listener: mFocusChangeListeners) {
                listener.onGlobalFocusChanged(oldFocus, newFocus);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Fix for infinite restart on startup crashes.
        long count = SettingsStore.getInstance(getBaseContext()).getCrashRestartCount();
        boolean cancelRestart = count > CrashReporterService.MAX_RESTART_COUNT;
        if (cancelRestart) {
            super.onCreate(savedInstanceState);
            Log.e(LOGTAG, "Cancel Restart");
            finish();
            return;
        }
        SettingsStore.getInstance(getBaseContext()).incrementCrashRestartCount();
        mHandler.postDelayed(() -> SettingsStore.getInstance(getBaseContext()).resetCrashRestartCount(), RESET_CRASH_COUNT_DELAY);
        // Set a global exception handler as soon as possible
        GlobalExceptionHandler.register(this.getApplicationContext());

        LocaleUtils.init(this);

        if (DeviceType.isOculusBuild()) {
            workaroundGeckoSigAction();
        }
        mUiThread = Thread.currentThread();

        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        SessionStore.get().setContext(this, extras);
        SessionStore.get().initializeStores(this);
        ((VRBrowserApplication)getApplication()).getRepository().migrateOldBookmarks();

        // Create broadcast receiver for getting crash messages from crash process
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CrashReporterService.CRASH_ACTION);
        registerReceiver(mCrashReceiver, intentFilter, getString(R.string.app_permission_name), null);

        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgetUpdateListeners = new LinkedList<>();
        mPermissionListeners = new LinkedList<>();
        mFocusChangeListeners = new LinkedList<>();
        mWorldClickListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();
        mBrightnessQueue = new LinkedList<>();
        mCurrentBrightness = Pair.create(null, 1.0f);

        mWidgets = new HashMap<>();
        mWidgetContainer = new FrameLayout(this);

        mPermissionDelegate = new PermissionDelegate(this, this);

        mAudioEngine = new AudioEngine(this, null);
        mAudioEngine.setEnabled(SettingsStore.getInstance(this).isAudioEnabled());
        mAudioEngine.preloadAsync(() -> {
            Log.i(LOGTAG, "AudioEngine sounds preloaded!");
            // mAudioEngine.playSound(AudioEngine.Sound.AMBIENT, true);
        });
        mAudioUpdateRunnable = () -> mAudioEngine.update();

        mSettings = SettingsStore.getInstance(this);

        queueRunnable(() -> createOffscreenDisplay());
        final String tempPath = getCacheDir().getAbsolutePath();
        queueRunnable(() -> setTemporaryFilePath(tempPath));
        updateFoveatedLevel();

        initializeWidgets();

        loadFromIntent(getIntent());

        // Setup the search engine
        mSearchEngineWrapper = SearchEngineWrapper.get(this);
        mSearchEngineWrapper.registerForUpdates();

        GeolocationWrapper.update(this);

        mConnectivityReceiver = new ConnectivityReceiver();
        mPoorPerformanceWhiteList = new HashSet<>();
    }

    protected void initializeWidgets() {
        mWindows = new Windows(this);
        mWindows.setDelegate(this::attachToWindow);

        // Create Browser navigation widget
        mNavigationBar = new NavigationBarWidget(this);

        // Create keyboard widget
        mKeyboard = new KeyboardWidget(this);

        // Create the tray
        mTray = new TrayWidget(this);

        // Empty widget just for handling focus on empty space
        mRootWidget = new RootWidget(this);
        mRootWidget.setClickCallback(() -> {
            for (WorldClickListener listener: mWorldClickListeners) {
                listener.onWorldClick();
            }
        });

        // Add widget listeners
        mTray.addListeners(mWindows);

        attachToWindow(mWindows.getFocusedWindow(), null);

        addWidgets(Arrays.asList(mRootWidget, mNavigationBar, mKeyboard, mTray));
    }

    private void attachToWindow(@NonNull WindowWidget aWindow, @Nullable WindowWidget aPrevWindow) {
        mPermissionDelegate.setParentWidgetHandle(aWindow.getHandle());
        mNavigationBar.attachToWindow(aWindow);
        mKeyboard.attachToWindow(aWindow);
        mTray.attachToWindow(aWindow);

        if (aPrevWindow != null) {
            updateWidget(mNavigationBar);
            updateWidget(mKeyboard);
            updateWidget(mTray);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        TelemetryWrapper.start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (SettingsStore.getInstance(this).getCylinderDensity() > 0.0f) {
            TelemetryWrapper.queueCurvedModeActiveEvent();
        }

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

        SessionStore.get().onPause();

        mWindows.onPause();

        for (Widget widget: mWidgets.values()) {
            widget.onPause();
        }
        mConnectivityReceiver.unregister(this);
        // Reset so the dialog will show again on resume.
        mConnectionAvailable = true;
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onPause();
        }
        mWidgetContainer.getViewTreeObserver().removeOnGlobalFocusChangeListener(globalFocusListener);
        super.onPause();
    }

    @Override
    protected void onResume() {
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusListener);
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onResume();
        }

        SessionStore.get().onResume();

        mAudioEngine.resumeEngine();
        for (Widget widget: mWidgets.values()) {
            widget.onResume();
        }
        handleConnectivityChange();
        mConnectivityReceiver.register(this, () -> runOnUiThread(() -> handleConnectivityChange()));
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        // Unregister the crash service broadcast receiver
        unregisterReceiver(mCrashReceiver);
        mSearchEngineWrapper.unregisterForUpdates();

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

        // Remove all widget listeners
        mTray.removeListeners(mWindows);
        mTray.onDestroy();
        mWindows.onDestroy();

        SessionStore.get().onDestroy();

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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        SessionStore.get().onConfigurationChanged(newConfig);

        super.onConfigurationChanged(newConfig);
    }

    void loadFromIntent(final Intent intent) {
        if (GeckoRuntime.ACTION_CRASHED.equals(intent.getAction())) {
            handleCrashIntent(intent);
        }

        Uri uri = intent.getData();
        if (uri == null && intent.getExtras() != null && intent.getExtras().containsKey("url")) {
            uri = Uri.parse(intent.getExtras().getString("url"));
        }

        SessionStack activeStore = SessionStore.get().getActiveStore();

        Bundle extras = intent.getExtras();
        if (extras != null && extras.containsKey("homepage")) {
            Uri homepageUri = Uri.parse(extras.getString("homepage"));
            SettingsStore.getInstance(this).setHomepage(homepageUri.toString());
        }
        if (extras != null && extras.containsKey("e10s")) {
            boolean wasEnabled = SettingsStore.getInstance(this).isMultiprocessEnabled();
            boolean enabled = extras.getBoolean("e10s", wasEnabled);
            if (wasEnabled != enabled) {
                SettingsStore.getInstance(this).setMultiprocessEnabled(enabled);
                SessionStore.get().setMultiprocess(enabled);
            }
        }

        if (activeStore != null) {
            if (activeStore.getCurrentSession() == null) {
                String url = (uri != null ? uri.toString() : null);
                activeStore.newSessionWithUrl(url);
                Log.d(LOGTAG, "Creating session and loading URI from intent: " + url);

            } else if (uri != null) {
                Log.d(LOGTAG, "Loading URI from intent: " + uri.toString());
                activeStore.loadUri(uri.toString());

            } else {
                mWindows.getFocusedWindow().loadHomeIfNotRestored();
            }
        }
    }

    private void handleConnectivityChange() {
        boolean connected = ConnectivityReceiver.isNetworkAvailable(this);
        if (connected != mConnectionAvailable && mWindows.getFocusedWindow() != null) {
            mWindows.getFocusedWindow().setNoInternetToastVisible(!connected);
        }
        mConnectionAvailable = connected;
    }

    private void handleCrashIntent(@NonNull final Intent intent) {
        Log.e(LOGTAG, "======> Got crashed intent");
        Log.d(LOGTAG, "======> Dump File: " +
                intent.getStringExtra(GeckoRuntime.EXTRA_MINIDUMP_PATH));
        Log.d(LOGTAG, "======> Extras File: " +
                intent.getStringExtra(GeckoRuntime.EXTRA_EXTRAS_PATH));
        Log.d(LOGTAG, "======> Fatal: " +
                intent.getBooleanExtra(GeckoRuntime.EXTRA_CRASH_FATAL, false));

        boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        if (isCrashReportingEnabled) {
            sendCrashData(intent);

        } else {
            if (mCrashDialog == null) {
                mCrashDialog = new CrashDialogWidget(this);
                mCrashDialog.setCrashDialogDelegate(() -> sendCrashData(intent));
            }

            mCrashDialog.show(UIWidget.REQUEST_FOCUS);
        }
    }

    private void sendCrashData(final Intent intent) {
        ThreadUtils.postToBackgroundThread(() -> {
            try {
                GeckoResult<String> result = CrashReporter.sendCrashReport(VRBrowserActivity.this, intent, getString(R.string.crash_app_name));

                result.then(crashID -> {
                    Log.e(LOGTAG, "Submitted crash report id: " + crashID);
                    Log.e(LOGTAG, "Report available at: https://crash-stats.mozilla.com/report/index/" + crashID);
                    return null;
                }, (GeckoResult.OnExceptionListener<Void>) ex -> {
                    Log.e(LOGTAG, "Failed to submit crash report: " + ex.getMessage());
                    return null;
                });
            } catch (IOException | URISyntaxException e) {
                Log.e(LOGTAG, "Failed to send crash report: " + e.toString());
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mIsPresentingImmersive) {
            queueRunnable(() -> exitImmersiveNative());
            return;
        }
        if (mBackHandlers.size() > 0) {
            mBackHandlers.getLast().run();
            return;
        }
        if (!mWindows.handleBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (DeviceType.isOculusBuild()) {
            int action = event.getAction();
            if (action != KeyEvent.ACTION_DOWN) {
                return super.dispatchKeyEvent(event);
            }
            int keyCode = event.getKeyCode();
            boolean result;
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    result = callOnAudioManager((AudioManager aManager) -> aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI));
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    result = callOnAudioManager((AudioManager aManager) -> aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI));
                    break;
                default:
                    return super.dispatchKeyEvent(event);
            }
            return result || super.dispatchKeyEvent(event);

        } else if (DeviceType.isGoogleVR()) {
            boolean result;
            switch( event.getKeyCode() ) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    result = true;
                    break;
                default:
                    result = super.dispatchKeyEvent(event);
                    break;
            }
            return result;
        }
        return super.dispatchKeyEvent(event);
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
        runOnUiThread(() -> {
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
            View view = (View) widget;
            if (view.getParent() == null) {
                mWidgetContainer.addView(view, new FrameLayout.LayoutParams(widget.getPlacement().viewWidth(), widget.getPlacement().viewHeight()));
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void dispatchCreateWidgetLayer(final int aHandle, final Surface aSurface, final int aWidth, final int aHeight, final long aNativeCallback) {
        runOnUiThread(() -> {
            final Widget widget = mWidgets.get(aHandle);
            if (widget == null) {
                Log.e(LOGTAG, "Widget " + aHandle + " not found");
                return;
            }

            Runnable aFirstDrawCallback = () -> {
                if (aNativeCallback != 0) {
                    queueRunnable(() -> runCallbackNative(aNativeCallback));
                }
                if (aSurface != null && !widget.getFirstDraw()) {
                    widget.setFirstDraw(true);
                    updateWidget(widget);
                }
            };


            widget.setSurface(aSurface, aWidth, aHeight, aFirstDrawCallback);

            View view = (View) widget;
            // Add widget to a virtual display for invalidation
            if (aSurface != null && view.getParent() == null) {
                mWidgetContainer.addView(view, new FrameLayout.LayoutParams(widget.getPlacement().viewWidth(), widget.getPlacement().viewHeight()));
            } else if (aSurface == null && view.getParent() != null) {
                mWidgetContainer.removeView(view);
            }
            view.postInvalidate();
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aPressed, final float aX, final float aY) {
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);
            if (!isWidgetInputEnabled(widget)) {
                widget = null; // Fallback to mRootWidget in order to allow world clicks to dismiss UI.
            }
            if (widget instanceof WindowWidget) {
                WindowWidget window = (WindowWidget) widget;
                boolean focused = mWindows.getFocusedWindow() == window;
                if (!focused && aPressed) {
                    // Focus the window when pressed
                    mWindows.focusWindow(window);
                    // Discard first click.
                    return;
                } else if (!focused) {
                    // Do not send hover events to not focused windows.
                    widget = null;
                }
            }


            float scale = widget != null ? widget.getPlacement().textureScale : 1.0f;
            final float x = aX / scale;
            final float y = aY / scale;

            if (widget == null) {
                MotionEventGenerator.dispatch(mRootWidget, aDevice, aPressed, x, y);
            } else if (widget.getBorderWidth() > 0) {
                final int border = widget.getBorderWidth();
                MotionEventGenerator.dispatch(widget, aDevice, aPressed, x - border, y - border);
            } else {
                MotionEventGenerator.dispatch(widget, aDevice, aPressed, x, y);
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleScrollEvent(final int aHandle, final int aDevice, final float aX, final float aY) {
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);
            if (!isWidgetInputEnabled(widget)) {
                return;
            }
            if (widget != null) {
                float scrollDirection = mSettings.getScrollDirection() == 0 ? 1.0f : -1.0f;
                MotionEventGenerator.dispatchScroll(widget, aDevice, aX * scrollDirection, aY * scrollDirection);
            } else {
                Log.e(LOGTAG, "Failed to find widget for scroll event: " + aHandle);
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleGesture(final int aType) {
        runOnUiThread(() -> {
            boolean consumed = false;
            if ((aType == GestureSwipeLeft) && (mLastGesture == GestureSwipeLeft)) {
                Log.d(LOGTAG, "Go back!");
                SessionStore.get().getActiveStore().goBack();

                consumed = true;
            } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                Log.d(LOGTAG, "Go forward!");
                SessionStore.get().getActiveStore().goForward();
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
        });
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @Keep
    void handleBack() {
        runOnUiThread(() -> {
            // On WAVE VR, the back button no longer seems to work.
            if (DeviceType.isWaveBuild()) {
                onBackPressed();
                return;
            }
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            dispatchKeyEvent(new KeyEvent (KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        });
    }

    @Keep
    @SuppressWarnings({"UnusedDeclaration"})
    void handleAudioPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        mAudioEngine.setPose(qx, qy, qz, qw, px, py, pz);

        // https://developers.google.com/vr/reference/android/com/google/vr/sdk/audio/GvrAudioEngine.html#resume()
        // The initialize method must be called from the main thread at a regular rate.
        runOnUiThread(mAudioUpdateRunnable);
    }

    @Keep
    @SuppressWarnings("unused")
    void handleResize(final int aHandle, final float aWorldWidth, final float aWorldHeight) {
        runOnUiThread(() -> {
            mWindows.getFocusedWindow().handleResizeEvent(aWorldWidth, aWorldHeight);
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleMoveEnd(final int aHandle, final float aDeltaX, final float aDeltaY, final float aDeltaZ, final float aRotation) {
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);
            if (widget != null) {
                widget.handleMoveEvent(aDeltaX, aDeltaY, aDeltaZ, aRotation);
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void registerExternalContext(long aContext) {
        ServoUtils.setExternalContext(aContext);
        GeckoVRManager.setExternalContext(aContext);
    }

    class PauseCompositorRunnable implements Runnable {
        public boolean done;
        @Override
        public void run() {
            synchronized (VRBrowserActivity.this) {
                Log.d(LOGTAG, "About to pause Compositor");
                mWindows.pauseCompositor();
                Log.d(LOGTAG, "Compositor Paused");
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
        // Show the window in front of you when you exit immersive mode.
        resetUIYaw();

        TelemetryWrapper.uploadImmersiveToHistogram();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            mWindows.resumeCompositor();
            Log.d(LOGTAG, "Compositor Resumed");
        }, 20);
    }

    @Keep
    @SuppressWarnings("unused")
    void renderPointerLayer(final Surface aSurface, final long aNativeCallback) {
        runOnUiThread(() -> {
            try {
                Canvas canvas = aSurface.lockHardwareCanvas();
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setDither(true);
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                final float x = canvas.getWidth() * 0.5f;
                final float y = canvas.getHeight() * 0.5f;
                final float radius = canvas.getWidth() * 0.4f;
                canvas.drawCircle(x, y, radius, paint);
                paint.setColor(Color.BLACK);
                paint.setStrokeWidth(4);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(x, y, radius, paint);
                aSurface.unlockCanvasAndPost(canvas);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            if (aNativeCallback != 0) {
                queueRunnable(() -> runCallbackNative(aNativeCallback));
            }
        });
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
    public boolean areLayersEnabled() {
        return SettingsStore.getInstance(this).getLayersEnabled();
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

    @Keep
    @SuppressWarnings("unused")
    private void setDeviceType(int aType) {

        runOnUiThread(() -> DeviceType.setType(aType));
    }

    @Keep
    @SuppressWarnings("unused")
    private void haltActivity(final int aReason) {
        runOnUiThread(() -> {
            if (mConnectionAvailable && mWindows.getFocusedWindow() != null) {
                mWindows.getFocusedWindow().showAlert(
                        getString(R.string.not_entitled_title),
                        getString(R.string.not_entitled_message, getString(R.string.app_name)),
                        () -> VRBrowserActivity.this.finish());
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    private void handlePoorPerformance() {
        runOnUiThread(() -> {
            if (!mSettings.isPerformanceMonitorEnabled()) {
                return;
            }
            // Don't block poorly performing immersive pages.
            if (mIsPresentingImmersive) {
                return;
            }
            WindowWidget window = mWindows.getFocusedWindow();
            if (window == null) {
                return;
            }
            final String originalUrl = window.getSessionStack().getCurrentUri();
            if (mPoorPerformanceWhiteList.contains(originalUrl)) {
                return;
            }
            window.getSessionStack().loadUri("about:blank");
            final String[] buttons = {getString(R.string.ok_button), getString(R.string.performance_unblock_page)};
            window.showButtonPrompt(getString(R.string.performance_title), getString(R.string.performance_message), buttons, new ConfirmPromptWidget.ConfirmPromptDelegate() {
                @Override
                public void confirm(int index) {
                    if (index == GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE) {
                        mPoorPerformanceWhiteList.add(originalUrl);
                        window.getSessionStack().loadUri(originalUrl);
                    }
                }

                @Override
                public void dismiss() {

                }
            });
        });
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
        runOnUiThread(() -> {
            mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 16, 16);
            mOffscreenDisplay.setContentView(mWidgetContainer);
        });
    }

    @Override
    public int newWidgetHandle() {
        return mWidgetHandleIndex++;
    }


    public void addWidgets(final Iterable<? extends Widget> aWidgets) {
        for (Widget widget: aWidgets) {
            mWidgets.put(widget.getHandle(), widget);
            ((View)widget).setVisibility(widget.getPlacement().visible ? View.VISIBLE : View.GONE);
        }
        queueRunnable(() -> {
            for (Widget widget: aWidgets) {
                addWidgetNative(widget.getHandle(), widget.getPlacement());
            }
        });
    }

    private void updateActiveDialog(final Widget aWidget) {
        if (!aWidget.isDialog()) {
            return;
        }

        if (aWidget.isVisible()) {
            mActiveDialog = aWidget;
        } else if (aWidget == mActiveDialog && !aWidget.isVisible()) {
            mActiveDialog = null;
        }
    }

    private boolean isWidgetInputEnabled(Widget aWidget) {
        return mActiveDialog == null || aWidget == null || mActiveDialog == aWidget || aWidget instanceof KeyboardWidget;
    }

    // WidgetManagerDelegate
    @Override
    public void addWidget(Widget aWidget) {
        mWidgets.put(aWidget.getHandle(), aWidget);
        ((View)aWidget).setVisibility(aWidget.getPlacement().visible ? View.VISIBLE : View.GONE);
        queueRunnable(() -> addWidgetNative(aWidget.getHandle(), aWidget.getPlacement()));
        updateActiveDialog(aWidget);
    }

    @Override
    public void updateWidget(final Widget aWidget) {
        queueRunnable(() -> updateWidgetNative(aWidget.getHandle(), aWidget.getPlacement()));

        final int textureWidth = aWidget.getPlacement().textureWidth();
        final int textureHeight = aWidget.getPlacement().textureHeight();
        final int viewWidth = aWidget.getPlacement().viewWidth();
        final int viewHeight = aWidget.getPlacement().viewHeight();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)((View)aWidget).getLayoutParams();
        if (params == null) {
            // Widget not added yet
            return;
        }
        if (params.width != viewWidth || params.height != viewHeight) {
            params.width = viewWidth;
            params.height = viewHeight;
            ((View)aWidget).setLayoutParams(params);
            aWidget.resizeSurface(textureWidth, textureHeight);
        }

        boolean visible = aWidget.getPlacement().visible;
        View view = (View)aWidget;
        if (visible != (view.getVisibility() == View.VISIBLE)) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        for (UpdateListener listener: mWidgetUpdateListeners) {
            listener.onWidgetUpdate(aWidget);
        }
        updateActiveDialog(aWidget);
    }

    @Override
    public void removeWidget(final Widget aWidget) {
        mWidgets.remove(aWidget.getHandle());
        mWidgetContainer.removeView((View) aWidget);
        aWidget.setFirstDraw(false);
        queueRunnable(() -> removeWidgetNative(aWidget.getHandle()));
        if (aWidget == mActiveDialog) {
            mActiveDialog = null;
        }
    }

    @Override
    public void updateVisibleWidgets() {
        queueRunnable(this::updateVisibleWidgetsNative);
    }

    @Override
    public void startWidgetResize(final Widget aWidget, float aMaxWidth, float aMaxHeight) {
        queueRunnable(() -> startWidgetResizeNative(aWidget.getHandle(), aMaxWidth, aMaxHeight));
    }

    @Override
    public void finishWidgetResize(final Widget aWidget) {
        queueRunnable(() -> finishWidgetResizeNative(aWidget.getHandle()));
    }

    @Override
    public void startWidgetMove(final Widget aWidget, @WidgetMoveBehaviourFlags int aMoveBehaviour) {
        queueRunnable(() -> startWidgetMoveNative(aWidget.getHandle(), aMoveBehaviour));
    }

    @Override
    public void finishWidgetMove() {
        queueRunnable(() -> finishWidgetMoveNative());
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
    public void addWorldClickListener(WorldClickListener aListener) {
        if (!mWorldClickListeners.contains(aListener)) {
            mWorldClickListeners.add(aListener);
        }
    }

    @Override
    public void removeWorldClickListener(WorldClickListener aListener) {
        mWorldClickListeners.remove(aListener);
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
    public void setIsServoSession(boolean aIsServo) {
      queueRunnable(() -> setIsServo(aIsServo));
    }

    @Override
    public void pushWorldBrightness(Object aKey, float aBrightness) {
        if (mCurrentBrightness.second != aBrightness) {
            queueRunnable(() -> setWorldBrightnessNative(aBrightness));
        }
        mBrightnessQueue.add(mCurrentBrightness);
        mCurrentBrightness = Pair.create(aKey, aBrightness);
    }

    @Override
    public void setWorldBrightness(Object aKey, final float aBrightness) {
        if (mCurrentBrightness.first == aKey) {
            if (mCurrentBrightness.second != aBrightness) {
                mCurrentBrightness = Pair.create(aKey, aBrightness);
                queueRunnable(() -> setWorldBrightnessNative(aBrightness));
            }
        } else {
            for (int i = mBrightnessQueue.size() - 1; i >= 0; --i) {
                if (mBrightnessQueue.get(i).first == aKey) {
                    mBrightnessQueue.set(i, Pair.create(aKey, aBrightness));
                    break;
                }
            }
        }
    }

    @Override
    public void popWorldBrightness(Object aKey) {
        if (mBrightnessQueue.size() == 0) {
            return;
        }
        if (mCurrentBrightness.first == aKey) {
            float brightness = mCurrentBrightness.second;
            mCurrentBrightness = mBrightnessQueue.removeLast();
            if (mCurrentBrightness.second != brightness) {
                queueRunnable(() -> setWorldBrightnessNative(mCurrentBrightness.second));
            }

            return;
        }
        for (int i = mBrightnessQueue.size() - 1; i >= 0; --i) {
            if (mBrightnessQueue.get(i).first == aKey) {
                mBrightnessQueue.remove(i);
                break;
            }
        }
    }

    @Override
    public void setTrayVisible(boolean visible) {
        mTray.setTrayVisible(visible);
    }

    @Override
    public void setControllersVisible(final boolean aVisible) {
        queueRunnable(() -> setControllersVisibleNative(aVisible));
    }

    @Override
    public void setWindowSize(float targetWidth, float targetHeight) {
        mWindows.getFocusedWindow().resizeByMultiplier(targetWidth / targetHeight, 1.0f);
    }

    @Override
    public void keyboardDismissed() {
        mNavigationBar.showVoiceSearch();
    }

    @Override
    public void updateEnvironment() {
        queueRunnable(() -> updateEnvironmentNative());
    }

    @Override
    public void updateFoveatedLevel() {
        final int appLevel = SettingsStore.getInstance(this).getFoveatedLevelApp();
        queueRunnable(() -> updateFoveatedLevelNative(appLevel));
    }

    @Override
    public void updatePointerColor() {
        queueRunnable(() -> updatePointerColorNative());
    }

    @Override
    public boolean isPermissionGranted(@NonNull String permission) {
        return mPermissionDelegate.isPermissionGranted(permission);
    }

    @Override
    public void requestPermission(String uri, @NonNull String permission, GeckoSession.PermissionDelegate.Callback aCallback) {
        SessionStack activeStore = SessionStore.get().getActiveStore();
        if (uri != null && !uri.isEmpty()) {
            mPermissionDelegate.onAppPermissionRequest(activeStore.getCurrentSession(), uri, permission, aCallback);
        } else {
            mPermissionDelegate.onAndroidPermissionsRequest(activeStore.getCurrentSession(), new String[]{permission}, aCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull  String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        runOnUiThread(() -> {
            for (PermissionListener listener : mPermissionListeners) {
                listener.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        });
    }

    @Override
    public void showVRVideo(final int aWindowHandle, final @VideoProjectionMenuWidget.VideoProjectionFlags int aVideoProjection) {
        queueRunnable(() -> showVRVideoNative(aWindowHandle, aVideoProjection));
    }

    @Override
    public void hideVRVideo() {
        queueRunnable(this::hideVRVideoNative);
    }

    @Override
    public void resetUIYaw() {
        queueRunnable(this::resetUIYawNative);
    }

    @Override
    public void setCylinderDensity(final float aDensity) {
        if (mWindows != null && aDensity == 0.0f && mWindows.getWindowsCount() > 1) {
            return;
        }
        queueRunnable(() -> setCylinderDensityNative(aDensity));
        if (mWindows != null) {
            mWindows.updateCurvedMode(false);
        }
    }

    @Override
    public void setCPULevel(int aCPULevel) {
        queueRunnable(() -> setCPULevelNative(aCPULevel));
    }

    @Override
    public void openNewWindow(String uri) {
        WindowWidget newWindow = mWindows.addWindow();
        if (newWindow != null) {
            newWindow.getSessionStack().newSessionWithUrl(uri);
        }
    }

    @Override
    public WindowWidget getFocusedWindow() {
        return mWindows.getFocusedWindow();
    }

    private native void addWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateVisibleWidgetsNative();
    private native void removeWidgetNative(int aHandle);
    private native void startWidgetResizeNative(int aHandle, float maxWidth, float maxHeight);
    private native void finishWidgetResizeNative(int aHandle);
    private native void startWidgetMoveNative(int aHandle, int aMoveBehaviour);
    private native void finishWidgetMoveNative();
    private native void setWorldBrightnessNative(float aBrigthness);
    private native void setTemporaryFilePath(String aPath);
    private native void exitImmersiveNative();
    private native void workaroundGeckoSigAction();
    private native void updateEnvironmentNative();
    private native void updatePointerColorNative();
    private native void showVRVideoNative(int aWindowHandler, int aVideoProjection);
    private native void hideVRVideoNative();
    private native void resetUIYawNative();
    private native void setControllersVisibleNative(boolean aVisible);
    private native void runCallbackNative(long aCallback);
    private native void setCylinderDensityNative(float aDensity);
    private native void setCPULevelNative(@CPULevelFlags int aCPULevel);
    private native void setIsServo(boolean aIsServo);
    private native void updateFoveatedLevelNative(int appLevel);

}
