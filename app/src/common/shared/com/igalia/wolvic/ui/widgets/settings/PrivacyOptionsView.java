/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.OptionsPrivacyBinding;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.ui.views.settings.RadioGroupSetting;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.utils.DeviceType;

import java.util.ArrayList;

class PrivacyOptionsView extends SettingsView {

    private OptionsPrivacyBinding mBinding;
    private ArrayList<Pair<SwitchSetting, String>> mPermissionButtons;

    public PrivacyOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        updateUI();

        ((Application)aContext.getApplicationContext()).registerActivityLifecycleCallbacks(mLifeCycleListener);
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_privacy, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> resetOptions());

        // Options
        mBinding.showTermsButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.TERMS_OF_SERVICE));

        mBinding.showPrivacyButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.PRIVACY_POLICY));

        mBinding.clearCookiesSite.setOnClickListener(v -> {
            SessionStore.get().clearCache(
                    WRuntime.ClearFlags.SITE_DATA |
                            WRuntime.ClearFlags.COOKIES |
                            WRuntime.ClearFlags.SITE_SETTINGS);
        });

        mBinding.clearWebContent.setOnClickListener(v -> {
            SessionStore.get().clearCache(WRuntime.ClearFlags.ALL_CACHES);
        });

        mBinding.clearUserData.setOnClickListener(v -> {
            showClearUserDataDialog();
        });

        mBinding.permissionsTitle.setText(getContext().getString(R.string.security_options_permissions_title, getContext().getString(R.string.app_name)));

        mPermissionButtons = new ArrayList<>();
        mPermissionButtons.add(Pair.create(mBinding.microphonePermissionSwitch, Manifest.permission.RECORD_AUDIO));

        if (DeviceType.isOculusBuild() || DeviceType.isWaveBuild()) {
            mBinding.cameraPermissionSwitch.setVisibility(View.GONE);
        } else {
            mPermissionButtons.add(Pair.create(mBinding.cameraPermissionSwitch, Manifest.permission.CAMERA));
        }

        if (DeviceType.isOculusBuild()) {
            mBinding.storagePermissionSwitch.setVisibility(View.GONE);
            mBinding.locationPermissionSwitch.setVisibility(View.GONE);
        } else {
            mPermissionButtons.add(Pair.create(mBinding.storagePermissionSwitch, Manifest.permission.WRITE_EXTERNAL_STORAGE));
            mPermissionButtons.add(Pair.create(mBinding.locationPermissionSwitch, Manifest.permission.ACCESS_FINE_LOCATION));

            // Display a warning message if approximate location is available but precise location is not.
            boolean hasCoarseLocation = mWidgetManager.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
            boolean hasFineLocation = mWidgetManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION);
            mBinding.locationPermissionWarning.setVisibility((hasCoarseLocation && !hasFineLocation) ? VISIBLE : GONE);
        }

        for (Pair<SwitchSetting, String> button : mPermissionButtons) {
            if (button.second.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                button.first.setChecked(mWidgetManager.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION));
            } else {
                button.first.setChecked(mWidgetManager.isPermissionGranted(button.second));
            }
            button.first.setOnCheckedChangeListener((compoundButton, enabled, apply) ->
                    togglePermission(button.first, button.second));
        }

        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(mDrmContentListener);
        mBinding.drmContentPlaybackSwitch.setDescription(getResources().getString(R.string.security_options_drm_content_v1, getResources().getString(R.string.sumo_drm_url)));
        mBinding.drmContentPlaybackSwitch.setLinkClickListener((widget, url) -> {
            mWidgetManager.openNewTabForeground(url);
            exitWholeSettings();
        });
        setDrmContent(SettingsStore.getInstance(getContext()).isDrmContentPlaybackEnabled(), false);

        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(mNotificationsListener);
        setNotifications(SettingsStore.getInstance(getContext()).isNotificationsEnabled(), false);

        mBinding.speechDataSwitch.setOnCheckedChangeListener(mSpeechDataListener);
        setSpeechData(SettingsStore.getInstance(getContext()).isSpeechDataCollectionEnabled(), false);

        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(mTelemetryListener);
        setTelemetry(SettingsStore.getInstance(getContext()).isTelemetryEnabled(), false);

        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(mCrashReportsListener);
        setCrashReports(SettingsStore.getInstance(getContext()).isCrashReportingEnabled(), false);

        mBinding.useSystemRootCASwitch.setOnCheckedChangeListener(mUseSystemRootCAListener);
        setUseSystemRootCA(SettingsStore.getInstance(getContext()).isSystemRootCAEnabled(), false);

        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(mPopUpsBlockingListener);
        setPopUpsBlocking(SettingsStore.getInstance(getContext()).isPopUpsBlockingEnabled(), false);

        mBinding.popUpsBlockingExceptionsButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.POPUP_EXCEPTIONS));

        mBinding.restoreTabsSwitch.setOnCheckedChangeListener(mRestoreTabsListener);
        setRestoreTabs(SettingsStore.getInstance(getContext()).isRestoreTabsEnabled(), false);

        mBinding.autocompleteSwitch.setOnCheckedChangeListener(mAutocompleteListener);
        setAutocomplete(SettingsStore.getInstance(getContext()).isAutocompleteEnabled(), false);

        mBinding.searchEngineButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.SEARCH_ENGINE));
        String searchEngineName = SearchEngineWrapper.get(getContext()).resolveCurrentSearchEngine().getName();
        mBinding.searchEngineDescription.setText(searchEngineName);

        mBinding.webxrSwitch.setOnCheckedChangeListener(mWebXRListener);
        setWebXR(SettingsStore.getInstance(getContext()).isWebXREnabled(), false);
        mBinding.webxrExceptionsButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.WEBXR_EXCEPTIONS));

        mBinding.trackingProtectionButton.setOnClickListener(v -> mDelegate.showView(SettingViewType.TRACKING_EXCEPTION));
        mBinding.trackingProtectionButton.setDescription(getResources().getString(R.string.privacy_options_tracking, getResources().getString(R.string.sumo_etp_url)));
        mBinding.trackingProtectionButton.setLinkClickListener((widget, url) -> {
            mWidgetManager.openNewTabForeground(url);
            exitWholeSettings();
        });
        int etpLevel = SettingsStore.getInstance(getContext()).getTrackingProtectionLevel();
        mBinding.trackingProtectionRadio.setOnCheckedChangeListener(mTrackingProtectionListener);
        setTrackingProtection(mBinding.trackingProtectionRadio.getIdForValue(etpLevel), false);

        mBinding.loginsAndPasswords.setOnClickListener(view -> mDelegate.showView(SettingViewType.LOGINS_AND_PASSWORDS));
    }

    private void togglePermission(SwitchSetting aButton, String aPermission) {
        if (mWidgetManager.isPermissionGranted(aPermission)) {
            showAlert(aButton.getDescription(), getContext().getString(R.string.security_options_permissions_reject_message));
            aButton.setChecked(true);

        } else {
            mWidgetManager.requestPermission("", aPermission, false, new WSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    aButton.setChecked(true);
                }
                @Override
                public void reject() {

                }
            });
        }
    }

    private SwitchSetting.OnCheckedChangeListener mDrmContentListener = (compoundButton, value, doApply) -> {
        setDrmContent(value, doApply);
    };

    private RadioGroupSetting.OnCheckedChangeListener mTrackingProtectionListener = (radioGroup, checkedId, doApply) -> {
        setTrackingProtection(checkedId, true);
    };

    private SwitchSetting.OnCheckedChangeListener mNotificationsListener = (compoundButton, value, doApply) -> {
        setNotifications(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mSpeechDataListener = (compoundButton, value, doApply) -> {
        setSpeechData(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mTelemetryListener = (compoundButton, value, doApply) -> {
        setTelemetry(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mCrashReportsListener = (compoundButton, value, doApply) -> {
        setCrashReports(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mUseSystemRootCAListener = (compoundButton, value, doApply) -> {
        setUseSystemRootCA(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mPopUpsBlockingListener = (compoundButton, value, doApply) -> {
        setPopUpsBlocking(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mRestoreTabsListener = (compoundButton, value, doApply) -> {
        setRestoreTabs(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mAutocompleteListener = (compoundButton, value, doApply) -> {
        setAutocomplete(value, doApply);
    };

    private SwitchSetting.OnCheckedChangeListener mWebXRListener = (compoundButton, value, doApply) -> {
        setWebXR(value, doApply);
    };

    private void resetOptions() {
        if (mBinding.drmContentPlaybackSwitch.isChecked() != SettingsStore.DRM_PLAYBACK_DEFAULT) {
            setDrmContent(SettingsStore.DRM_PLAYBACK_DEFAULT, true);
        }

        if (!mBinding.trackingProtectionRadio.getValueForId(mBinding.trackingProtectionRadio.getCheckedRadioButtonId()).equals(SettingsStore.TRACKING_DEFAULT)) {
            setTrackingProtection(mBinding.trackingProtectionRadio.getIdForValue(SettingsStore.TRACKING_DEFAULT), true);
        }

        if (mBinding.notificationsPermissionSwitch.isChecked() != SettingsStore.NOTIFICATIONS_DEFAULT) {
            setNotifications(SettingsStore.NOTIFICATIONS_DEFAULT, true);
        }

        if (mBinding.speechDataSwitch.isChecked() != SettingsStore.SPEECH_DATA_COLLECTION_DEFAULT) {
            setSpeechData(SettingsStore.SPEECH_DATA_COLLECTION_DEFAULT, true);
        }
        SettingsStore.getInstance(getContext()).setSpeechDataCollectionReviewed(false);

        if (mBinding.telemetryDataSwitch.isChecked() != SettingsStore.TELEMETRY_DEFAULT) {
            setTelemetry(SettingsStore.TELEMETRY_DEFAULT, true);
        }

        if (mBinding.crashReportsDataSwitch.isChecked() != SettingsStore.CRASH_REPORTING_DEFAULT) {
            setCrashReports(SettingsStore.CRASH_REPORTING_DEFAULT, true);
        }

        if (mBinding.useSystemRootCASwitch.isChecked() != SettingsStore.SYSTEM_ROOT_CA_DEFAULT) {
            setUseSystemRootCA(SettingsStore.SYSTEM_ROOT_CA_DEFAULT, true);
        }

        if (mBinding.popUpsBlockingSwitch.isChecked() != SettingsStore.POP_UPS_BLOCKING_DEFAULT) {
            setPopUpsBlocking(SettingsStore.POP_UPS_BLOCKING_DEFAULT, true);
        }

        if (mBinding.restoreTabsSwitch.isChecked() != SettingsStore.RESTORE_TABS_ENABLED) {
            setRestoreTabs(SettingsStore.RESTORE_TABS_ENABLED, true);
        }

        if (mBinding.autocompleteSwitch.isChecked() != SettingsStore.AUTOCOMPLETE_ENABLED) {
            setAutocomplete(SettingsStore.AUTOCOMPLETE_ENABLED, true);
        }

        if (mBinding.webxrSwitch.isChecked() != SettingsStore.WEBXR_ENABLED_DEFAULT) {
            setWebXR(SettingsStore.WEBXR_ENABLED_DEFAULT, true);
        }
    }

    private void setDrmContent(boolean value, boolean doApply) {
        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(null);
        mBinding.drmContentPlaybackSwitch.setValue(value, false);
        mBinding.drmContentPlaybackSwitch.setOnCheckedChangeListener(mDrmContentListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(value);
        }
    }

    private void setTrackingProtection(int checkedId, boolean doApply) {
        mBinding.trackingProtectionRadio.setOnCheckedChangeListener(null);
        mBinding.trackingProtectionRadio.setChecked(checkedId, false);
        mBinding.trackingProtectionRadio.setOnCheckedChangeListener(mTrackingProtectionListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setTrackingProtectionLevel((Integer)mBinding.trackingProtectionRadio.getValueForId(checkedId));
        }
    }

    private void setNotifications(boolean value, boolean doApply) {
        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(null);
        mBinding.notificationsPermissionSwitch.setValue(value, false);
        mBinding.notificationsPermissionSwitch.setOnCheckedChangeListener(mNotificationsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setNotificationsEnabled(value);
        }
    }

    private void setSpeechData(boolean value, boolean doApply) {
        mBinding.speechDataSwitch.setOnCheckedChangeListener(null);
        mBinding.speechDataSwitch.setValue(value, false);
        mBinding.speechDataSwitch.setOnCheckedChangeListener(mSpeechDataListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setSpeechDataCollectionEnabled(value);
        }
    }

    private void setTelemetry(boolean value, boolean doApply) {
        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(null);
        mBinding.telemetryDataSwitch.setValue(value, false);
        mBinding.telemetryDataSwitch.setOnCheckedChangeListener(mTelemetryListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setTelemetryEnabled(value);
        }
    }

    private void setCrashReports(boolean value, boolean doApply) {
        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(null);
        mBinding.crashReportsDataSwitch.setValue(value, false);
        mBinding.crashReportsDataSwitch.setOnCheckedChangeListener(mCrashReportsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setCrashReportingEnabled(value);
        }
    }

    private void setUseSystemRootCA(boolean value, boolean doApply) {
        mBinding.useSystemRootCASwitch.setOnCheckedChangeListener(null);
        mBinding.useSystemRootCASwitch.setValue(value, false);
        mBinding.useSystemRootCASwitch.setOnCheckedChangeListener(mUseSystemRootCAListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setSystemRootCAEnabled(value);
            showRestartDialog();
        }
    }

    private void setPopUpsBlocking(boolean value, boolean doApply) {
        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(null);
        mBinding.popUpsBlockingSwitch.setValue(value, false);
        mBinding.popUpsBlockingSwitch.setOnCheckedChangeListener(mPopUpsBlockingListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setPopUpsBlockingEnabled(value);
        }
    }

    private void setRestoreTabs(boolean value, boolean doApply) {
        mBinding.restoreTabsSwitch.setOnCheckedChangeListener(null);
        mBinding.restoreTabsSwitch.setValue(value, false);
        mBinding.restoreTabsSwitch.setOnCheckedChangeListener(mRestoreTabsListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setRestoreTabsEnabled(value);
        }
    }

    private void setAutocomplete(boolean value, boolean doApply) {
        mBinding.autocompleteSwitch.setOnCheckedChangeListener(null);
        mBinding.autocompleteSwitch.setValue(value, false);
        mBinding.autocompleteSwitch.setOnCheckedChangeListener(mAutocompleteListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setAutocompleteEnabled(value);
        }
    }

    private void setWebXR(boolean value, boolean doApply) {
        mBinding.webxrSwitch.setOnCheckedChangeListener(null);
        mBinding.webxrSwitch.setValue(value, false);
        mBinding.webxrSwitch.setOnCheckedChangeListener(mWebXRListener);

        if (doApply) {
            SettingsStore.getInstance(getContext()).setWebXREnabled(value);
            for (WindowWidget window: mWidgetManager.getWindows().getCurrentWindows()) {
                window.getSession().reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }
    }

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.privacy_options_height));
    }

    @Override
    public void releasePointerCapture() {
        super.releasePointerCapture();
        ((Application)getContext().getApplicationContext()).unregisterActivityLifecycleCallbacks(mLifeCycleListener);
    }

    private Application.ActivityLifecycleCallbacks mLifeCycleListener = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
            // Refresh permission settings status after a permission has been requested
            for (Pair<SwitchSetting, String> button: mPermissionButtons) {
                button.first.setValue(mWidgetManager.isPermissionGranted(button.second), false);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            activity.getApplication().unregisterActivityLifecycleCallbacks(mLifeCycleListener);
        }
    };

    @Override
    protected SettingViewType getType() {
        return SettingViewType.PRIVACY;
    }

}
