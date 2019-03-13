/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.options;

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
import org.mozilla.vrbrowser.ui.views.settings.DoubleEditSetting;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SingleEditSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.dialogs.RestartDialogWidget;

public class ControllerOptionsWidget extends UIWidget implements WidgetManagerDelegate.WorldClickListener {
    private AudioEngine mAudio;
    private UIButton mBackButton;

    private RadioGroupSetting mPointerColorRadio;
    private RadioGroupSetting mScrollDirectionRadio;


    private ButtonSetting mResetButton;

    private ScrollView mScrollbar;

    public ControllerOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ControllerOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ControllerOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_controller, this);
        mAudio = AudioEngine.fromContext(aContext);

        mBackButton = findViewById(R.id.backButton);
        mBackButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            hide(REMOVE_WIDGET);
            if (mDelegate != null) {
                mDelegate.onDismiss();
            }
        });

        mScrollbar = findViewById(R.id.scrollbar);

        int color = SettingsStore.getInstance(getContext()).getPointerColor();
        mPointerColorRadio = findViewById(R.id.pointer_color_radio);
        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);
        setPointerColor(mPointerColorRadio.getIdForValue(color), false);

        int scrollDirection = SettingsStore.getInstance(getContext()).getScrollDirection();
        mScrollDirectionRadio = findViewById(R.id.scroll_direction_radio);
        mScrollDirectionRadio.setOnCheckedChangeListener(mScrollDirectionListener);
        setScrollDirection(mScrollDirectionRadio.getIdForValue(scrollDirection), false);

        mResetButton = findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(v -> resetOptions());
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
    public void show() {
        super.show();

        mWidgetManager.addWorldClickListener(this);
        mScrollbar.scrollTo(0, 0);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.removeWorldClickListener(this);
    }

    private void resetOptions() {
        if (!mPointerColorRadio.getValueForId(mPointerColorRadio.getCheckedRadioButtonId()).equals(SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT)) {
            setPointerColor(mPointerColorRadio.getIdForValue(SettingsStore.POINTER_COLOR_DEFAULT_DEFAULT), true);
        }
        if (!mScrollDirectionRadio.getValueForId(mScrollDirectionRadio.getCheckedRadioButtonId()).equals(SettingsStore.SCROLL_DIRECTION_DEFAULT)) {
            setScrollDirection(mScrollDirectionRadio.getIdForValue(SettingsStore.SCROLL_DIRECTION_DEFAULT), true);
        }
    }

    private void setPointerColor(int checkedId, boolean doApply) {
        mPointerColorRadio.setOnCheckedChangeListener(null);
        mPointerColorRadio.setChecked(checkedId, doApply);
        mPointerColorRadio.setOnCheckedChangeListener(mPointerColorListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPointerColor((int)mPointerColorRadio.getValueForId(checkedId));
            mWidgetManager.updatePointerColor();
        }
    }

    private void setScrollDirection(int checkedId, boolean doApply) {
        mScrollDirectionRadio.setOnCheckedChangeListener(null);
        mScrollDirectionRadio.setChecked(checkedId, doApply);
        mScrollDirectionRadio.setOnCheckedChangeListener(mScrollDirectionListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setScrollDirection((int)mScrollDirectionRadio.getValueForId(checkedId));
        }
    }

    private RadioGroupSetting.OnCheckedChangeListener mPointerColorListener = (radioGroup, checkedId, doApply) -> {
        setPointerColor(checkedId, doApply);
    };

    private RadioGroupSetting.OnCheckedChangeListener mScrollDirectionListener = (radioGroup, checkedId, doApply) -> {
        setScrollDirection(checkedId, doApply);
    };

    // WorldClickListener
    @Override
    public void onWorldClick() {
        if (isVisible()) {
            onDismiss();
        }
    }
}
