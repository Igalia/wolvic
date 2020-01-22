/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
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
import android.os.Process;
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

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoVRManager;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;
import org.mozilla.vrbrowser.crashreporting.GlobalExceptionHandler;
import org.mozilla.vrbrowser.geolocation.GeolocationWrapper;
import org.mozilla.vrbrowser.input.MotionEventGenerator;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.OffscreenDisplay;
import org.mozilla.vrbrowser.ui.widgets.KeyboardWidget;
import org.mozilla.vrbrowser.ui.widgets.NavigationBarWidget;
import org.mozilla.vrbrowser.ui.widgets.RootWidget;
import org.mozilla.vrbrowser.ui.widgets.TrayWidget;
import org.mozilla.vrbrowser.ui.widgets.UISurfaceTextureRenderer;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.Widget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.ui.widgets.dialogs.CrashDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.dialogs.WhatsNewWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.VideoProjectionMenuWidget;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver.Delegate;
import org.mozilla.vrbrowser.utils.DeviceType;
import org.mozilla.vrbrowser.utils.LocaleUtils;
import org.mozilla.vrbrowser.utils.ServoUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate, ComponentCallbacks2 {

    private BroadcastReceiver mCrashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(CrashReporterService.CRASH_ACTION)) {
                Intent crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG);
                handleContentCrashIntent(crashIntent);
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

    static final String LOGTAG = SystemUtils.createLogtag(VRBrowserActivity.class);
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
    CopyOnWriteArrayList<Delegate> mConnectivityListeners;
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
    private float mCurrentCylinderDensity = 0;

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
        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
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

        LocaleUtils.init();

        if (DeviceType.isOculusBuild()) {
            workaroundGeckoSigAction();
        }
        mUiThread = Thread.currentThread();

        BitmapCache.getInstance(this).onCreate();

        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        SessionStore.get().setContext(this, extras);
        SessionStore.get().initializeServices();
        SessionStore.get().initializeStores(this);
        SessionStore.get().setLocales(LocaleUtils.getPreferredLocales(this));

        // Create broadcast receiver for getting crash messages from crash process
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CrashReporterService.CRASH_ACTION);
        registerReceiver(mCrashReceiver, intentFilter, BuildConfig.APPLICATION_ID + "." + getString(R.string.app_permission_name), null);

        mLastGesture = NoGesture;
        super.onCreate(savedInstanceState);

        mWidgetUpdateListeners = new LinkedList<>();
        mPermissionListeners = new LinkedList<>();
        mFocusChangeListeners = new LinkedList<>();
        mWorldClickListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();
        mBrightnessQueue = new LinkedList<>();
        mConnectivityListeners = new CopyOnWriteArrayList<>();
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

        queueRunnable(() -> {
            createOffscreenDisplay();
            createCaptureSurface();
        });
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
        checkForCrash();
    }

    protected void initializeWidgets() {
        UISurfaceTextureRenderer.setUseHardwareAcceleration(SettingsStore.getInstance(getBaseContext()).isUIHardwareAccelerationEnabled());
        UISurfaceTextureRenderer.setRenderActive(true);
        mWindows = new Windows(this);
        mWindows.setDelegate(new Windows.Delegate() {
            @Override
            public void onFocusedWindowChanged(@NonNull WindowWidget aFocusedWindow, @Nullable WindowWidget aPrevFocusedWindow) {
                attachToWindow(aFocusedWindow, aPrevFocusedWindow);
                mTray.setAddWindowVisible(mWindows.canOpenNewWindow());
                mNavigationBar.hideNotifications();
            }
            @Override
            public void onWindowBorderChanged(@NonNull WindowWidget aChangeWindow) {
                mKeyboard.proxifyLayerIfNeeded(mWindows.getCurrentWindows());
            }

            @Override
            public void onWindowsMoved() {
                mNavigationBar.hideNotifications();
                updateWidget(mTray);
            }

            @Override
            public void onWindowClosed() {
                mTray.setAddWindowVisible(mWindows.canOpenNewWindow());
                mNavigationBar.hideNotifications();
                updateWidget(mTray);
            }

            @Override
            public void onWindowVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {
                @CPULevelFlags int cpuLevel = mWindows.isVideoAvailable() ? WidgetManagerDelegate.CPU_LEVEL_HIGH :
                        WidgetManagerDelegate.CPU_LEVEL_NORMAL;

                queueRunnable(() -> setCPULevelNative(cpuLevel));
            }
        });

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
        mTray.setAddWindowVisible(mWindows.canOpenNewWindow());

        attachToWindow(mWindows.getFocusedWindow(), null);

        addWidgets(Arrays.asList(mRootWidget, mNavigationBar, mKeyboard, mTray));

        // Show the what's upp dialog if we haven't showed it yet and this is v6.
        if (!SettingsStore.getInstance(this).isWhatsNewDisplayed()) {
            final WhatsNewWidget whatsNew = new WhatsNewWidget(this);
            whatsNew.setLoginOrigin(Accounts.LoginOrigin.NONE);
            whatsNew.getPlacement().parentHandle = mWindows.getFocusedWindow().getHandle();
            whatsNew.show(UIWidget.REQUEST_FOCUS);
        }
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
        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
        super.onStart();
        TelemetryWrapper.start();
        UISurfaceTextureRenderer.setRenderActive(true);
    }

    @Override
    protected void onStop() {
        SettingsStore.getInstance(getBaseContext()).setPid(0);
        super.onStop();

        TelemetryWrapper.stop();
        GleanMetricsService.sessionStop();
        UISurfaceTextureRenderer.setRenderActive(false);
    }

    @Override
    protected void onPause() {
        if (mIsPresentingImmersive) {
            // This needs to be sync to ensure that WebVR is correctly paused.
            // Also prevents a deadlock in onDestroy when the BrowserWidget is released.
            exitImmersiveSync();
        }
        mAudioEngine.pauseEngine();

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
        MotionEventGenerator.clearDevices();
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusListener);
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onResume();
        }

        mWindows.onResume();

        mAudioEngine.resumeEngine();
        for (Widget widget: mWidgets.values()) {
            widget.onResume();
        }
        mConnectivityListeners.forEach((listener) -> {
            listener.OnConnectivityChanged(ConnectivityReceiver.isNetworkAvailable(this));
        });
        mConnectivityReceiver.register(this, mConnectivityDelegate);

        // If we're signed-in, poll for any new device events (e.g. received tabs) on activity resume.
        // There's no push support right now, so this helps with the perception of speedy tab delivery.
        ((VRBrowserApplication)getApplicationContext()).getAccounts().refreshDevicesAsync();
        ((VRBrowserApplication)getApplicationContext()).getAccounts().pollForEventsAsync();

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        SettingsStore.getInstance(getBaseContext()).setPid(0);
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
        mWindows.onDestroy();

        BitmapCache.getInstance(this).onDestroy();

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
            loadFromIntent(intent);

        } else if (GeckoRuntime.ACTION_CRASHED.equals(intent.getAction())) {
            Log.e(LOGTAG, "Restarted after a crash");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        SessionStore.get().onConfigurationChanged(newConfig);

        super.onConfigurationChanged(newConfig);
    }

    void loadFromIntent(final Intent intent) {
        if (GeckoRuntime.ACTION_CRASHED.equals(intent.getAction())) {
            Log.e(LOGTAG,"Loading from crash Intent");
        }

        Uri uri = intent.getData();

        boolean openInWindow = false;
        boolean openInTab = false;
        boolean openInBackground = false;

        Bundle extras = intent.getExtras();
        if (extras != null) {
            // If there is no data uri and there is a url parameter we get that
            if (uri == null && extras.containsKey("url")) {
                uri = Uri.parse(intent.getExtras().getString("url"));
            }

            // Overwrite the stored homepage
            if (extras.containsKey("homepage")) {
                Uri homepageUri = Uri.parse(extras.getString("homepage"));
                SettingsStore.getInstance(this).setHomepage(homepageUri.toString());
            }

            // Enable/Disbale e10s
            if (extras.containsKey("e10s")) {
                boolean wasEnabled = SettingsStore.getInstance(this).isMultiprocessEnabled();
                boolean enabled = extras.getBoolean("e10s", wasEnabled);
                if (wasEnabled != enabled) {
                    SettingsStore.getInstance(this).setMultiprocessEnabled(enabled);
                    SessionStore.get().resetMultiprocess();
                }
            }

            // Open the provided URL in a new tab, if there is no URL provided we just open the homepage
            if (extras.containsKey("create_new_tab")) {
                openInTab = extras.getBoolean("create_new_tab", false);
                if (uri == null) {
                    uri = Uri.parse(SettingsStore.getInstance(this).getHomepage());
                }
            }

            // Open the tab in background/foreground, if there is no URL provided we just open the homepage
            if (extras.containsKey("background")) {
                openInBackground = extras.getBoolean("background", false);
                if (uri == null) {
                    uri = Uri.parse(SettingsStore.getInstance(this).getHomepage());
                }
            }

            // Open the provided URL in a new window, if there is no URL provided we just open the homepage
            if (extras.containsKey("create_new_window")) {
                openInWindow = extras.getBoolean("create_new_window", false);
                if (uri == null) {
                    uri = Uri.parse(SettingsStore.getInstance(this).getHomepage());
                }
            }
        }

        // If there is a URI we open it
        if (uri != null) {
            Log.d(LOGTAG, "Loading URI from intent: " + uri.toString());

            if (openInWindow) {
                openNewWindow(uri.toString());

            } else if (openInTab) {
                if (openInBackground) {
                    openNewTab(uri.toString());

                } else {
                    openNewTabForeground(uri.toString());
                }

            } else {
                SessionStore.get().getActiveSession().loadUri(uri.toString());
            }

        } else {
            mWindows.getFocusedWindow().loadHomeIfNotRestored();
        }
    }

    private ConnectivityReceiver.Delegate mConnectivityDelegate = connected -> {
        mConnectivityListeners.forEach((listener) -> listener.OnConnectivityChanged(connected));
        mConnectionAvailable = connected;
    };

    private void checkForCrash() {
        final ArrayList<String> files = CrashReporterService.findCrashFiles(getBaseContext());
        if (files.isEmpty()) {
            Log.d(LOGTAG, "No crash files found.");
            return;
        }
        boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        if (isCrashReportingEnabled) {
            SystemUtils.postCrashFiles(this, files);

        } else {
            if (mCrashDialog == null) {
                mCrashDialog = new CrashDialogWidget(this, files);
            }
            mCrashDialog.show(UIWidget.REQUEST_FOCUS);
        }
    }

    private void handleContentCrashIntent(@NonNull final Intent intent) {
        Log.e(LOGTAG, "Got content crashed intent");
        final String dumpFile = intent.getStringExtra(GeckoRuntime.EXTRA_MINIDUMP_PATH);
        final String extraFile = intent.getStringExtra(GeckoRuntime.EXTRA_EXTRAS_PATH);
        Log.d(LOGTAG, "Dump File: " + dumpFile);
        Log.d(LOGTAG, "Extras File: " + extraFile);
        Log.d(LOGTAG, "Fatal: " + intent.getBooleanExtra(GeckoRuntime.EXTRA_CRASH_FATAL, false));

        boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        if (isCrashReportingEnabled) {
            SystemUtils.postCrashFiles(this, dumpFile, extraFile);

        } else {
            if (mCrashDialog == null) {
                mCrashDialog = new CrashDialogWidget(this, dumpFile, extraFile);
            }
            mCrashDialog.show(UIWidget.REQUEST_FOCUS);
        }
    }

    @Override
    public void onTrimMemory(int level) {

        // Determine which lifecycle or system event was raised.
        switch (level) {

            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                // Curently ignore these levels. They are handled somewhere else.
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                // It looks like these come in all at the same time so just always suspend inactive Sessions.
                Log.d(LOGTAG, "Memory pressure, suspending inactive sessions.");
                SessionStore.get().suspendAllInactiveSessions();
                break;
            default:
                Log.e(LOGTAG, "onTrimMemory unknown level: " + level);
                break;
        }
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
        if (mKeyboard.dispatchKeyEvent(event)) {
            return true;
        }
        final int keyCode = event.getKeyCode();
        if (DeviceType.isOculusBuild()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
                // Eat search key, otherwise it causes a crash on Oculus
                return true;
            }
            int action = event.getAction();
            if (action != KeyEvent.ACTION_DOWN) {
                return super.dispatchKeyEvent(event);
            }
            boolean result;
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    result = callOnAudioManager((AudioManager aManager) -> aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI));
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    result = callOnAudioManager((AudioManager aManager) -> aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI));
                    break;
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    result = callOnAudioManager((AudioManager aManager) -> aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI));
                    break;
                default:
                    return super.dispatchKeyEvent(event);
            }
            return result || super.dispatchKeyEvent(event);

        } else if (DeviceType.isGoogleVR()) {
            boolean result;
            switch (keyCode) {
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
                Runnable aFirstDrawCallback = () -> {
                    if (!widget.isFirstPaintReady()) {
                        widget.setFirstPaintReady(true);
                        updateWidget(widget);
                    }
                };
                widget.setSurfaceTexture(aTexture, aWidth, aHeight, aFirstDrawCallback);
            }
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
                if (aSurface != null && !widget.isFirstPaintReady()) {
                    widget.setFirstPaintReady(true);
                    updateWidget(widget);
                }
            };

            widget.setSurface(aSurface, aWidth, aHeight, aFirstDrawCallback);

            UIWidget view = (UIWidget) widget;
            // Add widget to a virtual display for invalidation
            if (aSurface != null && view.getParent() == null) {
                mWidgetContainer.addView(view, new FrameLayout.LayoutParams(widget.getPlacement().viewWidth(), widget.getPlacement().viewHeight()));
            }  else if (aSurface == null && view.getParent() != null) {
                mWidgetContainer.removeView(view);
            }
            view.setResizing(false);
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
                SessionStore.get().getActiveSession().goBack();

                consumed = true;
            } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                Log.d(LOGTAG, "Go forward!");
                SessionStore.get().getActiveSession().goForward();
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
        mWindows.enterImmersiveMode();
        TelemetryWrapper.startImmersive();
        GleanMetricsService.startImmersive();
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
        mWindows.exitImmersiveMode();
        // Show the window in front of you when you exit immersive mode.
        resetUIYaw();

        TelemetryWrapper.uploadImmersiveToHistogram();
        GleanMetricsService.stopImmersive();
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
                        index -> finish());
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
            final String originalUri = window.getSession().getCurrentUri();
            if (mPoorPerformanceWhiteList.contains(originalUri)) {
                return;
            }
            window.getSession().loadHomePage();
            final String[] buttons = {getString(R.string.ok_button), getString(R.string.performance_unblock_page)};
            window.showConfirmPrompt(getString(R.string.performance_title), getString(R.string.performance_message), buttons, index -> {
                if (index == PromptDialogWidget.NEGATIVE) {
                    mPoorPerformanceWhiteList.add(originalUri);
                    window.getSession().loadUri(originalUri);
                }
            });
        });
    }

    private SurfaceTexture createSurfaceTexture() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0]);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(LOGTAG, "OpenGL Error creating SurfaceTexture: " + error);
        }

        return new SurfaceTexture(ids[0]);
    }

    void createOffscreenDisplay() {
        final SurfaceTexture texture = createSurfaceTexture();
        runOnUiThread(() -> {
            mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 16, 16);
            mOffscreenDisplay.setContentView(mWidgetContainer);
        });
    }

    void createCaptureSurface() {
        final SurfaceTexture texture = createSurfaceTexture();
        runOnUiThread(() -> {
            SettingsStore settings = SettingsStore.getInstance(this);
            texture.setDefaultBufferSize(settings.getWindowWidth(), settings.getWindowHeight());
            BitmapCache.getInstance(this).setCaptureSurface(texture);
        });
    }

    @Override
    public int newWidgetHandle() {
        return mWidgetHandleIndex++;
    }


    public void addWidgets(final Iterable<? extends Widget> aWidgets) {
        for (Widget widget : aWidgets) {
            addWidget(widget);
        }
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
        final int handle = aWidget.getHandle();
        final WidgetPlacement clone = aWidget.getPlacement().clone();
        queueRunnable(() -> addWidgetNative(handle, clone));
        updateActiveDialog(aWidget);
    }

    @Override
    public void updateWidget(final Widget aWidget) {
        final int handle = aWidget.getHandle();
        final WidgetPlacement clone = aWidget.getPlacement().clone();
        queueRunnable(() -> updateWidgetNative(handle, clone));

        final int textureWidth = aWidget.getPlacement().textureWidth();
        final int textureHeight = aWidget.getPlacement().textureHeight();
        final int viewWidth = aWidget.getPlacement().viewWidth();
        final int viewHeight = aWidget.getPlacement().viewHeight();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)((View)aWidget).getLayoutParams();
        if (params == null) {
            // Widget not added yet
            return;
        }
        UIWidget view = (UIWidget)aWidget;

        if (params.width != viewWidth || params.height != viewHeight) {
            params.width = viewWidth;
            params.height = viewHeight;
            if (view.isLayer()) {
                // Reuse last frame and do not render while resizing surface with Layers enabled.
                // Fixes resizing glitches.
                view.setResizing(true);
            }
            ((View)aWidget).setLayoutParams(params);
            aWidget.resizeSurface(textureWidth, textureHeight);
        }

        boolean visible = aWidget.getPlacement().visible;

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
        aWidget.setFirstPaintReady(false);
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
    public void startWidgetResize(final Widget aWidget, float aMaxWidth, float aMaxHeight, float minWidth, float minHeight) {
        mWindows.enterResizeMode();
        queueRunnable(() -> startWidgetResizeNative(aWidget.getHandle(), aMaxWidth, aMaxHeight, minWidth, minHeight));
    }

    @Override
    public void finishWidgetResize(final Widget aWidget) {
        mWindows.exitResizeMode();
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
    public void addConnectivityListener(Delegate aListener) {
        if (!mConnectivityListeners.contains(aListener)) {
            mConnectivityListeners.add(aListener);
        }
    }

    @Override
    public void removeConnectivityListener(Delegate aListener) {
        mConnectivityListeners.remove(aListener);
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
        if (mTray != null && !mTray.isReleased()) {
            mTray.setTrayVisible(visible);
        }
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
        Session session = SessionStore.get().getActiveSession();
        if (uri != null && !uri.isEmpty()) {
            mPermissionDelegate.onAppPermissionRequest(session.getGeckoSession(), uri, permission, aCallback);
        } else {
            mPermissionDelegate.onAndroidPermissionsRequest(session.getGeckoSession(), new String[]{permission}, aCallback);
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
        mCurrentCylinderDensity = aDensity;
        queueRunnable(() -> setCylinderDensityNative(aDensity));
        if (mWindows != null) {
            mWindows.updateCurvedMode(false);
        }
    }

    @Override
    public float getCylinderDensity() {
        return mCurrentCylinderDensity;
    }

    @Override
    public boolean canOpenNewWindow() {
        return mWindows.canOpenNewWindow();
    }

    @Override
    public void openNewWindow(String uri) {
        WindowWidget newWindow = mWindows.addWindow();
        if (newWindow != null) {
            newWindow.getSession().loadUri(uri);
        }
    }

    @Override
    public void openNewTab(@NonNull String uri) {
        mWindows.addBackgroundTab(mWindows.getFocusedWindow(), uri);
    }

    @Override
    public void openNewTabForeground(@NonNull String uri) {
        mWindows.addTab(mWindows.getFocusedWindow(), uri);
    }

    @Override
    public WindowWidget getFocusedWindow() {
        return mWindows.getFocusedWindow();
    }

    @Override
    public TrayWidget getTray() {
        return mTray;
    }

    @Override
    public void saveState() {
        mWindows.saveState();
    }

    private native void addWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateVisibleWidgetsNative();
    private native void removeWidgetNative(int aHandle);
    private native void startWidgetResizeNative(int aHandle, float maxWidth, float maxHeight, float minWidth, float minHeight);
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
