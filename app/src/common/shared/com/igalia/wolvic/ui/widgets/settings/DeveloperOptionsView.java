/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.OptionsDeveloperBinding;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.dialogs.RestartDialogWidget;
import com.igalia.wolvic.utils.DeviceType;

class DeveloperOptionsView extends SettingsView {

    private OptionsDeveloperBinding mBinding;

    public DeveloperOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_developer, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        // Switches
        mBinding.remoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        setRemoteDebugging(SettingsStore.getInstance(getContext()).isRemoteDebuggingEnabled(), false);

        mBinding.debugLoggingSwitch.setOnCheckedChangeListener(mDebugLogginListener);
        setDebugLogging(SettingsStore.getInstance(getContext()).isDebugLoggingEnabled(), false);

        mBinding.performanceMonitorSwitch.setOnCheckedChangeListener(mPerformanceListener);
        setPerformance(SettingsStore.getInstance(getContext()).isPerformanceMonitorEnabled(), false);
        // Hide Performance Monitor switch until it can handle multiple windows.
        mBinding.performanceMonitorSwitch.setVisibility(View.GONE);

        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(mUIHardwareAccelerationListener);
        setUIHardwareAcceleration(SettingsStore.getInstance(getContext()).isUIHardwareAccelerationEnabled(), false);

        mBinding.bypassCacheOnReloadSwitch.setOnCheckedChangeListener(mBypassCacheOnReloadListener);
        setBypassCacheOnReload(SettingsStore.getInstance(getContext()).isBypassCacheOnReloadEnabled(), false);

        if (BuildConfig.DEBUG) {
            mBinding.webglOutOfProcessSwitch.setOnCheckedChangeListener(mWebGLOutOfProcessListener);
            setWebGLOutOfProcess(SettingsStore.getInstance(getContext()).isWebGLOutOfProcess(), false);
        } else {
            mBinding.webglOutOfProcessSwitch.setVisibility(View.GONE);
        }

