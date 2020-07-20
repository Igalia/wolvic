/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsEnvironmentBinding;
import org.mozilla.vrbrowser.ui.views.settings.ImageRadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.Environment;
import org.mozilla.vrbrowser.utils.EnvironmentUtils;
import org.mozilla.vrbrowser.utils.EnvironmentsManager;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

class EnvironmentOptionsView extends SettingsView implements EnvironmentsManager.EnvironmentListener {

    private OptionsEnvironmentBinding mBinding;
    private ImageRadioGroupSetting mEnvironmentsRadio;
    private EnvironmentsManager mEnvironmentsManager;

    public EnvironmentOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mEnvironmentsManager = mWidgetManager.getServicesProvider().getEnvironmentsManager();

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_environment, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        mEnvironmentsRadio = findViewById(R.id.environmentRadio);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);
        updateEnvironments();

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

        mEnvironmentsManager.addListener(this);
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_NO_DIM_BRIGHTNESS);
    }

    @Override
    public void onHidden() {
        mWidgetManager.popWorldBrightness(this);
        mEnvironmentsManager.removeListener(this);
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

    private ImageRadioGroupSetting.OnCheckedChangeListener mEnvsListener = this::setEnv;

    private void setEnv(int checkedId, boolean doApply) {
        mEnvironmentsRadio.setOnCheckedChangeListener(null);
        mEnvironmentsRadio.setChecked(checkedId, doApply);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);

        String value = (String) mEnvironmentsRadio.getValueForId(checkedId);

        mEnvironmentsManager.setOrDownloadEnvironment(value);
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.ENVIRONMENT;
    }

    /**
     * Called every time the environments panel is opened. This method loads the cached remote properties
     * environments for the current app version and add all the existing environments to the environments list.
     */
    private void updateEnvironments() {
        String env = SettingsStore.getInstance(getContext()).getEnvironment();

        Environment[] properties = EnvironmentUtils.getExternalEnvironments(getContext());
        if (properties != null) {
            Arrays.stream(properties).forEach(environment -> {
                mEnvironmentsRadio.addOption(
                        environment.getValue(),
                        environment.getTitle(),
                        getContext().getDrawable(R.color.asphalt));

                BitmapCache.getInstance(getContext()).getBitmap(environment.getThumbnail()).thenAccept(bitmap -> {
                    if (bitmap == null) {
                        ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().backgroundThread().post(() -> {
                            try {
                                URL url = new URL(environment.getThumbnail());
                                Bitmap thumbnail = BitmapFactory.decodeStream(url.openStream());
                                ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread().execute(() -> {
                                    mEnvironmentsRadio.updateOption(
                                            environment.getValue(),
                                            environment.getTitle(),
                                            new BitmapDrawable(getContext().getResources(), thumbnail)
                                    );
                                    BitmapCache.getInstance(getContext()).addBitmap(environment.getThumbnail(), thumbnail);
                                });

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                    } else {
                        mEnvironmentsRadio.updateOption(
                                environment.getValue(),
                                environment.getValue(),
                                new BitmapDrawable(getContext().getResources(), bitmap)
                        );
                    }
                }).exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
            });
        }

        setEnv(mEnvironmentsRadio.getIdForValue(env), false);
    }

    @Override
    public void onEnvironmentSetSuccess(@NonNull String envId) {
        
    }

    @Override
    public void onEnvironmentSetError(@NonNull String error) {
        setEnv(mEnvironmentsRadio.getIdForValue(SettingsStore.getInstance(getContext()).getEnvironment()), false);
        mWidgetManager.getFocusedWindow().showAlert(
                getContext().getString(R.string.environment_error_title),
                error,
                null
        );
    }
}
