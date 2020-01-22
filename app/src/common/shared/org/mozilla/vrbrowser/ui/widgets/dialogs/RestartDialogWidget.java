/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.utils.SystemUtils;

public class RestartDialogWidget extends PromptDialogWidget {

    public RestartDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        setButtons(new int[] {
                R.string.restart_later_dialog_button,
                R.string.restart_now_dialog_button
        });
        setButtonsDelegate(index -> {
            if (index == PromptDialogWidget.NEGATIVE) {
                onDismiss();

            } else if (index == PromptDialogWidget.POSITIVE) {
                mWidgetManager.saveState();
                postDelayed(() -> SystemUtils.scheduleRestart(getContext(), 100), 500);
            }
        });
        setCheckboxVisible(false);
        setDescriptionVisible(false);

        setIcon(R.drawable.ff_logo);
        setTitle(R.string.restart_dialog_restart);
        setBody(getContext().getString(R.string.restart_dialog_text, getContext().getString(R.string.app_name)));
    }

}
