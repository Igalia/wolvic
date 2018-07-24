/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.support.v7.widget.AppCompatImageButton;

import org.mozilla.vrbrowser.R;

public class UIButton extends AppCompatImageButton implements CustomUIButton {
    private ColorStateList mTintColorList;
    private Drawable mPrivateModeBackground;
    private Drawable mBackground;
    private ColorStateList mPrivateModeTintColorList;

    public UIButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    public UIButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mTintColorList = attributes.getColorStateList(R.styleable.UIButton_tintColorList);
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setColorFilter(color);
        }
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mPrivateModeBackground = attributes.getDrawable(R.styleable.UIButton_privateModeBackground);
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mPrivateModeTintColorList = attributes.getColorStateList(R.styleable.UIButton_privateModeTintColorList);
        attributes.recycle();

        mBackground = getBackground();

        setSoundEffectsEnabled(false);
    }

    public void setTintColorList(int aColorListId) {
        mTintColorList = getContext().getResources().getColorStateList(
                aColorListId,
                getContext().getTheme());
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setColorFilter(color);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mTintColorList != null && mTintColorList.isStateful()) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setColorFilter(color);
        }
    }

    @Override
    public void setPrivateMode(boolean isEnabled) {
        if (isEnabled) {
            if (mPrivateModeBackground != null)
                setBackground(mPrivateModeBackground);

            if (mPrivateModeTintColorList != null) {
                int color = mPrivateModeTintColorList.getColorForState(getDrawableState(), 0);
                setColorFilter(color);
            }

        } else {
            if (mBackground != null)
                setBackground(mBackground);

            if(mTintColorList != null) {
                int color = mTintColorList.getColorForState(getDrawableState(), 0);
                setColorFilter(color);
            }
        }
    }
}
