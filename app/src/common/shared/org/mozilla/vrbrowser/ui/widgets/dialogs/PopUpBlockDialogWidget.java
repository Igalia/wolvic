/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;

import org.mozilla.vrbrowser.R;

public class PopUpBlockDialogWidget extends PromptDialogWidget {

    private boolean mIsChecked;

    public PopUpBlockDialogWidget(Context aContext) {
        super(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        setDescriptionVisible(false);

        setButtons(new int[] {
                R.string.popup_block_button_cancel,
                R.string.popup_block_button_show
        });

        setIcon(R.drawable.ff_logo);
        setTitle(String.format(getContext().getString(R.string.popup_block_title), getContext().getString(R.string.app_name)));
        setBody(R.string.popup_block_description);
        setCheckboxText(R.string.popup_block_checkbox);

        mBinding.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> mIsChecked = isChecked);

    }

    public boolean askAgain() {
        return !mIsChecked;
    }

}
