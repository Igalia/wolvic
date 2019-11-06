/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.databinding.OptionsFxaAccountBinding;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Objects;
import java.util.concurrent.Executor;

import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;
import mozilla.components.service.fxa.SyncEngine;
import mozilla.components.service.fxa.sync.SyncReason;
import mozilla.components.service.fxa.sync.SyncStatusObserver;

class FxAAccountOptionsView extends SettingsView {

    private static final String LOGTAG = SystemUtils.createLogtag(FxAAccountOptionsView.class);

    private OptionsFxaAccountBinding mBinding;
    private Accounts mAccounts;
    private Executor mUIThreadExecutor;

    public FxAAccountOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
        super(aContext, aWidgetManager);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_fxa_account, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mBinding.signButton.setOnClickListener(view -> mAccounts.logoutAsync());
        mBinding.bookmarksSyncSwitch.setOnCheckedChangeListener(mBookmarksSyncListener);

        mBinding.historySyncSwitch.setOnCheckedChangeListener(mHistorySyncListener);

        updateCurrentAccountState();

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> resetOptions());
    }

    @Override
    public void onShown() {
        super.onShown();

        mAccounts.addAccountListener(mAccountListener);
        mAccounts.addSyncListener(mSyncListener);

        mBinding.bookmarksSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE), false);
        mBinding.historySyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE), false);

        mBinding.setIsSyncing(mAccounts.isSyncing());
    }

    @Override
    public void onHidden() {
        super.onHidden();

        mAccounts.removeAccountListener(mAccountListener);
        mAccounts.removeSyncListener(mSyncListener);
    }

    private SwitchSetting.OnCheckedChangeListener mBookmarksSyncListener = (compoundButton, value, apply) -> {
        mAccounts.setSyncStatus(SyncEngine.Bookmarks.INSTANCE, value);
        mAccounts.syncNowAsync(SyncReason.EngineChange.INSTANCE, false);
    };

    private SwitchSetting.OnCheckedChangeListener mHistorySyncListener = (compoundButton, value, apply) -> {
        mAccounts.setSyncStatus(SyncEngine.History.INSTANCE, value);
        mAccounts.syncNowAsync(SyncReason.EngineChange.INSTANCE, false);
    };

    private void resetOptions() {
        mAccounts.setSyncStatus(SyncEngine.Bookmarks.INSTANCE, SettingsStore.BOOKMARKS_SYNC_DEFAULT);
        mAccounts.setSyncStatus(SyncEngine.History.INSTANCE, SettingsStore.HISTORY_SYNC_DEFAULT);
        mAccounts.syncNowAsync(SyncReason.EngineChange.INSTANCE, false);
    }

    private SyncStatusObserver mSyncListener = new SyncStatusObserver() {
        @Override
        public void onStarted() {
            mBinding.setIsSyncing(true);
        }

        @Override
        public void onIdle() {
            mBinding.setIsSyncing(false);

            mBinding.bookmarksSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE), false);
            mBinding.historySyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE), false);

            // This shouldn't be necessary but for some reason the buttons stays hovered after the sync.
            // I guess Android is after enabling it it's state is restored to the latest one (hovered)
            // Probably an Android bindings bug.
            mBinding.bookmarksSyncSwitch.setHovered(false);
            mBinding.historySyncSwitch.setHovered(false);
        }

        @Override
        public void onError(@Nullable Exception e) {
            mBinding.setIsSyncing(false);
        }
    };

    void updateCurrentAccountState() {
        switch(mAccounts.getAccountStatus()) {
            case NEEDS_RECONNECT:
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_reconnect);
                break;

            case SIGNED_IN:
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_out);
                Profile profile = mAccounts.accountProfile();
                if (profile != null) {
                    updateProfile(profile);

                } else {
                    Objects.requireNonNull(mAccounts.updateProfileAsync()).
                            thenAcceptAsync((u) -> updateProfile(mAccounts.accountProfile()), mUIThreadExecutor).
                            exceptionally(throwable -> {
                                Log.d(LOGTAG, "Error getting the account profile: " + throwable.getLocalizedMessage());
                                throwable.printStackTrace();
                                return null;
                            });
                }
                break;

            case SIGNED_OUT:
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_in);
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + mAccounts.getAccountStatus());
        }
    }

    private void updateProfile(Profile profile) {
        if (profile != null) {
            mBinding.accountEmail.setText(profile.getEmail());
        }
    }

    private AccountObserver mAccountListener = new AccountObserver() {

        @Override
        public void onAuthenticated(@NotNull OAuthAccount oAuthAccount, @NotNull AuthType authType) {
            mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_out);
        }

        @Override
        public void onProfileUpdated(@NotNull Profile profile) {
            post(() -> mBinding.accountEmail.setText(profile.getEmail()));
        }

        @Override
        public void onLoggedOut() {
            post(FxAAccountOptionsView.this::onDismiss);
        }

        @Override
        public void onAuthenticationProblems() {
            post(FxAAccountOptionsView.this::onDismiss);
        }
    };

}