/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.PopupBlockDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class PopUpBlockDialogWidget extends BaseAppDialogWidget {

    private PopupBlockDialogBinding mPopUpBinding;
    private boolean mIsChecked;

    public PopUpBlockDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public PopUpBlockDialogWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public PopUpBlockDialogWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mPopUpBinding = DataBindingUtil.inflate(inflater, R.layout.popup_block_dialog, mBinding.dialogContent, true);
        mPopUpBinding.contentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> mIsChecked = isChecked);

        mWidgetPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.pop_up_app_dialog_width);

        setButtons(new int[] {
                R.string.popup_block_button_do_not_show,
                R.string.popup_block_button_show
        });

    }

    public boolean askAgain() {
        return !mIsChecked;
    }

}
