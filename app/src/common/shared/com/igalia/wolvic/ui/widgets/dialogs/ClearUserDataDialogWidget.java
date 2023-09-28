/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import android.app.ActivityManager;
import android.content.Context;
import android.view.Gravity;

import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.SystemUtils;

public class ClearUserDataDialogWidget extends PromptDialogWidget {

    public ClearUserDataDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        setButtons(new int[] {
                R.string.cancel_button,
                R.string.developer_options_clear_cache
        });
        setButtonsDelegate((index, isChecked) -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                onDismiss();

            } else if (index == PromptDialogWidget.POSITIVE) {
                final ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    activityManager.clearApplicationUserData();
                }
            }
        });
        setCheckboxVisible(false);
        setDescriptionVisible(false);

        setIcon(R.drawable.ff_logo);
        setTitle(getContext().getString(R.string.clear_user_data_dialog_title, getContext().getString(R.string.app_name)));
        setBody(getContext().getString(R.string.clear_user_data_dialog_text, getContext().getString(R.string.app_name)));
        setBodyGravity(Gravity.NO_GRAVITY);
    }
}
