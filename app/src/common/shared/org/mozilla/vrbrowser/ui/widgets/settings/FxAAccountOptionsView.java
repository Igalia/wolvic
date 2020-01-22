/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.databinding.OptionsFxaAccountBinding;
import org.mozilla.vrbrowser.ui.views.settings.SwitchSetting;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.dialogs.SignOutDialogWidget;
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
    private Places mPlaces;
    private Executor mUIThreadExecutor;
    private SignOutDialogWidget mSignOutDialog;

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
        mPlaces = ((VRBrowserApplication)getContext().getApplicationContext()).getPlaces();

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mBinding.signButton.setOnClickListener(this::signOut);
        mBinding.syncButton.setOnClickListener(this::sync);

        mBinding.setIsSyncing(mAccounts.isSyncing());
        mBinding.setLastSync(mAccounts.lastSync());

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

        updateSyncBindings(mAccounts.isSyncing());
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
        mBinding.bookmarksSyncSwitch.setValue(SettingsStore.BOOKMARKS_SYNC_DEFAULT, true);
        mBinding.historySyncSwitch.setValue(SettingsStore.HISTORY_SYNC_DEFAULT, true);
    }

    private SyncStatusObserver mSyncListener = new SyncStatusObserver() {
        @Override
        public void onStarted() {
            updateSyncBindings(true);
        }

        @Override
        public void onIdle() {
            updateSyncBindings(false);

            mBinding.bookmarksSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE), false);
            mBinding.historySyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE), false);

            // This shouldn't be necessary but for some reason the buttons stays hovered after the sync.
            // I guess Android restoring it to the latest state (hovered) before being disabled
            // Probably an Android bindings bug.
            mBinding.bookmarksSyncSwitch.setHovered(false);
            mBinding.historySyncSwitch.setHovered(false);
            mBinding.syncButton.setHovered(false);
        }

        @Override
        public void onError(@Nullable Exception e) {
            updateSyncBindings(false);
        }
    };

    private void updateSyncBindings(boolean isSyncing) {
        mBinding.setIsSyncing(isSyncing);
        mBinding.setLastSync(mAccounts.lastSync());
        mBinding.executePendingBindings();
    }

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
                    try {
                        Objects.requireNonNull(mAccounts.updateProfileAsync()).
                                thenAcceptAsync((u) -> updateProfile(mAccounts.accountProfile()), mUIThreadExecutor).
                                exceptionally(throwable -> {
                                    Log.d(LOGTAG, "Error getting the account profile: " + throwable.getLocalizedMessage());
                                    return null;
                                });

                    } catch (NullPointerException e) {
                        Log.d(LOGTAG, "Error getting the account profile: " + e.getLocalizedMessage());
                    }
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

    private void sync(View view) {
        mAccounts.syncNowAsync(SyncReason.User.INSTANCE, false);
        mAccounts.updateProfileAsync();
    }

    private void signOut(View view) {
        if (mSignOutDialog == null) {
            mSignOutDialog = new SignOutDialogWidget(getContext());
        }

        exitWholeSettings();
        mSignOutDialog.show(UIWidget.REQUEST_FOCUS);
    }

    private AccountObserver mAccountListener = new AccountObserver() {

        @Override
        public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {
            mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_out);
        }

        @Override
        public void onProfileUpdated(@NonNull Profile profile) {
            mBinding.accountEmail.setText(profile.getEmail());
            mBinding.setLastSync(mAccounts.lastSync());
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