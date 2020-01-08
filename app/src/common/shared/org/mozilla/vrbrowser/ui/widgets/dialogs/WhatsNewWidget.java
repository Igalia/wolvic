/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.Log;

import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class WhatsNewWidget extends PromptDialogWidget {

    private Accounts mAccounts;
    private Accounts.LoginOrigin mLoginOrigin;
    private Executor mUIThreadExecutor;

    public WhatsNewWidget(Context aContext) {
        super(aContext);

        initialize(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();
        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();

        setButtons(new int[] {
                R.string.whats_new_button_start_browsing,
                R.string.whats_new_button_sign_in
        });
        setButtonsDelegate(index -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                onDismiss();

            } else if (index == PromptDialogWidget.POSITIVE) {
                signIn();
            }
        });

        setCheckboxVisible(false);

        setIcon(R.drawable.ic_asset_image_accounts);
        setTitle(R.string.whats_new_title_1);
        setBody(R.string.whats_new_body_1);
        setDescription(R.string.whats_new_body_sub_1);
    }

    public void setLoginOrigin(Accounts.LoginOrigin origin) {
        mLoginOrigin = origin;
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        SettingsStore.getInstance(getContext()).setWhatsNewDisplayed(true);
    }

    private void signIn() {
        if (mAccounts.getAccountStatus() == Accounts.AccountStatus.SIGNED_IN) {
            mAccounts.logoutAsync();

        } else {
            UIDialog.closeAllDialogs();

            CompletableFuture<String> result = mAccounts.authUrlAsync();
            if (result != null) {
                result.thenAcceptAsync((url) -> {
                    if (url == null) {
                        mAccounts.logoutAsync();

                    } else {
                        mAccounts.setLoginOrigin(mLoginOrigin);
                        mWidgetManager.openNewTabForeground(url);
                        mWidgetManager.getFocusedWindow().getSession().loadUri(url);
                        mWidgetManager.getFocusedWindow().getSession().setUaMode(GeckoSessionSettings.USER_AGENT_MODE_VR);
                        GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.FXA_LOGIN);
                    }

                }, mUIThreadExecutor).exceptionally(throwable -> {
                    Log.d(LOGTAG, "Error getting the authentication URL: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
                });
            }
        }
    }

}
