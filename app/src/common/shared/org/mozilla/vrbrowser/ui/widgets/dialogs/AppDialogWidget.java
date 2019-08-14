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
import org.mozilla.vrbrowser.databinding.AppDialogBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class AppDialogWidget extends UIDialog {

    public interface Delegate {
        void onButtonClicked(int index);
        void onMessageLinkClicked(@NonNull String url);
    }

    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    private AppDialogBinding mBinding;
    private Delegate mAppDialogDelegate;

    public AppDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public AppDialogWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public AppDialogWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.app_dialog, this, true);

        mBinding.leftButton.setOnClickListener(v ->  {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(LEFT);
            }

            AppDialogWidget.this.onDismiss();
        });
        mBinding.rightButton.setOnClickListener(v -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(RIGHT);
            }

            AppDialogWidget.this.onDismiss();
        });
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.app_dialog_width);
        aPlacement.height = WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels)/2;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.app_dialog_z_distance);
    }


    @Override
    public void show(@ShowFlags int aShowFlags) {
        measure(View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        super.show(aShowFlags);

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);

        ViewTreeObserver viewTreeObserver = getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mWidgetPlacement.height = (int)(getHeight()/mWidgetPlacement.density);
                    mWidgetManager.updateWidget(AppDialogWidget.this);
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

    public void setDelegate(Delegate delegate) {
        mAppDialogDelegate = delegate;
    }

    public void setTitle(@StringRes int title) {
        mBinding.title.setText(title);
    }

    public void setTitle(String title) {
        mBinding.title.setText(title);
    }

    public void setMessage(@StringRes int message) {
        ViewUtils.setTextViewHTML(mBinding.message, getResources().getString(message), (widget, url) -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onMessageLinkClicked(url);
                onDismiss();
            }
        });
    }

    public void setMessage(String message) {
        mBinding.message.setText(message);
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
