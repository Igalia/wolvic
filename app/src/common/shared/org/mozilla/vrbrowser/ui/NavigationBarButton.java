/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.support.v7.widget.AppCompatImageButton;

import org.mozilla.vrbrowser.R;

public class NavigationBarButton extends AppCompatImageButton implements CustomUIButton {
    private ColorStateList mTintColorList;

    public NavigationBarButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    public NavigationBarButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NavigationBarButton, defStyleAttr, 0);
        mTintColorList = a.getColorStateList(R.styleable.NavigationBarButton_tintColorList);
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setColorFilter(color);
        }
        a.recycle();

        setSoundEffectsEnabled(false);
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
    public void setTintColorList(int aColorListId) {
        mTintColorList = getContext().getResources().getColorStateList(
                aColorListId,
                getContext().getTheme());
        if (mTintColorList != null) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            setColorFilter(color);
        }
    }
}
