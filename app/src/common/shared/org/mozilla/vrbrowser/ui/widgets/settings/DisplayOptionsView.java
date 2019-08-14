/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.OptionsDisplayBinding;
import org.mozilla.vrbrowser.ui.views.settings.RadioGroupSetting;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.DeviceType;

class DisplayOptionsView extends SettingsView {

    private OptionsDisplayBinding mBinding;
    private String mDefaultHomepageUrl;

    public DisplayOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_display, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setResetClickListener(mResetListener);

        // Options
        mBinding.curvedDisplaySwitch.setOnCheckedChangeListener(mCurvedDisplayListener);
        setCurvedDisplay(SettingsStore.getInstance(getContext()).getCylinderDensity() > 0.0f, false);

        int uaMode = SettingsStore.getInstance(getContext()).getUaMode();
        mBinding.uaRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(mBinding.uaRadio.getIdForValue(uaMode), false);

        int msaaLevel = SettingsStore.getInstance(getContext()).getMSAALevel();
        mBinding.msaaRadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setMSAAMode(mBinding.msaaRadio.getIdForValue(msaaLevel), false);

        mBinding.autoplaySwitch.setOnCheckedChangeListener(mAutoplayListener);
        setAutoplay(SessionStore.get().getAutoplayEnabled(), false);

        mDefaultHomepageUrl = getContext().getString(R.string.homepage_url);

        mBinding.homepageEdit.setHint1(getContext().getString(R.string.homepage_hint, getContext().getString(R.string.app_name)));
        mBinding.homepageEdit.setDefaultFirstValue(mDefaultHomepageUrl);
        mBinding.homepageEdit.setFirstText(SettingsStore.getInstance(getContext()).getHomepage());
        mBinding.homepageEdit.setOnClickListener(mHomepageListener);
        setHomepage(SettingsStore.getInstance(getContext()).getHomepage());

        if (DeviceType.isOculusBuild() || DeviceType.isWaveBuild()) {
            mBinding.foveatedAppRadio.setVisibility(View.VISIBLE);
            // Uncomment this when Foveated Rendering for WebVR makes more sense
            // mFoveatedWebVRRadio.setVisibility(View.VISIBLE);
            int level = SettingsStore.getInstance(getContext()).getFoveatedLevelApp();
            setFoveatedLevel(mBinding.foveatedAppRadio, mBinding.foveatedAppRadio.getIdForValue(level), false);
            mBinding.foveatedAppRadio.setOnCheckedChangeListener((compoundButton, checkedId, apply) -> setFoveatedLevel(mBinding.foveatedAppRadio, checkedId, apply));

            level = SettingsStore.getInstance(getContext()).getFoveatedLevelWebVR();
            setFoveatedLevel(mBinding.foveatedWebvrRadio, mBinding.foveatedWebvrRadio.getIdForValue(level), false);
            mBinding.foveatedWebvrRadio.setOnCheckedChangeListener((compoundButton, checkedId, apply) -> setFoveatedLevel(mBinding.foveatedWebvrRadio, checkedId, apply));
        }

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

