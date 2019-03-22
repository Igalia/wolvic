/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.settings.ButtonSetting;
import org.mozilla.vrbrowser.ui.views.settings.ImageRadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.LocaleUtils;

public class EnvironmentOptionsWidget extends UIWidget implements
        WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.FocusChangeListener {

    private AudioEngine mAudio;
    private UIButton mBackButton;

    private SwitchSetting mEnvOverrideSwitch;
    private ImageRadioGroupSetting mEnvironmentsRadio;
    private int mRestartDialogHandle = -1;

    private ButtonSetting mResetButton;

    private ScrollView mScrollbar;

    public EnvironmentOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public EnvironmentOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public EnvironmentOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_environment, this);

        mAudio = AudioEngine.fromContext(aContext);

        mWidgetManager.addFocusChangeListener(this);
        mWidgetManager.addWorldClickListener(this);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        String env = SettingsStore.getInstance(getContext()).getEnvironment();
        mEnvironmentsRadio = findViewById(R.id.environmentRadio);
        mEnvironmentsRadio.setOnCheckedChangeListener(mEnvsListener);
        setEnv(mEnvironmentsRadio.getIdForValue(env), false);

        mEnvOverrideSwitch = findViewById(R.id.envOverrideSwitch);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);
        setEnvOverride(SettingsStore.getInstance(getContext()).isEnvironmentOverrideEnabled());
        mEnvOverrideSwitch.setHelpDelegate(() -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            SessionStore.get().loadUri(getContext().getString(R.string.environment_override_help_url));
            hide(REMOVE_WIDGET);
        });

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(mResetListener);

        mScrollbar = findViewById(R.id.scrollbar);
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

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);
        mWidgetManager.removeWorldClickListener(this);

        super.releaseWidget();
    }

    @Override
    public void show() {
        super.show();

        mScrollbar.scrollTo(0, 0);
        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_NO_DIM_BRIGHTNESS);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.popWorldBrightness(this);
    }

    private void setEnvOverride(boolean value) {
        mEnvOverrideSwitch.setOnCheckedChangeListener(null);
        mEnvOverrideSwitch.setValue(value, false);
        mEnvOverrideSwitch.setOnCheckedChangeListener(mEnvOverrideListener);

        SettingsStore.getInstance(getContext()).setEnvironmentOverrideEnabled(value);
    }

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;
        if (mEnvOverrideSwitch.isChecked() != SettingsStore.ENV_OVERRIDE_DEFAULT) {
            setEnvOverride(SettingsStore.ENV_OVERRIDE_DEFAULT);
            restart = true;
        }

        if (!mEnvironmentsRadio.getValueForId(mEnvironmentsRadio.getCheckedRadioButtonId()).equals(SettingsStore.ENV_DEFAULT)) {
            setEnv(mEnvironmentsRadio.getIdForValue(SettingsStore.ENV_DEFAULT), true);
        }

        if (restart)
            showRestartDialog();
    };

    private SwitchSetting.OnCheckedChangeListener mEnvOverrideListener = (compoundButton, value, doApply) -> {
        setEnvOverride(value);
        showRestartDialog();
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

    private void showRestartDialog() {
        hide(UIWidget.REMOVE_WIDGET);

        UIWidget widget = getChild(mRestartDialogHandle);
        if (widget == null) {
            widget = createChild(RestartDialogWidget.class, false);
            mRestartDialogHandle = widget.getHandle();
            widget.setDelegate(() -> onRestartDialogDismissed());
        }

        widget.show();
    }

    private void onRestartDialogDismissed() {
        show();
    }

    // WindowManagerDelegate.FocusChangeListener
    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible() && findViewById(newFocus.getId()) == null) {
            onDismiss();
        }
    }

    // WorldClickListener
    @Override
    public void onWorldClick() {
        if (isVisible()) {
            onDismiss();
        }
    }
}
