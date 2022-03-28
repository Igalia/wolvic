/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.AppCompatButton;

import com.igalia.wolvic.R;

public class UITextButton extends AppCompatButton implements CustomUIButton {

    private enum State {
        NORMAL,
        PRIVATE,
        ACTIVE
    }

    private ColorStateList mTintColorList;
    private Drawable mPrivateModeBackground;
    private Drawable mActiveModeBackground;
    private Drawable mBackground;
    private @IdRes int mTintColorListRes;
    private @IdRes int mPrivateModeTintColorListRes;
    private @IdRes int mActiveModeTintColorListRes;
    private State mState;

    public UITextButton(Context context) {
        this(context, null);
    }

    public UITextButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    public UITextButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mTintColorListRes = attributes.getResourceId(R.styleable.UIButton_tintColorList, 0);
        if (mTintColorListRes != 0) {
            setTintColorList(mTintColorListRes);
        }
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mPrivateModeBackground = attributes.getDrawable(R.styleable.UIButton_privateModeBackground);
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mActiveModeBackground = attributes.getDrawable(R.styleable.UIButton_activeModeBackground);
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mPrivateModeTintColorListRes = attributes.getResourceId(R.styleable.UIButton_privateModeTintColorList, 0);
        attributes.recycle();

        attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mActiveModeTintColorListRes = attributes.getResourceId(R.styleable.UIButton_activeModeTintColorList, 0);
        attributes.recycle();

        mBackground = getBackground();

        mState = State.NORMAL;
    }

    public void setTintColorList(int aColorListId) {
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
    public void setPrivateMode(boolean isPrivateMode) {
        if (isPrivateMode) {
            setPrivate();

        } else {
            setNormal();
        }
    }

    public void updateBackground(Drawable aRegularBackground, Drawable aPrivateBackground) {
        mBackground = aRegularBackground;
        mPrivateModeBackground = aPrivateBackground;
        if (mState == State.PRIVATE) {
            setBackground(mPrivateModeBackground);
        } else {
            setBackground(mBackground);
        }
    }

    public void setActiveMode(boolean isActive) {
        if (isActive) {
            setActive();

        } else {
            setNormal();
        }
    }

    public boolean isActive() {
        return mState == State.ACTIVE;
    }

    public boolean isPrivate() {
        return mState == State.PRIVATE;
    }

    private void setPrivate() {
        mState = State.PRIVATE;
        if (mPrivateModeBackground != null) {
            setBackground(mPrivateModeBackground);
        }

        if (mPrivateModeTintColorListRes != 0) {
            setTintColorList(mPrivateModeTintColorListRes);
        }
    }

    private void setNormal() {
        mState = State.NORMAL;
        if (mBackground != null) {
            setBackground(mBackground);
        }

        if(mTintColorListRes != 0) {
            setTintColorList(mTintColorListRes);
        }
    }

    private void setActive() {
        mState = State.ACTIVE;
        if (mActiveModeBackground != null) {
            setBackground(mActiveModeBackground);
        }

        if (mActiveModeTintColorListRes != 0) {
            setTintColorList(mActiveModeTintColorListRes);
        }
    }
}