        mBinding.windowSizeEdit.setHint1(String.valueOf(SettingsStore.WINDOW_WIDTH_DEFAULT));
        mBinding.windowSizeEdit.setHint2(String.valueOf(SettingsStore.WINDOW_HEIGHT_DEFAULT));
        mBinding.windowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.WINDOW_WIDTH_DEFAULT));
        mBinding.windowSizeEdit.setDefaultSecondValue(String.valueOf(SettingsStore.WINDOW_HEIGHT_DEFAULT));
        mBinding.windowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowWidth()));
        mBinding.windowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getWindowHeight()));
        mBinding.windowSizeEdit.setOnClickListener(mWindowSizeListener);
        setWindowSize(
                SettingsStore.getInstance(getContext()).getWindowWidth(),
                SettingsStore.getInstance(getContext()).getWindowHeight(),
                false);

        mBinding.maxWindowSizeEdit.setHint1(String.valueOf(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT));
        mBinding.maxWindowSizeEdit.setHint2(String.valueOf(SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT));
        mBinding.maxWindowSizeEdit.setDefaultFirstValue(String.valueOf(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT));
        mBinding.maxWindowSizeEdit.setDefaultSecondValue(String.valueOf(SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT));
        mBinding.maxWindowSizeEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowWidth()));
        mBinding.maxWindowSizeEdit.setSecondText(Integer.toString(SettingsStore.getInstance(getContext()).getMaxWindowHeight()));
        mBinding.maxWindowSizeEdit.setOnClickListener(mMaxWindowSizeListener);
        setMaxWindowSize(
                SettingsStore.getInstance(getContext()).getMaxWindowWidth(),
                SettingsStore.getInstance(getContext()).getMaxWindowHeight(),
                false);
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

        if (mBinding.windowSizeEdit.isEditing()) {
            editing = true;
            mBinding.windowSizeEdit.cancel();
        }

        if (mBinding.maxWindowSizeEdit.isEditing()) {
            editing = true;
            mBinding.maxWindowSizeEdit.cancel();
        }

        if (mBinding.homepageEdit.isEditing()) {
            editing = true;
            mBinding.homepageEdit.cancel();
        }

        return editing;
    }

    private RadioGroupSetting.OnCheckedChangeListener mUaModeListener = (radioGroup, checkedId, doApply) -> {
        setUaMode(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mMSSAChangeListener = (radioGroup, checkedId, doApply) -> {
        setMSAAMode(checkedId, true);
    };

    private SwitchSetting.OnCheckedChangeListener mAutoplayListener = (compoundButton, enabled, apply) -> {
        setAutoplay(enabled, true);
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
            float newDensity = Float.parseFloat(mBinding.densityEdit.getFirstText());
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
            int newDpi = Integer.parseInt(mBinding.dpiEdit.getFirstText());
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
            int newWindowWidth = Integer.parseInt(mBinding.windowSizeEdit.getFirstText());
            int newWindowHeight = Integer.parseInt(mBinding.windowSizeEdit.getSecondText());
            setWindowSize(newWindowWidth, newWindowHeight, true);

        } catch (NumberFormatException e) {
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
        }
    };

    private OnClickListener mMaxWindowSizeListener = (view) -> {
        try {
            int newMaxWindowWidth = Integer.parseInt(mBinding.maxWindowSizeEdit.getFirstText());
            int newMaxWindowHeight = Integer.parseInt(mBinding.maxWindowSizeEdit.getSecondText());
            setMaxWindowSize(newMaxWindowWidth, newMaxWindowHeight, true);

        } catch (NumberFormatException e) {
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);
        }
    };

    private SwitchSetting.OnCheckedChangeListener mCurvedDisplayListener = (compoundButton, enabled, apply) ->
            setCurvedDisplay(enabled, true);

    private OnClickListener mResetListener = (view) -> {
        boolean restart = false;

        if (!mBinding.uaRadio.getValueForId(mBinding.uaRadio.getCheckedRadioButtonId()).equals(SettingsStore.UA_MODE_DEFAULT)) {
            setUaMode(mBinding.uaRadio.getIdForValue(SettingsStore.UA_MODE_DEFAULT), true);
        }
        if (!mBinding.msaaRadio.getValueForId(mBinding.msaaRadio.getCheckedRadioButtonId()).equals(SettingsStore.MSAA_DEFAULT_LEVEL)) {
            setMSAAMode(mBinding.msaaRadio.getIdForValue(SettingsStore.MSAA_DEFAULT_LEVEL), true);
        }
        if (DeviceType.isOculusBuild() || DeviceType.isWaveBuild()) {
            if (!mBinding.foveatedAppRadio.getValueForId(mBinding.foveatedAppRadio.getCheckedRadioButtonId()).equals(SettingsStore.FOVEATED_APP_DEFAULT_LEVEL)) {
                setFoveatedLevel(mBinding.foveatedAppRadio, mBinding.foveatedAppRadio.getIdForValue(SettingsStore.FOVEATED_APP_DEFAULT_LEVEL), true);
            }
            if (!mBinding.foveatedWebvrRadio.getValueForId(mBinding.foveatedWebvrRadio.getCheckedRadioButtonId()).equals(SettingsStore.FOVEATED_WEBVR_DEFAULT_LEVEL)) {
                setFoveatedLevel(mBinding.foveatedWebvrRadio, mBinding.foveatedWebvrRadio.getIdForValue(SettingsStore.FOVEATED_WEBVR_DEFAULT_LEVEL), true);
            }
        }

        restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
        restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);

        try {
            if (Integer.parseInt(mBinding.windowSizeEdit.getFirstText()) != SettingsStore.WINDOW_WIDTH_DEFAULT ||
                    Integer.parseInt(mBinding.windowSizeEdit.getSecondText()) != SettingsStore.WINDOW_HEIGHT_DEFAULT) {
                setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
            }
            if (Integer.parseInt(mBinding.maxWindowSizeEdit.getFirstText()) != SettingsStore.MAX_WINDOW_WIDTH_DEFAULT ||
                    Integer.parseInt(mBinding.maxWindowSizeEdit.getSecondText()) != SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT) {
                setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);
            }
        } catch (NumberFormatException ex) {
            setWindowSize(SettingsStore.WINDOW_WIDTH_DEFAULT, SettingsStore.WINDOW_HEIGHT_DEFAULT, true);
            setMaxWindowSize(SettingsStore.MAX_WINDOW_WIDTH_DEFAULT, SettingsStore.MAX_WINDOW_HEIGHT_DEFAULT, true);
        }

        setHomepage(mDefaultHomepageUrl);
        setAutoplay(SettingsStore.AUTOPLAY_ENABLED, true);
        setCurvedDisplay(false, true);

        if (restart) {
            showRestartDialog();
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

    private void setAutoplay(boolean value, boolean doApply) {
        mBinding.autoplaySwitch.setOnCheckedChangeListener(null);
        mBinding.autoplaySwitch.setValue(value, false);
        mBinding.autoplaySwitch.setOnCheckedChangeListener(mAutoplayListener);

        if (doApply) {
            SessionStore.get().setAutoplayEnabled(value);
        }
    }

    private void setHomepage(String newHomepage) {
        mBinding.homepageEdit.setOnClickListener(null);
        mBinding.homepageEdit.setFirstText(newHomepage);
        SettingsStore.getInstance(getContext()).setHomepage(newHomepage);
        mBinding.homepageEdit.setOnClickListener(mHomepageListener);
    }

    private void setUaMode(int checkId, boolean doApply) {
        mBinding.uaRadio.setOnCheckedChangeListener(null);
        mBinding.uaRadio.setChecked(checkId, doApply);
        mBinding.uaRadio.setOnCheckedChangeListener(mUaModeListener);

        SettingsStore.getInstance(getContext()).setUaMode(checkId);

        if (doApply) {
            SessionStore.get().setUaMode((Integer)mBinding.uaRadio.getValueForId(checkId));
        }
    }

    private void setMSAAMode(int checkedId, boolean doApply) {
        mBinding.msaaRadio.setOnCheckedChangeListener(null);
        mBinding.msaaRadio.setChecked(checkedId, doApply);
        mBinding.msaaRadio.setOnCheckedChangeListener(mMSSAChangeListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setMSAALevel((Integer)mBinding.msaaRadio.getValueForId(checkedId));
            showRestartDialog();
        }
    }

    private void setFoveatedLevel(RadioGroupSetting aSetting, int checkedId, boolean doApply) {
        RadioGroupSetting.OnCheckedChangeListener listener = aSetting.getOnCheckdChangeListener();
        aSetting.setOnCheckedChangeListener(null);
        aSetting.setChecked(checkedId, doApply);
        aSetting.setOnCheckedChangeListener(listener);

        int level = (Integer)aSetting.getValueForId(checkedId);

        if (aSetting == mBinding.foveatedAppRadio) {
            SettingsStore.getInstance(getContext()).setFoveatedLevelApp(level);
        } else {
            SettingsStore.getInstance(getContext()).setFoveatedLevelWebVR(level);
        }

        if (doApply) {
            mWidgetManager.updateFoveatedLevel();
            // "WaveVR WVR_RenderFoveation(false) doesn't work properly, we need to restart."
            if (level == 0 && DeviceType.isWaveBuild()) {
                showRestartDialog();
            }
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
        int prevDensity = SettingsStore.getInstance(getContext()).getDisplayDpi();
        if (newDpi <= 0) {
            newDpi = prevDensity;

        } else if (prevDensity != newDpi) {
            SettingsStore.getInstance(getContext()).setDisplayDpi(newDpi);
            restart = true;
        }
        mBinding.dpiEdit.setFirstText(Integer.toString(newDpi));
        mBinding.dpiEdit.setOnClickListener(mDpiListener);

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
        mBinding.windowSizeEdit.setFirstText(newWindowWidthStr);
        String newWindowHeightStr = Integer.toString(newWindowHeight);
        mBinding.windowSizeEdit.setSecondText(newWindowHeightStr);
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
                SettingsStore.getInstance(getContext()).setMaxWindowWidth(newMaxWindowWidth);
                SettingsStore.getInstance(getContext()).setMaxWindowHeight(newMaxWindowHeight);
                showRestartDialog();
            }
        }

        String newMaxWindowWidthStr = Integer.toString(newMaxWindowWidth);
        mBinding.maxWindowSizeEdit.setFirstText(newMaxWindowWidthStr);
        String newMaxWindowHeightStr = Integer.toString(newMaxWindowHeight);
        mBinding.maxWindowSizeEdit.setSecondText(newMaxWindowHeightStr);
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
            if (mBinding.windowSizeEdit.contains(oldFocus) &&
                    (newFocus != null && !mBinding.windowSizeEdit.contains(newFocus)) &&
                    mBinding.windowSizeEdit.isEditing()) {
                mBinding.windowSizeEdit.cancel();
            }
            if (mBinding.maxWindowSizeEdit.contains(oldFocus) &&
                    (newFocus != null && !mBinding.maxWindowSizeEdit.contains(newFocus)) &&
                    mBinding.maxWindowSizeEdit.isEditing()) {
                mBinding.maxWindowSizeEdit.cancel();
            }
            if (mBinding.homepageEdit.contains(oldFocus) && mBinding.homepageEdit.isEditing()) {
                mBinding.homepageEdit.cancel();
            }
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.display_options_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.display_options_height));
    }

}
