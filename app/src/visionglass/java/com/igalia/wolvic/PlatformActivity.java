/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.usblib.DisplayMode;
import com.huawei.usblib.DisplayModeCallback;
import com.huawei.usblib.OnConnectionListener;
import com.huawei.usblib.VisionGlass;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.databinding.VisionglassLayoutBinding;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlatformActivity extends FragmentActivity implements SensorEventListener, OnConnectionListener {
    static String LOGTAG = SystemUtils.createLogtag(PlatformActivity.class);

    public static final String HUAWEI_USB_PERMISSION = "com.huawei.usblib.USB_PERMISSION";

    private boolean mIsAskingForPermission;
    private PhoneUIViewModel mViewModel;
    VisionglassLayoutBinding mBinding;
    private DisplayManager mDisplayManager;
    private Display mPresentationDisplay;
    private VisionGlassPresentation mActivePresentation;
    private int mDisplayModeRetryCount = 0;
    private int mUSBPermissionRequestCount = 0;
    private boolean mSwitchedTo3DMode = false;
    private boolean mShouldRecalibrateAfterIMURestart = false;
    private AlignPhoneDialogFragment mAlignDialogFragment;
    private AlignNotificationUIDialog mAlignNotificationUIDialog;

    @SuppressWarnings("unused")
    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }

    public static boolean isPositionTrackingSupported() {
        // Vision Glass is a 3DoF device.
        return false;
    }

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    protected String getEyeTrackingPermissionString() { return null; }

    private final ArrayList<Runnable> mPendingEvents = new ArrayList<>();
    private SensorManager mSensorManager;

    final Object mRenderLock = new Object();

    private final Runnable activityDestroyedRunnable = () -> {
        synchronized (mRenderLock) {
            activityDestroyed();
            mRenderLock.notifyAll();
        }
    };

    private final Runnable activityPausedRunnable = () -> {
        synchronized (mRenderLock) {
            activityPaused();
            mRenderLock.notifyAll();
        }
    };
    private final Runnable activityResumedRunnable = this::activityResumed;

    private interface VRBrowserActivityCallback {
        void run(VRBrowserActivity activity);
    };

    /**
     * Use this method to run callbacks for phone UI buttons. It provides access to the Windows
     * object in VRBrowserActivity. It's a bit ugly but it's the best we can do with the current
     * architecture where there are multiple PlatformActivity's.
    */
    private void runVRBrowserActivityCallback(VRBrowserActivityCallback callback) {
        Context context = getApplicationContext();
        assert context instanceof VRBrowserApplication;
        assert ((VRBrowserApplication)context).getCurrentActivity() instanceof VRBrowserActivity;

        callback.run((VRBrowserActivity)((VRBrowserApplication)context).getCurrentActivity());
    }

    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOGTAG, "USB permission broadcast; waiting for permission: " + mIsAskingForPermission + "; intent: " + intent.toString());

            mIsAskingForPermission = false;
            initVisionGlass();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);

        // Before we do anything else, we need to ensure that the user has accepted the legal terms.
        SettingsStore settings = SettingsStore.getInstance(this);
        if (!settings.isTermsServiceAccepted() && !settings.isPrivacyPolicyAccepted()) {
            Intent intent = new Intent(this, FirstRunActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Alternatively: android.hardware.usb.action.USB_DEVICE_ATTACHED, USB_DEVICE_DETACHED.
        IntentFilter usbPermissionFilter = new IntentFilter();
        usbPermissionFilter.addAction(HUAWEI_USB_PERMISSION);
        registerReceiver(mUsbPermissionReceiver, usbPermissionFilter, Context.RECEIVER_NOT_EXPORTED);

        initializeAGConnect();

        initVisionGlassPhoneUI();

        mDisplayManager.registerDisplayListener(mDisplayListener, null);

        VisionGlass.getInstance().init(getApplication());
        VisionGlass.getInstance().setOnConnectionListener(this);
        initVisionGlass();
    }

    @Override
    public void onConnectionChange(boolean b) {
        if (VisionGlass.getInstance().isConnected()) {
            Log.d(LOGTAG, "onConnectionChange: Device connected");
            if (mViewModel.getConnectionState().getValue() == PhoneUIViewModel.ConnectionState.DISCONNECTED) {
                mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.CONNECTING);
            }
            initVisionGlass();
        } else {
            Log.d(LOGTAG, "onConnectionChange: Device disconnected");

            // reset internal state when the device disconnects
            mUSBPermissionRequestCount = 0;
            mDisplayModeRetryCount = 0;
            mSwitchedTo3DMode = false;
            mPresentationDisplay = null;
            mActivePresentation = null;

            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
        }
    }

    private void registerPhoneIMUListener() {
        mShouldRecalibrateAfterIMURestart = true;
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME);
    }

    private void reorientController() {
        mAlignDialogFragment.dismiss();

        if (mAlignNotificationUIDialog != null) {
            mAlignNotificationUIDialog.hide(UIWidget.REMOVE_WIDGET);
            mAlignNotificationUIDialog = null;
        }

        mSensorManager.unregisterListener(this);
        registerPhoneIMUListener();

        runVRBrowserActivityCallback(activity -> activity.recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL));
    }

    private void onConnectionStateChanged(PhoneUIViewModel.ConnectionState connectionState) {
        Log.d(LOGTAG, "Connection state updated: " + connectionState);

        if (connectionState == PhoneUIViewModel.ConnectionState.ACTIVE) {
            mAlignDialogFragment.show(getSupportFragmentManager(), "AlignDialogFragment");

            if (mAlignNotificationUIDialog == null) {
                mAlignNotificationUIDialog = new AlignNotificationUIDialog(this);
            }
            mAlignNotificationUIDialog.show(UIWidget.REQUEST_FOCUS);
        } else if (mAlignDialogFragment.isAdded()) {
            mAlignDialogFragment.dismiss();
        }
    }

    private void initVisionGlassPhoneUI() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTheme(R.style.FxR_Dark);

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_WolvicPhone);
        LayoutInflater themedInflater = getLayoutInflater().cloneInContext(themedContext);
        mBinding = DataBindingUtil.setContentView(this, R.layout.visionglass_layout);
        mBinding = DataBindingUtil.inflate(themedInflater, R.layout.visionglass_layout, null, false);
        setContentView(mBinding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mViewModel = new ViewModelProvider(this).get(PhoneUIViewModel.class);
        mBinding.setViewModel(mViewModel);
        mBinding.setLifecycleOwner(this);

        mViewModel.getConnectionState().observe(this, this::onConnectionStateChanged);

        mAlignDialogFragment = new AlignPhoneDialogFragment(R.style.Theme_WolvicPhone);
        mAlignDialogFragment.setOnRealignButtonClickListener(v -> {
            reorientController();
        });

        mBinding.realignButton.setOnClickListener(v -> reorientController());

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void showAlertDialog(String description) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.phone_ui_alert_dialog_title);
        builder.setMessage(description);
        builder.setPositiveButton(R.string.ok_button, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void initVisionGlass() {
        Log.d(LOGTAG, "initVisionGlass");

        if (!VisionGlass.getInstance().isConnected()) {
            Log.d(LOGTAG, "Glasses not connected yet");
            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
            return;
        }

        if (!VisionGlass.getInstance().hasUsbPermission()) {
            if (!mIsAskingForPermission) {
                // Note that the system will ask for permissions by itself. This is just a fallback.
                if (mUSBPermissionRequestCount++ > 1) {
                    showAlertDialog(getString(R.string.phone_ui_usb_alert_dialog_description));
                    mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.PERMISSIONS_UNAVAILABLE);
                    return;
                }
                Log.d(LOGTAG, "Asking for USB permission");
                mIsAskingForPermission = true;
                mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.REQUESTING_PERMISSIONS);
                VisionGlass.getInstance().requestUsbPermission();
            }
            return;
        }

        if (mSwitchedTo3DMode && mPresentationDisplay != null && mActivePresentation != null) {
            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.ACTIVE);
            // Recenter the UI so that the user sees the browser window in front. We must do this
            // at this point to ensure that everything is properly initialized.
            runVRBrowserActivityCallback(activity -> activity.recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL));
            return;
        } else {
            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.CONNECTED);
        }

        // Don't try to set the 3D mode if we've already switched to it or if we're retrying.
        if (mSwitchedTo3DMode || mDisplayModeRetryCount > 1) {
            Log.d(LOGTAG, "initVisionGlass, we've already switched to 3D mode or we're retrying");
            return;
        }

        VisionGlass.getInstance().setDisplayMode(DisplayMode.vr3d, new DisplayModeCallback() {
            @Override
            public void onSuccess(DisplayMode displayMode) {
                Log.d(LOGTAG, "Successfully switched to 3D mode");
                mSwitchedTo3DMode = true;
            }

            @Override
            public void onError(String s, int i) {
                Log.d(LOGTAG, "Error " + i + "; failed to switch to 3D mode: " + s);
                if (++mDisplayModeRetryCount < 3) {
                    VisionGlass.getInstance().setDisplayMode(DisplayMode.vr3d, this);
                } else {
                    showAlertDialog(getString(R.string.phone_ui_set3dmode_alert_dialog_description));
                    mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISPLAY_UNAVAILABLE);
                }
            }
        });
    }

    private float[] fromSensorManagerToWorld(float[] eventValues) {
        float[] sensorQuaternion = new float[4];
        SensorManager.getQuaternionFromVector(sensorQuaternion, eventValues);

        // The quaternion is returned in the form [w, x, y, z] but we use it as [x, y, z, w].
        // Apart from that we have to transform the sensor coordinate system to our world coordinate
        // system. This is the correspodence of sensor axis (X,Y,Z) to world axis (x,y,z):
        // X -> x, Y -> -z, Z -> y (in world coordinates -z is forward, so Y from the device).
        // https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-coords
        float[] q = new float[4];
        q[0] = sensorQuaternion[1];
        q[1] = sensorQuaternion[3];
        q[2] = -sensorQuaternion[2];
        q[3] = sensorQuaternion[0];
        return q;
    }

    // SensorEventListener overrides
    @Override
    public void onSensorChanged(SensorEvent event) {
        // retrieve the device orientation from sensorevent in the form of quaternion
        if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR)
            return;

        final float[] quaternion = fromSensorManagerToWorld(event.values);

        queueRunnable(() -> setControllerOrientation(quaternion[0], quaternion[1], quaternion[2], quaternion[3]));

        if (mShouldRecalibrateAfterIMURestart) {
            mShouldRecalibrateAfterIMURestart = false;
            queueRunnable(this::calibrateController);
        }

        mBinding.realignButton.updatePosition(-quaternion[1], -quaternion[0]);

        if (mAlignDialogFragment != null && mAlignDialogFragment.isVisible()) {
            mAlignDialogFragment.updatePosition(-quaternion[1], -quaternion[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent aEvent) {
        if (aEvent.getActionIndex() != 0) {
            Log.e(LOGTAG,"aEvent.getActionIndex()=" + aEvent.getActionIndex());
            return false;
        }

        if (aEvent.getAction() != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }

        final float xx = aEvent.getX(0);
        final float yy = aEvent.getY(0);
        queueRunnable(() -> touchEvent(false, xx, yy));
        return true;
    }

    @Override
    protected void onPause() {
        Log.d(LOGTAG, "PlatformActivity onPause");
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // This check is needed to prevent a crash when pausing before 3D mode has started.
        if (mActivePresentation != null) {
            synchronized (mRenderLock) {
                queueRunnable(activityPausedRunnable);
                try {
                    mRenderLock.wait();
                } catch(InterruptedException e) {
                    Log.e(LOGTAG, "activityPausedRunnable interrupted: " + e);
                }
            }
            mActivePresentation.mGLView.onPause();
        }

        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "PlatformActivity onResume");
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        registerPhoneIMUListener();

        // Sometimes no display event is emitted so we need to call updateDisplays() from here.
        if (VisionGlass.getInstance().isConnected() && VisionGlass.getInstance().hasUsbPermission() && mSwitchedTo3DMode && mActivePresentation == null) {
            updateDisplays();
        }

        if (mActivePresentation != null && mActivePresentation.mGLView != null)
            mActivePresentation.mGLView.onResume();

        queueRunnable(activityResumedRunnable);
    }

    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "PlatformActivity onDestroy");
        super.onDestroy();
        unregisterReceiver(mUsbPermissionReceiver);
        synchronized (mRenderLock) {
            if (queueRunnable(activityDestroyedRunnable)) {
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Log.e(LOGTAG, "activityDestroyedRunnable interrupted: " + e.toString());
                }
            }
        }
    }

    boolean queueRunnable(@NonNull Runnable aRunnable) {
        if (mActivePresentation != null) {
            mActivePresentation.mGLView.queueEvent(aRunnable);
            return true;
        } else {
            synchronized (mPendingEvents) {
                mPendingEvents.add(aRunnable);
            }
            if (mActivePresentation != null) {
                notifyPendingEvents();
                return true;
            }
        }
        return false;
    }

    private void notifyPendingEvents() {
        synchronized (mPendingEvents) {
            for (Runnable runnable: mPendingEvents) {
                mActivePresentation.mGLView.queueEvent(runnable);
            }
            mPendingEvents.clear();
        }
    }

    private void updateDisplays() {
        // a display may be added before we receive the USB permission
        if (!VisionGlass.getInstance().hasUsbPermission()) {
            Log.d(LOGTAG, "updateDisplays: no USB permissions yet");
            return;
        }

        Display[] displays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        if (displays.length > 0) {
            runOnUiThread(() -> showPresentation(displays[0]));
            return;
        }

        mPresentationDisplay = null;
        if (mActivePresentation != null) {
            mActivePresentation.cancel();
            mActivePresentation = null;
        }

        if (!VisionGlass.getInstance().isConnected()) {
            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
            return;
        }

        // This can happen after switching to 3D mode because the user has not accepted permissions
        // but also when the system is about to replace the display.
        mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISPLAY_UNAVAILABLE);
    }

    private void initializeAGConnect() {
        try {
            String speechService = SettingsStore.getInstance(this).getVoiceSearchService();
            if (SpeechServices.HUAWEI_ASR.equals(speechService) && StringUtils.isEmpty(BuildConfig.HVR_API_KEY)) {
                Log.e(LOGTAG, "HVR API key is not available");
                return;
            }
            MLApplication.getInstance().setApiKey(BuildConfig.HVR_API_KEY);
            try {
                SpeechRecognizer speechRecognizer = SpeechServices.getInstance(this, speechService);
                ((VRBrowserApplication) getApplicationContext()).setSpeechRecognizer(speechRecognizer);
            } catch (Exception e) {
                Log.e(LOGTAG, "Exception creating the speech recognizer: " + e);
                ((VRBrowserApplication) getApplicationContext()).setSpeechRecognizer(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) {
        return new PlatformActivityPluginVisionGlass(delegate);
    }

    private class PlatformActivityPluginVisionGlass extends PlatformActivityPlugin {
        private WidgetManagerDelegate mDelegate;
        private WMediaSession.Delegate mMediaSessionDelegate;
        private GestureDetector mGestureDetector;

        PlatformActivityPluginVisionGlass(WidgetManagerDelegate delegate) {
            mDelegate = delegate;
            setupPhoneUI();
        }

        @Override
        public void onVideoAvailabilityChange() {
            boolean isAvailable = mDelegate.getWindows().isVideoAvailable();

            mViewModel.updateIsPlayingMedia(isAvailable);

            Media media = getActiveMedia();
            if (!isAvailable) {
                if (media != null) {
                    media.removeMediaListener(mMediaSessionDelegate);
                    mMediaSessionDelegate = null;
                }
                return;
            }

            assert(media != null);
            media.addMediaListener(new WMediaSession.Delegate() {
                @Override
                public void onPlay(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
                    mBinding.playButton.setIconResource(R.drawable.ic_icon_media_pause);
                }

                @Override
                public void onPause(@NonNull WSession session, @NonNull WMediaSession mediaSession) {
                    mBinding.playButton.setIconResource(R.drawable.ic_icon_media_play);
                }

                @Override
                public void onPositionState(@NonNull WSession session, @NonNull WMediaSession mediaSession, @NonNull WMediaSession.PositionState state) {
                    mBinding.mediaSeekbar.setProgress((int) ((state.position / state.duration) * 100), false);
                }
            });
        }

        private Media getActiveMedia() {
            assert mDelegate.getWindows() != null;
            assert mDelegate.getWindows().getFocusedWindow() != null;
            return mDelegate.getWindows().getFocusedWindow().getSession().getActiveVideo();
        }

        // Setup the phone UI callbacks that require access to the WindowManagerDelegate.
        @SuppressLint("ClickableViewAccessibility")
        private void setupPhoneUI() {
            mBinding.homeButton.setOnClickListener(v -> {
                mDelegate.getWindows().getFocusedWindow().loadHome();
            });

            mBinding.headlockToggleButton.setChecked(
                    SettingsStore.getInstance(PlatformActivity.this).isHeadLockEnabled());
            mBinding.headlockToggleButton.setOnClickListener(v -> {
                mDelegate.setHeadLockEnabled(mBinding.headlockToggleButton.isChecked());
            });

            mBinding.playButton.setOnClickListener(v -> {
                Media media = getActiveMedia();
                if (media == null)
                    return;
                if (media.isPlaying()) {
                    media.pause();
                } else {
                    media.play();
                }
            });

            mBinding.seekBackwardButton.setOnClickListener(v -> {
                Media media = getActiveMedia();
                if (media == null)
                    return;
                media.seekBackward();
            });

            mBinding.seekForwardButton.setOnClickListener(v -> {
                Media media = getActiveMedia();
                if (media == null)
                    return;
                media.seekForward();
            });

            mBinding.voiceSearchButton.setOnClickListener(v -> {
                if (mDelegate.getKeyboard().getVisibility() == View.VISIBLE) {
                    mDelegate.getKeyboard().simulateVoiceButtonClick();
                } else {
                    mDelegate.getNavigationBar().onVoiceSearchClicked();
                }
            });

            mBinding.muteButton.setOnClickListener(v -> {
                Media media = getActiveMedia();
                if (media == null)
                    return;
                media.setMuted(!media.isMuted());
                mBinding.muteButton.setIconResource(!media.isMuted() ? R.drawable.ic_icon_media_volume : R.drawable.ic_icon_media_volume_muted);
            });

            mBinding.mediaSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Media media = getActiveMedia();
                    if (media == null || !media.canSeek() || media.getDuration() == 0)
                        return;
                    media.seek((seekBar.getProgress() / 100.0) * media.getDuration());
                }
            });

            mGestureDetector = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                    // Use inverted axis so the scroll feels more natural.
                    notifyOnScrollEvent(-distanceX, -distanceY);
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    // We should be really using onDown for this, but we cannot do that because
                    // onDown is the precursor of other events like onScroll.
                    queueRunnable(() -> touchEvent(true, 0, 0));
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    queueRunnable(() -> touchEvent(false, 0, 0));
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    queueRunnable(() -> touchEvent(false, 0, 0));
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    // Used to perform implement click and hold for scrolling or moving widgets.
                    queueRunnable(() -> touchEvent(true, 0, 0));
                }
            });

            mBinding.touchpad.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Seems redundant with onSingleTapXXX events but we need to handle this for the
                    // onLongPress case. When that happens we don't get onSingleTapXXX gestures but
                    // we still need to notify that the touch event has ended.
                    queueRunnable(() -> touchEvent(false, 0, 0));
                }
                return mGestureDetector.onTouchEvent(event);
            });
        }

        @Override
        boolean onBackPressed() {
            // User pressed Back on the phone and VR is not active, so we use the default behaviour.
            if (mViewModel.getConnectionState().getValue() != PhoneUIViewModel.ConnectionState.ACTIVE) {
                PlatformActivity.super.onBackPressed();
                return true;
            }
            return false;
        }
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                private void callUpdateIfIsPresentation(int displayId) {
                    Display display = mDisplayManager.getDisplay(displayId);
                    if (display != null && (display.getFlags() & Display.FLAG_PRESENTATION) == 0)
                        return;
                    Log.d(LOGTAG, "display listener: calling updateDisplay with " + displayId);
                    updateDisplays();
                }

                @Override
                public void onDisplayAdded(int displayId) {
                    Log.d(LOGTAG, "display listener: onDisplayAdded displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    Log.d(LOGTAG, "display listener: onDisplayChanged displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    Log.d(LOGTAG, "display listener: onDisplayRemoved displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }
            };

    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog.equals(mActivePresentation)) {
                        mActivePresentation = null;
                    }
                }
            };

    private void showPresentation(@NonNull Display presentationDisplay) {
        if (presentationDisplay.equals(mPresentationDisplay) && mActivePresentation != null) {
            mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.ACTIVE);
            return;
        }

        Log.d(LOGTAG, "Starting IMU");
        VisionGlass.getInstance().startImu((w, x, y, z) -> queueRunnable(() -> setHead(x, y, z, w)));

        VisionGlassPresentation presentation = new VisionGlassPresentation(this, presentationDisplay);
        Display.Mode[] modes = presentationDisplay.getSupportedModes();
        Log.d(LOGTAG, "showPresentation supported modes: " + Arrays.toString(modes));
        presentation.setPreferredDisplayMode(modes[0].getModeId());
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);

        mPresentationDisplay = presentationDisplay;
        mActivePresentation = presentation;

        mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.ACTIVE);
    }

    private final class VisionGlassPresentation extends Presentation {

        private GLSurfaceView mGLView;

        public VisionGlassPresentation(Context context, Display display) {
            super(context, display);
        }

        /**
         * Sets the preferred display mode id for the presentation.
         */
        public void setPreferredDisplayMode(int modeId) {
            Log.d(LOGTAG, "VisionGlassPresentation setPreferredDisplayMode: " + modeId);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = modeId;
            getWindow().setAttributes(params);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // Be sure to call the super class.
            super.onCreate(savedInstanceState);
            Log.d(LOGTAG, "VisionGlassPresentation onCreate");

            // Inflate the layout.
            setContentView(R.layout.visionglass_presentation_layout);

            mGLView = findViewById(R.id.gl_presentation_view);
            mGLView.setEGLContextClientVersion(3);
            mGLView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
            mGLView.setPreserveEGLContextOnPause(true);

            mGLView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                    Log.d(LOGTAG, "VisionGlassPresentation onSurfaceCreated");
                    activityCreated(getAssets());
                    notifyPendingEvents();
                }

                @Override
                public void onSurfaceChanged(GL10 gl, int width, int height) {
                    Log.d(LOGTAG, "VisionGlassPresentation onSurfaceChanged");
                    updateViewport(width, height);
                }

                @Override
                public void onDrawFrame(GL10 gl) {
                    drawGL();
                }
            });
        }
    }

    @Keep
    @SuppressWarnings("unused")
    private void setRenderMode(final int aMode) {}

    private native void activityCreated(Object aAssetManager);
    private native void updateViewport(int width, int height);
    private native void activityPaused();
    private native void activityResumed();
    private native void activityDestroyed();
    private native void drawGL();
    private native void touchEvent(boolean aDown, float aX, float aY);
    private native void setHead(double x, double y, double z, double w);
    private native void setControllerOrientation(double x, double y, double z, double w);
    private native void calibrateController();
}