        mBinding.localAddonSwitch.setOnCheckedChangeListener(mLocalAddonListener);
        setLocalAddon(SettingsStore.getInstance(getContext()).isLocalAddonAllowed(), false);
    }

    private SwitchSetting.OnCheckedChangeListener mRemoteDebuggingListener = (compoundButton, value, doApply) -> {
        setRemoteDebugging(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mPerformanceListener = (compoundButton, value, doApply) -> {
        setPerformance(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mDebugLogginListener = (compoundButton, value, doApply) -> {
        setDebugLogging(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mUIHardwareAccelerationListener = (compoundButton, value, doApply) -> {
        setUIHardwareAcceleration(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mBypassCacheOnReloadListener = (compundButton, value, doApply) -> {
        setBypassCacheOnReload(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mWebGLOutOfProcessListener = (compundButton, value, doApply) -> {
        setWebGLOutOfProcess(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mLocalAddonListener = (compoundButton, value, doApply) -> {
        setLocalAddon(value, doApply);
    };

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;

        if (mBinding.remoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
            setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
        }

        if (mBinding.performanceMonitorSwitch.isChecked() != SettingsStore.PERFORMANCE_MONITOR_DEFAULT) {
            setPerformance(SettingsStore.PERFORMANCE_MONITOR_DEFAULT, true);
        }

        boolean prevDebugLoggingSelection = mBinding.debugLoggingSwitch.isChecked();
        if (prevDebugLoggingSelection != SettingsStore.DEBUG_LOGGING_DEFAULT) {
            setDebugLogging(SettingsStore.DEBUG_LOGGING_DEFAULT, true);
            restart = true;
        }

        boolean prevHardwareAccelerationSelection = mBinding.hardwareAccelerationSwitch.isChecked();
        if (prevHardwareAccelerationSelection != SettingsStore.UI_HARDWARE_ACCELERATION_DEFAULT) {
            setUIHardwareAcceleration(SettingsStore.UI_HARDWARE_ACCELERATION_DEFAULT, true);
            restart = true;
        }

        if (mBinding.bypassCacheOnReloadSwitch.isChecked() != SettingsStore.BYPASS_CACHE_ON_RELOAD) {
            setBypassCacheOnReload(SettingsStore.BYPASS_CACHE_ON_RELOAD, true);
        }

        boolean prevWebglOutOfProcessSelection = mBinding.webglOutOfProcessSwitch.isChecked();
        if (BuildConfig.DEBUG && prevWebglOutOfProcessSelection != SettingsStore.WEBGL_OUT_OF_PROCESS) {
            setWebGLOutOfProcess(SettingsStore.WEBGL_OUT_OF_PROCESS, true);
            restart = true;
        }

        if (mBinding.localAddonSwitch.isChecked() != SettingsStore.LOCAL_ADDON_ALLOWED) {
            setLocalAddon(SettingsStore.LOCAL_ADDON_ALLOWED, true);
        }

        if (restart) {
            showRestartDialog(() -> {
                setDebugLogging(prevDebugLoggingSelection, true);
                setUIHardwareAcceleration(prevHardwareAccelerationSelection, true);
                setWebGLOutOfProcess(prevWebglOutOfProcessSelection, true);
            });
        }
    };

    private RestartDialogWidget.CancelCallback cancelReset(boolean restartDebugLogging,
                                                           boolean restartUIHardwareAcceleration,
                                                           boolean restartWebGLOutOfProcess) {
        RestartDialogWidget.CancelCallback cancelCallback = () -> {
            if (restartDebugLogging) {
                setDebugLogging(!SettingsStore.DEBUG_LOGGING_DEFAULT, true);
            }

            if (restartUIHardwareAcceleration) {
                setUIHardwareAcceleration(!SettingsStore.UI_HARDWARE_ACCELERATION_DEFAULT, true);
            }

            if (restartWebGLOutOfProcess) {
                setWebGLOutOfProcess(!SettingsStore.WEBGL_OUT_OF_PROCESS, true);
            }
        };

        return cancelCallback;
    }

    private void setRemoteDebugging(boolean value, boolean doApply) {
        mBinding.remoteDebuggingSwitch.setOnCheckedChangeListener(null);
        mBinding.remoteDebuggingSwitch.setValue(value, doApply);
        mBinding.remoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);

        SettingsStore.getInstance(getContext()).setRemoteDebuggingEnabled(value);

        if (doApply) {
            SessionStore.get().setRemoteDebugging(value);
        }
    }

    private void setUIHardwareAcceleration(boolean value, boolean doApply) {
        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(null);
        mBinding.hardwareAccelerationSwitch.setValue(value, false);
        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(mUIHardwareAccelerationListener);
        mBinding.hardwareAccelerationSwitch.setVisibility(DeviceType.getType() == DeviceType.MagicLeap2 ? View.GONE : View.VISIBLE);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setUIHardwareAccelerationEnabled(value);
            showRestartDialog(() -> {
                setUIHardwareAcceleration(!value, true);
            });
        }
    }

    private void setPerformance(boolean value, boolean doApply) {
        mBinding.performanceMonitorSwitch.setOnCheckedChangeListener(null);
        mBinding.performanceMonitorSwitch.setValue(value, false);
        mBinding.performanceMonitorSwitch.setOnCheckedChangeListener(mPerformanceListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPerformanceMonitorEnabled(value);
        }
    }

    private void setDebugLogging(boolean value, boolean doApply) {
        mBinding.debugLoggingSwitch.setOnCheckedChangeListener(null);
        mBinding.debugLoggingSwitch.setValue(value, false);
        mBinding.debugLoggingSwitch.setOnCheckedChangeListener(mDebugLogginListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setDebugLoggingEnabled(value);
            showRestartDialog(() -> {
                setDebugLogging(!value, true);
            });
        }
    }

    private void setBypassCacheOnReload(boolean value, boolean doApply) {
        mBinding.bypassCacheOnReloadSwitch.setOnCheckedChangeListener(null);
        mBinding.bypassCacheOnReloadSwitch.setValue(value, false);
        mBinding.bypassCacheOnReloadSwitch.setOnCheckedChangeListener(mBypassCacheOnReloadListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setBypassCacheOnReload(value);
        }
    }

    private void setWebGLOutOfProcess(boolean value, boolean doApply) {
        mBinding.webglOutOfProcessSwitch.setOnCheckedChangeListener(null);
        mBinding.webglOutOfProcessSwitch.setValue(value, false);
        mBinding.webglOutOfProcessSwitch.setOnCheckedChangeListener(mWebGLOutOfProcessListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setWebGLOutOfProcess(value);
            showRestartDialog(() -> {
                setWebGLOutOfProcess(!value, true);
            });
        }
    }

    private void setLocalAddon(boolean value, boolean doApply) {
        mBinding.localAddonSwitch.setOnCheckedChangeListener(null);
        mBinding.localAddonSwitch.setValue(value, false);
        mBinding.localAddonSwitch.setOnCheckedChangeListener(mLocalAddonListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setLocalAddonAllowed(value);
        }
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.LANGUAGE_VOICE;
    }

}
