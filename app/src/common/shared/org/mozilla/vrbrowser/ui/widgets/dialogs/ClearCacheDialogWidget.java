/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.IntDef;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.ClearCacheDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class ClearCacheDialogWidget extends BaseAppDialogWidget {

    @IntDef(value = { TODAY, YESTERDAY, LAST_WEEK, EVERYTHING})
    public @interface ClearCacheRange {}
    public static final int TODAY = 0;
    public static final int YESTERDAY = 1;
    public static final int LAST_WEEK = 2;
    public static final int EVERYTHING = 3;

    private ClearCacheDialogBinding mClearCacheBinding;

    public ClearCacheDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ClearCacheDialogWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ClearCacheDialogWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mClearCacheBinding = DataBindingUtil.inflate(inflater, R.layout.clear_cache_dialog, mBinding.dialogContent, true);
        mClearCacheBinding.clearCacheRadio.setChecked(0, false);

        mWidgetPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.cache_app_dialog_width);
    }

    public @ClearCacheRange int getSelectedRange() {
        return mClearCacheBinding.clearCacheRadio.getCheckedRadioButtonId();
    }

}
