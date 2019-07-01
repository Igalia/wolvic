/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.widget.ScrollView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.engine.SessionManager;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.ImageRadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

class EnvironmentOptionsView extends SettingsView {
    private AudioEngine mAudio;
    private UIButton mBackButton;
    private SwitchSetting mEnvOverrideSwitch;
    private ImageRadioGroupSetting mEnvironmentsRadio;
    private ButtonSetting mResetButton;
    private ScrollView mScrollbar;

    public EnvironmentOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_environment, this);

        mAudio = AudioEngine.fromContext(aContext);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        String env = SettingsStore.getInstance(getContext()).getEnvironment();
        mEnvironmentsRadio = findViewById(R.id.environmentRadio);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);
        setEnv(mEnvironmentsRadio.getIdForValue(env), false);

        mEnvOverrideSwitch = findViewById(R.id.envOverrideSwitch);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        setEnvOverride(SettingsStore.getInstance(getContext()).isEnvironmentOverrideEnabled());
        mEnvOverrideSwitch.setHelpDelegate(() -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            SessionManager.get().getActiveStore().loadUri(getContext().getString(R.string.environment_override_help_url));
            exitWholeSettings();
        });

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
    }

    @Override
    public void onShown() {
        super.onShown();

        mScrollbar.scrollTo(0, 0);
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_NO_DIM_BRIGHTNESS);
    }

    @Override
    public void onHidden() {
        mWidgetManager.popWorldBrightness(this);
    }

    private void setEnvOverride(boolean value) {
        mEnvOverrideSwitch.setOnCheckedChangeListener(null);
        mEnvOverrideSwitch.setValue(value, false);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);

        SettingsStore.getInstance(getContext()).setEnvironmentOverrideEnabled(value);
    }

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;
        if (mEnvOverrideSwitch.isChecked() != SettingsStore.ENV_OVERRIDE_DEFAULT) {
            setEnvOverride(SettingsStore.ENV_OVERRIDE_DEFAULT);
            restart = true;
        }

        if (!mEnvironmentsRadio.getValueForId(mEnvironmentsRadio.getCheckedRadioButtonId()).equals(SettingsStore.ENV_DEFAULT)) {
            setEnv(mEnvironmentsRadio.getIdForValue(SettingsStore.ENV_DEFAULT), true);
        }

        if (restart)
            showRestartDialog();
    };

    private SwitchSetting.OnCheckedChangeListener mEnvOverrideListener = (compoundButton, value, doApply) -> {
        setEnvOverride(value);
        showRestartDialog();
    };

    private ImageRadioGroupSetting.OnCheckedChangeListener mEnvsListener = (checkedId, doApply) -> {
        setEnv(checkedId, doApply);
    };

    private void setEnv(int checkedId, boolean doApply) {
        mEnvironmentsRadio.setOnCheckedChangeListener(null);
        mEnvironmentsRadio.setChecked(checkedId, doApply);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);

        SettingsStore.getInstance(getContext()).setEnvironment((String) mEnvironmentsRadio.getValueForId(checkedId));

        if (doApply) {
            mWidgetManager.updateEnvironment();
        }
    }

}
