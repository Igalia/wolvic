package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.databinding.DeprecatedVersionDialogBinding;

public class DeprecatedVersionDialogWidget extends PromptDialogWidget {

    private Delegate mDialogDelegate;
    private DeprecatedVersionDialogBinding mBinding;

    public DeprecatedVersionDialogWidget(@NonNull Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public static final int DISMISS = 0, SHOW_INFO = 1, OPEN_STORE = 2;

    @IntDef({DISMISS, SHOW_INFO, OPEN_STORE})
    @interface UserResponse {
    }

    public interface Delegate {
        void onUserResponse(@UserResponse int response);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.deprecated_version_dialog, this, true);

        mBinding.dismissButton.setOnClickListener(v -> {
            if (mDialogDelegate != null) {
                mDialogDelegate.onUserResponse(DISMISS);
            }
            onDismiss();
        });
        mBinding.infoButton.setOnClickListener(v -> {
            if (mDialogDelegate != null) {
                mDialogDelegate.onUserResponse(SHOW_INFO);
            }
            onDismiss();
        });
        mBinding.storeButton.setOnClickListener(v -> {
            if (mDialogDelegate != null) {
                mDialogDelegate.onUserResponse(OPEN_STORE);
            }
            onDismiss();
        });
    }

    public void setDelegate(Delegate delegate) {
        mDialogDelegate = delegate;
    }

    @Override
    public void onWorldClick() {
        // ignored: this is a modal dialog
    }
}
