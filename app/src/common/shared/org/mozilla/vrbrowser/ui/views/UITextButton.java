/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;

import org.mozilla.vrbrowser.R;

public class UITextButton extends AppCompatButton implements CustomUIButton {
    private ColorStateList mTintColorList;

    public UITextButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    public UITextButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.UITextButton, defStyleAttr, 0);
        mTintColorList = a.getColorStateList(R.styleable.UITextButton_textTintColorList);
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setTextColor(color);
        }
        a.recycle();

        setSoundEffectsEnabled(false);
    }

    private void setTintColorList(int aColorListId) {
        mTintColorList = getContext().getResources().getColorStateList(
                aColorListId,
                getContext().getTheme());
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setTextColor(color);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mTintColorList != null && mTintColorList.isStateful()) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setTextColor(color);
        }
    }

    @Override
    public void setPrivateMode(boolean isEnabled) {
        if (isEnabled) {
            setBackground(getContext().getDrawable(R.drawable.main_button_private));
            setTintColorList(R.drawable.main_button_icon_color_private);

        } else {
            setBackground(getContext().getDrawable(R.drawable.main_button));
            setTintColorList(R.drawable.main_button_icon_color);
        }
    }
}
