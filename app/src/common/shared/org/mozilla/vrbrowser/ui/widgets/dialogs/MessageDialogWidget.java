/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.MessageDialogBinding;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class MessageDialogWidget extends BaseAppDialogWidget {

    public interface Delegate {
        void onMessageLinkClicked(@NonNull String url);
    }

    private MessageDialogBinding mMessageBinding;
    private Delegate mMessageDialogDelegate;

    public MessageDialogWidget(Context aContext) {
        super(aContext);
    }

    @Override
    protected void initialize(Context aContext) {
        super.initialize(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mMessageBinding = DataBindingUtil.inflate(inflater, R.layout.message_dialog, mBinding.dialogContent, true);
    }

    public void setMessageDelegate(Delegate delegate) {
        mMessageDialogDelegate = delegate;
    }

    public void setMessage(@StringRes int message) {
        ViewUtils.setTextViewHTML(mMessageBinding.message, getResources().getString(message), (widget, url) -> {
            if (mMessageDialogDelegate != null) {
                mMessageDialogDelegate.onMessageLinkClicked(url);
                onDismiss();
            }
        });
    }

    public void setMessage(String message) {
        mMessageBinding.message.setText(message);
    }

}
