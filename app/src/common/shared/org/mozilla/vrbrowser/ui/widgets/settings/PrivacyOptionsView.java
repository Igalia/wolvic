/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.Manifest;
import android.content.Context;
import android.util.Pair;
import android.widget.ScrollView;
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

import java.util.ArrayList;

class PrivacyOptionsView extends SettingsView {
    private AudioEngine mAudio;
    private UIButton mBackButton;
    private SwitchSetting mTrackingSetting;
    private ButtonSetting mResetButton;
    private ArrayList<Pair<ButtonSetting, String>> mPermissionButtons;
    private int mAlertDialogHandle;
    private ScrollView mScrollbar;

    public PrivacyOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_privacy, this);
        mAudio = AudioEngine.fromContext(aContext);

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

        mTrackingSetting = findViewById(R.id.trackingProtectionButton);
        mTrackingSetting.setChecked(SettingsStore.getInstance(getContext()).isTrackingProtectionEnabled());
        mTrackingSetting.setOnCheckedChangeListener((compoundButton, enabled, apply) -> {
            SettingsStore.getInstance(getContext()).setTrackingProtectionEnabled(enabled);
            SessionStore.get().setTrackingProtection(enabled);
        });

        TextView permissionsTitleText = findViewById(R.id.permissionsTitle);
        permissionsTitleText.setText(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));

        mPermissionButtons = new ArrayList<>();
        mPermissionButtons.add(Pair.create(findViewById(R.id.cameraPermissionButton), Manifest.permission.CAMERA));
        mPermissionButtons.add(Pair.create(findViewById(R.id.microphonePermissionButton), Manifest.permission.RECORD_AUDIO));
        mPermissionButtons.add(Pair.create(findViewById(R.id.locationPermissionButton), Manifest.permission.ACCESS_FINE_LOCATION));
        mPermissionButtons.add(Pair.create(findViewById(R.id.storagePermissionButton), Manifest.permission.READ_EXTERNAL_STORAGE));

        for (Pair<ButtonSetting, String> button: mPermissionButtons) {
            if (mWidgetManager.isPermissionGranted(button.second)) {
                button.first.setShowAsLabel(true);
                button.first.setButtonText(getContext().getString(R.string.permission_enabled));
            }
            button.first.setOnClickListener(v -> {
                togglePermission(button.first, button.second);
            });
        }


        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(v -> resetOptions());
    }

    @Override
    public void onShown() {
        super.onShown();
        mScrollbar.scrollTo(0, 0);
    }

    @Override
    public void onHidden() {
        super.onHidden();
    }

    private void togglePermission(ButtonSetting aButton, String aPermission) {
        if (mWidgetManager.isPermissionGranted(aPermission)) {
            showAlert(aButton.getDescription(), getContext().getString(R.string.security_options_permissions_reject_message));
        } else {
            mWidgetManager.requestPermission("", aPermission, new GeckoSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    aButton.setShowAsLabel(true);
                    aButton.setButtonText(getContext().getString(R.string.permission_enabled));
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
}
