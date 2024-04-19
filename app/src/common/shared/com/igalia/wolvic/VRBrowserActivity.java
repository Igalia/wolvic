/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import static com.igalia.wolvic.ui.widgets.UIWidget.REMOVE_WIDGET;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.os.BatteryManager;
import android.os.Build;
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
import androidx.fragment.app.FragmentController;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.audio.AndroidMediaPlayer;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.PermissionDelegate;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.EngineProvider;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.crashreporting.CrashReporterService;
import com.igalia.wolvic.crashreporting.GlobalExceptionHandler;
import com.igalia.wolvic.geolocation.GeolocationWrapper;
import com.igalia.wolvic.input.MotionEventGenerator;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.OffscreenDisplay;
import com.igalia.wolvic.ui.adapters.Language;
import com.igalia.wolvic.ui.widgets.AppServicesProvider;
import com.igalia.wolvic.ui.widgets.KeyboardWidget;
import com.igalia.wolvic.ui.widgets.NavigationBarWidget;
import com.igalia.wolvic.ui.widgets.RootWidget;
import com.igalia.wolvic.ui.widgets.TrayWidget;
import com.igalia.wolvic.ui.widgets.UISurfaceTextureRenderer;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WebXRInterstitialWidget;
import com.igalia.wolvic.ui.widgets.Widget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.Windows;
import com.igalia.wolvic.ui.widgets.dialogs.CrashDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.DeprecatedVersionDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.LegalDocumentDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.PromptDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SendTabDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.WhatsNewWidget;
import com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate,
        ComponentCallbacks2, LifecycleOwner, ViewModelStoreOwner, SharedPreferences.OnSharedPreferenceChangeListener, PlatformActivityPlugin.PlatformActivityPluginListener {

    public static final String CUSTOM_URI_SCHEME = "wolvic";
    public static final String CUSTOM_URI_HOST = "com.igalia.wolvic";
    public static final String EXTRA_INTENT_CMD = "intent_cmd";
    public static final String JSON_OVR_SOCIAL_LAUNCH = "ovr_social_launch";
    public static final String JSON_DEEPLINK_MESSAGE = "deeplink_message";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_OPEN_IN_BACKGROUND = "background";
    public static final String EXTRA_CREATE_NEW_WINDOW = "create_new_window";
    public static final String EXTRA_HIDE_WEBXR_INTERSTITIAL = "hide_webxr_interstitial";
    public static final String EXTRA_HIDE_WHATS_NEW = "hide_whats_new";
    public static final String EXTRA_KIOSK = "kiosk";
    private static final long BATTERY_UPDATE_INTERVAL = 60 * 1_000_000_000L; // 60 seconds

    private BroadcastReceiver mCrashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if((intent.getAction() != null) && intent.getAction().equals(CrashReporterService.CRASH_ACTION)) {
                Intent crashIntent;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                    crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG, Intent.class);
                } else {
                    crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG);
                }
                handleContentCrashIntent(crashIntent);
            }
        }
    };

    private LifecycleRegistry mLifeCycle;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        // Ensure that mLifeCycle is initialized, because this method
        // may be called early by a superclass at construction time.
        return getLifecycleRegistry();
    }

    private LifecycleRegistry getLifecycleRegistry() {
        if (mLifeCycle == null) {
            mLifeCycle = new LifecycleRegistry(this);
        }
        return mLifeCycle;
    }

    private final ViewModelStore mViewModelStore;

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return mViewModelStore;
    }

    public VRBrowserActivity() {
        getLifecycleRegistry().setCurrentState(Lifecycle.State.INITIALIZED);

        mViewModelStore = new ViewModelStore();
    }

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
    static final int UPDATE_NATIVE_WIDGETS_DELAY = 50; // milliseconds

    // Passthrough was enabled on Pico version 5.7.1, via XR_FB_passthrough extension
    static final String kPicoVersionPassthroughUpdate = "5.7.1";

    static final String LOGTAG = SystemUtils.createLogtag(VRBrowserActivity.class);
    ConcurrentHashMap<Integer, Widget> mWidgets;
    private int mWidgetHandleIndex = 1;
    AudioEngine mAudioEngine;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler(Looper.getMainLooper());
    Runnable mAudioUpdateRunnable;
    Windows mWindows;
    RootWidget mRootWidget;
    KeyboardWidget mKeyboard;
    NavigationBarWidget mNavigationBar;
    CrashDialogWidget mCrashDialog;
    TrayWidget mTray;
    WhatsNewWidget mWhatsNewWidget = null;
    WebXRInterstitialWidget mWebXRInterstitial;
    PermissionDelegate mPermissionDelegate;
    LinkedList<UpdateListener> mWidgetUpdateListeners;
    LinkedList<PermissionListener> mPermissionListeners;
    LinkedList<FocusChangeListener> mFocusChangeListeners;
    LinkedList<WorldClickListener> mWorldClickListeners;
    LinkedList<WebXRListener> mWebXRListeners;
    LinkedList<Runnable> mBackHandlers;
    private boolean mIsPresentingImmersive = false;
    private Thread mUiThread;
    private LinkedList<Pair<Object, Float>> mBrightnessQueue;
    private Pair<Object, Float> mCurrentBrightness;
    private SearchEngineWrapper mSearchEngineWrapper;
    private SettingsStore mSettings;
    private SharedPreferences mPrefs;
    private boolean mConnectionAvailable = true;
    private AudioManager mAudioManager;
    private Widget mActiveDialog;
    private Set<String> mPoorPerformanceAllowList;
    private float mCurrentCylinderDensity = 0;
    private boolean mHideWebXRIntersitial = false;
    private FragmentController mFragmentController;
    private LinkedHashMap<Integer, WidgetPlacement> mPendingNativeWidgetUpdates = new LinkedHashMap<>();
    private ScheduledThreadPoolExecutor mPendingNativeWidgetUpdatesExecutor = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> mNativeWidgetUpdatesTask = null;
    private Media mPrevActiveMedia = null;
    private boolean mIsPassthroughEnabled = false;
    private long mLastBatteryUpdate = System.nanoTime();
    private int mLastBatteryLevel = -1;
    private PlatformActivityPlugin mPlatformPlugin;
    private int mLastMotionEventWidgetHandle;

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
            triggerHapticFeedback();
            for (FocusChangeListener listener: mFocusChangeListeners) {
                listener.onGlobalFocusChanged(oldFocus, newFocus);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        Context newContext = LocaleUtils.init(base);
        super.attachBaseContext(newContext);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFragmentController = FragmentController.createController(new FragmentControllerCallbacks(this, new Handler(Looper.getMainLooper()), 0));
        mFragmentController.attachHost(null);
        mFragmentController.dispatchActivityCreated();

        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
        ((VRBrowserApplication)getApplication()).onActivityCreate(this);
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

        if (DeviceType.isOculusBuild()) {
            workaroundGeckoSigAction();
        }
        mUiThread = Thread.currentThread();

        BitmapCache.getInstance(this).onCreate();

        WRuntime runtime = EngineProvider.INSTANCE.getOrCreateRuntime(this);
        runtime.appendAppNotesToCrashReport("Wolvic " + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "-" + BuildConfig.FLAVOR + "-" + BuildConfig.BUILD_TYPE + " (" + BuildConfig.GIT_HASH + ")");

        // Create broadcast receiver for getting crash messages from crash process
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CrashReporterService.CRASH_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(mCrashReceiver, intentFilter, BuildConfig.APPLICATION_ID + "." + getString(R.string.app_permission_name), null, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mCrashReceiver, intentFilter, BuildConfig.APPLICATION_ID + "." + getString(R.string.app_permission_name), null);
        }

        mLastGesture = NoGesture;
        mWidgetUpdateListeners = new LinkedList<>();
        mPermissionListeners = new LinkedList<>();
        mFocusChangeListeners = new LinkedList<>();
        mWorldClickListeners = new LinkedList<>();
        mWebXRListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();
        mBrightnessQueue = new LinkedList<>();
        mCurrentBrightness = Pair.create(null, 1.0f);
        mWidgets = new ConcurrentHashMap<>();

        super.onCreate(savedInstanceState);

        mWidgetContainer = new FrameLayout(this);
        runtime.setContainerView(mWidgetContainer);

        mPermissionDelegate = new PermissionDelegate(this, this);

        mAudioEngine = new AudioEngine(this, new AndroidMediaPlayer(getBaseContext()));
        mAudioEngine.setEnabled(SettingsStore.getInstance(this).isAudioEnabled());
        mAudioEngine.preloadAsync(() -> {
            Log.i(LOGTAG, "AudioEngine sounds preloaded!");
            // mAudioEngine.playSound(AudioEngine.Sound.AMBIENT, true);
        });
        mAudioUpdateRunnable = () -> mAudioEngine.update();

        mSettings = SettingsStore.getInstance(this);
        mSettings.initModel(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        queueRunnable(() -> {
            createOffscreenDisplay();
            createCaptureSurface();
        });
        final String tempPath = getCacheDir().getAbsolutePath();
        queueRunnable(() -> setTemporaryFilePath(tempPath));

        initializeWidgets();

        loadFromIntent(getIntent());

        // Setup the search engine
        mSearchEngineWrapper = SearchEngineWrapper.get(this);
        mSearchEngineWrapper.registerForUpdates();

        getServicesProvider().getConnectivityReceiver().addListener(mConnectivityDelegate);

        GeolocationWrapper.INSTANCE.update(this);

        initializeSpeechRecognizer();

        mPoorPerformanceAllowList = new HashSet<>();
        checkForCrash();

        setHeadLockEnabled(mSettings.isHeadLockEnabled());

        // Show the launch dialogs, if needed.
        if (!showTermsServiceDialogIfNeeded()) {
            if (!showPrivacyDialogIfNeeded()) {
                showWhatsNewDialogIfNeeded();
            }
        }

        // Show the deprecated version dialog, if needed.
        showDeprecatedVersionDialogIfNeeded();

        getLifecycleRegistry().setCurrentState(Lifecycle.State.CREATED);
    }

    protected void initializeWidgets() {
        UISurfaceTextureRenderer.setRenderActive(true);

        // Empty widget just for handling focus on empty space
        mRootWidget = new RootWidget(this);
        mRootWidget.setClickCallback(() -> {
            for (WorldClickListener listener: mWorldClickListeners) {
                listener.onWorldClick();
            }
        });

        // Create Browser navigation widget
        mNavigationBar = new NavigationBarWidget(this);

        // Create keyboard widget
        mKeyboard = new KeyboardWidget(this);

        // Create the WebXR interstitial
        mWebXRInterstitial = new WebXRInterstitialWidget(this);

        // Windows
        mWindows = new Windows(this);
        mWindows.setDelegate(new Windows.Delegate() {
            @Override
            public void onFocusedWindowChanged(@NonNull WindowWidget aFocusedWindow, @Nullable WindowWidget aPrevFocusedWindow) {
                attachToWindow(aFocusedWindow, aPrevFocusedWindow);
                mTray.setAddWindowVisible(mWindows.canOpenNewWindow());
                mNavigationBar.hideAllNotifications();
            }
            @Override
            public void onWindowBorderChanged(@NonNull WindowWidget aChangeWindow) {
                mKeyboard.proxifyLayerIfNeeded(mWindows.getCurrentWindows());
            }

            @Override
            public void onWindowsMoved() {
                mNavigationBar.hideAllNotifications();
                updateWidget(mTray);
            }

            @Override
            public void onWindowClosed() {
                mTray.setAddWindowVisible(mWindows.canOpenNewWindow());
                mNavigationBar.hideAllNotifications();
                updateWidget(mTray);
            }

            @Override
            public void onWindowVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {
                @CPULevelFlags int cpuLevel = mWindows.isVideoAvailable() ? WidgetManagerDelegate.CPU_LEVEL_HIGH :
                        WidgetManagerDelegate.CPU_LEVEL_NORMAL;

                queueRunnable(() -> setCPULevelNative(cpuLevel));

                if (mPlatformPlugin != null) {
                    mPlatformPlugin.onVideoAvailabilityChange();
                }
            }
        });

        // Create the tray
        mTray = new TrayWidget(this);
        mTray.addListeners(mWindows);
        mTray.setAddWindowVisible(mWindows.canOpenNewWindow());
        attachToWindow(mWindows.getFocusedWindow(), null);

        addWidgets(Arrays.asList(mRootWidget, mNavigationBar, mKeyboard, mTray, mWebXRInterstitial));

        // Create the platform plugin after widgets are created to be extra safe.
        mPlatformPlugin = createPlatformPlugin(this);
        if (mPlatformPlugin != null)
            mPlatformPlugin.registerListener(this);

        mWindows.restoreSessions();
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

    WRuntime.CrashReportIntent getCrashReportIntent() {
        return EngineProvider.INSTANCE.getOrCreateRuntime(this).getCrashReportIntent();
    }

    private void initializeSpeechRecognizer() {
        try {
            String speechService = SettingsStore.getInstance(this).getVoiceSearchService();
            SpeechRecognizer speechRecognizer = SpeechServices.getInstance(this, speechService);
            ((VRBrowserApplication) getApplication()).setSpeechRecognizer(speechRecognizer);
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception creating the speech recognizer: " + e);
            ((VRBrowserApplication) getApplication()).setSpeechRecognizer(null);
        }
    }

    // A dialog to tell App Lab users to download Wolvic from the Meta store.
    // Returns true if the dialog was shown, false otherwise.
    private void showDeprecatedVersionDialogIfNeeded() {
        // Only show this dialog to users running the App Lab version of Wolvic.
        if (!DeviceType.getStoreType().equals(DeviceType.StoreType.META_APP_LAB))
            return;

        DeprecatedVersionDialogWidget deprecatedVersionDialog = new DeprecatedVersionDialogWidget(this);

        deprecatedVersionDialog.setDelegate(response -> {
            switch (response) {
                case DeprecatedVersionDialogWidget.OPEN_STORE:
                    Intent storeIntent = getStoreIntent();
                    if (storeIntent != null) {
                        Log.w(LOGTAG, "Start app store activity.");
                        startActivity(storeIntent);
                    } else {
                        Log.e(LOGTAG, "Unsupported: can not start app store activity.");
                    }
                    break;

                case DeprecatedVersionDialogWidget.SHOW_INFO:
                    mWindows.openNewTabAfterRestore(getString(R.string.deprecated_version_dialog_info_url), Windows.OPEN_IN_FOREGROUND);
                    break;

                case DeprecatedVersionDialogWidget.DISMISS:
                    // no action
            }
        });
        deprecatedVersionDialog.attachToWindow(mWindows.getFocusedWindow());
        deprecatedVersionDialog.show(UIWidget.REQUEST_FOCUS);
    }

    // Returns true if the dialog was shown, false otherwise.
    private boolean showTermsServiceDialogIfNeeded() {
        if (SettingsStore.getInstance(this).isTermsServiceAccepted()) {
            return false;
        }

        LegalDocumentDialogWidget termsServiceDialog =
                new LegalDocumentDialogWidget(this, LegalDocumentDialogWidget.LegalDocument.TERMS_OF_SERVICE);

        termsServiceDialog.setDelegate(response -> {
            if (response) {
                SettingsStore.getInstance(this).setTermsServiceAccepted(true);
                if (!showPrivacyDialogIfNeeded()) {
                    showWhatsNewDialogIfNeeded();
                }
            } else {
                // TODO ask for confirmation ("are you really sure that you want to close Wolvic?")
                Log.w(LOGTAG, "The user rejected the privacy policy, closing the app.");
                finish();
            }
        });
        termsServiceDialog.attachToWindow(mWindows.getFocusedWindow());
        termsServiceDialog.show(UIWidget.REQUEST_FOCUS);
        return true;
    }

    // Returns true if the dialog was shown, false otherwise.
    private boolean showPrivacyDialogIfNeeded() {
        if (SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
            return false;
        }

        LegalDocumentDialogWidget privacyPolicyDialog
                = new LegalDocumentDialogWidget(this, LegalDocumentDialogWidget.LegalDocument.PRIVACY_POLICY);
        privacyPolicyDialog.setDelegate(response -> {
            if (response) {
                SettingsStore.getInstance(this).setPrivacyPolicyAccepted(true);
                showWhatsNewDialogIfNeeded();
            } else {
                // TODO ask for confirmation ("are you really sure that you want to close Wolvic?")
                Log.w(LOGTAG, "The user rejected the privacy policy, closing the app.");
                finish();
            }
        });
        privacyPolicyDialog.attachToWindow(mWindows.getFocusedWindow());
        privacyPolicyDialog.show(UIWidget.REQUEST_FOCUS);
        return true;
    }

    private void showWhatsNewDialogIfNeeded() {
        if (SettingsStore.getInstance(this).isWhatsNewDisplayed() || mWindows.getFocusedWindow().isKioskMode()) {
            return;
        }

        mWhatsNewWidget = new WhatsNewWidget(this);
        mWhatsNewWidget.setLoginOrigin(Accounts.LoginOrigin.NONE);
        mWhatsNewWidget.getPlacement().parentHandle = mWindows.getFocusedWindow().getHandle();
        mWhatsNewWidget.show(UIWidget.REQUEST_FOCUS);
    }

    @Override
    protected void onStart() {
        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
        super.onStart();
        mFragmentController.dispatchStart();
        getLifecycleRegistry().setCurrentState(Lifecycle.State.STARTED);
        if (mTray == null) {
            Log.e(LOGTAG, "Failed to start Tray clock");
        } else {
            mTray.start(this);
        }
    }

    @Override
    protected void onStop() {
        SettingsStore.getInstance(getBaseContext()).setPid(0);
        super.onStop();
        mFragmentController.dispatchStop();
        TelemetryService.sessionStop();
        if (mTray != null) {
            mTray.stop(this);
        }
    }

    public void flushBackHandlers() {
        int backCount = mBackHandlers.size();
        while (backCount > 0) {
            mBackHandlers.getLast().run();
            int newBackCount = mBackHandlers.size();
            if (newBackCount == backCount) {
                Log.e(LOGTAG, "Back counter is not decreasing,");
                break;
            }
            backCount = newBackCount;
        }
    }

    @Override
    protected void onPause() {
        if (mIsPresentingImmersive) {
            // This needs to be sync to ensure that WebVR is correctly paused.
            // Also prevents a deadlock in onDestroy when the BrowserWidget is released.
            exitImmersiveSync();
        }

        mAudioEngine.pauseEngine();
        mFragmentController.dispatchPause();

        mWindows.onPause();

        for (Widget widget: mWidgets.values()) {
            widget.onPause();
        }
        // Reset so the dialog will show again on resume.
        mConnectionAvailable = true;
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onPause();
        }
        mWidgetContainer.getViewTreeObserver().removeOnGlobalFocusChangeListener(globalFocusListener);
        super.onPause();
        UISurfaceTextureRenderer.setRenderActive(false);
    }

    @Override
    protected void onResume() {
        UISurfaceTextureRenderer.setRenderActive(true);
        MotionEventGenerator.clearDevices();
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusListener);
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onResume();
        }

        mFragmentController.dispatchResume();
        mWindows.onResume();

        mAudioEngine.resumeEngine();
        for (Widget widget: mWidgets.values()) {
            widget.onResume();
        }

        // If we're signed-in, poll for any new device events (e.g. received tabs) on activity resume.
        // There's no push support right now, so this helps with the perception of speedy tab delivery.
        ((VRBrowserApplication)getApplicationContext()).getAccounts().refreshDevicesAsync();
        ((VRBrowserApplication)getApplicationContext()).getAccounts().pollForEventsAsync();

        super.onResume();
        ((VRBrowserApplication)getApplication()).setCurrentActivity(this);
        getLifecycleRegistry().setCurrentState(Lifecycle.State.RESUMED);
    }

    @Override
    protected void onDestroy() {
        ((VRBrowserApplication)getApplication()).onActivityDestroy();
        SettingsStore.getInstance(getBaseContext()).setPid(0);
        // Unregister the crash service broadcast receiver
        unregisterReceiver(mCrashReceiver);
        mSearchEngineWrapper.unregisterForUpdates();
        if (mPlatformPlugin != null)
            mPlatformPlugin.unregisterListener(this);

        mFragmentController.dispatchDestroy();

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

        mTray.removeListeners(mWindows);

        // Remove all widget listeners
        mWindows.onDestroy();

        BitmapCache.getInstance(this).onDestroy();

        SessionStore.get().onDestroy();

        getServicesProvider().getConnectivityReceiver().removeListener(mConnectivityDelegate);

        mPrefs.unregisterOnSharedPreferenceChangeListener(this);

        super.onDestroy();
        getLifecycleRegistry().setCurrentState(Lifecycle.State.DESTROYED);
        mViewModelStore.clear();
        // Always exit to work around https://github.com/MozillaReality/FirefoxReality/issues/3363
        finish();
        System.exit(0);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.d(LOGTAG,"VRBrowserActivity onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);

        if (getCrashReportIntent().action_crashed.equals(intent.getAction())) {
            Log.e(LOGTAG, "Restarted after a crash");
        } else {
            loadFromIntent(intent);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Language language = LocaleUtils.getDisplayLanguage(this);
        newConfig.setLocale(language.getLocale());
        // TODO: Deprecated updateConfiguration(Configuration,DisplayMetrics),
        //  see https://github.com/Igalia/wolvic/issues/797
        getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());

        LocaleUtils.update(this, language);

        SessionStore.get().onConfigurationChanged(newConfig);
        mWidgets.forEach((i, widget) -> widget.onConfigurationChanged(newConfig));
        SendTabDialogWidget.getInstance(this).onConfigurationChanged(newConfig);

        SearchEngineWrapper s = SearchEngineWrapper.get(this);
        s.setupPreferredSearchEngine();
        s.setCurrentSearchEngine(null);

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(R.string.settings_key_voice_search_service))) {
                initializeSpeechRecognizer();
            } else if (key.equals(getString(R.string.settings_key_head_lock))) {
                boolean isHeadLockEnabled = SettingsStore.getInstance(this).isHeadLockEnabled();
                setHeadLockEnabled(isHeadLockEnabled);
                if (!isHeadLockEnabled)
                    recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL);
            }
    }

    void loadFromIntent(final Intent intent) {
        if (getCrashReportIntent().action_crashed.equals(intent.getAction())) {
            Log.e(LOGTAG,"Loading from crash Intent");
        }

        // FIXME https://github.com/MozillaReality/FirefoxReality/issues/3066
        if (DeviceType.isOculusBuild()) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String cmd = bundle.getString(EXTRA_INTENT_CMD);
                if ((cmd != null) && (cmd.length() > 0)) {
                    try {
                        JSONObject object = new JSONObject(cmd);
                        JSONObject launch = object.getJSONObject(JSON_OVR_SOCIAL_LAUNCH);
                        String msg = launch.getString(JSON_DEEPLINK_MESSAGE);
                        Log.d(LOGTAG, "deeplink message: " + msg);
                        onAppLink(msg);
                        return;
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "Error parsing deeplink JSON: " + ex.toString());
                    }
                }
            }
        }

        boolean openInWindow = false;
        boolean openInBackground = false;
        boolean openInKioskMode = false;

        Uri dataUri = intent.getData();
        Uri targetUri = null;
        Bundle extras;

        if (dataUri != null && dataUri.getScheme().equals(CUSTOM_URI_SCHEME) && dataUri.getHost().equals(CUSTOM_URI_HOST)) {
            Log.d(LOGTAG, "Parsing custom URI from intent: " + dataUri);

            extras = new Bundle();
            Set<String> keys = dataUri.getQueryParameterNames();
            for (String key : keys) {
                String queryParameter = dataUri.getQueryParameter(key);
                if (queryParameter == null)
                    continue;

                // all supported parameters are booleans, except "url"
                String lowerCaseKey = key.toLowerCase();
                if (lowerCaseKey.equals(EXTRA_URL))
                    extras.putString(lowerCaseKey, queryParameter);
                else
                    extras.putBoolean(lowerCaseKey, Boolean.parseBoolean(queryParameter));
            }
        } else {
            targetUri = intent.getData();
            extras = intent.getExtras();
        }

        if (extras != null) {
            // targetUri will be null here if the data URI is empty or contains a custom URI;
            // in that case, we will use the "url" parameter if it exists
            if (targetUri == null && extras.containsKey(EXTRA_URL)) {
                targetUri = Uri.parse(extras.getString(EXTRA_URL));
            }
            // SEND Actions received WebBrowser share dialogs
            if (targetUri == null && extras.containsKey(Intent.EXTRA_TEXT)) {
                String text = extras.getString(Intent.EXTRA_TEXT, "");
                int i = text.indexOf("https://");
                if (i < 0) {
                    i = text.indexOf("http://");
                }
                if (i >= 0) {
                    targetUri = Uri.parse(text.substring(i));
                }
            }

            // Open the tab in background/foreground, if there is no URL provided we just open the homepage
            if (extras.containsKey(EXTRA_OPEN_IN_BACKGROUND)) {
                openInBackground = extras.getBoolean(EXTRA_OPEN_IN_BACKGROUND, false);
                if (targetUri == null) {
                    targetUri = Uri.parse(SettingsStore.getInstance(this).getHomepage());
                }
            }

            // Open the provided URL in a new window, if there is no URL provided we just open the homepage
            if (extras.containsKey(EXTRA_CREATE_NEW_WINDOW)) {
                openInWindow = extras.getBoolean(EXTRA_CREATE_NEW_WINDOW, false);
                if (targetUri == null) {
                    targetUri = Uri.parse(SettingsStore.getInstance(this).getHomepage());
                }
            }

            if (extras.containsKey(EXTRA_HIDE_WEBXR_INTERSTITIAL)) {
                mHideWebXRIntersitial = extras.getBoolean(EXTRA_HIDE_WEBXR_INTERSTITIAL, false);
                if (mHideWebXRIntersitial) {
                    setWebXRIntersitialState(WEBXR_INTERSTITIAL_HIDDEN);
                }
            }

            if (extras.containsKey(EXTRA_HIDE_WHATS_NEW)) {
                boolean hideWhatsNew = extras.getBoolean(EXTRA_HIDE_WHATS_NEW, false);
                if (hideWhatsNew && mWhatsNewWidget != null) {
                    mWhatsNewWidget.hide(REMOVE_WIDGET);
                }
            }

            openInKioskMode = extras.getBoolean(EXTRA_KIOSK, false);
        }

        // If there is a target URI we open it
        if (targetUri != null) {
            Log.d(LOGTAG, "Loading URI from intent: " + targetUri);

            int location = Windows.OPEN_IN_FOREGROUND;

            if (openInKioskMode) {
                // FIXME this might not work as expected if the app was already running
                mWindows.openInKioskMode(targetUri.toString());
            } else {
                if (openInWindow) {
                    location = Windows.OPEN_IN_NEW_WINDOW;
                } else if (openInBackground) {
                    location = Windows.OPEN_IN_BACKGROUND;
                }
                if (location == Windows.OPEN_IN_FOREGROUND) {
                    mWindows.findTabAndSelect(targetUri.toString());
                } else {
                    mWindows.openNewTabAfterRestore(targetUri.toString(), location);
                }
            }
        } else if (mWindows.getFocusedWindow().isCurrentUriBlank()) {
            mWindows.getFocusedWindow().loadHome();
        }
    }

    private ConnectivityReceiver.Delegate mConnectivityDelegate = connected -> {
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
        final String dumpFile = intent.getStringExtra(getCrashReportIntent().extra_minidump_path);
        final String extraFile = intent.getStringExtra(getCrashReportIntent().extra_extras_path);
        Log.d(LOGTAG, "Dump File: " + dumpFile);
        Log.d(LOGTAG, "Extras File: " + extraFile);
        Log.d(LOGTAG, "Fatal: " + intent.getBooleanExtra(getCrashReportIntent().extra_crash_fatal, false));

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

    FrameLayout getWidgetContainer() {
        return mWidgetContainer;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

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

    private void showAppExitDialog() {
        mWindows.getFocusedWindow().showConfirmPrompt(
            getString(R.string.app_name),
            getString(R.string.exit_confirm_dialog_body, getString(R.string.app_name)),
                new String[]{
                        getString(R.string.exit_confirm_dialog_button_cancel),
                        getString(R.string.exit_confirm_dialog_button_quit),
                }, (index, isChecked) -> {
                    if (index == PromptDialogWidget.POSITIVE) {
                        VRBrowserActivity.super.onBackPressed();
                        finishAndRemoveTask();
                    }
                });
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        if (mIsPresentingImmersive) {
            queueRunnable(this::exitImmersiveNative);
            return;
        }
        if (mBackHandlers.size() > 0) {
            mBackHandlers.getLast().run();
            return;
        }
        if (!mWindows.handleBack()) {
            showAppExitDialog();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isNotSpecialKey(event) && mKeyboard.dispatchKeyEvent(event)) {
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

        }
        return super.dispatchKeyEvent(event);
    }

    final Object mWaitLock = new Object();

    final Runnable mExitImmersive = new Runnable() {
        @Override
        public void run() {
            exitImmersiveNative();
            synchronized(mWaitLock) {
                mWaitLock.notifyAll();
            }
        }
    };

    private void exitImmersiveSync() {
        synchronized (mWaitLock) {
            queueRunnable(mExitImmersive);
            try {
                mWaitLock.wait();
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

            FinalizerRunnable firstDrawCallback = new FinalizerRunnable(() -> {
                if (aNativeCallback != 0) {
                    queueRunnable(() -> runCallbackNative(aNativeCallback));
                }
                if (aSurface != null && !widget.isFirstPaintReady()) {
                    widget.setFirstPaintReady(true);
                    updateWidget(widget);
                }
            },
            () -> {
                if (aNativeCallback != 0) {
                    queueRunnable(() -> deleteCallbackNative(aNativeCallback));
                }
            });

            widget.setSurface(aSurface, aWidth, aHeight, firstDrawCallback);

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
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aFocused, final boolean aPressed, final float aX, final float aY) {
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);

            if (!isWidgetInputEnabled(widget)) {
                widget = null; // Fallback to mRootWidget in order to allow world clicks to dismiss UI.
            }
            mLastMotionEventWidgetHandle = widget != null ? widget.getHandle() : 0;

            float scale = widget != null ? widget.getPlacement().textureScale : SettingsStore.getInstance(this).getDisplayDpi() / 100.0f;
            // We shouldn't divide the scale factor when we pass the motion event to the web engine
            if (widget instanceof WindowWidget) {
                WindowWidget windowWidget = (WindowWidget) widget;
                if (!windowWidget.isLibraryVisible()) {
                    scale = 1.0f;
                }
            }
            final float x = aX / scale;
            final float y = aY / scale;

            if (widget == null) {
                MotionEventGenerator.dispatch(this, mRootWidget, aDevice, aFocused, aPressed, x, y);

            } else if (widget.getBorderWidth() > 0) {
                final int border = widget.getBorderWidth();
                MotionEventGenerator.dispatch(this, widget, aDevice, aFocused, aPressed, x - border, y - border);

            } else {
                MotionEventGenerator.dispatch(this, widget, aDevice, aFocused, aPressed, x, y);
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
            if (widget == null) {
                if (getNavigationBar().isInVRVideo()) {
                    widget = getNavigationBar().getMediaControlsWidget();
                } else {
                    Log.e(LOGTAG, "Failed to find widget for scroll event: " + aHandle);
                    return;
                }
            }
            float scrollDirection = mSettings.getScrollDirection() == 0 ? 1.0f : -1.0f;
            MotionEventGenerator.dispatchScroll(widget, aDevice, true,aX * scrollDirection, aY * scrollDirection);
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

    @SuppressWarnings({"UnusedDeclaration"})
    @Keep
    void handleAppExit() {
        runOnUiThread(() -> {
            showAppExitDialog();
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
        runOnUiThread(() -> mWindows.getFocusedWindow().handleResizeEvent(aWorldWidth, aWorldHeight));
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
        EngineProvider.INSTANCE.getOrCreateRuntime(this).setExternalVRContext(aContext);
    }

    final Object mCompositorLock = new Object();

    class PauseCompositorRunnable implements Runnable {
        public boolean done;
        @Override
        public void run() {
            synchronized (mCompositorLock) {
                Log.d(LOGTAG, "About to pause Compositor");
                mWindows.pauseCompositor();
                Log.d(LOGTAG, "Compositor Paused");
                done = true;
                mCompositorLock.notify();
            }
        }
    }

    @Keep
    @SuppressWarnings("unused")
    void onEnterWebXR() {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = true;
        runOnUiThread(() -> {
            mWindows.enterImmersiveMode();
            for (WebXRListener listener: mWebXRListeners) {
                listener.onEnterWebXR();
            }
        });
        TelemetryService.startImmersive();

        PauseCompositorRunnable runnable = new PauseCompositorRunnable();

        synchronized (mCompositorLock) {
            runOnUiThread(runnable);
            while (!runnable.done) {
                try {
                    mCompositorLock.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "Waiting for compositor pause interrupted");
                }
            }
        }
    }

    @Keep
    @SuppressWarnings("unused")
    void onExitWebXR(long aCallback) {
        if (Thread.currentThread() == mUiThread) {
            return;
        }
        mIsPresentingImmersive = false;
        runOnUiThread(() -> {
            mWindows.exitImmersiveMode();
            for (WebXRListener listener: mWebXRListeners) {
                listener.onExitWebXR();
            }
        });

        // Show the window in front of you when you exit immersive mode.
        recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL);

        TelemetryService.stopImmersive();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!mWindows.isPaused()) {
                Log.d(LOGTAG, "Compositor resume begin");
                mWindows.resumeCompositor();
                if (aCallback != 0) {
                    queueRunnable(() -> runCallbackNative(aCallback));
                }
                Log.d(LOGTAG, "Compositor resume end");
            }
        }, 20);
    }
    @Keep
    @SuppressWarnings("unused")
    void onDismissWebXRInterstitial() {
        runOnUiThread(() -> {
            for (WebXRListener listener: mWebXRListeners) {
                listener.onDismissWebXRInterstitial();
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void onWebXRRenderStateChange(boolean aRendering) {
        runOnUiThread(() -> {
            for (WebXRListener listener: mWebXRListeners) {
                listener.onWebXRRenderStateChange(aRendering);
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void renderPointerLayer(final Surface aSurface, int color, final long aNativeCallback) {
        runOnUiThread(() -> {
            try {
                Canvas canvas = aSurface.lockHardwareCanvas();
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setDither(true);
                paint.setColor(color);
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
        final File path = getExternalFilesDir(null);
        if (path == null) {
            return "";
        }
        return path.getAbsolutePath();
    }

    @Keep
    @SuppressWarnings("unused")
    public boolean isOverrideEnvPathEnabled() {
        return SettingsStore.getInstance(this).isEnvironmentOverrideEnabled();
    }

    @Keep
    @SuppressWarnings("unused")
    public void checkTogglePassthrough() {
        if (mSettings.isStartWithPassthroughEnabled() && !mIsPassthroughEnabled) {
            runOnUiThread(this::togglePassthrough);
        }
    }

    @Keep
    @SuppressWarnings("unused")
    public void resetWindowsPosition() {
        // Reset the position of the windows when we are not in headlock and window movement is enabled.
        if (!mSettings.isHeadLockEnabled() && mSettings.isWindowMovementEnabled()) {
            runOnUiThread(() -> mWindows.resetWindowsPosition());
        }
    }

    @Keep
    @SuppressWarnings("unused")
    public boolean areLayersEnabled() {
        return SettingsStore.getInstance(this).getLayersEnabled();
    }

    @Keep
    @SuppressWarnings("unused")
    public String getActiveEnvironment() {
        return getServicesProvider().getEnvironmentsManager().getOrDownloadEnvironment();
    }

    @Keep
    @SuppressWarnings("unused")
    public int getPointerColor() {
        return SettingsStore.getInstance(this).getPointerColor();
    }

    private void setUseHardwareAcceleration() {
        UISurfaceTextureRenderer.setUseHardwareAcceleration(SettingsStore.getInstance(getBaseContext()).isUIHardwareAccelerationEnabled());
    }

    @Keep
    @SuppressWarnings("unused")
    private void setDeviceType(int aType) {
        DeviceType.setType(aType);
        setUseHardwareAcceleration();
    }

    @Keep
    @SuppressWarnings("unused")
    private void haltActivity(final int aReason) {
        runOnUiThread(() -> {
            if (mConnectionAvailable && mWindows.getFocusedWindow() != null) {
                mWindows.getFocusedWindow().showAlert(
                        getString(R.string.not_entitled_title),
                        getString(R.string.not_entitled_message, getString(R.string.app_name)),
                        (index, isChecked) -> finish());
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
            if (window == null || window.getSession() == null) {
                return;
            }
            final String originalUri = window.getSession().getCurrentUri();
            if (mPoorPerformanceAllowList.contains(originalUri)) {
                return;
            }
            window.getSession().loadHomePage();
            final String[] buttons = {getString(R.string.ok_button), getString(R.string.performance_unblock_page)};
            window.showConfirmPrompt(getString(R.string.performance_title),
                    getString(R.string.performance_message),
                    buttons,
                    (index, isChecked) -> {
                if (index == PromptDialogWidget.NEGATIVE) {
                    mPoorPerformanceAllowList.add(originalUri);
                    window.getSession().loadUri(originalUri);
                }
            });
        });
    }

    @Keep
    @SuppressWarnings("unused")
    private void onAppLink(String aJSON) {
        runOnUiThread(() -> {
            try {
                JSONObject object = new JSONObject(aJSON);
                String uri = object.optString(EXTRA_URL);
                Session session = SessionStore.get().getActiveSession();
                if (!StringUtils.isEmpty(uri) && session != null) {
                    session.loadUri(uri);
                }

            } catch (Exception ex) {
                Log.e(LOGTAG, "Error parsing app link JSON: " + ex.toString());
            }

        });
    }

    @Keep
    @SuppressWarnings("unused")
    private void disableLayers() {
        runOnUiThread(() -> {
            SettingsStore.getInstance(this).setDisableLayers(true);
        });
    }

    @Keep
    @SuppressWarnings("unused")
    private void appendAppNotesToCrashReport(String aNotes) {
        runOnUiThread(() -> EngineProvider.INSTANCE.getOrCreateRuntime(VRBrowserActivity.this).appendAppNotesToCrashReport(aNotes));
    }

    @Keep
    @SuppressWarnings("unused")
    private void updateControllerBatteryLevels(final int leftLevel, final int rightLevel) {
        runOnUiThread(() -> updateBatteryLevels(leftLevel, rightLevel));
    }

    private void updateBatteryLevels(final int leftLevel, final int rightLevel) {
        long currentTime = System.nanoTime();
        if (((currentTime - mLastBatteryUpdate) >= BATTERY_UPDATE_INTERVAL) || mLastBatteryLevel == -1) {
            mLastBatteryUpdate = currentTime;
            BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
            mLastBatteryLevel = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        mTray.setBatteryLevels(mLastBatteryLevel, isCharging, leftLevel, rightLevel);
    }

    @Keep
    @SuppressWarnings("unused")
    private void onAppFocusChanged(final boolean aIsFocused) {
        runOnUiThread(() -> {
            Session session = SessionStore.get().getActiveSession();
            if (session.getActiveVideo() == null || !session.getActiveVideo().isActive())
                return;
            if (aIsFocused) {
                if (mPrevActiveMedia != null && mPrevActiveMedia == session.getActiveVideo())
                    mPrevActiveMedia.play();
            } else if (session.getActiveVideo().isPlaying()) {
                mPrevActiveMedia = session.getActiveVideo();
                mPrevActiveMedia.pause();
            }
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isWidgetInputEnabled(Widget aWidget) {
        return mActiveDialog == null || aWidget == null || mActiveDialog == aWidget || aWidget instanceof KeyboardWidget;
    }

    // WidgetManagerDelegate
    @Override
    public void addWidget(Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWidgets.put(aWidget.getHandle(), aWidget);
        ((View)aWidget).setVisibility(aWidget.getPlacement().visible ? View.VISIBLE : View.GONE);
        final int handle = aWidget.getHandle();
        final WidgetPlacement clone = aWidget.getPlacement().clone();
        queueRunnable(() -> addWidgetNative(handle, clone));
        updateActiveDialog(aWidget);
    }

    private void enqueueUpdateWidgetNativeCall(int handle, WidgetPlacement placement) {
        mPendingNativeWidgetUpdates.put(handle, placement);

        if (mNativeWidgetUpdatesTask == null || mNativeWidgetUpdatesTask.isDone()) {
            mNativeWidgetUpdatesTask = mPendingNativeWidgetUpdatesExecutor.schedule(() -> {
                for (Map.Entry<Integer, WidgetPlacement> entry : mPendingNativeWidgetUpdates.entrySet()) {
                    queueRunnable(() -> updateWidgetNative(entry.getKey(), entry.getValue()));
                }
                mPendingNativeWidgetUpdates.clear();
            }, UPDATE_NATIVE_WIDGETS_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void updateWidget(final Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        // Enqueue widget update calls in order to batch updates on the same widget. If a widget
        // updates several times in a short period of time, it's enough to call the native
        // method just once. This effectively reduces the amount of XR layer creation/destruction.
        enqueueUpdateWidgetNativeCall(aWidget.getHandle(), aWidget.getPlacement().clone());

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

        if (aWidget == mKeyboard && mPlatformPlugin != null) {
            mPlatformPlugin.onKeyboardVisibilityChange(visible);
        }

        for (UpdateListener listener: mWidgetUpdateListeners) {
            listener.onWidgetUpdate(aWidget);
        }
        updateActiveDialog(aWidget);
    }

    @Override
    public void removeWidget(final Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWidgets.remove(aWidget.getHandle());
        mWidgetContainer.removeView((View) aWidget);
        aWidget.setFirstPaintReady(false);
        queueRunnable(() -> removeWidgetNative(aWidget.getHandle()));
        if (aWidget == mActiveDialog) {
            mActiveDialog = null;
        }
    }

    @Override
    public void updateWidgetsPlacementTranslationZ() {
        for (Widget widget: mWidgets.values()) {
            widget.getPlacement().updateCylinderMapRadius();
            widget.updatePlacementTranslationZ();
            updateWidget(widget);
        }
    }

    @Override
    public void updateVisibleWidgets() {
        queueRunnable(this::updateVisibleWidgetsNative);
    }

    @Override
    public void recreateWidgetSurface(Widget aWidget) {
        queueRunnable(() -> recreateWidgetSurfaceNative(aWidget.getHandle()));
    }

    @Override
    public void startWidgetResize(final Widget aWidget, float aMaxWidth, float aMaxHeight, float minWidth, float minHeight) {
        if (aWidget == null) {
            return;
        }
        mWindows.enterResizeMode();
        queueRunnable(() -> startWidgetResizeNative(aWidget.getHandle(), aMaxWidth, aMaxHeight, minWidth, minHeight));
    }

    @Override
    public void finishWidgetResize(final Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWindows.exitResizeMode();
        queueRunnable(() -> finishWidgetResizeNative(aWidget.getHandle()));
    }

    @Override
    public void startWidgetMove(final Widget aWidget, @WidgetMoveBehaviourFlags int aMoveBehaviour) {
        if (aWidget == null) {
            return;
        }
        queueRunnable(() -> startWidgetMoveNative(aWidget.getHandle(), aMoveBehaviour));
    }

    @Override
    public void finishWidgetMove() {
        queueRunnable(this::finishWidgetMoveNative);
    }

    @Override
    public void addUpdateListener(@NonNull UpdateListener aUpdateListener) {
        if (!mWidgetUpdateListeners.contains(aUpdateListener)) {
            mWidgetUpdateListeners.add(aUpdateListener);
        }
    }

    @Override
    public void removeUpdateListener(@NonNull UpdateListener aUpdateListener) {
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
    public void addFocusChangeListener(@NonNull FocusChangeListener aListener) {
        if (!mFocusChangeListeners.contains(aListener)) {
            mFocusChangeListeners.add(aListener);
        }
    }

    @Override
    public void removeFocusChangeListener(@NonNull FocusChangeListener aListener) {
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
    public void addWebXRListener(WebXRListener aListener) {
        mWebXRListeners.add(aListener);
    }

    @Override
    public void removeWebXRListener(WebXRListener aListener) {
        mWebXRListeners.remove(aListener);
    }

    @Override
    public void setWebXRIntersitialState(@WebXRInterstitialState int aState) {
        queueRunnable(() -> setWebXRIntersitialStateNative(aState));
    }

    @Override
    public boolean isWebXRIntersitialHidden() {
        return mHideWebXRIntersitial;
    }

    @Override
    public boolean isWebXRPresenting() {
        return mIsPresentingImmersive;
    }

    @Override
    public void pushBackHandler(@NonNull Runnable aRunnable) {
        mBackHandlers.addLast(aRunnable);
    }

    @Override
    public void popBackHandler(@NonNull Runnable aRunnable) {
        mBackHandlers.removeLastOccurrence(aRunnable);
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
    public void triggerHapticFeedback() {
        SettingsStore settings = SettingsStore.getInstance(this);
        if (settings.isHapticFeedbackEnabled()) {
            queueRunnable(() -> triggerHapticFeedbackNative(settings.getHapticPulseDuration(), settings.getHapticPulseIntensity()));
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
        if (mPlatformPlugin != null)
            mPlatformPlugin.onKeyboardVisibilityChange(false);
    }

    @Override
    public void updateEnvironment() {
        queueRunnable(this::updateEnvironmentNative);
    }

    @Override
    public void updateKeyboardDictionary() {
        mKeyboard.updateDictionary();
    }

    @Override
    public void updatePointerColor() {
        queueRunnable(this::updatePointerColorNative);
    }

    @Override
    public boolean isPermissionGranted(@NonNull String permission) {
        return mPermissionDelegate.isPermissionGranted(permission);
    }

    @Override
    public void requestPermission(String originator, @NonNull String permission, OriginatorType originatorType, WSession.PermissionDelegate.Callback aCallback) {
        Session session = SessionStore.get().getActiveSession();
        if (originatorType == OriginatorType.WEBSITE) {
            mPermissionDelegate.onWebsitePermissionRequest(session.getWSession(), originator, permission, aCallback);
        } else {
            mPermissionDelegate.onAndroidPermissionsRequest(session.getWSession(), new String[]{permission}, aCallback);
        }
    }

    @Override
    @Deprecated
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
    public void togglePassthrough() {
        mIsPassthroughEnabled = !mIsPassthroughEnabled;
        queueRunnable(() -> togglePassthroughNative());
    }

    @Override
    public boolean isPassthroughEnabled() {
        return mIsPassthroughEnabled;
    }
    @Override
    public boolean isPassthroughSupported() {
        return DeviceType.isOculusBuild() || DeviceType.isLynx() || DeviceType.isSnapdragonSpaces() ||
               (DeviceType.isPicoXR() && Build.ID.compareTo(kPicoVersionPassthroughUpdate) >= 0);
    }

    @Override
    public boolean isPageZoomEnabled() {
        return BuildConfig.ENABLE_PAGE_ZOOM;
    }

    @Override
    public void setHeadLockEnabled(boolean isHeadLockEnabled) {
        queueRunnable(() -> setHeadLockEnabledNative(isHeadLockEnabled));
    }

    @Override
    public void recenterUIYaw(@YawTarget int aTarget) {
        queueRunnable(() -> recenterUIYawNative(aTarget));
    }

    @Override
    public void setCylinderDensity(final float aDensity) {
        if (mWindows != null && aDensity == 0.0f && mWindows.getWindowsCount() > 1) {
            return;
        }
        setCylinderDensityForce(aDensity);
    }

    @Override
    public void setCylinderDensityForce(final float aDensity) {
        mCurrentCylinderDensity = aDensity;
        queueRunnable(() -> setCylinderDensityNative(aDensity));
        if (mWindows != null) {
            mWindows.updateCurvedMode(false);
        }
    }

    @Override
    public void setCenterWindows(boolean isCenterWindows) {
        if (mWindows != null) {
            mWindows.setCenterWindows(isCenterWindows);
            updateVisibleWidgets();
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
        if ((newWindow != null) && (newWindow.getSession() != null)) {
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

    private boolean openNewTabNoInterrupt(@NonNull WindowWidget window, @NonNull String uri) {
        if (window.getSession() == null || window.getSession().getActiveVideo() != null) {
            return false;
        }

        mWindows.addTab(window, uri);
        mWindows.focusWindow(window);
        return true;
    }
    @Override
    public void openNewPageNoInterrupt(@NonNull String uri) {
        if (openNewTabNoInterrupt(mWindows.getFocusedWindow(), uri)) { return; }

        // If we have video playing in current window, ensure we don't open a new tab
        // in a window that has active video
        if (mWindows.getWindowsCount() > 1) {
            for (WindowWidget window : mWindows.getCurrentWindows()) {
                if (openNewTabNoInterrupt(window, uri)) { return; }
            }
        }
        // All the current opened Windows have video playing, so we have to open uri in a new window.
        // If we have maximum window number, then open the uri as a new tab in current window.
        if (canOpenNewWindow()) {
            openNewWindow(uri);
        } else {
            openNewTabForeground(uri);
        }
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
    public NavigationBarWidget getNavigationBar() {
        return mNavigationBar;
    }

    @Override
    public Windows getWindows() {
        return mWindows;
    }

    @Override
    public void saveState() {
        mWindows.saveState();
    }

    @Override
    public void updateLocale(@NonNull Context context) {
        onConfigurationChanged(context.getResources().getConfiguration());
        getApplication().onConfigurationChanged(context.getResources().getConfiguration());
    }

    @Override
    @NonNull
    public AppServicesProvider getServicesProvider() {
        return (AppServicesProvider)getApplication();
    }

    @Override
    public KeyboardWidget getKeyboard() { return mKeyboard; }

    @Override
    public void onPlatformScrollEvent(float distanceX, float distanceY) {
        float SCROLL_SCALE = 32;
        handleScrollEvent(mLastMotionEventWidgetHandle, 0, distanceX / SCROLL_SCALE, distanceY / SCROLL_SCALE);
    }

    private native void addWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateVisibleWidgetsNative();
    private native void removeWidgetNative(int aHandle);
    private native void recreateWidgetSurfaceNative(int aHandle);
    private native void startWidgetResizeNative(int aHandle, float maxWidth, float maxHeight, float minWidth, float minHeight);
    private native void finishWidgetResizeNative(int aHandle);
    private native void startWidgetMoveNative(int aHandle, int aMoveBehaviour);
    private native void finishWidgetMoveNative();
    private native void setWorldBrightnessNative(float aBrightness);
    private native void triggerHapticFeedbackNative(float aPulseDuration, float aPulseIntensity);
    private native void setTemporaryFilePath(String aPath);
    private native void exitImmersiveNative();
    private native void workaroundGeckoSigAction();
    private native void updateEnvironmentNative();
    private native void updatePointerColorNative();
    private native void showVRVideoNative(int aWindowHandler, int aVideoProjection);
    private native void hideVRVideoNative();
    private native void togglePassthroughNative();
    private native void setHeadLockEnabledNative(boolean isEnabled);
    private native void recenterUIYawNative(@YawTarget int aTarget);
    private native void setControllersVisibleNative(boolean aVisible);
    private native void runCallbackNative(long aCallback);
    private native void deleteCallbackNative(long aCallback);
    private native void setCylinderDensityNative(float aDensity);
    private native void setCPULevelNative(@CPULevelFlags int aCPULevel);
    private native void setWebXRIntersitialStateNative(@WebXRInterstitialState int aState);
    private native void setIsServo(boolean aIsServo);
}
