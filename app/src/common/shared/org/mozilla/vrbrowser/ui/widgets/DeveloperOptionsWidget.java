/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.settings.DoubleEditSetting;
import org.mozilla.vrbrowser.ui.settings.SingleEditSetting;
import org.mozilla.vrbrowser.ui.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.settings.SwitchSetting;

public class DeveloperOptionsWidget extends UIWidget {

    private static final String LOGTAG = "VRB";

    private AudioEngine mAudio;
    private UIButton mBackButton;

    private SwitchSetting mRemoteDebuggingSwitch;
    private SwitchSetting mConsoleLogsSwitch;
    private SwitchSetting mEnvOverrideSwitch;
    private SwitchSetting mMultiprocessSwitch;

    private RadioGroupSetting mEnvironmentsRadio;
    private RadioGroupSetting mPointerColorRadio;
    private RadioGroupSetting mUaModeRadio;
    private RadioGroupSetting mMSAARadio;
    private RadioGroupSetting mEventsRadio;

    private SingleEditSetting mDensityEdit;
    private SingleEditSetting mDpiEdit;
    private DoubleEditSetting mWindowSizeEdit;
    private DoubleEditSetting mMaxWindowSizeEdit;

    private ButtonSetting mResetButton;

    private int mRestartDialogHandle = -1;

