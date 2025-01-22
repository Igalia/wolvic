/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.OptionsFxaAccountBinding;
import com.igalia.wolvic.ui.views.settings.SwitchSetting;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.dialogs.SignOutDialogWidget;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.SystemUtils;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Executor;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthFlowError;
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
    private SignOutDialogWidget mSignOutDialog;

    public FxAAccountOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager) {
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
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_fxa_account, this, true);

        mScrollbar = mBinding.scrollbar;

        // Header
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();

        mBinding.signButton.setOnClickListener(this::signOut);
        mBinding.syncButton.setOnClickListener(this::sync);

        mBinding.deviceNameEdit.setHint1(DeviceType.getDeviceName(getContext()));
        mBinding.deviceNameEdit.setDefaultFirstValue(DeviceType.getDeviceName(getContext()));
        mBinding.deviceNameEdit.setFirstText(SettingsStore.getInstance(getContext()).getDeviceName());
        mBinding.deviceNameEdit.setOnClickListener(mDeviceNameListener);
        setDeviceName(SettingsStore.getInstance(getContext()).getDeviceName(), false);

        mBinding.setIsSyncing(mAccounts.isSyncing());
        mBinding.setLastSync(mAccounts.lastSync());

        mBinding.bookmarksSyncSwitch.setOnCheckedChangeListener(mBookmarksSyncListener);
        mBinding.historySyncSwitch.setOnCheckedChangeListener(mHistorySyncListener);
        mBinding.loginsSyncSwitch.setOnCheckedChangeListener(mLoginsSyncListener);

        updateCurrentAccountState();

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(v -> resetOptions());
    }

    @Override
    protected void onDismiss() {
        if (isEditing()) {
            return;
        }
        super.onDismiss();
    }

    @Override
    public boolean isEditing() {
        boolean editing = mBinding.deviceNameEdit.isEditing();
        if (editing) {
            mBinding.deviceNameEdit.cancel();
        }

        return editing;
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == null) {
            return;
        }
        if (mBinding.deviceNameEdit.contains(oldFocus) && mBinding.deviceNameEdit.isEditing()) {
            mBinding.deviceNameEdit.cancel();
        }
    }

    @Override
    public void onShown() {
        super.onShown();

        mAccounts.addAccountListener(mAccountListener);
        mAccounts.addSyncListener(mSyncListener);

        mBinding.bookmarksSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE), false);
        mBinding.historySyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE), false);
        mBinding.loginsSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Passwords.INSTANCE), false);

        updateSyncBindings(mAccounts.isSyncing());
    }

    @Override
    public void onHidden() {
        if (isEditing()) {
            return;
        }
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

    private SwitchSetting.OnCheckedChangeListener mLoginsSyncListener = (compoundButton, value, apply) -> {
        mAccounts.setSyncStatus(SyncEngine.Passwords.INSTANCE, value);
        mAccounts.syncNowAsync(SyncReason.EngineChange.INSTANCE, false);
    };

    private void resetOptions() {
        mBinding.bookmarksSyncSwitch.setValue(SettingsStore.BOOKMARKS_SYNC_DEFAULT, true);
        mBinding.historySyncSwitch.setValue(SettingsStore.HISTORY_SYNC_DEFAULT, true);
        mBinding.loginsSyncSwitch.setValue(SettingsStore.LOGIN_SYNC_DEFAULT, true);
        setDeviceName(DeviceType.getDeviceName(getContext()), true);
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
            mBinding.loginsSyncSwitch.setValue(mAccounts.isEngineEnabled(SyncEngine.Passwords.INSTANCE), false);

            // This shouldn't be necessary but for some reason the buttons stays hovered after the sync.
            // I guess Android restoring it to the latest state (hovered) before being disabled
            // Probably an Android bindings bug.
            mBinding.bookmarksSyncSwitch.setHovered(false);
            mBinding.historySyncSwitch.setHovered(false);
            mBinding.loginsSyncSwitch.setHovered(false);
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

    private void setDeviceName(String newDeviceName, boolean doApply) {
        mBinding.deviceNameEdit.setOnClickListener(null);
        if (newDeviceName.isEmpty()) {
            newDeviceName = DeviceType.getDeviceName(getContext());
        }
        if (doApply) {
            String prevDeviceName = SettingsStore.getInstance(getContext()).getDeviceName();
            if (!Objects.equals(prevDeviceName, newDeviceName)) {
                SettingsStore.getInstance(getContext()).setDeviceName(newDeviceName);
                Continuation<? super Unit> completion = new Continuation<Unit>() {
                    @NonNull
                    @Override
                    public CoroutineContext getContext() {
                        return EmptyCoroutineContext.INSTANCE;
                    }

                    @Override
                    public void resumeWith(@NonNull Object o) {}
                };
                mAccounts.setDeviceName(newDeviceName, completion);
            }
        }
        mBinding.deviceNameEdit.setFirstText(newDeviceName);
        mBinding.deviceNameEdit.setOnClickListener(mDeviceNameListener);
    }

    void updateCurrentAccountState() {
        switch(mAccounts.getAccountStatus()) {
            case NEEDS_RECONNECT:
                Log.e(LOGTAG, "TabReceived : updateCurrentAccountState NEEDS_RECONNECT");
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_reconnect);
                break;

            case SIGNED_IN:
                Log.e(LOGTAG, "TabReceived : updateCurrentAccountState SIGNED_IN");
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_out);
                Profile profile = mAccounts.accountProfile();
                if (profile != null) {
                    Log.e(LOGTAG, "TabReceived : updateCurrentAccountState updateProfile(profile)");
                    updateProfile(profile);

                } else {
                    Log.e(LOGTAG, "TabReceived : updateCurrentAccountState profile is NULL, refreshing");
                    try {

                        // authenticatedAccount()


                        Objects.requireNonNull(mAccounts.updateProfileAsync()).
                                thenAcceptAsync((u) -> {
                                            Log.e(LOGTAG, "TabReceived : updateCurrentAccountState refreshed profile received: " + u);
                                            Log.e(LOGTAG, "TabReceived : updateCurrentAccountState refreshed profile   stored: " + mAccounts.accountProfile());
                                            updateProfile(mAccounts.accountProfile());
                                        }
                                        , mUIThreadExecutor).
                                exceptionally(throwable -> {
                                    Log.e(LOGTAG, "TabReceived : updateCurrentAccountState Error getting the account profile");
                                    Log.d(LOGTAG, "Error getting the account profile: " + throwable.getLocalizedMessage());
                                    return null;
                                });

                    } catch (NullPointerException e) {
                        Log.d(LOGTAG, "Error getting the account profile: " + e.getLocalizedMessage());
                        Log.e(LOGTAG, "TabReceived : updateCurrentAccountState Error getting the account profile");
                    }
                }
                break;

            case SIGNED_OUT:
                Log.e(LOGTAG, "TabReceived : updateCurrentAccountState SIGNED_OUT");
                mBinding.signButton.setButtonText(R.string.settings_fxa_account_sign_in);
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + mAccounts.getAccountStatus());
        }
    }

    private void updateProfile(Profile profile) {


        if (profile != null) {
            Log.e(LOGTAG, "TabReceived : updateProfile is " + profile.getEmail() + " " + profile.getDisplayName());
            mBinding.accountEmail.setText(profile.getEmail());
        } else {
            Log.e(LOGTAG, "TabReceived : updateProfile was NULL");
        }
    }

    private void sync(View view) {

        Log.e(LOGTAG, "TabReceived : sync button");

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

    private OnClickListener mDeviceNameListener = (view) -> {
            String newDeviceName = mBinding.deviceNameEdit.getFirstText();
            setDeviceName(newDeviceName, true);
    };

    private AccountObserver mAccountListener = new AccountObserver() {

        @Override
        public void onReady(@Nullable OAuthAccount oAuthAccount) {

        }

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

        @Override
        public void onFlowError(@NotNull AuthFlowError authFlowError) {
            post(FxAAccountOptionsView.this::onDismiss);
        }
    };

    @Override
    protected SettingViewType getType() {
        return SettingViewType.FXA;
    }

}