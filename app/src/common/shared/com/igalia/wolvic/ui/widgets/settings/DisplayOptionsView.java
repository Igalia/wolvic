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
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsDisplayBinding;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.Objects;

import java.util.ArrayList;
import java.util.List;

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

        int uaMode = SettingsStore.getInstance(getContext()).getUaMode();
        mBinding.uaRadio.setOnCheckedChangeListener(mUaModeListener);
        setUaMode(mBinding.uaRadio.getIdForValue(uaMode), false);

        int msaaLevel = SettingsStore.getInstance(getContext()).getMSAALevel();
        mBinding.msaaRadio.setOnCheckedChangeListener(mMSSAChangeListener);
        setMSAAMode(mBinding.msaaRadio.getIdForValue(msaaLevel), false);

        List<String> windowSizePresets = new ArrayList<>();
        for (SettingsStore.WindowSizePreset preset : SettingsStore.WindowSizePreset.values()) {
            windowSizePresets.add(getContext().getString(R.string.window_size_preset, preset.width, preset.height));
        }
        mBinding.windowsSize.setOptions(windowSizePresets.toArray(new String[0]));
        mBinding.windowsSize.setOnCheckedChangeListener(mWindowsSizeChangeListener);
        int windowWidth = SettingsStore.getInstance(getContext()).getWindowWidth();
        int windowHeight = SettingsStore.getInstance(getContext()).getWindowHeight();
        SettingsStore.WindowSizePreset windowSizePreset = SettingsStore.WindowSizePreset.fromValues(windowWidth, windowHeight);
        setWindowsSizePreset(windowSizePreset.ordinal(), false);

        int homepageId = getHomepageId(SettingsStore.getInstance(getContext()).getHomepage());
        mBinding.homepage.setOnCheckedChangeListener(mHomepageChangeListener);
        setHomepage(homepageId, false);

        mBinding.autoplaySwitch.setOnCheckedChangeListener(mAutoplayListener);
        setAutoplay(SettingsStore.getInstance(getContext()).isAutoplayEnabled(), false);

        mBinding.startWithPassthroughSwitch.setOnCheckedChangeListener(mStartWithPassthroughListener);
        setStartWithPassthrough(SettingsStore.getInstance(getContext()).isStartWithPassthroughEnabled());

        if (mWidgetManager != null && mWidgetManager.isPassthroughSupported()) {
            mBinding.startWithPassthroughSwitch.setVisibility(View.VISIBLE);
        } else {
            mBinding.startWithPassthroughSwitch.setVisibility(View.GONE);
        }

        mBinding.latinAutoCompleteSwitch.setOnCheckedChangeListener(mLatinAutoCompleteListener);
        setLatinAutoComplete(SettingsStore.getInstance(getContext()).isLatinAutoCompleteEnabled(), false);

        mBinding.headLockSwitch.setOnCheckedChangeListener(mHeadLockListener);
        setHeadLock(SettingsStore.getInstance(getContext()).isHeadLockEnabled(), false);

        mBinding.openTabsInBackgroundSwitch.setOnCheckedChangeListener(mOpenTabsInBackgroundListener);
        setOpenTabsInBackground(SettingsStore.getInstance(getContext()).isOpenTabsInBackgroundEnabled(), false);

        @SettingsStore.TabsLocation int tabsLocation = SettingsStore.getInstance(getContext()).getTabsLocation();
        mBinding.tabsLocationRadio.setOnCheckedChangeListener(mTabsLocationChangeListener);
        setTabsLocation(mBinding.tabsLocationRadio.getIdForValue(tabsLocation), false);

        mDefaultHomepageUrl = getContext().getString(R.string.HOMEPAGE_URL);

        mBinding.homepageEdit.setHint1(getContext().getString(R.string.homepage_hint, getContext().getString(R.string.app_name)));
        mBinding.homepageEdit.setDefaultFirstValue(mDefaultHomepageUrl);
        mBinding.homepageEdit.setFirstText(SettingsStore.getInstance(getContext()).getHomepage());
        mBinding.homepageEdit.setOnSaveClickedListener(mHomepageListener);
        setHomepage(SettingsStore.getInstance(getContext()).getHomepage());

        mBinding.densityEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mBinding.densityEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DENSITY_DEFAULT));
        mBinding.densityEdit.setFirstText(Float.toString(SettingsStore.getInstance(getContext()).getDisplayDensity()));
        mBinding.densityEdit.setOnSaveClickedListener(mDensityListener);
        setDisplayDensity(SettingsStore.getInstance(getContext()).getDisplayDensity());

        mBinding.dpiEdit.setHint1(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mBinding.dpiEdit.setDefaultFirstValue(String.valueOf(SettingsStore.DISPLAY_DPI_DEFAULT));
        mBinding.dpiEdit.setFirstText(Integer.toString(SettingsStore.getInstance(getContext()).getDisplayDpi()));
        mBinding.dpiEdit.setOnSaveClickedListener(mDpiListener);
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

    private RadioGroupSetting.OnCheckedChangeListener mUaModeListener = (radioGroup, checkedId, doApply) -> {
        setUaMode(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mMSSAChangeListener = (radioGroup, checkedId, doApply) -> {
        setMSAAMode(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mWindowsSizeChangeListener = (radioGroup, checkedId, doApply) -> {
        setWindowsSizePreset(checkedId, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mHomepageChangeListener = (radioGroup, checkedId, doApply) -> {
        setHomepage(checkedId, true);
    };

    private SwitchSetting.OnCheckedChangeListener mAutoplayListener = (compoundButton, enabled, apply) -> {
        setAutoplay(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mStartWithPassthroughListener = (compoundButton, value, doApply) -> {
        setStartWithPassthrough(value);
    };

    private SwitchSetting.OnCheckedChangeListener mLatinAutoCompleteListener = (compoundButton, enabled, apply) -> {
        setLatinAutoComplete(enabled, true);
    };

    private SwitchSetting.OnCheckedChangeListener mHeadLockListener = (compoundButton, value, doApply) -> {
        setHeadLock(value, true);
    };

    private SwitchSetting.OnCheckedChangeListener mOpenTabsInBackgroundListener = (compoundButton, value, doApply) -> {
        setOpenTabsInBackground(value, true);
    };

    private RadioGroupSetting.OnCheckedChangeListener mTabsLocationChangeListener = (radioGroup, checkedId, doApply) -> {
        setTabsLocation(checkedId, true);
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
                showRestartDialog(() -> {setDisplayDensity(prevDensity);});
            }

        } catch (NumberFormatException e) {
            float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
            if (setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT)) {
                showRestartDialog(() -> {setDisplayDensity(prevDensity);});
            }
        }
    };

    private OnClickListener mDpiListener = (view) -> {
        try {
            int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
            int newDpi = Integer.parseInt(mBinding.dpiEdit.getFirstText());
            if (setDisplayDpi(newDpi)) {
                showRestartDialog(() -> {setDisplayDpi(prevDpi);});
            }

        } catch (NumberFormatException e) {
            int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
            if (setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT)) {
                showRestartDialog(() -> {setDisplayDpi(prevDpi);});
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
        if (!mBinding.tabsLocationRadio.getValueForId(mBinding.tabsLocationRadio.getCheckedRadioButtonId()).equals(SettingsStore.TABS_LOCATION_DEFAULT)) {
            setTabsLocation(mBinding.tabsLocationRadio.getIdForValue(SettingsStore.TABS_LOCATION_DEFAULT), true);
        }

        if (mBinding.windowsSize.getCheckedRadioButtonId() != SettingsStore.WINDOW_SIZE_PRESET_DEFAULT.ordinal()) {
            setWindowsSizePreset(SettingsStore.WINDOW_SIZE_PRESET_DEFAULT.ordinal(), true);
        }
        
        int defaultHomepageId = getHomepageId(mDefaultHomepageUrl);
        if (mBinding.homepage.getCheckedRadioButtonId() != defaultHomepageId) {
            setHomepage(defaultHomepageId, true);
        }

        float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
        restart = restart | setDisplayDensity(SettingsStore.DISPLAY_DENSITY_DEFAULT);
        int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
        restart = restart | setDisplayDpi(SettingsStore.DISPLAY_DPI_DEFAULT);


        setHomepage(mDefaultHomepageUrl);
        setAutoplay(SettingsStore.AUTOPLAY_ENABLED, true);
        setCurvedDisplay(false, true);
        setHeadLock(SettingsStore.HEAD_LOCK_DEFAULT, true);
        setLatinAutoComplete(SettingsStore.LATIN_AUTO_COMPLETE_ENABLED, true);
        setCenterWindows(SettingsStore.CENTER_WINDOWS_DEFAULT, true);

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
    }

    private void setOpenTabsInBackground(boolean value, boolean doApply) {
        mBinding.openTabsInBackgroundSwitch.setOnCheckedChangeListener(null);
        mBinding.openTabsInBackgroundSwitch.setValue(value, false);
        mBinding.openTabsInBackgroundSwitch.setOnCheckedChangeListener(mOpenTabsInBackgroundListener);

        SettingsStore settingsStore = SettingsStore.getInstance(getContext());
        if (doApply) {
            settingsStore.setOpenTabsInBackgroundEnabled(value);
        }
    }

    private void setTabsLocation(int checkedId, boolean doApply) {
        mBinding.tabsLocationRadio.setOnCheckedChangeListener(null);
        mBinding.tabsLocationRadio.setChecked(checkedId, doApply);
        mBinding.tabsLocationRadio.setOnCheckedChangeListener(mTabsLocationChangeListener);

        if (doApply) {
            int tabsLocationValue = (Integer) mBinding.tabsLocationRadio.getValueForId(checkedId);
            SettingsStore.getInstance(getContext()).setTabsLocation(tabsLocationValue);
        }
    }

    private void setHomepage(int checkedId, boolean doApply) {
        mBinding.homepage.setOnCheckedChangeListener(null);
        mBinding.homepage.setChecked(checkedId, doApply);
        mBinding.homepage.setOnCheckedChangeListener(mHomepageChangeListener);

        if (checkedId == 0) {
            mBinding.homepageEdit.setVisibility(View.GONE);
            SettingsStore.getInstance(getContext()).setHomepage(UrlUtils.ABOUT_NEWTAB);
        } else if (checkedId == 1) {
            mBinding.homepageEdit.setVisibility(View.GONE);
            SettingsStore.getInstance(getContext()).setHomepage(mDefaultHomepageUrl);
        } else if (checkedId == 2) {
            mBinding.homepageEdit.setVisibility(View.VISIBLE);
        }
    }

    private int getHomepageId(String homepage) {
        if (Objects.equals(homepage, UrlUtils.ABOUT_NEWTAB)) {
            return 0;
        } else if (Objects.equals(homepage, getContext().getString(R.string.HOMEPAGE_URL))) {
            return 1;
        } else {
            return 2;
        }
    }

    private void setHomepage(String newHomepage) {
        if (mBinding.homepageEdit.getVisibility() != VISIBLE) {
            return;
        }
        mBinding.homepageEdit.setOnSaveClickedListener(null);
        mBinding.homepageEdit.setFirstText(newHomepage);
        SettingsStore.getInstance(getContext()).setHomepage(newHomepage);
        mBinding.homepageEdit.setOnSaveClickedListener(mHomepageListener);
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
            showRestartDialog(() -> {setMSAAMode(previouslyCheckedMSAAId, true);});
        }
    }

    private void setWindowsSizePreset(int checkedId, boolean doApply) {
        mBinding.windowsSize.setOnCheckedChangeListener(null);
        mBinding.windowsSize.setChecked(checkedId, doApply);
        mBinding.windowsSize.setOnCheckedChangeListener(mWindowsSizeChangeListener);

        SettingsStore.getInstance(getContext()).setWindowSizePreset(checkedId);
    }

    private boolean setDisplayDensity(float newDensity) {
        mBinding.densityEdit.setOnSaveClickedListener(null);
        boolean restart = false;
        float prevDensity = SettingsStore.getInstance(getContext()).getDisplayDensity();
        if (newDensity <= 0) {
            newDensity = prevDensity;

        } else if (prevDensity != newDensity) {
            SettingsStore.getInstance(getContext()).setDisplayDensity(newDensity);
            restart = true;
        }
        mBinding.densityEdit.setFirstText(Float.toString(newDensity));
        mBinding.densityEdit.setOnSaveClickedListener(mDensityListener);

        return restart;
    }

    private boolean setDisplayDpi(int newDpi) {
        mBinding.dpiEdit.setOnSaveClickedListener(null);
        boolean restart = false;
        int prevDpi = SettingsStore.getInstance(getContext()).getDisplayDpi();
        if (newDpi < SettingsStore.DISPLAY_DPI_MIN || newDpi > SettingsStore.DISPLAY_DPI_MAX) {
            newDpi = prevDpi;

        } else if (prevDpi != newDpi) {
            SettingsStore.getInstance(getContext()).setDisplayDpi(newDpi);
            restart = true;
        }
        mBinding.dpiEdit.setFirstText(Integer.toString(newDpi));
        mBinding.dpiEdit.setOnSaveClickedListener(mDpiListener);

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
