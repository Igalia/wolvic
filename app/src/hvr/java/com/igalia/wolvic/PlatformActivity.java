/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.huawei.hms.analytics.HiAnalytics;
import com.huawei.hms.analytics.HiAnalyticsInstance;
import com.huawei.hms.analytics.HiAnalyticsTools;
import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.hms.push.RemoteMessage;
import com.huawei.hvr.LibUpdateClient;
import com.igalia.wolvic.browser.PermissionDelegate;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.geolocation.HVRLocationManager;
import com.igalia.wolvic.messaging.WolvicHmsMessageService;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.telemetry.HVRTelemetry;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.SystemNotification;
import com.igalia.wolvic.ui.widgets.SystemNotificationsManager;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public abstract class PlatformActivity extends FragmentActivity implements SurfaceHolder.Callback, WidgetManagerDelegate {
    public static final String TAG = "PlatformActivity";
    private HVRLocationManager mLocationManager;
    private SharedPreferences mPrefs;
    private BroadcastReceiver mHmsMessageBroadcastReceiver;
    private final Runnable mPhoneBackHandler = super::onBackPressed;

    static {
        Log.i(TAG, "LoadLibrary");
    }

    public static boolean filterPermission(final String aPermission) {
        return false;
    }

    public static boolean isNotSpecialKey(KeyEvent event) {
        return true;
    }

    public static boolean isPositionTrackingSupported() {
        return nativeIsPositionTrackingSupported();
    }

    protected Intent getStoreIntent() {
        // Dummy implementation.
        return null;
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if (key.equals(getString(R.string.settings_key_privacy_policy_accepted))) {
                    if (SettingsStore.getInstance(PlatformActivity.this).isPrivacyPolicyAccepted()) {
                        Log.d(TAG, "PushKit: privacy policy is accepted, calling getHmsMessageServiceToken");
                        enablePrivacySensitiveServices();
                    } else {
                        Log.d(TAG, "PushKit: privacy policy is denied, calling deleteHmsMessageServiceToken");
                        disablePrivacySensitiveServices();
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "PlatformActivity onCreate");
        super.onCreate(savedInstanceState);
        mLocationManager = new HVRLocationManager(this);
        PermissionDelegate.sPlatformLocationOverride = session -> mLocationManager.start(session);

        mHmsMessageBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "PushKit: mHmsMessageBroadcastReceiver " + intent);
                handlemHmsMessageBroadcast(intent);
            }
        };

        // We need to wait until the Privacy Policy is accepted before using remote services.
        if (SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
            enablePrivacySensitiveServices();
        }
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

        initializeAGConnect();

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

        RemoteMessage message = intent.getParcelableExtra(WolvicHmsMessageService.MESSAGE_EXTRA);
        Log.i(TAG, "PushKit: received remote message " + message);

        String title = null;
        String body = null;
        SystemNotification.Action action = null;

        if (message.getNotification() != null && message.getNotification().getTitle() != null) {
            Log.d(TAG, "PushKit: message has a Notification object");
            // the message was already parsed into a notification object
            RemoteMessage.Notification remoteNotification = message.getNotification();
            title = remoteNotification.getTitle();
            body = remoteNotification.getBody();

            if (remoteNotification.getClickAction() != null) {
                try {
                    JSONObject actionJson = new JSONObject(remoteNotification.getClickAction());
                    action = new SystemNotification.Action(actionJson.optInt("type"),
                            actionJson.optString("intent"),
                            actionJson.optString("url"),
                            actionJson.optString("action"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // the message data contains a JSON description
            String dataString = message.getData();

            Log.d(TAG, "PushKit: message has data: " + message.getData());

            try {
                JSONObject dataJson = new JSONObject(dataString);
                JSONObject messageJson = dataJson.getJSONObject("message");

                JSONObject notificationJson = messageJson.optJSONObject("notification");
                if (notificationJson != null) {
                    title = notificationJson.getString("title");
                    body = notificationJson.getString("body");
                }

                JSONObject androidJson = messageJson.optJSONObject("android");
                if (androidJson != null) {
                    JSONObject androidNotificationJson = androidJson.optJSONObject("notification");
                    if (androidNotificationJson != null) {
                        String androidTitle = androidNotificationJson.optString("title");
                        title = androidTitle.isEmpty() ? title : androidTitle;
                        String androidBody = androidNotificationJson.optString("body");
                        body = androidBody.isEmpty() ? body : androidBody;
                        JSONObject actionJson = androidNotificationJson.getJSONObject("click_action");
                        action = new SystemNotification.Action(actionJson.optInt("type"),
                                actionJson.optString("intent"),
                                actionJson.optString("url"),
                                actionJson.optString("action"));
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "PushKit: error parsing JSON: " + e);
                e.printStackTrace();
            }
        }

        Log.d(TAG, "PushKit: title= " + title + " , body= " + body + " , action= " + action);

        if (title != null && body != null) {
            SystemNotification systemNotification = new SystemNotification(title, body, action, Calendar.getInstance());
            Log.i(TAG, "PushKit: created SystemNotification: " + systemNotification);
            // FIXME here and in a few other places we cast the current Context to WidgetManagerDelegate
            UIWidget parentWidget = this.getTray();
            SystemNotificationsManager.getInstance().addNewSystemNotification(systemNotification, parentWidget);
        }
    }

    private void showPhoneUI() {
        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        manager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // create the activity again, so the theme is set up properly
                popBackHandler(mPhoneBackHandler);
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
        pushBackHandler(mPhoneBackHandler);
    }

    private void initializeVR() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setTheme(R.style.FxR_Dark);
        SurfaceView mView = new SurfaceView(this);
        setContentView(mView);

        mView.getHolder().addCallback(this);
        new LibUpdateClient(this).runUpdate();
        nativeOnCreate();
    }

    private void initializeAGConnect() {
        try {
            String speechService = SettingsStore.getInstance(this).getVoiceSearchService();
            if (SpeechServices.HUAWEI_ASR.equals(speechService) && StringUtils.isEmpty(BuildConfig.HVR_API_KEY)) {
                Log.e(TAG, "HVR API key is not available");
                return;
            }
            MLApplication.getInstance().setApiKey(BuildConfig.HVR_API_KEY);
            TelemetryService.setService(new HVRTelemetry(this));
            try {
                SpeechRecognizer speechRecognizer = SpeechServices.getInstance(this, speechService);
                ((VRBrowserApplication) getApplicationContext()).setSpeechRecognizer(speechRecognizer);
            } catch (Exception e) {
                Log.e(TAG, "Exception creating the speech recognizer: " + e);
                ((VRBrowserApplication) getApplicationContext()).setSpeechRecognizer(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enablePrivacySensitiveServices() {
        Log.d(TAG, "Privacy policy is accepted, initializing services");
        HiAnalyticsInstance instance = HiAnalytics.getInstance(getApplicationContext());
        instance.setUserProfile("userKey", BuildConfig.HVR_API_KEY);
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            HiAnalyticsTools.enableLog();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(WolvicHmsMessageService.MESSAGE_RECEIVED_ACTION);
        registerReceiver(mHmsMessageBroadcastReceiver, filter);

        setHmsMessageServiceAutoInit(true);
        getHmsMessageServiceToken();
    }

    private void disablePrivacySensitiveServices() {
        unregisterReceiver(mHmsMessageBroadcastReceiver);
        setHmsMessageServiceAutoInit(false);
        deleteHmsMessageServiceToken();
    }


    private void setHmsMessageServiceAutoInit(boolean enabled) {
        Log.d(TAG, "PushKit: setHmsMessageServiceAutoInit");
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_AUTO_INIT);
        intent.putExtra(WolvicHmsMessageService.ENABLED_EXTRA, enabled);
        startService(intent);
    }

    private void getHmsMessageServiceToken() {
        Log.d(TAG, "PushKit: getHmsMessageServiceToken");
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_GET_TOKEN);
        startService(intent);
    }

    private void deleteHmsMessageServiceToken() {
        Log.d(TAG, "PushKit: deleteHmsMessageServiceToken");
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_DELETE_TOKEN);
        startService(intent);
    }

    private void stopHmsMessageService() {
        Log.d(TAG, "PushKit: stopHmsMessageService");
        Intent intent = new Intent(this, WolvicHmsMessageService.class);
        intent.putExtra(WolvicHmsMessageService.COMMAND, WolvicHmsMessageService.COMMAND_STOP_SERVICE);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "PlatformActivity onDestroy");

        stopHmsMessageService();
        unregisterReceiver(mHmsMessageBroadcastReceiver);
        mPrefs.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

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
        queueRunnable(this::nativeOnStop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "PlatformActivity onPause");
        queueRunnable(this::nativeOnPause);
    }

    @Override
    public void onBackPressed() {
        queueRunnable(() -> {
            finish();
            System.exit(0);
        });
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
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        Log.i(TAG, "PlatformActivity surfaceDestroyed");
        queueRunnable(this::nativeOnSurfaceDestroyed);
    }

    public final PlatformActivityPlugin createPlatformPlugin(WidgetManagerDelegate delegate) { return null; }

    protected boolean platformExit() {
        return false;
    }
    protected native void queueRunnable(Runnable aRunnable);
    protected native void nativeOnCreate();
    protected native void nativeOnDestroy();
    protected native void nativeOnPause();
    protected native void nativeOnStop();
    protected native void nativeOnResume();
    protected native void nativeOnSurfaceChanged(Surface surface);
    protected native void nativeOnSurfaceDestroyed();
    protected static native boolean nativeIsPositionTrackingSupported();
}



