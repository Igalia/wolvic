/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.SendTabsDisplayBinding;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.ConstellationState;
import mozilla.components.concept.sync.Device;
import mozilla.components.concept.sync.DeviceCapability;
import mozilla.components.concept.sync.DeviceConstellationObserver;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;

public class SendTabDialogWidget extends SettingDialogWidget implements
        DeviceConstellationObserver,
        AccountObserver {

    private SendTabsDisplayBinding mSendTabsDialogBinding;
    private Accounts mAccounts;
    private List<Device> mDevicesList = new ArrayList<>();
    private WhatsNewWidget mWhatsNew;
    private String mSessionId;

    public SendTabDialogWidget(@NonNull Context aContext) {
        super(aContext);
    }

    @Override
    protected void initialize(@NonNull Context aContext) {
        super.initialize(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mSendTabsDialogBinding = DataBindingUtil.inflate(inflater, R.layout.send_tabs_display, mBinding.content, true);
        mSendTabsDialogBinding.setIsSyncing(false);
        mSendTabsDialogBinding.setIsEmpty(false);

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();

        mBinding.headerLayout.setTitle(getResources().getString(R.string.send_tab_dialog_title));
        mBinding.headerLayout.setDescription(R.string.send_tab_dialog_description);
        mBinding.footerLayout.setFooterButtonText(R.string.send_tab_dialog_button);
        mBinding.footerLayout.setFooterButtonClickListener(this::sendTabButtonClick);
    }

    @Override
    public void show(int aShowFlags) {
        mAccounts.addAccountListener(this);
        mAccounts.addDeviceConstellationListener(this);

        if (mAccounts.isSignedIn()) {
            mBinding.footerLayout.setFooterButtonVisibility(View.GONE);
            mAccounts.refreshDevicesAsync();
            mSendTabsDialogBinding.setIsSyncing(true);

            super.show(aShowFlags);

        } else {
            showWhatsNewDialog();
        }
    }

    @Override
    public void hide(int aHideFlags) {
        super.hide(aHideFlags);

        mAccounts.removeAccountListener(this);
        mAccounts.removeDeviceConstellationListener(this);
    }

    public void setSessionId(@Nullable String sessionId) {
        mSessionId = sessionId;
    }

    private void sendTabButtonClick(View v) {
        Device device = mDevicesList.get(mSendTabsDialogBinding.devicesList.getCheckedRadioButtonId());
        Session session = SessionStore.get().getActiveSession();
        if (mSessionId != null) {
            session = SessionStore.get().getSession(mSessionId);
        }

        // At some point we will support sending to multiple devices or to all of them
        mAccounts.sendTabs(Collections.singletonList(device), session.getCurrentUri(), session.getCurrentTitle());

        // Show the tab sent notifications in the tray
        mWidgetManager.getTray().showTabSentNotification();

        onDismiss();
    }

    private void showWhatsNewDialog() {
        mWhatsNew = new WhatsNewWidget(getContext());
        mWhatsNew.setLoginOrigin(Accounts.LoginOrigin.SEND_TABS);
        mWhatsNew.show(UIWidget.REQUEST_FOCUS);
    }

    // DeviceConstellationObserver

    @Override
    public void onDevicesUpdate(@NonNull ConstellationState constellationState) {
        post(() -> {
            mSendTabsDialogBinding.setIsSyncing(false);

            List<Device> list = constellationState.getOtherDevices().stream()
                    .filter(device -> device.getCapabilities().contains(DeviceCapability.SEND_TAB)).collect(Collectors.toList());
            if (!mDevicesList.equals(list)) {
                mDevicesList = list;

                List<String> devicesNamesList = new ArrayList<>();
                mDevicesList.forEach((device) -> devicesNamesList.add(device.getDisplayName()));
                mSendTabsDialogBinding.devicesList.setOptions(devicesNamesList.toArray(new String[]{}));
            }

            if (!mDevicesList.isEmpty()) {
                mBinding.footerLayout.setFooterButtonVisibility(View.VISIBLE);
                mSendTabsDialogBinding.devicesList.setChecked(0, false);
            }

            mSendTabsDialogBinding.setIsEmpty(mDevicesList.isEmpty());
            mBinding.footerLayout.setFooterButtonVisibility(mDevicesList.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    // AccountObserver

    @Override
    public void onLoggedOut() {
        if (isVisible()) {
            hide(KEEP_WIDGET);
        }
        showWhatsNewDialog();
    }

    @Override
    public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {
        if (mAccounts.getLoginOrigin() == Accounts.LoginOrigin.SEND_TABS) {
            show(REQUEST_FOCUS);
        }
    }

    @Override
    public void onProfileUpdated(@NonNull Profile profile) {

    }

    @Override
    public void onAuthenticationProblems() {
        if (isVisible()) {
            hide(KEEP_WIDGET);
        }
        showWhatsNewDialog();
    }
}
