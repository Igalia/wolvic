/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewTreeObserver;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.CheckboxDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class CheckboxDialogWidget extends UIDialog {

    public interface Delegate {
        void onButtonClicked(int index);
        default void onDismiss() {}
    }

    public static final int NEGATIVE = 0;
    public static final int POSITIVE = 1;

    protected CheckboxDialogBinding mBinding;
    private Delegate mAppDialogDelegate;

    public CheckboxDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.checkbox_dialog, this, true);

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
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.checkbox_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.checkbox_dialog_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.base_app_dialog_z_distance);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        super.show(aShowFlags);

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);

        ViewTreeObserver viewTreeObserver = getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mWidgetPlacement.height = (int)(getHeight()/mWidgetPlacement.density);
                    mWidgetManager.updateWidget(CheckboxDialogWidget.this);
                }
            });
        }
    }

    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mWidgetManager.popWorldBrightness(this);
    }

    public void setButtonsDelegate(Delegate delegate) {
        mAppDialogDelegate = delegate;
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

    public void setBody(String body) {
        mBinding.body.setText(body);
    }

    public void setBody(@StringRes int title) {
        mBinding.body.setText(title);
    }

    public void setCheckboxText(@StringRes int text) {
        mBinding.checkbox.setText(text);
    }

    public void setCheckboxText(String text) {
        mBinding.checkbox.setText(text);
    }

    public void setButtons(@NonNull @StringRes int[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);
        }
        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);
        }
    }

    public void setButtons(@NonNull String[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);
        }
        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);
        }
    }

    public boolean isChecked() {
        return mBinding.checkbox.isChecked();
    }

}
