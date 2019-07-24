/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.DeviceType;

import java.util.ArrayList;

class PrivacyOptionsView extends SettingsView {
    private AudioEngine mAudio;
    private UIButton mBackButton;
    private SwitchSetting mDrmContentPlaybackSwitch;
    private SwitchSetting mTrackingSetting;
    private SwitchSetting mNotificationsPermissionSwitch;
    private ButtonSetting mResetButton;
    private ArrayList<Pair<SwitchSetting, String>> mPermissionButtons;
    private SwitchSetting mSpeechDataSwitch;
    private SwitchSetting mTelemetryDataSwitch;
    private SwitchSetting mCrashreportsDataSwitch;

    public PrivacyOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_privacy, this);
        mAudio = AudioEngine.fromContext(aContext);

        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(mLifeCycleListener);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            onDismiss();
        });

        mScrollbar = findViewById(R.id.scrollbar);

        ButtonSetting privacyPolicy = findViewById(R.id.showPrivacyButton);
        privacyPolicy.setOnClickListener(v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            GeckoSession session = SessionStore.get().getCurrentSession();
            if (session == null) {
                int sessionId = SessionStore.get().createSession();
                SessionStore.get().setCurrentSession(sessionId);
            }

            SessionStore.get().loadUri(getContext().getString(R.string.private_policy_url));
            exitWholeSettings();
        });

        mDrmContentPlaybackSwitch = findViewById(R.id.drmContentPlaybackSwitch);
        mDrmContentPlaybackSwitch.setChecked(SettingsStore.getInstance(getContext()).isDrmContentPlaybackEnabled());
        mDrmContentPlaybackSwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) -> {
            SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(enabled);
            // TODO Enable/Disable DRM content playback
        });
        mDrmContentPlaybackSwitch.setLinkClickListner((widget, url) -> {
            SessionStore.get().loadUri(url);
            exitWholeSettings();
        });

        mTrackingSetting = findViewById(R.id.trackingProtectionSwitch);
        mTrackingSetting.setChecked(SettingsStore.getInstance(getContext()).isTrackingProtectionEnabled());
        mTrackingSetting.setOnCheckedChangeListener((compoundButton, enabled, apply) -> {
            SettingsStore.getInstance(getContext()).setTrackingProtectionEnabled(enabled);
            SessionStore.get().setTrackingProtection(enabled);
        });

        TextView permissionsTitleText = findViewById(R.id.permissionsTitle);
        permissionsTitleText.setText(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));

        mPermissionButtons = new ArrayList<>();
        mPermissionButtons.add(Pair.create(findViewById(R.id.cameraPermissionSwitch), Manifest.permission.CAMERA));
        mPermissionButtons.add(Pair.create(findViewById(R.id.microphonePermissionSwitch), Manifest.permission.RECORD_AUDIO));
        mPermissionButtons.add(Pair.create(findViewById(R.id.locationPermissionSwitch), Manifest.permission.ACCESS_FINE_LOCATION));
        mPermissionButtons.add(Pair.create(findViewById(R.id.storagePermissionSwitch), Manifest.permission.READ_EXTERNAL_STORAGE));

        if (DeviceType.isOculusBuild()) {
            findViewById(R.id.cameraPermissionSwitch).setVisibility(View.GONE);
        }

        for (Pair<SwitchSetting, String> button: mPermissionButtons) {
            button.first.setChecked(mWidgetManager.isPermissionGranted(button.second));
            button.first.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                    togglePermission(button.first, button.second));
        }

        mNotificationsPermissionSwitch = findViewById(R.id.notificationsPermissionSwitch);
        mNotificationsPermissionSwitch.setChecked(SettingsStore.getInstance(getContext()).isNotificationsEnabled());
        mNotificationsPermissionSwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) -> {
            SettingsStore.getInstance(getContext()).setNotificationsEnabled(enabled);
        });

        mSpeechDataSwitch = findViewById(R.id.speechDataSwitch);
        mSpeechDataSwitch.setChecked(SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled());
        mSpeechDataSwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                SettingsStore.getInstance(getContext()).setSpeechDataCollectionEnabled(enabled));

        mTelemetryDataSwitch = findViewById(R.id.telemetryDataSwitch);
        mTelemetryDataSwitch.setChecked(SettingsStore.getInstance(getContext()).isTelemetryEnabled());
        mTelemetryDataSwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                SettingsStore.getInstance(getContext()).setTelemetryEnabled(enabled));

        mCrashreportsDataSwitch = findViewById(R.id.crashReportsDataSwitch);
        mCrashreportsDataSwitch.setChecked(SettingsStore.getInstance(getContext()).isCrashReportingEnabled());
        mCrashreportsDataSwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                SettingsStore.getInstance(getContext()).setCrashReportingEnabled(enabled));

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(v -> resetOptions());
    }

    private void togglePermission(SwitchSetting aButton, String aPermission) {
        if (mWidgetManager.isPermissionGranted(aPermission)) {
            showAlert(aButton.getDescription(), getContext().getString(R.string.security_options_permissions_reject_message));
            aButton.setChecked(true);

        } else {
            mWidgetManager.requestPermission("", aPermission, new GeckoSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    aButton.setChecked(true);
                }
                @Override
                public void reject() {

                }
            });
        }
    }

    private void resetOptions() {
        if (mTrackingSetting.isChecked() != SettingsStore.TRACKING_DEFAULT) {
            mTrackingSetting.setChecked(SettingsStore.TRACKING_DEFAULT);
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height));
    }

    @Override
    public void releasePointerCapture() {
        super.releasePointerCapture();
        ((Application)getContext().getApplicationContext()).unregisterActivityLifecycleCallbacks(mLifeCycleListener);
    }

    private Application.ActivityLifecycleCallbacks mLifeCycleListener = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
            // Refresh permission settings status after a permission has been requested
            for (Pair<SwitchSetting, String> button: mPermissionButtons) {
                button.first.setValue(mWidgetManager.isPermissionGranted(button.second), false);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }
    };

}
