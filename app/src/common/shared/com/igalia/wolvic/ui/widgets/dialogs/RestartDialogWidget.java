/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;

import com.igalia.wolvic.R;
import com.igalia.wolvic.utils.SystemUtils;

public class RestartDialogWidget extends PromptDialogWidget {

    private CancelCallback mCancelCallback;

    private boolean noButtonsClicked;

    public RestartDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public void setCancelCallback(CancelCallback cancelCallback) {
        mCancelCallback = cancelCallback;
    }

    @Override
    public void updateUI() {
        noButtonsClicked = true;

        super.updateUI();

        setButtons(new int[] {
                R.string.restart_later_dialog_button,
                R.string.restart_now_dialog_button,
                R.string.restart_back_dialog_button,
        });
        setButtonsDelegate((index, isChecked) -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                noButtonsClicked = false;
                onDismiss();
            } else if (index == PromptDialogWidget.POSITIVE) {
                noButtonsClicked = false;
                mWidgetManager.saveState();
                postDelayed(() -> SystemUtils.restart(getContext()), 500);
            } else if (index == PromptDialogWidget.BACK) {
                noButtonsClicked = false;
                if (mCancelCallback != null) {
                    mCancelCallback.cancel();
                }
                onDismiss();
            }
            noButtonsClicked = true;
        });
        setCheckboxVisible(false);
        setDescriptionVisible(false);

        setIcon(R.drawable.ff_logo);
        setTitle(R.string.restart_dialog_restart);
        setBody(getContext().getString(R.string.restart_dialog_text, getContext().getString(R.string.app_name)));
    }

    @Override
    public void onDismiss() {
        if (noButtonsClicked && mCancelCallback != null) {
            mCancelCallback.cancel();
        }

        hide(REMOVE_WIDGET);
    }

    public interface CancelCallback {
        void cancel();
    }
}
