/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.settings.SettingsWidget;

import java.util.Objects;
import java.util.concurrent.Executor;

public class SignOutDialogWidget extends PromptDialogWidget {

    private Accounts mAccounts;
    private Executor mUIThreadExecutor;
    private Places mPlaces;

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

        setButtons(new int[] {
                R.string.fxa_signout_confirmation_signout,
                R.string.fxa_signout_confirmation_cancel
        });
        setButtonsDelegate(index -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                try {
                    Objects.requireNonNull(mAccounts.logoutAsync()).thenAcceptAsync(unit -> {
                        if (isChecked()) {
                            // Clear History and Bookmarks
                            mPlaces.clear();
                        }
                        hide(UIWidget.REMOVE_WIDGET);
                        mWidgetManager.getTray().toggleSettingsDialog();

                    }, mUIThreadExecutor);

                } catch(NullPointerException e) {
                    e.printStackTrace();
                }

            } else if (index == PromptDialogWidget.POSITIVE) {
                hide(UIWidget.REMOVE_WIDGET);
                mWidgetManager.getTray().toggleSettingsDialog(SettingsWidget.SettingDialog.FXA);
            }
        });
        setDelegate(() -> mWidgetManager.getTray().toggleSettingsDialog(SettingsWidget.SettingDialog.FXA));

        setDescriptionVisible(false);

        setTitle(R.string.fxa_signout_confirmation_title);
        setBody(R.string.fxa_signout_confirmation_body);
        setCheckboxText(R.string.fxa_signout_confirmation_checkbox);
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
