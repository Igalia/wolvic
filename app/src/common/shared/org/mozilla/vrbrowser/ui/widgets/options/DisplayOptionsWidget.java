/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.options;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
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
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.dialogs.RestartDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class DisplayOptionsWidget extends UIWidget implements
        WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.FocusChangeListener {

    private AudioEngine mAudio;
    private UIButton mBackButton;

    private SwitchSetting mCurvedDisplaySwitch;
    private RadioGroupSetting mUaModeRadio;
    private RadioGroupSetting mMSAARadio;

    private SingleEditSetting mDensityEdit;
    private SingleEditSetting mDpiEdit;
    private DoubleEditSetting mWindowSizeEdit;
    private DoubleEditSetting mMaxWindowSizeEdit;

    private ButtonSetting mResetButton;

    private int mRestartDialogHandle = -1;
    private ScrollView mScrollbar;

    public DisplayOptionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public DisplayOptionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public DisplayOptionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.options_display, this);

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

        mCurvedDisplaySwitch = findViewById(R.id.curved_display_switch);
        mCurvedDisplaySwitch.setChecked(SettingsStore.getInstance(getContext()).getCylinderDensity() > 0.0f);
        mCurvedDisplaySwitch.setOnCheckedChangeListener((compoundButton, enabled, apply) -> {
            SettingsStore.getInstance(getContext()).setCylinderDensity(enabled ? SettingsStore.CYLINDER_DENSITY_ENABLED_DEFAULT : 0.0f);
            showRestartDialog();
        });

        int uaMode = SettingsStore.getInstance(getContext()).getUaMode();
        mUaModeRadio = findViewById(R.id.ua_radio);
        mUaModeRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(mUaModeRadio.getIdForValue(uaMode), false);

        int msaaLevel = SettingsStore.getInstance(getContext()).getMSAALevel();
        mMSAARadio = findViewById(R.id.msaa_radio);
        mMSAARadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setMSAAMode(mMSAARadio.getIdForValue(msaaLevel), false);

        mDensityEdit = findViewById(R.id.density_edit);
        mDensityEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mDensityEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mDensityEdit.setFirstText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mDensityEdit.setOnClickListener(mDensityListener);
        setDisplayDensity(SettingsStore.getInstance(getContext()).getDisplayDensity());

        mDpiEdit = findViewById(R.id.dpi_edit);
        mDpiEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mDpiEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mDpiEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mDpiEdit.setOnClickListener(mDpiListener);
        setDisplayDpi(SettingsStore.getInstance(getContext()).getDisplayDpi());

        mWindowSizeEdit = findViewById(R.id.windowSize_edit);
        mWindowSizeEdit.setHint1(String.valueOf(SettingsStore.WINDOW_WIDTH_DEFAULT));
        mWindowSizeEdit.setHint2(String.valueOf(SettingsStore.WINDOW_HEIGHT_DEFAULT));
        mWindowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.WINDOW_WIDTH_DEFAULT));
        mWindowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.WINDOW_HEIGHT_DEFAULT));
        mWindowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowWidth()));
        mWindowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowHeight()));
        mWindowSizeEdit.setOnClickListener(mWindowSizeListener);
        setWindowSize(
                SettingsStore.getInstance(getContext()).getWindowWidth(),
                SettingsStore.getInstance(getContext()).getWindowHeight(),
                false);

        mMaxWindowSizeEdit = findViewById(R.id.maxWindowSize_edit);
        mMaxWindowSizeEdit.setHint1(String.valueOf(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT));
        mMaxWindowSizeEdit.setHint2(String.valueOf(SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT));
        mMaxWindowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT));
        mMaxWindowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT));
        mMaxWindowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowWidth()));
        mMaxWindowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowHeight()));
        mMaxWindowSizeEdit.setOnClickListener(mMaxWindowSizeListener);
        setMaxWindowSize(
                SettingsStore.getInstance(getContext()).getMaxWindowWidth(),
                SettingsStore.getInstance(getContext()).getMaxWindowHeight(),
                false);

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
    public void show() {
        super.show();

        mWidgetManager.addWorldClickListener(this);
        mWidgetManager.addFocusChangeListener(this);
        mScrollbar.scrollTo(0, 0);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mDensityEdit.cancel();
        mDpiEdit.cancel();
        mWindowSizeEdit.cancel();
        mMaxWindowSizeEdit.cancel();

        mWidgetManager.removeWorldClickListener(this);
        mWidgetManager.removeFocusChangeListener(this);
    }

    @Override
    protected void onDismiss() {
        boolean dismiss = true;

        if (mDensityEdit.isEditing()) {
            dismiss = false;
            mDensityEdit.cancel();
        }

        if (mDpiEdit.isEditing()) {
            dismiss = false;
            mDpiEdit.cancel();
        }

        if (mWindowSizeEdit.isEditing()) {
            dismiss = false;
            mWindowSizeEdit.cancel();
        }

        if (mMaxWindowSizeEdit.isEditing()) {
            dismiss = false;
            mMaxWindowSizeEdit.cancel();
        }

        if (dismiss) {
            super.onDismiss();
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


    private RadioGroupSetting.OnCheckedChangeListener mUaModeListener = (radioGroup, checkedId, doApply) -> {
        setUaMode(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mMSSAChangeListener = (radioGroup, checkedId, doApply) -> {
        setMSAAMode(checkedId, true);
    };

    private OnClickListener mDensityListener = (view) -> {
        try {
            float newDensity = Float.parseFloat(mDensityEdit.getFirstText());
            if (setDisplayDensity(newDensity)) {
                showRestartDialog();
            }

        } catch (NumberFormatException e) {
            if (setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mDpiListener = (view) -> {
        try {
            int newDpi = Integer.parseInt(mDpiEdit.getFirstText());
            if (setDisplayDpi(newDpi)) {
                showRestartDialog();
            }

        } catch (NumberFormatException e) {
            if (setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT)) {
                showRestartDialog();
            }
        }
    };

    private OnClickListener mWindowSizeListener = (view) -> {
        try {
            int newWindowWidth = Integer.parseInt(mWindowSizeEdit.getFirstText());
            int newWindowHeight = Integer.parseInt(mWindowSizeEdit.getSecondText());
            setWindowSize(newWindowWidth, newWindowHeight, true);

        } catch (NumberFormatException e) {
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
        }
    };

    private OnClickListener mMaxWindowSizeListener = (view) -> {
        try {
            int newMaxWindowWidth = Integer.parseInt(mMaxWindowSizeEdit.getFirstText());
            int newMaxWindowHeight = Integer.parseInt(mMaxWindowSizeEdit.getSecondText());
            setMaxWindowSize(newMaxWindowWidth, newMaxWindowHeight, true);

        } catch (NumberFormatException e) {
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);
        }
    };

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;

        if (!mUaModeRadio.getValueForId(mUaModeRadio.getCheckedRadioButtonId()).equals(SettingsStore.UA_MODE_DEFAULT)) {
            setUaMode(mUaModeRadio.getIdForValue(SettingsStore.UA_MODE_DEFAULT), true);
        }
        if (!mMSAARadio.getValueForId(mMSAARadio.getCheckedRadioButtonId()).equals(SettingsStore.MSAA_DEFAULT_LEVEL)) {
            setMSAAMode(mMSAARadio.getIdForValue(SettingsStore.MSAA_DEFAULT_LEVEL), true);
        }

        restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
        restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);

        if (Integer.parseInt(mWindowSizeEdit.getFirstText()) != SettingsStore.WINDOW_WIDTH_DEFAULT ||
                Integer.parseInt(mWindowSizeEdit.getSecondText()) != SettingsStore.WINDOW_HEIGHT_DEFAULT) {
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
        }
        if (Integer.parseInt(mMaxWindowSizeEdit.getFirstText()) != SettingsStore.MAX_WINDOW_WIDTH_DEFAULT ||
                Integer.parseInt(mMaxWindowSizeEdit.getSecondText()) != SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT) {
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);
        }

        if (restart)
            showRestartDialog();
    };

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

    private boolean setDisplayDensity(float newDensity) {
        mDensityEdit.setOnClickListener(null);
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
        mDpiEdit.setOnClickListener(null);
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
                mWidgetManager.setWindowSize(newWindowWidth, newWindowHeight);
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

    // WindowManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus != null) {
            if (mDensityEdit.contains(oldFocus) && mDensityEdit.isEditing()) {
                mDensityEdit.cancel();
            }
            if (mDpiEdit.contains(oldFocus) && mDpiEdit.isEditing()) {
                mDpiEdit.cancel();
            }
            if (mWindowSizeEdit.contains(oldFocus) &&
                    (newFocus != null && !mWindowSizeEdit.contains(newFocus)) &&
                    mWindowSizeEdit.isEditing()) {
                mWindowSizeEdit.cancel();
            }
            if (mMaxWindowSizeEdit.contains(oldFocus) &&
                    (newFocus != null && !mMaxWindowSizeEdit.contains(newFocus)) &&
                    mMaxWindowSizeEdit.isEditing()) {
                mMaxWindowSizeEdit.cancel();
            }
        }

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
