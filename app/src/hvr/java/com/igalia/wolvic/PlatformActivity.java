/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.hms.push.RemoteMessage;
import com.huawei.hvr.LibUpdateClient;
import com.igalia.wolvic.browser.PermissionDelegate;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.utils.StringUtils;

public class PlatformActivity extends Activity implements SurfaceHolder.Callback, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = "PlatformActivity";
    private SurfaceView mView;
    private Context mContext = null;
    private HVRLocationManager mLocationManager;
    private Dialog mActiveDialog;
    private SharedPreferences mPrefs;
    private BroadcastReceiver mHmsMessageBroadcastReceiver;

    static {
        Log.i(TAG, "LoadLibrary");
    }

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);
        mContext = this;
        mLocationManager = new HVRLocationManager(this);
        PermissionDelegate.sPlatformLocationOverride = session -> mLocationManager.start(session);

        mHmsMessageBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handlemHmsMessageBroadcast(intent);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(WolvicHmsMessageService.MESSAGE_RECEIVED_ACTION);
        registerReceiver(mHmsMessageBroadcastReceiver, filter);

        if (SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
            setHmsMessageServiceAutoInit(true);
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (manager.getDisplays().length < 2) {
            showPhoneUI();
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            initializeVR();
        }
    }

    private void handlemHmsMessageBroadcast(Intent intent) {
        if (!WolvicHmsMessageService.MESSAGE_RECEIVED_ACTION.equals(intent.getAction()))
            return;

        RemoteMessage.Notification notification = intent.getParcelableExtra(WolvicHmsMessageService.NOTIFICATION_EXTRA);
        Log.i(TAG, "PushKit received notification: " + notification);

        // TODO use all the content in the incoming message's notification
        showIncomingMessageNotification(notification.getTitle());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.settings_key_privacy_policy_accepted))) {
            if (SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
                // We need to wait until the Privacy Policy is accepted to request a message token.
                getHmsMessageServiceToken();
            } else {
                deleteHmsMessageServiceToken();
            }
        }
    }

    private void showPhoneUI() {
        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        manager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // create the activity again, so the theme is set up properly
                recreate();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
            }
        }, null);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTheme(R.style.Theme_WolvicPhone);
        setContentView(R.layout.activity_main);
    }

    private void initializeVR() {
        if (mActiveDialog != null) {
            mActiveDialog.dismiss();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setTheme(R.style.FxR_Dark);
        mView = new SurfaceView(this);
        setContentView(mView);

        mView.getHolder().addCallback(this);
        new LibUpdateClient(this).runUpdate();
        nativeOnCreate();

        initializeAGConnect();
    }

    private void initializeAGConnect() {
        try {
            String speechService = SettingsStore.getInstance(this).getVoiceSearchService();
            if (SpeechServices.HUAWEI_ASR.equals(speechService) && StringUtils.isEmpty(BuildConfig.HVR_ML_API_KEY)) {
                Log.e(TAG, "HVR API key is not available");
                return;
            }
            MLApplication.getInstance().setApiKey(BuildConfig.HVR_ML_API_KEY);
            TelemetryService.setService(new HVRTelemetry(this));
            try {
                SpeechRecognizer speechRecognizer = SpeechServices.getInstance(this, speechService);
                ((VRBrowserApplication) getApplicationContext()).setSpeechRecognizer(speechRecognizer);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setHmsMessageServiceAutoInit(boolean enabled) {
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_AUTO_INIT);
        intent.putExtra(WolvicHmsMessageService.ENABLED_EXTRA, enabled);
        startService(intent);
    }

    private void getHmsMessageServiceToken() {
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_GET_TOKEN);
        startService(intent);
    }

    private void deleteHmsMessageServiceToken() {
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_DELETE_TOKEN);
        startService(intent);
    }

    private void stopHmsMessageService() {
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_STOP_SERVICE);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "PlatformActivity onDestroy");

        stopHmsMessageService();
        unregisterReceiver(mHmsMessageBroadcastReceiver);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);

        super.onDestroy();
        nativeOnDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "PlatformActivity onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "PlatformActivity onStop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "PlatformActivity onPause");
        queueRunnable(this::nativeOnPause);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "PlatformActivity onResume");
        queueRunnable(this::nativeOnResume);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "makelele life surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "PlatformActivity surfaceChanged");
        queueRunnable(() -> nativeOnSurfaceChanged(holder.getSurface()));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "PlatformActivity surfaceDestroyed");
        queueRunnable(this::nativeOnSurfaceDestroyed);
    }

    protected void showIncomingMessageNotification(String message) {
        Log.w(TAG, "showIncomingMessageNotification: not implemented");
    }

    protected boolean platformExit() {
        return false;
    }
    protected native void queueRunnable(Runnable aRunnable);
    protected native void nativeOnCreate();
    protected native void nativeOnDestroy();
    protected native void nativeOnPause();
    protected native void nativeOnResume();
    protected native void nativeOnSurfaceChanged(Surface surface);
    protected native void nativeOnSurfaceDestroyed();
}



