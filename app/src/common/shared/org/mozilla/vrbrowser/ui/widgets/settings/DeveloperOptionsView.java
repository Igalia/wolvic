/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.view.View;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;

import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

class DeveloperOptionsView extends SettingsView {
    private AudioEngine mAudio;
    private UIButton mBackButton;
    private SwitchSetting mRemoteDebuggingSwitch;
    private SwitchSetting mConsoleLogsSwitch;
    private SwitchSetting mMultiprocessSwitch;
    private SwitchSetting mPerformanceSwitch;
    private SwitchSetting mServoSwitch;
    private ButtonSetting mResetButton;

    public DeveloperOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_developer, this);

        mAudio = AudioEngine.fromContext(aContext);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        mRemoteDebuggingSwitch = findViewById(R.id.remote_debugging_switch);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        setRemoteDebugging(SettingsStore.getInstance(getContext()).isRemoteDebuggingEnabled(), false);

        mConsoleLogsSwitch = findViewById(R.id.show_console_switch);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        setConsoleLogs(SettingsStore.getInstance(getContext()).isConsoleLogsEnabled(), false);

        mMultiprocessSwitch = findViewById(R.id.multiprocess_switch);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        setMultiprocess(SettingsStore.getInstance(getContext()).isMultiprocessEnabled(), false);

        mPerformanceSwitch = findViewById(R.id.performance_monitor_switch);
        mPerformanceSwitch.setOnCheckedChangeListener(mPerformanceListener);
        setPerformance(SettingsStore.getInstance(getContext()).isPerformanceMonitorEnabled(), false);

        mServoSwitch = findViewById(R.id.servo_switch);
        if (!isServoAvailable()) {
            mServoSwitch.setVisibility(View.GONE);
        } else {
            mServoSwitch.setOnCheckedChangeListener(mServoListener);
            setServo(SettingsStore.getInstance(getContext()).isServoEnabled(), false);
        }

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
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

    private SwitchSetting.OnCheckedChangeListener mServoListener = (compoundButton, b, doApply) -> {
        setServo(b, true);
    };

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;
        if (mRemoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
            setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
            restart = true;
        }

        if (mConsoleLogsSwitch.isChecked() != SettingsStore.CONSOLE_LOGS_DEFAULT) {
            setConsoleLogs(SettingsStore.CONSOLE_LOGS_DEFAULT, true);
        }
        if (mMultiprocessSwitch.isChecked() != SettingsStore.MULTIPROCESS_DEFAULT) {
            setMultiprocess(SettingsStore.MULTIPROCESS_DEFAULT, true);
        }
        if (mServoSwitch.isChecked() != SettingsStore.SERVO_DEFAULT) {
            setServo(SettingsStore.SERVO_DEFAULT, true);
        }

        if (restart && mDelegate != null) {
            showRestartDialog();
        }
    };

    private void setRemoteDebugging(boolean value, boolean doApply) {
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(null);
        mRemoteDebuggingSwitch.setValue(value, doApply);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);

        SettingsStore.getInstance(getContext()).setRemoteDebuggingEnabled(value);

        if (doApply) {
            SessionStore.get().setRemoteDebugging(value);
        }
    }

    private void setConsoleLogs(boolean value, boolean doApply) {
        mConsoleLogsSwitch.setOnCheckedChangeListener(null);
        mConsoleLogsSwitch.setValue(value, doApply);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);

        SettingsStore.getInstance(getContext()).setConsoleLogsEnabled(value);

        if (doApply) {
            SessionStore.get().setConsoleOutputEnabled(value);
        }
    }

    private void setMultiprocess(boolean value, boolean doApply) {
        mMultiprocessSwitch.setOnCheckedChangeListener(null);
        mMultiprocessSwitch.setValue(value, false);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);

        SettingsStore.getInstance(getContext()).setMultiprocessEnabled(value);

        if (doApply) {
            SessionStore.get().setMultiprocess(value);
        }
    }

    private void setPerformance(boolean value, boolean doApply) {
        mPerformanceSwitch.setOnCheckedChangeListener(null);
        mPerformanceSwitch.setValue(value, false);
        mPerformanceSwitch.setOnCheckedChangeListener(mPerformanceListener);

        SettingsStore.getInstance(getContext()).setPerformanceMonitorEnabled(value);
    }

    private void setServo(boolean value, boolean doApply) {
        mServoSwitch.setOnCheckedChangeListener(null);
        mServoSwitch.setValue(value, false);
        mServoSwitch.setOnCheckedChangeListener(mServoListener);

        SettingsStore.getInstance(getContext()).setServoEnabled(value);

        if (doApply) {
            SessionStore.get().setServo(value);
        }
    }

}
