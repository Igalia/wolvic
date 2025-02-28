/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsControllerBinding;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

class ControllerOptionsView extends SettingsView {

    private OptionsControllerBinding mBinding;

    public ControllerOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_controller, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> resetOptions());

        int selectionMethod = SettingsStore.getInstance(getContext()).getWindowSelectionMethod();
        mBinding.windowSelectionRadio.setOnCheckedChangeListener(mWindowSelectionMethodListener);
        setWindowSelectionMethod(mBinding.windowSelectionRadio.getIdForValue(selectionMethod), false);

        int color = SettingsStore.getInstance(getContext()).getPointerColor();
        mBinding.pointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);
        setPointerColor(mBinding.pointerColorRadio.getIdForValue(color), false);

        int scrollDirection = SettingsStore.getInstance(getContext()).getScrollDirection();
        mBinding.scrollDirectionRadio.setOnCheckedChangeListener(mScrollDirectionListener);
        setScrollDirection(mBinding.scrollDirectionRadio.getIdForValue(scrollDirection), false);

        mBinding.soundEffectSwitch.setOnCheckedChangeListener(mSoundEffectListener);
        setSoundEffect(SettingsStore.getInstance(getContext()).isAudioEnabled(), false);

        mBinding.hapticFeedbackSwitch.setOnCheckedChangeListener(mHapticFeedbackListener);
        setHapticFeedbackEnabled(SettingsStore.getInstance(getContext()).isHapticFeedbackEnabled(), false);

        if (mWidgetManager.isEyeTrackingSupported()) {
            mBinding.pointerModeRadio.setOnCheckedChangeListener(mPointerModeListener);
            setPointerMode(SettingsStore.getInstance(getContext()).getPointerMode(), false);
        } else {
            mBinding.pointerModeRadio.setVisibility(GONE);
        }

        if (mWidgetManager.isHandTrackingSupported() && mWidgetManager.areControllersAvailable()) {
            setHandTrackingEnabled(mWidgetManager.isHandTrackingEnabled(), false);
        } else {
            mBinding.handtrackingSwitch.setVisibility(GONE);
        }
    }

    private void resetOptions() {
        if (!mBinding.windowSelectionRadio.getValueForId(mBinding.windowSelectionRadio.getCheckedRadioButtonId()).equals(SettingsStore.WINDOW_SELECTION_METHOD_DEFAULT)) {
            setWindowSelectionMethod(mBinding.windowSelectionRadio.getIdForValue(SettingsStore.WINDOW_SELECTION_METHOD_DEFAULT), true);
        }
        if (!mBinding.pointerColorRadio.getValueForId(mBinding.pointerColorRadio.getCheckedRadioButtonId()).equals(SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT)) {
            setPointerColor(mBinding.pointerColorRadio.getIdForValue(SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT), true);
        }
        if (!mBinding.scrollDirectionRadio.getValueForId(mBinding.scrollDirectionRadio.getCheckedRadioButtonId()).equals(SettingsStore.SCROLL_DIRECTION_DEFAULT)) {
            setScrollDirection(mBinding.scrollDirectionRadio.getIdForValue(SettingsStore.SCROLL_DIRECTION_DEFAULT), true);
        }
        setSoundEffect(SettingsStore.AUDIO_ENABLED, true);
        setHapticFeedbackEnabled(SettingsStore.HAPTIC_FEEDBACK_ENABLED, true);
        setPointerMode(SettingsStore.POINTER_MODE_DEFAULT, true);
        setHandTrackingEnabled(true, true);
    }

    private void setWindowSelectionMethod(int checkedId, boolean doApply) {
        mBinding.windowSelectionRadio.setOnCheckedChangeListener(null);
        mBinding.windowSelectionRadio.setChecked(checkedId, doApply);
        mBinding.windowSelectionRadio.setOnCheckedChangeListener(mWindowSelectionMethodListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setWindowSelectionMethod((int)mBinding.windowSelectionRadio.getValueForId(checkedId));
        }
    }

    private void setPointerColor(int checkedId, boolean doApply) {
        mBinding.pointerColorRadio.setOnCheckedChangeListener(null);
        mBinding.pointerColorRadio.setChecked(checkedId, doApply);
        mBinding.pointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPointerColor((int)mBinding.pointerColorRadio.getValueForId(checkedId));
            mWidgetManager.updatePointerColor();
        }
    }

    private void setScrollDirection(int checkedId, boolean doApply) {
        mBinding.scrollDirectionRadio.setOnCheckedChangeListener(null);
        mBinding.scrollDirectionRadio.setChecked(checkedId, doApply);
        mBinding.scrollDirectionRadio.setOnCheckedChangeListener(mScrollDirectionListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setScrollDirection((int)mBinding.scrollDirectionRadio.getValueForId(checkedId));
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

    private void setHapticFeedbackEnabled(boolean value, boolean doApply) {
        mBinding.hapticFeedbackSwitch.setOnCheckedChangeListener(null);
        mBinding.hapticFeedbackSwitch.setValue(value, false);
        mBinding.hapticFeedbackSwitch.setOnCheckedChangeListener(mHapticFeedbackListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setHapticFeedbackEnabled(value);
        }
    }

    private void setPointerMode(@WidgetManagerDelegate.PointerMode int value, boolean doApply) {
        mBinding.pointerModeRadio.setOnCheckedChangeListener(null);
        mBinding.pointerModeRadio.setChecked(value, false);
        mBinding.pointerModeRadio.setOnCheckedChangeListener(mPointerModeListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPointerMode(value);
            mWidgetManager.setPointerMode(value);
        }
    }

    private void setHandTrackingEnabled(boolean value, boolean doApply) {
        mBinding.handtrackingSwitch.setOnCheckedChangeListener(null);
        mBinding.handtrackingSwitch.setValue(value, false);
        mBinding.handtrackingSwitch.setOnCheckedChangeListener(mHandtrackingListener);

        if (doApply) {
            mWidgetManager.setHandTrackingEnabled(value);
        }
    }

    private RadioGroupSetting.OnCheckedChangeListener mWindowSelectionMethodListener = (radioGroup, checkedId, doApply) -> {
        setWindowSelectionMethod(checkedId, doApply);
    };

    private RadioGroupSetting.OnCheckedChangeListener mPointerColorListener = (radioGroup, checkedId, doApply) -> {
        setPointerColor(checkedId, doApply);
    };

    private RadioGroupSetting.OnCheckedChangeListener mScrollDirectionListener = (radioGroup, checkedId, doApply) -> {
        setScrollDirection(checkedId, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mSoundEffectListener = (compoundButton, enabled, apply) -> {
        setSoundEffect(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mHapticFeedbackListener = (compoundButton, enabled, apply) ->
    setHapticFeedbackEnabled(enabled, true);

    private RadioGroupSetting.OnCheckedChangeListener mPointerModeListener = (compoundButton, checkedId, doApply) -> {
        if (checkedId == WidgetManagerDelegate.TRACKED_POINTER) {
            setPointerMode(checkedId, doApply);
            return;
        }
        assert checkedId == WidgetManagerDelegate.TRACKED_EYE;
        mWidgetManager.checkEyeTrackingPermissions((aPermissionGranted) -> {
            if (aPermissionGranted) {
                setPointerMode(checkedId, doApply);
            } else {
                // Revert to pointer mode if permission is not granted
                setPointerMode(WidgetManagerDelegate.TRACKED_POINTER, false);
            }
        });
    };

    private SwitchSetting.OnCheckedChangeListener mHandtrackingListener = (compoundButton, enabled, apply) ->
            setHandTrackingEnabled(enabled, true);

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.controller_options_height));
    }

    @Override
    protected SettingViewType getType() {
        return SettingViewType.CONTROLLER;
    }

}
