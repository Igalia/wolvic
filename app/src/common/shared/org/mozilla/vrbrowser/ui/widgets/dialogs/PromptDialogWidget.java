/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.PromptDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class PromptDialogWidget extends UIDialog {

    public interface Delegate {
        void onButtonClicked(int index);
        default void onDismiss() {}
    }

    public static final int NEGATIVE = 0;
    public static final int POSITIVE = 1;

    protected PromptDialogBinding mBinding;
    private Delegate mAppDialogDelegate;
    private Runnable mLinkDelegate;

    public PromptDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_dialog, this, true);

        mBinding.leftButton.setOnClickListener(v ->  {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(NEGATIVE);
            }
        });
        mBinding.rightButton.setOnClickListener(v -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(POSITIVE);
            }
        });
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        // We align it at the same position as the settings panel
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_z);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();

        super.show(aShowFlags);
    }

    public void setButtonsDelegate(Delegate delegate) {
        mAppDialogDelegate = delegate;
    }

    public void setLinkDelegate(@NonNull Runnable delegate) {
        mLinkDelegate = delegate;
    }

    public void setIconVisible(boolean visible) {
        mBinding.imageContainer.setVisibility(visible ? VISIBLE: GONE);
    }

    public void setIcon(Drawable icon) {
        mBinding.icon.setImageDrawable(icon);
    }

    public void setIcon(@DrawableRes int icon) {
        mBinding.icon.setImageResource(icon);
    }

    public void setTitle(@StringRes int title) {
        mBinding.title.setText(title);
    }

    public void setTitle(String title) {
        mBinding.title.setText(title);
    }

    public void setBody(@NonNull String body) {
        ViewUtils.setTextViewHTML(mBinding.body, body, (widget, url) -> {
            if (mLinkDelegate != null) {
                mLinkDelegate.run();
            }
        });
    }

    public void setBody(@StringRes int body) {
        ViewUtils.setTextViewHTML(mBinding.body, getResources().getString(body), (widget, url) -> {
            if (mLinkDelegate != null) {
                mLinkDelegate.run();
            }
        });
    }

    public void setBody(@NonNull CharSequence body) {
        mBinding.body.setText(body);
    }

    public void setCheckboxVisible(boolean visible) {
        mBinding.checkboxContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setCheckboxText(@StringRes int text) {
        mBinding.checkbox.setText(text);
    }

    public void setCheckboxText(String text) {
        mBinding.checkbox.setText(text);
    }

    public void setDescriptionVisible(boolean visible) {
        mBinding.descriptionContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setDescription(@StringRes int description) {
        mBinding.description.setText(description);
    }

    public void setDescription(String description) {
        mBinding.title.setText(description);
    }

    public void setButtons(@NonNull @StringRes int[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);

        } else {
            mBinding.leftButton.setVisibility(View.GONE);
        }

        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);

        } else {
            mBinding.rightButton.setVisibility(View.GONE);
        }
    }

    public void setButtons(@NonNull String[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);

        } else {
            mBinding.leftButton.setVisibility(View.GONE);
        }

        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);

        } else {
            mBinding.rightButton.setVisibility(View.GONE);
        }
    }

    public boolean isChecked() {
        return mBinding.checkbox.isChecked();
    }

}
