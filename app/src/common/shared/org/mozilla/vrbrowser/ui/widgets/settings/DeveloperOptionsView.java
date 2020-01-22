/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsDeveloperBinding;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

class DeveloperOptionsView extends SettingsView {

    private OptionsDeveloperBinding mBinding;

    public DeveloperOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

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

        mBinding.showConsoleSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        setConsoleLogs(SettingsStore.getInstance(getContext()).isConsoleLogsEnabled(), false);

        mBinding.multiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        setMultiprocess(SettingsStore.getInstance(getContext()).isMultiprocessEnabled(), false);

        mBinding.performanceMonitorSwitch.setOnCheckedChangeListener(mPerformanceListener);
        setPerformance(SettingsStore.getInstance(getContext()).isPerformanceMonitorEnabled(), false);
        // Hide Performance Monitor switch until it can handle multiple windows.
        mBinding.performanceMonitorSwitch.setVisibility(View.GONE);

        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(mUIHardwareAccelerationListener);
        setUIHardwareAcceleration(SettingsStore.getInstance(getContext()).isUIHardwareAccelerationEnabled(), false);

        if (BuildConfig.DEBUG) {
            mBinding.debugLoggingSwitch.setVisibility(View.GONE);
        } else {
            setDebugLogging(SettingsStore.getInstance(getContext()).isDebugLoggingEnabled(), false);
        }

        if (!isServoAvailable()) {
            mBinding.servoSwitch.setVisibility(View.GONE);

        } else {
            mBinding.servoSwitch.setOnCheckedChangeListener(mServoListener);
            setServo(SettingsStore.getInstance(getContext()).isServoEnabled(), false);
        }
    }

    private SwitchSetting.OnCheckedChangeListener mRemoteDebuggingListener = (compoundButton, value, doApply) -> {
        setRemoteDebugging(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mConsoleLogsListener = (compoundButton, value, doApply) -> {
        setConsoleLogs(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mMultiprocessListener = (compoundButton, value, doApply) -> {
        setMultiprocess(value, doApply);
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

    private SwitchSetting.OnCheckedChangeListener mServoListener = (compoundButton, b, doApply) -> {
        setServo(b, true);
    };

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;
        if (mBinding.remoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
            setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
        }

        if (mBinding.showConsoleSwitch.isChecked() != SettingsStore.CONSOLE_LOGS_DEFAULT) {
            setConsoleLogs(SettingsStore.CONSOLE_LOGS_DEFAULT, true);
        }
        if (mBinding.multiprocessSwitch.isChecked() != SettingsStore.MULTIPROCESS_DEFAULT) {
            setMultiprocess(SettingsStore.MULTIPROCESS_DEFAULT, true);
        }
        if (mBinding.servoSwitch.isChecked() != SettingsStore.SERVO_DEFAULT) {
            setServo(SettingsStore.SERVO_DEFAULT, true);
        }

        if (mBinding.performanceMonitorSwitch.isChecked() != SettingsStore.PERFORMANCE_MONITOR_DEFAULT) {
            setPerformance(SettingsStore.PERFORMANCE_MONITOR_DEFAULT, true);
        }

        if (mBinding.debugLoggingSwitch.isChecked() != SettingsStore.DEBUG_LOGGING_DEFAULT) {
            setDebugLogging(SettingsStore.DEBUG_LOGGING_DEFAULT, true);
            restart = true;
        }

        if (mBinding.hardwareAccelerationSwitch.isChecked() != SettingsStore.UI_HARDWARE_ACCELERATION_DEFAULT) {
            setUIHardwareAcceleration(SettingsStore.UI_HARDWARE_ACCELERATION_DEFAULT, true);
            restart = true;
        }

        if (restart) {
            showRestartDialog();
        }
    };

    private void setRemoteDebugging(boolean value, boolean doApply) {
        mBinding.remoteDebuggingSwitch.setOnCheckedChangeListener(null);
        mBinding.remoteDebuggingSwitch.setValue(value, doApply);
        mBinding.remoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);

        SettingsStore.getInstance(getContext()).setRemoteDebuggingEnabled(value);

        if (doApply) {
            SessionStore.get().setRemoteDebugging(value);
        }
    }

    private void setConsoleLogs(boolean value, boolean doApply) {
        mBinding.showConsoleSwitch.setOnCheckedChangeListener(null);
        mBinding.showConsoleSwitch.setValue(value, doApply);
        mBinding.showConsoleSwitch.setOnCheckedChangeListener(mConsoleLogsListener);

        SettingsStore.getInstance(getContext()).setConsoleLogsEnabled(value);

        if (doApply) {
            SessionStore.get().setConsoleOutputEnabled(value);
        }
    }

    private void setMultiprocess(boolean value, boolean doApply) {
        mBinding.multiprocessSwitch.setOnCheckedChangeListener(null);
        mBinding.multiprocessSwitch.setValue(value, false);
        mBinding.multiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);

        SettingsStore.getInstance(getContext()).setMultiprocessEnabled(value);

        if (doApply) {
            SessionStore.get().resetMultiprocess();
        }
    }

    private void setUIHardwareAcceleration(boolean value, boolean doApply) {
        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(null);
        mBinding.hardwareAccelerationSwitch.setValue(value, false);
        mBinding.hardwareAccelerationSwitch.setOnCheckedChangeListener(mUIHardwareAccelerationListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setUIHardwareAccelerationEnabled(value);
            showRestartDialog();
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
            showRestartDialog();
        }
    }

    private void setServo(boolean value, boolean doApply) {
        mBinding.servoSwitch.setOnCheckedChangeListener(null);
        mBinding.servoSwitch.setValue(value, false);
        mBinding.servoSwitch.setOnCheckedChangeListener(mServoListener);

        SettingsStore.getInstance(getContext()).setServoEnabled(value);

        if (doApply) {
            SessionStore.get().setServo(value);
        }
    }

}
