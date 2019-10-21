/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.BaseAppDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class BaseAppDialogWidget extends UIDialog {

    public interface Delegate {
        void onButtonClicked(int index);
        default void onDismiss() {}
    }

    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    protected BaseAppDialogBinding mBinding;
    private Delegate mAppDialogDelegate;

    public BaseAppDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public BaseAppDialogWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public BaseAppDialogWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.base_app_dialog, this, true);

        mBinding.leftButton.setOnClickListener(v ->  {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(LEFT);
            }

            BaseAppDialogWidget.this.onDismiss();
        });
        mBinding.rightButton.setOnClickListener(v -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(RIGHT);
            }

            BaseAppDialogWidget.this.onDismiss();
        });
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.base_app_dialog_width);
        aPlacement.height = WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels)/2;
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
                    mWidgetManager.updateWidget(BaseAppDialogWidget.this);
                }
            });
        }
    }

    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);
        mWidgetManager.popWorldBrightness(this);
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus == this && isVisible() && findViewById(newFocus.getId()) == null) {
            onDismiss();
        }
    }

    public void setButtonsDelegate(Delegate delegate) {
        mAppDialogDelegate = delegate;
    }

    public void setTitle(@StringRes int title) {
        mBinding.title.setText(title);
    }

    public void setTitle(String title) {
        mBinding.title.setText(title);
    }

    public void setButtons(@StringRes int[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[LEFT]);
        }
        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[RIGHT]);
        }
    }

    public void setButtons(@NonNull String[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[LEFT]);
        }
        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[RIGHT]);
        }
    }

}
