/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.support.v7.widget.AppCompatImageButton;

import org.mozilla.vrbrowser.R;

public class NavigationBarButton extends AppCompatImageButton {
    private int mDisabledTintColor;

    public NavigationBarButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    public NavigationBarButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NavigationBarButton, defStyleAttr, 0);
        mDisabledTintColor = a.getColor(0, R.styleable.NavigationBarButton_disabledTintColor);
        a.recycle();

        setSoundEffectsEnabled(false);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setColorFilter(Color.WHITE);
        } else {
            setColorFilter(mDisabledTintColor);
        }
    }

}
