/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsDisplayBinding;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.views.settings.SliderSetting;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

class DisplayOptionsView extends SettingsView {

    private OptionsDisplayBinding mBinding;
    private String mDefaultHomepageUrl;

    public DisplayOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_display, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mResetListener);

        // Options
        mBinding.curvedDisplaySwitch.setOnCheckedChangeListener(mCurvedDisplayListener);
        setCurvedDisplay(SettingsStore.getInstance(getContext()).getCylinderDensity() > 0.0f, false);

        mBinding.centerWindowsSwitch.setOnCheckedChangeListener(mCenterWindowsListener);
        setCenterWindows(SettingsStore.getInstance(getContext()).isCenterWindows(), false);

        float windowDistance = SettingsStore.getInstance(getContext()).getWindowDistance();
        mBinding.windowDistanceSlider.setOnValueChangeListener(mWindowDistanceListener);
        setWindowDistance(windowDistance, false);

        int uaMode = SettingsStore.getInstance(getContext()).getUaMode();
        mBinding.uaRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(mBinding.uaRadio.getIdForValue(uaMode), false);

        int msaaLevel = SettingsStore.getInstance(getContext()).getMSAALevel();
        mBinding.msaaRadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setMSAAMode(mBinding.msaaRadio.getIdForValue(msaaLevel), false);

        mBinding.autoplaySwitch.setOnCheckedChangeListener(mAutoplayListener);
        setAutoplay(SettingsStore.getInstance(getContext()).isAutoplayEnabled(), false);

        mBinding.startWithPassthroughSwitch.setOnCheckedChangeListener(mStartWithPassthroughListener);
        setStartWithPassthrough(SettingsStore.getInstance(getContext()).isStartWithPassthroughEnabled());

        if (mWidgetManager != null && mWidgetManager.isPassthroughSupported()) {
            mBinding.startWithPassthroughSwitch.setVisibility(View.VISIBLE);
        } else {
            mBinding.startWithPassthroughSwitch.setVisibility(View.GONE);
        }

        mBinding.soundEffectSwitch.setOnCheckedChangeListener(mSoundEffectListener);
        setSoundEffect(SettingsStore.getInstance(getContext()).isAudioEnabled(), false);

        mBinding.latinAutoCompleteSwitch.setOnCheckedChangeListener(mLatinAutoCompleteListener);
        setLatinAutoComplete(SettingsStore.getInstance(getContext()).isLatinAutoCompleteEnabled(), false);

        mBinding.headLockSwitch.setOnCheckedChangeListener(mHeadLockListener);
        setHeadLock(SettingsStore.getInstance(getContext()).isHeadLockEnabled(), false);

        mBinding.windowMovementSwitch.setOnCheckedChangeListener(mWindowMovementListener);
        setWindowMovement(SettingsStore.getInstance(getContext()).isWindowMovementEnabled(), false);

        mDefaultHomepageUrl = getContext().getString(R.string.HOMEPAGE_URL);

        mBinding.homepageEdit.setHint1(getContext().getString(R.string.homepage_hint, getContext().getString(R.string.app_name)));
        mBinding.homepageEdit.setDefaultFirstValue(mDefaultHomepageUrl);
        mBinding.homepageEdit.setFirstText(SettingsStore.getInstance(getContext()).getHomepage());
        mBinding.homepageEdit.setOnClickListener(mHomepageListener);
        setHomepage(SettingsStore.getInstance(getContext()).getHomepage());

        mBinding.densityEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mBinding.densityEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mBinding.densityEdit.setFirstText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mBinding.densityEdit.setOnClickListener(mDensityListener);
        setDisplayDensity(SettingsStore.getInstance(getContext()).getDisplayDensity());

        mBinding.dpiEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mBinding.dpiEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mBinding.dpiEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mBinding.dpiEdit.setOnClickListener(mDpiListener);
        setDisplayDpi(SettingsStore.getInstance(getContext()).getDisplayDpi());
    }

    @Override
    public void onHidden() {
        if (!isEditing()) {
            super.onHidden();
        }
    }

    @Override
    protected void onDismiss() {
        if (!isEditing()) {
            super.onDismiss();
        }
    }

    @Override
    public boolean isEditing() {
        boolean editing = false;

        if (mBinding.densityEdit.isEditing()) {
            editing = true;
            mBinding.densityEdit.cancel();
        }

        if (mBinding.dpiEdit.isEditing()) {
            editing = true;
            mBinding.dpiEdit.cancel();
        }

        if (mBinding.homepageEdit.isEditing()) {
            editing = true;
            mBinding.homepageEdit.cancel();
        }

        return editing;
    }

    private SliderSetting.OnValueChangeListener mWindowDistanceListener = (slider, value, doApply) -> {
        setWindowDistance(value, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mUaModeListener = (radioGroup, checkedId, doApply) -> {
        setUaMode(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mMSSAChangeListener = (radioGroup, checkedId, doApply) -> {
        setMSAAMode(checkedId, true);
    };

    private SwitchSetting.OnCheckedChangeListener mAutoplayListener = (compoundButton, enabled, apply) -> {
        setAutoplay(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mStartWithPassthroughListener = (compoundButton, value, doApply) -> {
        setStartWithPassthrough(value);
    };

    private SwitchSetting.OnCheckedChangeListener mSoundEffectListener = (compoundButton, enabled, apply) -> {
        setSoundEffect(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mLatinAutoCompleteListener = (compoundButton, enabled, apply) -> {
        setLatinAutoComplete(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mHeadLockListener = (compoundButton, value, doApply) -> {
        setHeadLock(value, true);
    };

    private SwitchSetting.OnCheckedChangeListener mWindowMovementListener = (compoundButton, enabled, apply) -> {
        setWindowMovement(enabled, true);
    };

    private OnClickListener mHomepageListener = (view) -> {
        if (!mBinding.homepageEdit.getFirstText().isEmpty()) {
            setHomepage(mBinding.homepageEdit.getFirstText());

        } else {
            setHomepage(mDefaultHomepageUrl);
        }
    };

    private OnClickListener mDensityListener = (view) -> {
        try {
            float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
            float newDensity = Float.parseFloat(mBinding.densityEdit.getFirstText());
            if (setDisplayDensity(newDensity)) {
                showRestartDialog(() -> {
                    setDisplayDensity(prevDensity);
                });
            }

        } catch (NumberFormatException e) {
            float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
            if (setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT)) {
                showRestartDialog(() -> {
                    setDisplayDensity(prevDensity);
                });
            }
        }
    };

    private OnClickListener mDpiListener = (view) -> {
        try {
            int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
            int newDpi = Integer.parseInt(mBinding.dpiEdit.getFirstText());
            if (setDisplayDpi(newDpi)) {
                showRestartDialog(() -> {
                    setDisplayDpi(prevDpi);
                });
            }

        } catch (NumberFormatException e) {
            int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
            if (setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT)) {
                showRestartDialog(() -> {
                    setDisplayDpi(prevDpi);
                });
            }
        }
    };

    private SwitchSetting.OnCheckedChangeListener mCurvedDisplayListener = (compoundButton, enabled, apply) ->
            setCurvedDisplay(enabled, true);

    private SwitchSetting.OnCheckedChangeListener mCenterWindowsListener = (compoundButton, enabled, apply) ->
            setCenterWindows(enabled, true);

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;

        if (!mBinding.uaRadio.getValueForId(mBinding.uaRadio.getCheckedRadioButtonId()).equals(SettingsStore.UA_MODE_DEFAULT)) {
            setUaMode(mBinding.uaRadio.getIdForValue(SettingsStore.UA_MODE_DEFAULT), true);
        }

        Object prevMSAA = mBinding.msaaRadio.getValueForId(mBinding.msaaRadio.getCheckedRadioButtonId());
        if (!prevMSAA.equals(SettingsStore.MSAA_DEFAULT_LEVEL)) {
            setMSAAMode(mBinding.msaaRadio.getIdForValue(SettingsStore.MSAA_DEFAULT_LEVEL), true);
            restart = true;
        }

        float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
        restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
        int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
        restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);


        setHomepage(mDefaultHomepageUrl);
        setAutoplay(SettingsStore.AUTOPLAY_ENABLED, true);
        setCurvedDisplay(false, true);
        setHeadLock(SettingsStore.HEAD_LOCK_DEFAULT, true);
        setSoundEffect(SettingsStore.AUDIO_ENABLED, true);
        setLatinAutoComplete(SettingsStore.LATIN_AUTO_COMPLETE_ENABLED, true);
        setCenterWindows(SettingsStore.CENTER_WINDOWS_DEFAULT, true);
        setWindowMovement(SettingsStore.WINDOW_MOVEMENT_DEFAULT, true);
        setWindowDistance(SettingsStore.WINDOW_DISTANCE_DEFAULT, true);

        if (mBinding.startWithPassthroughSwitch.isChecked() != SettingsStore.shouldStartWithPassthrougEnabled()) {
            setStartWithPassthrough(SettingsStore.shouldStartWithPassthrougEnabled());
        }

        if (restart) {
            showRestartDialog(() -> {
                setMSAAMode(mBinding.msaaRadio.getIdForValue(prevMSAA), true);
                setDisplayDensity(prevDensity);
                setDisplayDpi(prevDpi);
            });
        }
    };

    private void setCurvedDisplay(boolean value, boolean doApply) {
        mBinding.curvedDisplaySwitch.setOnCheckedChangeListener(null);
        mBinding.curvedDisplaySwitch.setValue(value, false);
        mBinding.curvedDisplaySwitch.setOnCheckedChangeListener(mCurvedDisplayListener);

        if (doApply) {
            float density = value ? SettingsStore.CYLINDER_DENSITY_ENABLED_DEFAULT : 0.0f;
            SettingsStore.getInstance(getContext()).setCylinderDensity(density);
            mWidgetManager.setCylinderDensity(density);
        }
    }

    private void setCenterWindows(boolean value, boolean doApply) {
        mBinding.centerWindowsSwitch.setOnCheckedChangeListener(null);
        mBinding.centerWindowsSwitch.setValue(value, false);
        mBinding.centerWindowsSwitch.setOnCheckedChangeListener(mCenterWindowsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setCenterWindows(value);
            mWidgetManager.setCenterWindows(value);
        }
    }

    private void setAutoplay(boolean value, boolean doApply) {
        mBinding.autoplaySwitch.setOnCheckedChangeListener(null);
        mBinding.autoplaySwitch.setValue(value, false);
        mBinding.autoplaySwitch.setOnCheckedChangeListener(mAutoplayListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setAutoplayEnabled(value);
        }
    }

    private void setStartWithPassthrough(boolean value) {
        mBinding.startWithPassthroughSwitch.setOnCheckedChangeListener(null);
        mBinding.startWithPassthroughSwitch.setValue(value, false);
        mBinding.startWithPassthroughSwitch.setOnCheckedChangeListener(mStartWithPassthroughListener);

        SettingsStore.getInstance(getContext()).setStartWithPassthroughEnabled(value);
    }

    private void setLatinAutoComplete(boolean value, boolean doApply) {
        mBinding.latinAutoCompleteSwitch.setOnCheckedChangeListener(null);
        mBinding.latinAutoCompleteSwitch.setValue(value, false);
        mBinding.latinAutoCompleteSwitch.setOnCheckedChangeListener(mLatinAutoCompleteListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setLatinAutoComplete(value);
        }
    }

    private void setHeadLock(boolean value, boolean doApply) {
        mBinding.headLockSwitch.setOnCheckedChangeListener(null);
        mBinding.headLockSwitch.setValue(value, false);
        mBinding.headLockSwitch.setOnCheckedChangeListener(mHeadLockListener);

        SettingsStore settingsStore = SettingsStore.getInstance(getContext());
        if (doApply) {
            settingsStore.setHeadLockEnabled(value);
        }

        if (value && settingsStore.isWindowMovementEnabled()) {
            // Disable window movement if head lock is enabled,
            // otherwise the windows might be moved out of the user's reach.
            setWindowMovement(false, true);
        }
    }

    private void setSoundEffect(boolean value, boolean doApply) {
        mBinding.soundEffectSwitch.setOnCheckedChangeListener(null);
        mBinding.soundEffectSwitch.setValue(value, false);
        mBinding.soundEffectSwitch.setOnCheckedChangeListener(mSoundEffectListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setAudioEnabled(value);
            AudioEngine.fromContext(getContext()).setEnabled(value);
        }
    }

    private void setWindowMovement(boolean value, boolean doApply) {
        mBinding.windowMovementSwitch.setOnCheckedChangeListener(null);
        mBinding.windowMovementSwitch.setValue(value, false);
        mBinding.windowMovementSwitch.setOnCheckedChangeListener(mWindowMovementListener);

        SettingsStore settingsStore = SettingsStore.getInstance(getContext());
        if (doApply) {
            settingsStore.setWindowMovementEnabled(value);
        }

        if (value && settingsStore.isHeadLockEnabled()) {
            // Disable head lock if window movement is enabled,
            // otherwise the windows might be moved out of the user's reach.
            setHeadLock(false, true);
        }
    }

    private void setHomepage(String newHomepage) {
        mBinding.homepageEdit.setOnClickListener(null);
        mBinding.homepageEdit.setFirstText(newHomepage);
        SettingsStore.getInstance(getContext()).setHomepage(newHomepage);
        mBinding.homepageEdit.setOnClickListener(mHomepageListener);
    }

    private void setWindowDistance(float value, boolean doApply) {
        mBinding.windowDistanceSlider.setOnValueChangeListener(null);
        mBinding.windowDistanceSlider.setValue(value, doApply);
        mBinding.windowDistanceSlider.setOnValueChangeListener(mWindowDistanceListener);

        SettingsStore.getInstance(getContext()).setWindowDistance(mBinding.windowDistanceSlider.getValue());
    }

    private void setUaMode(int checkId, boolean doApply) {
        mBinding.uaRadio.setOnCheckedChangeListener(null);
        mBinding.uaRadio.setChecked(checkId, doApply);
        mBinding.uaRadio.setOnCheckedChangeListener(mUaModeListener);

        SettingsStore.getInstance(getContext()).setUaMode((Integer)mBinding.uaRadio.getValueForId(checkId));
    }

    private void setMSAAMode(int checkedId, boolean doApply) {
        int previouslyCheckedMSAAId = mBinding.msaaRadio.getIdForValue(SettingsStore.getInstance(getContext()).getMSAALevel());

        mBinding.msaaRadio.setOnCheckedChangeListener(null);
        mBinding.msaaRadio.setChecked(checkedId, doApply);
        mBinding.msaaRadio.setOnCheckedChangeListener(mMSSAChangeListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setMSAALevel((Integer)mBinding.msaaRadio.getValueForId(checkedId));
            showRestartDialog(() -> {
                setMSAAMode(previouslyCheckedMSAAId, true);
            });
        }
    }

    private boolean setDisplayDensity(float newDensity) {
        mBinding.densityEdit.setOnClickListener(null);
        boolean restart = false;
        float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
        if (newDensity <= 0) {
            newDensity = prevDensity;

        } else if (prevDensity != newDensity) {
            SettingsStore.getInstance(getContext()).setDisplayDensity(newDensity);
            restart = true;
        }
        mBinding.densityEdit.setFirstText(Float.toString(newDensity));
        mBinding.densityEdit.setOnClickListener(mDensityListener);

        return restart;
    }

    private boolean setDisplayDpi(int newDpi) {
        mBinding.dpiEdit.setOnClickListener(null);
        boolean restart = false;
        int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
        if (newDpi < SettingsStore.DISPLAY_DPI_MIN || newDpi > SettingsStore.DISPLAY_DPI_MAX) {
            newDpi = prevDpi;

        } else if (prevDpi != newDpi) {
            SettingsStore.getInstance(getContext()).setDisplayDpi(newDpi);
            restart = true;
        }
        mBinding.dpiEdit.setFirstText(Integer.toString(newDpi));
        mBinding.dpiEdit.setOnClickListener(mDpiListener);

        return restart;
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus != null) {
            if (mBinding.densityEdit.contains(oldFocus) && mBinding.densityEdit.isEditing()) {
                mBinding.densityEdit.cancel();
            }
            if (mBinding.dpiEdit.contains(oldFocus) && mBinding.dpiEdit.isEditing()) {
                mBinding.dpiEdit.cancel();
            }
            if (mBinding.homepageEdit.contains(oldFocus) && mBinding.homepageEdit.isEditing()) {
                mBinding.homepageEdit.cancel();
            }
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.display_options_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.DISPLAY;
    }

}
