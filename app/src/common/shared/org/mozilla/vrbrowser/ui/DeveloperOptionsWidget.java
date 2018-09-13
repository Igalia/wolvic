/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.SettingsStore;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class DeveloperOptionsWidget extends UIWidget {

    private static final String LOGTAG = "VRB";

    private static final int COLOR_LAVANDER = Color.parseColor("#C27FFCFF");

    public enum InputMode {
        MOUSE,
        TOUCH
    }

    public enum UaMode {
        MOBILE,
        DESKTOP,
        VR
    }

    private AudioEngine mAudio;
    private Switch mRemoteDebuggingSwitch;
    private Switch mConsoleLogsSwitch;
    private Switch mEnvOverrideSwitch;
    private Switch mMultiprocessSwitch;
    private RadioGroup mUaModeRadio;
    private RadioButton mDesktopRadio;
    private RadioButton mRadioMobile;
    private RadioButton mVrRadio;
    private UIButton mBackButton;
    private RadioGroup mEventsRadio;
    private RadioButton mTouchRadio;
    private RadioButton mMouseRadio;
    private RadioGroup mEnvsRadio;
    private RadioButton mMeadowRadio;
    private RadioButton mCaveRadio;
    private RadioButton mVoidRadio;
    private RadioGroup mPointerColorRadio;
    private RadioButton mColorWhiteRadio;
    private RadioButton mColorPurpleRadio;
    private TextView mDensityButton;
    private TextView mDensityText;
    private DeveloperOptionsEditText mDensityEdit;
    private TextView mWindowSizeButton;
    private TextView mWindowWidthText;
    private TextView mWindowHeightText;
    private DeveloperOptionsEditText mWindowWidthEdit;
    private DeveloperOptionsEditText mWindowHeightEdit;
    private TextView mDpiButton;
    private TextView mDpiText;
    private DeveloperOptionsEditText mDpiEdit;
    private TextView mMaxWindowSizeButton;
    private TextView mMaxWindowWidthText;
    private DeveloperOptionsEditText mMaxWindowWidthEdit;
    private TextView mMaxWindowHeightText;
    private DeveloperOptionsEditText mMaxWindowHeightEdit;
    private TextView mResetButton;
    private TextView mRemoteDebuggingSwitchText;
    private TextView mConsoleLogsSwitchText;
    private TextView mEnvOverrideSwitchText;
    private TextView mMultiprocessSwitchText;
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

                onBackButton();
            }
        });


        mRemoteDebuggingSwitchText = findViewById(R.id.developer_options_remote_debugging_switch_text);
        mRemoteDebuggingSwitch = findViewById(R.id.developer_options_remote_debugging_switch);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        mRemoteDebuggingSwitch.setSoundEffectsEnabled(false);
        setRemoteDebugging(SettingsStore.getInstance(getContext()).isRemoteDebuggingEnabled(), false);

        mConsoleLogsSwitchText = findViewById(R.id.developer_options_show_console_switch_text);
        mConsoleLogsSwitch = findViewById(R.id.developer_options_show_console_switch);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        mConsoleLogsSwitch.setSoundEffectsEnabled(false);
        setConsoleLogs(SettingsStore.getInstance(getContext()).isConsoleLogsEnabled(), false);

        mEnvOverrideSwitchText = findViewById(R.id.developer_options_env_override_switch_text);
        mEnvOverrideSwitch = findViewById(R.id.developer_options_env_override_switch);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        mEnvOverrideSwitch.setSoundEffectsEnabled(false);
        setEnvOverride(SettingsStore.getInstance(getContext()).isEnvironmentOverrideEnabled());

        String env = SettingsStore.getInstance(getContext()).getEnvironment();
        mEnvsRadio = findViewById(R.id.radioEnv);
        mEnvsRadio.setSoundEffectsEnabled(false);
        mMeadowRadio = findViewById(R.id.radioMeadow);
        mMeadowRadio.setSoundEffectsEnabled(false);
        mCaveRadio = findViewById(R.id.radioCave);
        mCaveRadio.setSoundEffectsEnabled(false);
        mVoidRadio = findViewById(R.id.radioVoid);
        mVoidRadio.setSoundEffectsEnabled(false);
        mEnvsRadio.setOnCheckedChangeListener(mEnvsListener);
        setEnv(env, false);

        int pointerColor = SettingsStore.getInstance(getContext()).getPointerColor();
        mPointerColorRadio = findViewById(R.id.radioPointerColor);
        mPointerColorRadio.setSoundEffectsEnabled(false);
        mColorWhiteRadio = findViewById(R.id.radioColorWhite);
        mColorWhiteRadio.setSoundEffectsEnabled(false);
        mColorPurpleRadio = findViewById(R.id.radioColorPurple);
        mColorPurpleRadio.setSoundEffectsEnabled(false);
        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);
        setPointerColor(pointerColor, false);

        mMultiprocessSwitchText = findViewById(R.id.developer_options_multiprocess_switch_text);
        mMultiprocessSwitch = findViewById(R.id.developer_options_multiprocess_switch);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        mMultiprocessSwitch.setSoundEffectsEnabled(false);
        setMultiprocess(SettingsStore.getInstance(getContext()).isMultiprocessEnabled(), false);

        UaMode uaMode = UaMode.values()[SettingsStore.getInstance(getContext()).getUaMode()];
        mUaModeRadio = findViewById(R.id.radioUaMode);
        mUaModeRadio.setSoundEffectsEnabled(false);
        mDesktopRadio = findViewById(R.id.radioDesktop);
        mDesktopRadio.setSoundEffectsEnabled(false);
        mRadioMobile = findViewById(R.id.radioMobile);
        mRadioMobile.setSoundEffectsEnabled(false);
        mVrRadio = findViewById(R.id.radioVr);
        mVrRadio.setSoundEffectsEnabled(false);
        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(uaMode, false);

        InputMode inputMode = InputMode.values()[SettingsStore.getInstance(getContext()).getInputMode()];
        mEventsRadio = findViewById(R.id.radioEvents);
        mEventsRadio.setSoundEffectsEnabled(false);
        mTouchRadio = findViewById(R.id.radioTouch);
        mTouchRadio.setSoundEffectsEnabled(false);
        mMouseRadio = findViewById(R.id.radioMouse);
        mMouseRadio.setSoundEffectsEnabled(false);
        mEventsRadio.setOnCheckedChangeListener(mInputModeListener);
        setInputMode(inputMode);

        mDensityText = findViewById(R.id.densityText);
        mDensityText.setText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mDensityEdit = findViewById(R.id.densityEdit);
        mDensityEdit.setText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mDensityEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mDensityButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mDensityButton = findViewById(R.id.densityEditButton);
        mDensityButton.setSoundEffectsEnabled(false);
        mDensityButton.setOnClickListener(mDensityListener);
        setDisplayDensity(SettingsStore.getInstance(getContext()).getDisplayDensity());

        mWindowWidthText = findViewById(R.id.windowSizeWidthText);
        mWindowWidthText.setText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowWidth()));
        mWindowWidthEdit = findViewById(R.id.windowSizeWidthEdit);
        mWindowWidthEdit.setText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowWidth()));
        mWindowWidthEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mWindowSizeButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mWindowHeightText = findViewById(R.id.windowSizeHeightText);
        mWindowHeightText.setText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowHeight()));
        mWindowHeightEdit = findViewById(R.id.windowSizeHeightEdit);
        mWindowHeightEdit.setText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowHeight()));
        mWindowHeightEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mWindowSizeButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mWindowSizeButton = findViewById(R.id.windowSizeEditButton);
        mWindowSizeButton.setSoundEffectsEnabled(false);
        mWindowSizeButton.setOnClickListener(mWindowSizeListener);
        setWindowSize(
                SettingsStore.getInstance(getContext()).getWindowWidth(),
                SettingsStore.getInstance(getContext()).getWindowHeight(),
                false
        );

        mDpiText = findViewById(R.id.dpiText);
        mDpiText.setText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mDpiEdit = findViewById(R.id.dpiEdit);
        mDpiEdit.setText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mDpiEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mDpiButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mDpiButton = findViewById(R.id.dpiEditButton);
        mDpiButton.setSoundEffectsEnabled(false);
        mDpiButton.setOnClickListener(mDpiListener);
        setDisplayDpi(SettingsStore.getInstance(getContext()).getDisplayDpi());

        mMaxWindowWidthText = findViewById(R.id.maxWindowSizeWidthText);
        mMaxWindowWidthText.setText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowWidth()));
        mMaxWindowWidthEdit = findViewById(R.id.maxWindowSizeWidthEdit);
        mMaxWindowWidthEdit.setText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowWidth()));
        mMaxWindowWidthEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mMaxWindowSizeButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mMaxWindowHeightText = findViewById(R.id.maxWindowSizeHeightText);
        mMaxWindowHeightText.setText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowHeight()));
        mMaxWindowHeightEdit = findViewById(R.id.maxWindowSizeHeightEdit);
        mMaxWindowHeightEdit.setText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowHeight()));
        mMaxWindowHeightEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mMaxWindowSizeButton.callOnClick();
                    return true;
                }

                return false;
            }
        });
        mMaxWindowSizeButton = findViewById(R.id.maxWindowSizeEditButton);
        mMaxWindowSizeButton.setSoundEffectsEnabled(false);
        mMaxWindowSizeButton.setOnClickListener(mMaxWindowSizeListener);
        setMaxWindowSize(
                SettingsStore.getInstance(getContext()).getMaxWindowWidth(),
                SettingsStore.getInstance(getContext()).getMaxWindowHeight(),
                false
        );

        mResetButton= findViewById(R.id.resetButton);
        mResetButton.setSoundEffectsEnabled(false);
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
        UIWidget widget = getChild(mRestartDialogHandle);
        if (widget == null) {
            widget = createChild(RestartDialogWidget.class, false);
            mRestartDialogHandle = widget.getHandle();
        }

        widget.show();

        hide();
    }

    private CompoundButton.OnCheckedChangeListener mRemoteDebuggingListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setRemoteDebugging(b, true);
        }
    };

    private CompoundButton.OnCheckedChangeListener mConsoleLogsListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setConsoleLogs(b, true);
        }
    };

    private CompoundButton.OnCheckedChangeListener mEnvOverrideListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setEnvOverride(b);

            showRestartDialog();
        }
    };

    private CompoundButton.OnCheckedChangeListener mMultiprocessListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setMultiprocess(b, true);
        }
    };

    private RadioGroup.OnCheckedChangeListener mUaModeListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setUaMode(getUaModeFromRadio(checkedId), true);
        }
    };

    private RadioGroup.OnCheckedChangeListener mInputModeListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setInputMode(getInputModeFromRadio(checkedId));
        }
    };

    private RadioGroup.OnCheckedChangeListener mEnvsListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setEnv(getEnvFromRadio(checkedId), true);
        }
    };

    private RadioGroup.OnCheckedChangeListener mPointerColorListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            setPointerColor(getPointerColorFromRadio(checkedId), true);
        }
    };

    private OnClickListener mDensityListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mDensityEdit.getVisibility() == View.VISIBLE) {
                mDensityText.setVisibility(View.VISIBLE);
                mDensityEdit.setVisibility(View.GONE);
                mDensityButton.setText(R.string.developer_options_edit);

            } else {
                mDensityText.setVisibility(View.GONE);
                mDensityEdit.setVisibility(View.VISIBLE);
                mDensityButton.setText(R.string.developer_options_save);
            }

            float newDensity = Float.parseFloat(mDensityEdit.getText().toString());
            if (setDisplayDensity(newDensity)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mWindowSizeListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mWindowWidthEdit.getVisibility() == View.VISIBLE) {
                mWindowWidthText.setVisibility(View.VISIBLE);
                mWindowHeightText.setVisibility(View.VISIBLE);
                mWindowWidthEdit.setVisibility(View.GONE);
                mWindowHeightEdit.setVisibility(View.GONE);
                mWindowSizeButton.setText(R.string.developer_options_edit);

            } else {
                mWindowWidthText.setVisibility(View.GONE);
                mWindowHeightText.setVisibility(View.GONE);
                mWindowWidthEdit.setVisibility(View.VISIBLE);
                mWindowHeightEdit.setVisibility(View.VISIBLE);
                mWindowSizeButton.setText(R.string.developer_options_save);
            }

            int newWindowWidth = Integer.parseInt(mWindowWidthEdit.getText().toString());
            int newWindowHeight = Integer.parseInt(mWindowHeightEdit.getText().toString());
            setWindowSize(newWindowWidth, newWindowHeight, true);
        }
    };

    private OnClickListener mDpiListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mDpiEdit.getVisibility() == View.VISIBLE) {
                mDpiText.setVisibility(View.VISIBLE);
                mDpiEdit.setVisibility(View.GONE);
                mDpiButton.setText(R.string.developer_options_edit);

            } else {
                mDpiText.setVisibility(View.GONE);
                mDpiEdit.setVisibility(View.VISIBLE);
                mDpiButton.setText(R.string.developer_options_save);
            }

            int newDpi = Integer.parseInt(mDpiEdit.getText().toString());
            if (setDisplayDpi(newDpi)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mMaxWindowSizeListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mMaxWindowWidthEdit.getVisibility() == View.VISIBLE) {
                mMaxWindowWidthText.setVisibility(View.VISIBLE);
                mMaxWindowHeightText.setVisibility(View.VISIBLE);
                mMaxWindowWidthEdit.setVisibility(View.GONE);
                mMaxWindowHeightEdit.setVisibility(View.GONE);
                mMaxWindowSizeButton.setText(R.string.developer_options_edit);

            } else {
                mMaxWindowWidthText.setVisibility(View.GONE);
                mMaxWindowHeightText.setVisibility(View.GONE);
                mMaxWindowWidthEdit.setVisibility(View.VISIBLE);
                mMaxWindowHeightEdit.setVisibility(View.VISIBLE);
                mMaxWindowSizeButton.setText(R.string.developer_options_save);
            }

            int newMaxWindowWidth = Integer.parseInt(mMaxWindowWidthEdit.getText().toString());
            int newMaxWindowHeight = Integer.parseInt(mMaxWindowHeightEdit.getText().toString());
            setMaxWindowSize(newMaxWindowWidth, newMaxWindowHeight, true);
        }
    };

    private OnClickListener mResetListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            boolean restart = false;
            if (mRemoteDebuggingSwitch.isChecked() != SettingsStore.REMOTE_DEBUGGING_DEFAULT) {
                setRemoteDebugging(SettingsStore.REMOTE_DEBUGGING_DEFAULT, true);
                restart = true;
            }

            setConsoleLogs(SettingsStore.CONSOLE_LOGS_DEFAULT, true);

            if (mEnvOverrideSwitch.isChecked() != SettingsStore.ENV_OVERRIDE_DEFAULT) {
                setEnvOverride(SettingsStore.ENV_OVERRIDE_DEFAULT);
                restart = true;
            }

            if (!getEnvFromRadio(mEnvsRadio.getCheckedRadioButtonId()).equals(SettingsStore.ENV_DEFAULT)) {
                setEnv(SettingsStore.ENV_DEFAULT, true);
            }

            setMultiprocess(SettingsStore.MULTIPROCESS_DEFAULT, true);
            setUaMode(SettingsStore.UA_MODE_DEFAULT, true);
            setInputMode(SettingsStore.INPUT_MODE_DEFAULT);
            restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
            restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);

            if (restart)
                showRestartDialog();
        }
    };

    private void setRemoteDebugging(boolean value, boolean doApply) {
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(null);
        mRemoteDebuggingSwitch.setChecked(value);
        mRemoteDebuggingSwitch.setOnCheckedChangeListener(mRemoteDebuggingListener);
        mRemoteDebuggingSwitchText.setText(value ? getContext().getString(R.string.on) : getContext().getString(R.string.off));

        SettingsStore.getInstance(getContext()).setRemoteDebuggingEnabled(value);
        if (doApply) {
            SessionStore.get().setRemoteDebugging(value);
        }
    }

    private void setConsoleLogs(boolean value, boolean doApply) {
        mConsoleLogsSwitch.setOnCheckedChangeListener(null);
        mConsoleLogsSwitch.setChecked(value);
        mConsoleLogsSwitch.setOnCheckedChangeListener(mConsoleLogsListener);
        mConsoleLogsSwitchText.setText(value ? getContext().getString(R.string.on) : getContext().getString(R.string.off));

        SettingsStore.getInstance(getContext()).setConsoleLogsEnabled(value);

        if (doApply) {
            SessionStore.get().setConsoleOutputEnabled(value);
        }
    }

    private void setEnvOverride(boolean value) {
        mEnvOverrideSwitch.setOnCheckedChangeListener(null);
        mEnvOverrideSwitch.setChecked(value);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        mEnvOverrideSwitchText.setText(value ? getContext().getString(R.string.on) : getContext().getString(R.string.off));

        SettingsStore.getInstance(getContext()).setEnvironmentOverrideEnabled(value);
    }

    private void setMultiprocess(boolean value, boolean doApply) {
        mMultiprocessSwitch.setOnCheckedChangeListener(null);
        mMultiprocessSwitch.setChecked(value);
        mMultiprocessSwitch.setOnCheckedChangeListener(mMultiprocessListener);
        mMultiprocessSwitchText.setText(value ? getContext().getString(R.string.on) : getContext().getString(R.string.off));

        SettingsStore.getInstance(getContext()).setMultiprocessEnabled(value);

        if (doApply) {
            SessionStore.get().setMultiprocess(value);
        }
    }

    private UaMode getUaModeFromRadio(int checkedId) {
        UaMode uaMode;
        switch (checkedId) {
            case R.id.radioDesktop:
                uaMode = UaMode.DESKTOP;
                break;
            case  R.id.radioMobile:
                uaMode = UaMode.MOBILE;
                break;
            default:
                uaMode = UaMode.VR;
        }

        return uaMode;
    }

    private void setUaMode(UaMode uaMode, boolean doApply) {
        mUaModeRadio.setOnCheckedChangeListener(null);

        if (uaMode == UaMode.DESKTOP) {
            mDesktopRadio.setChecked(true);
            mRadioMobile.setChecked(false);
            mVrRadio.setChecked(false);

        } else if (uaMode == UaMode.MOBILE) {
            mDesktopRadio.setChecked(false);
            mRadioMobile.setChecked(true);
            mVrRadio.setChecked(false);

        } else if (uaMode == UaMode.VR) {
            mDesktopRadio.setChecked(false);
            mRadioMobile.setChecked(false);
            mVrRadio.setChecked(true);
        }

        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);

        SettingsStore.getInstance(getContext()).setUaMode(uaMode.ordinal());

        if (doApply) {
            SessionStore.get().setUaMode(uaMode.ordinal());
        }
    }

    private String getEnvFromRadio(int checkedId) {
        String env;
        switch (checkedId) {
            case R.id.radioMeadow:
                env = "meadow";
                break;
            case  R.id.radioCave:
                env = "cave";
                break;
            case  R.id.radioVoid:
                env = "void";
                break;
            default:
                env = "meadow";
        }

        return env;
    }

    private void setEnv(String env, boolean doApply) {
        mEnvsRadio.setOnCheckedChangeListener(null);

        if (env.equalsIgnoreCase("meadow")) {
            mCaveRadio.setChecked(false);
            mMeadowRadio.setChecked(true);
            mVoidRadio.setChecked(false);

        } else if (env.equalsIgnoreCase("cave")) {
            mCaveRadio.setChecked(true);
            mMeadowRadio.setChecked(false);
            mVoidRadio.setChecked(false);

        } else if (env.equalsIgnoreCase("void")) {
            mCaveRadio.setChecked(false);
            mMeadowRadio.setChecked(false);
            mVoidRadio.setChecked(true);
        }

        mEnvsRadio.setOnCheckedChangeListener(mEnvsListener);

        SettingsStore.getInstance(getContext()).setEnvironment(env);

        if (doApply) {
            mWidgetManager.updateEnvironment();
        }
    }

    private int getPointerColorFromRadio(int checkedId) {
        int color;
        switch (checkedId) {
            case R.id.radioColorWhite:
                color = SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT;
                break;
            case  R.id.radioColorPurple:
                color = COLOR_LAVANDER;
                break;
            default:
                color = SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT;
        }

        return color;
    }

    private void setPointerColor(int color, boolean doApply) {
        mPointerColorRadio.setOnCheckedChangeListener(null);

        if (color == SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT) {
            mColorPurpleRadio.setChecked(false);
            mColorWhiteRadio.setChecked(true);

        } else if (color == COLOR_LAVANDER) {
            mColorPurpleRadio.setChecked(true);
            mColorWhiteRadio.setChecked(false);
        }

        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);

        SettingsStore.getInstance(getContext()).setPointerColor(color);

        if (doApply) {
            mWidgetManager.updatePointerColor();
        }
    }

    private InputMode getInputModeFromRadio(int checkedId) {
        InputMode mode;
        switch (checkedId) {
            case R.id.radioMouse:
                mode = InputMode.MOUSE;
                break;
            default:
                mode = InputMode.TOUCH;
        }

        return mode;
    }

    private void setInputMode(InputMode mode) {
        mUaModeRadio.setOnCheckedChangeListener(null);

        if (mode == InputMode.MOUSE) {
            mTouchRadio.setChecked(false);
            mMouseRadio.setChecked(true);

        } else if (mode == InputMode.TOUCH) {
            mTouchRadio.setChecked(true);
            mMouseRadio.setChecked(false);
        }

        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);

        SettingsStore.getInstance(getContext()).setInputMode(mode.ordinal());
        // TODO: Wire it up
    }

    private boolean setDisplayDensity(float newDensity) {
        boolean updated = false;

        float prevDensity = Float.parseFloat(mDensityText.getText().toString());
        if (newDensity <= 0) {
            newDensity = prevDensity;

        } else if (prevDensity != newDensity) {
            SettingsStore.getInstance(getContext()).setDisplayDensity(newDensity);
            updated = true;
        }

        String newDensityStr = Float.toString(newDensity);
        mDensityText.setText(newDensityStr);
        mDensityEdit.setText(newDensityStr);

        return updated;
    }

    private void setWindowSize(int newWindowWidth, int newWindowHeight, boolean doApply) {
        int prevWindowWidth = Integer.parseInt(mWindowWidthText.getText().toString());
        if (newWindowWidth <= 0) {
            newWindowWidth = prevWindowWidth;
        }

        int prevWindowHeight = Integer.parseInt(mWindowHeightText.getText().toString());
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
        mWindowWidthEdit.setText(newWindowWidthStr);
        mWindowWidthText.setText(newWindowWidthStr);
        String newWindowHeightStr = Integer.toString(newWindowHeight);
        mWindowHeightEdit.setText(newWindowHeightStr);
        mWindowHeightText.setText(newWindowHeightStr);
    }

    private boolean setDisplayDpi(int newDpi) {
        boolean updated = false;

        int prevDpi = Integer.parseInt(mDpiText.getText().toString());
        if (newDpi <= 0) {
            newDpi = prevDpi;

        } else if (prevDpi != newDpi) {
            SettingsStore.getInstance(getContext()).setDisplayDpi(newDpi);
            showRestartDialog();
            updated = true;
        }

        String newDpiStr = Integer.toString(newDpi);
        mDpiText.setText(newDpiStr);
        mDpiEdit.setText(newDpiStr);

        return updated;
    }

    private void setMaxWindowSize(int newMaxWindowWidth, int newMaxWindowHeight, boolean doApply) {
        int prevMaxWindowWidth = Integer.parseInt(mMaxWindowWidthText.getText().toString());
        if (newMaxWindowWidth <= 0) {
            newMaxWindowWidth = prevMaxWindowWidth;
        }

        int prevMaxWindowHeight = Integer.parseInt(mMaxWindowHeightText.getText().toString());
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
        mMaxWindowWidthEdit.setText(newMaxWindowWidthStr);
        mMaxWindowWidthText.setText(newMaxWindowWidthStr);
        String newMaxWindowHeightStr = Integer.toString(newMaxWindowHeight);
        mMaxWindowHeightEdit.setText(newMaxWindowHeightStr);
        mMaxWindowHeightText.setText(newMaxWindowHeightStr);
    }

}
