/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.databinding.OptionsLoginsBinding;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

@SuppressLint("ViewConstructor")
class LoginAndPasswordsOptionsView extends SettingsView {

    private OptionsLoginsBinding mBinding;

    public LoginAndPasswordsOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_logins, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> mDelegate.showView(SettingViewType.PRIVACY));

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(view -> resetOptions());

        mBinding.autocompleteSwitch.setOnCheckedChangeListener(mAutocompleteListener);
        setAutocomplete(SettingsStore.getInstance(getContext()).isLoginAutocompleteEnabled(), false);

        mBinding.autofillSwitch.setOnCheckedChangeListener(mAutoFillListener);
        setAutoFill(SettingsStore.getInstance(getContext()).isAutoFillEnabled(), false);

        mBinding.loginSyncSwitch.setOnCheckedChangeListener(mLoginSyncListener);
        setLoginSync(SettingsStore.getInstance(getContext()).isLoginSyncEnabled(), false);

        mBinding.accountButton.setOnClickListener(view -> mDelegate.showView((SettingViewType.FXA)));
        mBinding.savedLoginsButton.setOnClickListener(view -> mDelegate.showView((SettingViewType.SAVED_LOGINS)));
        mBinding.exceptionsButton.setOnClickListener(view -> mDelegate.showView((SettingViewType.LOGIN_EXCEPTIONS)));
    }

    private SwitchSetting.OnCheckedChangeListener mAutocompleteListener = (compoundButton, value, doApply) -> setAutocomplete(value, doApply);

    private SwitchSetting.OnCheckedChangeListener mAutoFillListener = (compoundButton, value, doApply) -> setAutoFill(value, doApply);

    private SwitchSetting.OnCheckedChangeListener mLoginSyncListener = (compoundButton, value, doApply) -> setLoginSync(value, doApply);

    private void setAutocomplete(boolean value, boolean doApply) {
        mBinding.autocompleteSwitch.setOnCheckedChangeListener(null);
        mBinding.autocompleteSwitch.setValue(value, false);
        mBinding.autocompleteSwitch.setOnCheckedChangeListener(mAutocompleteListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setLoginAutocompleteEnabled(value);
        }
    }

    private void setAutoFill(boolean value, boolean doApply) {
        mBinding.autofillSwitch.setOnCheckedChangeListener(null);
        mBinding.autofillSwitch.setValue(value, false);
        mBinding.autofillSwitch.setOnCheckedChangeListener(mAutoFillListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setAutoFillEnabled(value);
            EngineProvider.INSTANCE.getOrCreateRuntime(getContext()).getSettings().setLoginAutofillEnabled(value);
        }
    }

    private void setLoginSync(boolean value, boolean doApply) {
        mBinding.loginSyncSwitch.setOnCheckedChangeListener(null);
        mBinding.loginSyncSwitch.setValue(value, false);
        mBinding.loginSyncSwitch.setOnCheckedChangeListener(mLoginSyncListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setLoginSyncEnabled(value);
        }
    }

    private void resetOptions() {
        if (mBinding.autocompleteSwitch.isChecked() != SettingsStore.AUTOCOMPLETE_ENABLED) {
            setAutocomplete(SettingsStore.AUTOCOMPLETE_ENABLED, true);
        }

        if (mBinding.autofillSwitch.isChecked() != SettingsStore.AUTOFILL_ENABLED) {
            setAutoFill(SettingsStore.AUTOFILL_ENABLED, true);
        }

        if (mBinding.loginSyncSwitch.isChecked() != SettingsStore.LOGIN_SYNC_DEFAULT) {
            setLoginSync(SettingsStore.LOGIN_SYNC_DEFAULT, true);
        }
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.LOGINS_AND_PASSWORDS;
    }
}
