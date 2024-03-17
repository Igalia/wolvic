/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import static com.igalia.wolvic.ui.widgets.settings.SettingsView.SettingViewType.FXA;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.Places;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.Objects;
import java.util.concurrent.Executor;

public class SignOutDialogWidget extends PromptDialogWidget {

    private Accounts mAccounts;
    private Executor mUIThreadExecutor;
    private Places mPlaces;

    private void showConfirmationDialog() {
        mWidgetManager.getFocusedWindow().showConfirmPrompt(
                getContext().getString(R.string.restart_dialog_restart),
                getContext().getString(R.string.restart_dialog_text, getContext().getString(R.string.app_name)),
                new String[]{
                        getContext().getString(R.string.restart_now_dialog_button),
                        getContext().getString(R.string.exit_confirm_dialog_button_cancel),
                },
                (index, isChecked) -> {
                    if (index == PromptDialogWidget.NEGATIVE) {
                        logout(() -> {
                            // Clear History and Bookmarks
                            mPlaces.clear();
                            mWidgetManager.saveState();
                            postDelayed(() -> SystemUtils.restart(getContext()), 500);
                        });
                    }
                }
        );
    }

    private void logout(Runnable callback) {
        Objects.requireNonNull(mAccounts.logoutAsync()).thenAcceptAsync(unit -> {
            callback.run();
        }, mUIThreadExecutor);
    }

    public SignOutDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        mUIThreadExecutor = ((VRBrowserApplication)getContext().getApplicationContext()).getExecutors().mainThread();
        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();
        mPlaces = ((VRBrowserApplication)getContext().getApplicationContext()).getPlaces();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        setButtons(new int[] {
                R.string.fxa_signout_confirmation_signout,
                R.string.fxa_signout_confirmation_cancel
        });
        setButtonsDelegate((index, isChecked) -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                try {
                    if (isChecked()) {
                        showConfirmationDialog();
                    } else {
                        logout(() -> {
                            hide(UIWidget.REMOVE_WIDGET);
                            mWidgetManager.getTray().toggleSettingsDialog();
                        });
                    }
                } catch(NullPointerException e) {
                    e.printStackTrace();
                }

            } else if (index == PromptDialogWidget.POSITIVE) {
                hide(UIWidget.REMOVE_WIDGET);
                mWidgetManager.getTray().toggleSettingsDialog(FXA);
            }
        });
        setDelegate(() -> mWidgetManager.getTray().toggleSettingsDialog(FXA));

        setDescriptionVisible(false);

        setTitle(R.string.fxa_signout_confirmation_title);
        setBody(R.string.fxa_signout_confirmation_body);
        setCheckboxText(R.string.fxa_signout_confirmation_checkbox_v1);
    }

    @Override
    public void show(int aShowFlags) {
        BitmapDrawable profilePicture = mAccounts.getProfilePicture();
        if (profilePicture != null) {
            setIcon(profilePicture);

        } else {
            setIcon(R.drawable.ic_icon_settings_account);
        }

        super.show(aShowFlags);
    }
}
