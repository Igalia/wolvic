/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsEnvironmentBinding;
import org.mozilla.vrbrowser.ui.views.settings.ImageRadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

class EnvironmentOptionsView extends SettingsView {

    private OptionsEnvironmentBinding mBinding;
    private ImageRadioGroupSetting mEnvironmentsRadio;

    public EnvironmentOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_environment, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setResetClickListener(mResetListener);

        String env = SettingsStore.getInstance(getContext()).getEnvironment();
        mEnvironmentsRadio = findViewById(R.id.environmentRadio);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);
        setEnv(mEnvironmentsRadio.getIdForValue(env), false);

        mBinding.envOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        setEnvOverride(SettingsStore.getInstance(getContext()).isEnvironmentOverrideEnabled());
        mBinding.envOverrideSwitch.setHelpDelegate(() -> {
            SessionStore.get().getActiveSession().loadUri(getContext().getString(R.string.environment_override_help_url));
            exitWholeSettings();
        });
    }

    @Override
    public void onShown() {
        super.onShown();

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_NO_DIM_BRIGHTNESS);
    }


    @Override
    public void onHidden() {
        mWidgetManager.popWorldBrightness(this);
    }

    private void setEnvOverride(boolean value) {
        mBinding.envOverrideSwitch.setOnCheckedChangeListener(null);
        mBinding.envOverrideSwitch.setValue(value, false);
        mBinding.envOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);

        SettingsStore.getInstance(getContext()).setEnvironmentOverrideEnabled(value);
        mWidgetManager.updateEnvironment();
    }

    private OnClickListener mResetListener = (view) -> {
        boolean updated = false;
        if (mBinding.envOverrideSwitch.isChecked() != SettingsStore.ENV_OVERRIDE_DEFAULT) {
            setEnvOverride(SettingsStore.ENV_OVERRIDE_DEFAULT);
            updated = true;
        }

        if (!mEnvironmentsRadio.getValueForId(mEnvironmentsRadio.getCheckedRadioButtonId()).equals(SettingsStore.ENV_DEFAULT)) {
            setEnv(mEnvironmentsRadio.getIdForValue(SettingsStore.ENV_DEFAULT), true);
            updated = true;
        }

        if (updated) {
            mWidgetManager.updateEnvironment();
        }
    };

    private SwitchSetting.OnCheckedChangeListener mEnvOverrideListener = (compoundButton, value, doApply) -> {
        setEnvOverride(value);
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