    public DeveloperOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public DeveloperOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public DeveloperOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.developer_options, this);

        mAudio = AudioEngine.fromContext(aContext);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                onDismiss();
            }
        });

        mRemoteDebuggingSwitch = findViewById(R.id.remote_debugging_switch);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        setRemoteDebugging(SettingsStore.getInstance(getContext()).isRemoteDebuggingEnabled(), false);

        mConsoleLogsSwitch = findViewById(R.id.show_console_switch);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        setConsoleLogs(SettingsStore.getInstance(getContext()).isConsoleLogsEnabled(), false);

        mEnvOverrideSwitch = findViewById(R.id.env_override_switch);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        setEnvOverride(SettingsStore.getInstance(getContext()).isEnvironmentOverrideEnabled());

        mMultiprocessSwitch = findViewById(R.id.multiprocess_switch);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        setMultiprocess(SettingsStore.getInstance(getContext()).isMultiprocessEnabled(), false);

        String env = SettingsStore.getInstance(getContext()).getEnvironment();
        mEnvironmentsRadio = findViewById(R.id.environment_radio);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);
        setEnv(mEnvironmentsRadio.getIdForValue(env), false);

        int color = SettingsStore.getInstance(getContext()).getPointerColor();
        mPointerColorRadio = findViewById(R.id.pointer_radio);
        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);
        setPointerColor(mPointerColorRadio.getIdForValue(color), false);

        int uaMode = SettingsStore.getInstance(getContext()).getUaMode();
        mUaModeRadio = findViewById(R.id.ua_radio);
        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(mUaModeRadio.getIdForValue(uaMode), false);

        int msaaLevel = SettingsStore.getInstance(getContext()).getMSAALevel();
        mMSAARadio = findViewById(R.id.msaa_radio);
        mMSAARadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setMSAAMode(mMSAARadio.getIdForValue(msaaLevel), false);

        int inputMode = SettingsStore.getInstance(getContext()).getInputMode();
        mEventsRadio = findViewById(R.id.events_radio);
        mEventsRadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setInputMode(mEventsRadio.getIdForValue(inputMode), false);

        mDensityEdit = findViewById(R.id.density_edit);
        mDensityEdit.setFirstText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mDensityEdit.setOnClickListener(mDensityListener);
        setDisplayDensity(SettingsStore.getInstance(getContext()).getDisplayDensity());

        mDpiEdit = findViewById(R.id.dpi_edit);
        mDpiEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mDpiEdit.setOnClickListener(mDpiListener);
        setDisplayDpi(SettingsStore.getInstance(getContext()).getDisplayDpi());

        mWindowSizeEdit = findViewById(R.id.windowSize_edit);
        mWindowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowWidth()));
        mWindowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowHeight()));
        mWindowSizeEdit.setOnClickListener(mWindowSizeListener);
        setWindowSize(
                SettingsStore.getInstance(getContext()).getWindowWidth(),
                SettingsStore.getInstance(getContext()).getWindowHeight(),
                false);

        mMaxWindowSizeEdit = findViewById(R.id.maxWindowSize_edit);
        mMaxWindowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowWidth()));
        mMaxWindowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowHeight()));
        mMaxWindowSizeEdit.setOnClickListener(mMaxWindowSizeListener);
        setMaxWindowSize(
                SettingsStore.getInstance(getContext()).getMaxWindowWidth(),
                SettingsStore.getInstance(getContext()).getMaxWindowHeight(),
                false);

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.developer_options_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_z);
    }

    private void showRestartDialog() {
        hide();

        UIWidget widget = getChild(mRestartDialogHandle);
        if (widget == null) {
            widget = createChild(RestartDialogWidget.class, false);
            mRestartDialogHandle = widget.getHandle();
            widget.setDelegate(new Delegate() {
                @Override
                public void onDismiss() {
                    onRestartDialogDismissed();
                }
            });
        }

        widget.show();
    }

    private void onRestartDialogDismissed() {
       show();
    }

    private SwitchSetting.OnCheckedChangeListener mRemoteDebuggingListener = new SwitchSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean value, boolean doApply) {
            setRemoteDebugging(value, doApply);
        }
    };

    private SwitchSetting.OnCheckedChangeListener mConsoleLogsListener = new SwitchSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean value, boolean doApply) {
            setConsoleLogs(value, doApply);
        }
    };

    private SwitchSetting.OnCheckedChangeListener mEnvOverrideListener = new SwitchSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean value, boolean doApply) {
            setEnvOverride(value);
            showRestartDialog();
        }
    };

    private SwitchSetting.OnCheckedChangeListener mMultiprocessListener = new SwitchSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean value, boolean doApply) {
            setMultiprocess(value, doApply);
        }
    };

    private RadioGroupSetting.OnCheckedChangeListener mUaModeListener = new RadioGroupSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId, boolean doApply) {
            setUaMode(checkedId, true);
        }
    };

    private RadioGroupSetting.OnCheckedChangeListener mMSSAChangeListener = new RadioGroupSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId, boolean doApply) {
            setMSAAMode(checkedId, true);
        }
    };

    private RadioGroupSetting.OnCheckedChangeListener mInputModeListener = new RadioGroupSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId, boolean doApply) {
            setInputMode(checkedId, doApply);
        }
    };

    private RadioGroupSetting.OnCheckedChangeListener mEnvsListener = new RadioGroupSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId, boolean doApply) {
            setEnv(checkedId, doApply);
        }
    };

    private RadioGroupSetting.OnCheckedChangeListener mPointerColorListener = new RadioGroupSetting.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId, boolean doApply) {
            setPointerColor(checkedId, doApply);
        }
    };

    private OnClickListener mDensityListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            float newDensity = Float.parseFloat(mDensityEdit.getFirstText());
            if (setDisplayDensity(newDensity)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mDpiListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            int newDpi = Integer.parseInt(mDpiEdit.getFirstText());
            if (setDisplayDpi(newDpi)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mWindowSizeListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            int newWindowWidth = Integer.parseInt(mWindowSizeEdit.getFirstText());
            int newWindowHeight = Integer.parseInt(mWindowSizeEdit.getSecondText());
            setWindowSize(newWindowWidth, newWindowHeight, true);
        }
    };

    private OnClickListener mMaxWindowSizeListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            int newMaxWindowWidth = Integer.parseInt(mMaxWindowSizeEdit.getFirstText());
            int newMaxWindowHeight = Integer.parseInt(mMaxWindowSizeEdit.getSecondText());
            setMaxWindowSize(newMaxWindowWidth, newMaxWindowHeight, true);
        }
    };

    private OnClickListener mResetListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            // Switches
            boolean restart = false;
            if (mRemoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
                setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
                restart = true;
            }

            setConsoleLogs(SettingsStore.CONSOLE_LOGS_DEFAULT, true);
            setMultiprocess(SettingsStore.MULTIPROCESS_DEFAULT, true);

            if (mEnvOverrideSwitch.isChecked() != SettingsStore.ENV_OVERRIDE_DEFAULT) {
                setEnvOverride(SettingsStore.ENV_OVERRIDE_DEFAULT);
                restart = true;
            }

            if (!mEnvironmentsRadio.getValueForId(mEnvironmentsRadio.getCheckedRadioButtonId()).equals(SettingsStore.ENV_DEFAULT)) {
                setEnv(mEnvironmentsRadio.getIdForValue(SettingsStore.ENV_DEFAULT), true);
            }

            // Radios
            setUaMode(mUaModeRadio.getIdForValue(SettingsStore.UA_MODE_DEFAULT), true);
            setMSAAMode(mMSAARadio.getIdForValue(SettingsStore.MSAA_DEFAULT_LEVEL), true);
            setInputMode(mEventsRadio.getIdForValue(SettingsStore.INPUT_MODE_DEFAULT), false);

            // Edits
            restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
            restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);

            if (restart)
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

    private void setEnvOverride(boolean value) {
        mEnvOverrideSwitch.setOnCheckedChangeListener(null);
        mEnvOverrideSwitch.setValue(value, false);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);

        SettingsStore.getInstance(getContext()).setEnvironmentOverrideEnabled(value);
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

    private void setUaMode(int checkId, boolean doApply) {
        mUaModeRadio.setOnCheckedChangeListener(null);
        mUaModeRadio.setChecked(checkId, doApply);
        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);

        SettingsStore.getInstance(getContext()).setUaMode(checkId);

        if (doApply) {
            SessionStore.get().setUaMode((Integer)mUaModeRadio.getValueForId(checkId));
        }
    }

    private void setMSAAMode(int checkedId, boolean doApply) {
        mMSAARadio.setOnCheckedChangeListener(null);
        mMSAARadio.setChecked(checkedId, doApply);
        mMSAARadio.setOnCheckedChangeListener(mMSSAChangeListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setMSAALevel((Integer)mMSAARadio.getValueForId(checkedId));
            showRestartDialog();
        }
    }

    private void setEnv(int checkedId, boolean doApply) {
        mEnvironmentsRadio.setOnCheckedChangeListener(null);
        mEnvironmentsRadio.setChecked(checkedId, doApply);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);

        SettingsStore.getInstance(getContext()).setEnvironment((String) mEnvironmentsRadio.getValueForId(checkedId));

        if (doApply) {
            mWidgetManager.updateEnvironment();
        }
    }

    private void setPointerColor(int checkedId, boolean doApply) {
        mPointerColorRadio.setOnCheckedChangeListener(null);
        mPointerColorRadio.setChecked(checkedId, doApply);
        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);

        SettingsStore.getInstance(getContext()).setPointerColor((int)mPointerColorRadio.getValueForId(checkedId));

        if (doApply) {
            mWidgetManager.updatePointerColor();
        }
    }

    private void setInputMode(int checkedId, boolean doApply) {
        mEventsRadio.setOnCheckedChangeListener(null);
        mEventsRadio.setChecked(checkedId, doApply);
        mEventsRadio.setOnCheckedChangeListener(mInputModeListener);

        SettingsStore.getInstance(getContext()).setInputMode((Integer)mEventsRadio.getValueForId(checkedId));
        // TODO: Wire it up
    }

    private boolean setDisplayDensity(float newDensity) {
        mDensityEdit.setOnClickListener((SingleEditSetting.OnClickListener)null);
        boolean restart = false;
        float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
        if (newDensity <= 0) {
            newDensity = prevDensity;

        } else if (prevDensity != newDensity) {
            SettingsStore.getInstance(getContext()).setDisplayDensity(newDensity);
            restart = true;
        }
        mDensityEdit.setFirstText(Float.toString(newDensity));
        mDensityEdit.setOnClickListener(mDensityListener);

        return restart;
    }

    private boolean setDisplayDpi(int newDpi) {
        mDpiEdit.setOnClickListener((SingleEditSetting.OnClickListener)null);
        boolean restart = false;
        int prevDensity = SettingsStore.getInstance(getContext()).getDisplayDpi();
        if (newDpi <= 0) {
            newDpi = prevDensity;

        } else if (prevDensity != newDpi) {
            SettingsStore.getInstance(getContext()).setDisplayDpi(newDpi);
            restart = true;
        }
        mDpiEdit.setFirstText(Integer.toString(newDpi));
        mDpiEdit.setOnClickListener(mDpiListener);

        return restart;
    }

    private void setWindowSize(int newWindowWidth, int newWindowHeight, boolean doApply) {
        int prevWindowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        if (newWindowWidth <= 0) {
            newWindowWidth = prevWindowWidth;
        }

        int prevWindowHeight = SettingsStore.getInstance(getContext()).getWindowHeight();
        if (newWindowHeight <= 0) {
            newWindowHeight = prevWindowHeight;
        }

        int maxWindowWidth = SettingsStore.getInstance(getContext()).getMaxWindowWidth();
        if (newWindowWidth > maxWindowWidth) {
            newWindowWidth = maxWindowWidth;
        }

        int maxWindowHeight = SettingsStore.getInstance(getContext()).getMaxWindowHeight();
        if (newWindowHeight > maxWindowHeight) {
            newWindowHeight = maxWindowHeight;
        }

        if (prevWindowWidth != newWindowWidth || prevWindowHeight != newWindowHeight) {
            SettingsStore.getInstance(getContext()).setWindowWidth(newWindowWidth);
            SettingsStore.getInstance(getContext()).setWindowHeight(newWindowHeight);

            if (doApply) {
                mWidgetManager.setBrowserSize(newWindowWidth, newWindowHeight);
            }
        }

        String newWindowWidthStr = Integer.toString(newWindowWidth);
        mWindowSizeEdit.setFirstText(newWindowWidthStr);
        String newWindowHeightStr = Integer.toString(newWindowHeight);
        mWindowSizeEdit.setSecondText(newWindowHeightStr);
    }

    private void setMaxWindowSize(int newMaxWindowWidth, int newMaxWindowHeight, boolean doApply) {
        int prevMaxWindowWidth = SettingsStore.getInstance(getContext()).getMaxWindowWidth();
        if (newMaxWindowWidth <= 0) {
            newMaxWindowWidth = prevMaxWindowWidth;
        }

        int prevMaxWindowHeight = SettingsStore.getInstance(getContext()).getMaxWindowHeight();
        if (newMaxWindowHeight <= 0) {
            newMaxWindowHeight = prevMaxWindowHeight;
        }

        int windowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        if (newMaxWindowWidth < windowWidth) {
            newMaxWindowWidth = windowWidth;
        }

        int windowHeight = SettingsStore.getInstance(getContext()).getWindowHeight();
        if (newMaxWindowHeight < windowHeight) {
            newMaxWindowHeight = windowHeight;
        }

        if (newMaxWindowWidth != prevMaxWindowWidth ||
                newMaxWindowHeight != prevMaxWindowHeight) {
            SettingsStore.getInstance(getContext()).setMaxWindowWidth(newMaxWindowWidth);
            SettingsStore.getInstance(getContext()).setMaxWindowHeight(newMaxWindowHeight);

            if (doApply) {
                SessionStore.get().setMaxWindowSize(newMaxWindowWidth, newMaxWindowHeight);
            }
        }

        String newMaxWindowWidthStr = Integer.toString(newMaxWindowWidth);
        mMaxWindowSizeEdit.setFirstText(newMaxWindowWidthStr);
        String newMaxWindowHeightStr = Integer.toString(newMaxWindowHeight);
        mMaxWindowSizeEdit.setSecondText(newMaxWindowHeightStr);
    }

}
