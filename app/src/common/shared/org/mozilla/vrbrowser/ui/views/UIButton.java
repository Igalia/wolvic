/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.Dimension;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.TooltipWidget;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

public class UIButton extends AppCompatImageButton implements CustomUIButton {

    private ColorStateList mTintColorList;
    private Drawable mPrivateModeBackground;
    private Drawable mActiveModeBackground;
    private Drawable mBackground;
    private @IdRes int mTintColorListRes;
    private @IdRes int mPrivateModeTintColorListRes;
    private @IdRes int mActiveModeTintColorListRes;
    private @IdRes int mNotificationModeTintColorListRes;
    private TooltipWidget mTooltipView;
    private String mTooltipText;
    private int mTooltipDelay;
    private float mTooltipDensity;
    private @LayoutRes int mTooltipLayout;
    private boolean mCurvedTooltip = true;
    private ViewUtils.TooltipPosition mTooltipPosition;
    private boolean mIsPrivate;
    private boolean mIsActive;
    private boolean mIsNotification;
    private ClipDrawable mClipDrawable;
    private Drawable mDrawable;
    private int mClipColor;

    public UIButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageButtonStyle);
    }

    @SuppressLint("ResourceType")
    public UIButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.UIButton, defStyleAttr, 0);
        mTintColorListRes = attributes.getResourceId(R.styleable.UIButton_tintColorList, 0);
        if (mTintColorListRes != 0) {
            setTintColorList(mTintColorListRes);
        }
        mBackground = attributes.getDrawable(R.styleable.UIButton_regularModeBackground);
        mPrivateModeBackground = attributes.getDrawable(R.styleable.UIButton_privateModeBackground);
        mActiveModeBackground = attributes.getDrawable(R.styleable.UIButton_activeModeBackground);
        mPrivateModeTintColorListRes = attributes.getResourceId(R.styleable.UIButton_privateModeTintColorList, 0);
        mActiveModeTintColorListRes = attributes.getResourceId(R.styleable.UIButton_activeModeTintColorList, 0);
        mNotificationModeTintColorListRes = attributes.getResourceId(R.styleable.UIButton_notificationModeTintColorList, 0);
        mTooltipDelay = attributes.getInt(R.styleable.UIButton_tooltipDelay, getResources().getInteger(R.integer.tooltip_delay));
        mTooltipPosition = ViewUtils.TooltipPosition.fromId(attributes.getInt(R.styleable.UIButton_tooltipPosition, ViewUtils.TooltipPosition.BOTTOM.ordinal()));
        TypedValue densityValue = new TypedValue();
        getResources().getValue(R.dimen.tooltip_default_density, densityValue, true);
        mTooltipDensity = attributes.getFloat(R.styleable.UIButton_tooltipDensity, densityValue.getFloat());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            TypedArray arr = context.obtainStyledAttributes(attrs, new int [] {android.R.attr.tooltipText});
            mTooltipText = arr.getString(0);
        }
        mTooltipLayout = attributes.getResourceId(R.styleable.UIButton_tooltipLayout, R.layout.tooltip);
        mClipDrawable = (ClipDrawable)attributes.getDrawable(R.styleable.UIButton_clipDrawable);
        mClipColor = attributes.getColor(R.styleable.UIButton_clipColor, 0);
        attributes.recycle();

        if (mBackground == null) {
            mBackground = getBackground();
        }

        if (mClipDrawable != null) {
            Drawable[] layers = new Drawable[] { mDrawable, mClipDrawable };
            setImageDrawable(new LayerDrawable(layers));
            mClipDrawable.setLevel(0);
            mClipDrawable.setTint(R.color.azure);
        }

        // Android >8 doesn't perform a click when long clicking in ImageViews even if long click is disabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setLongClickable(false);
            setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    long time = event.getEventTime() - event.getDownTime();
                    if (!v.isLongClickable() && time > ViewConfiguration.getLongPressTimeout()) {
                        performClick();
                    }
                }

                return false;
            });
        }
    }

    @Nullable
    @Override
    public CharSequence getTooltipText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getTooltipTextInternal();

        } else {
            return mTooltipText;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private CharSequence getTooltipTextInternal() {
        return super.getTooltipText();
    }

    @Override
    public void setTooltipText(@Nullable CharSequence tooltipText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTooltipTextInternal(tooltipText);
        }

        if (tooltipText != null) {
            mTooltipText = tooltipText.toString();

            if (mTooltipView != null && mTooltipView.isVisible()) {
                mTooltipView.setText(tooltipText.toString());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void setTooltipTextInternal(@Nullable CharSequence tooltipText) {
        super.setTooltipText(tooltipText);
    }

    public void setCurvedTooltip(boolean aEnabled) {
        mCurvedTooltip = aEnabled;
        if (mTooltipView != null) {
            mTooltipView.setCurvedMode(aEnabled);
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (getTooltipText() != null) {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                postDelayed(mShowTooltipRunnable, mTooltipDelay);

            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                removeCallbacks(mShowTooltipRunnable);
                post(mHideTooltipRunnable);
            }
        }

        return super.onHoverEvent(event);
    }

    public void setTintColorList(int aColorListId) {
        if (mDrawable == null) {
            return;
        }
        mTintColorList = getContext().getResources().getColorStateList(
                aColorListId,
                getContext().getTheme());
        int color = mTintColorList.getColorForState(getDrawableState(), 0);
        mDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        if (mClipDrawable != null) {
            mClipDrawable.setColorFilter(new PorterDuffColorFilter(mClipColor, PorterDuff.Mode.MULTIPLY));
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mDrawable == null) {
            return;
        }
        if (mTintColorList != null && mTintColorList.isStateful()) {
            int color = mTintColorList.getColorForState(getDrawableState(), 0);
            mDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            if (mClipDrawable != null) {
                mClipDrawable.setColorFilter(new PorterDuffColorFilter(mClipColor, PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    @Override
    public void setPrivateMode(boolean isPrivateMode) {
        mIsPrivate = isPrivateMode;
        updateButtonColor();
    }

    public void setActiveMode(boolean isActive) {
        mIsActive = isActive;
        updateButtonColor();
    }

    public void setNotificationMode(boolean isNotification) {
        mIsNotification = isNotification;
        updateButtonColor();
    }

    public boolean isActive() {
        return mIsActive;
    }

    public boolean isPrivate() {
        return mIsPrivate;
    }

    private void updateButtonColor() {
        if (mIsNotification) {
            setNotification();
        } else if (mIsPrivate) {
            setPrivate();
        } else if (mIsActive) {
            setActive();
        } else {
            setNormal();
        }
    }

    private void setPrivate() {
        if (mPrivateModeBackground != null) {
            setBackground(mPrivateModeBackground);
        }

        if (mPrivateModeTintColorListRes != 0) {
            setTintColorList(mPrivateModeTintColorListRes);
        }
    }

    private void setNormal() {
        if (mBackground != null) {
            setBackground(mBackground);
        }

        if(mTintColorListRes != 0) {
            setTintColorList(mTintColorListRes);
        }
    }

    private void setActive() {
        if (mActiveModeBackground != null) {
            setBackground(mActiveModeBackground);
        }

        if (mActiveModeTintColorListRes != 0) {
            setTintColorList(mActiveModeTintColorListRes);
        }
    }

    private void setNotification() {
        if (mActiveModeTintColorListRes != 0) {
            setTintColorList(mNotificationModeTintColorListRes);
        }
    }

    public void updateBackgrounds(int aBackgroundRes, int aPrivateModeRes, int aActiveModeRes) {
        mBackground = getContext().getDrawable(aBackgroundRes);
        mPrivateModeBackground = getContext().getDrawable(aPrivateModeRes);
        mActiveModeBackground = getContext().getDrawable(aActiveModeRes);
        updateButtonColor();
    }

    public void setRegularModeBackground(Drawable background) {
        mBackground = background;
        updateButtonColor();
    }

    public void setPrivateModeBackground(Drawable background) {
        mPrivateModeBackground = background;
        updateButtonColor();
    }

    public void setActiveModeBackground(Drawable background) {
        mActiveModeBackground = background;
        updateButtonColor();
    }

    private Runnable mShowTooltipRunnable = new Runnable() {
        @Override
        public void run() {
            if (mTooltipView != null && mTooltipView.isVisible()) {
                return;
            }

            if (mTooltipView == null) {
                mTooltipView = new TooltipWidget(getContext(), mTooltipLayout);
            }
            mTooltipView.setCurvedMode(mCurvedTooltip);
            if (getTooltipText() != null) {
                mTooltipView.setText(getTooltipText().toString());
            }

            Rect offsetViewBounds = new Rect();
            getDrawingRect(offsetViewBounds);
            UIWidget parent = ViewUtils.getParentWidget(UIButton.this);
            parent.offsetDescendantRectToMyCoords(UIButton.this, offsetViewBounds);

            float ratio = WidgetPlacement.viewToWidgetRatio(getContext(), parent);

            mTooltipView.getPlacement().parentHandle = parent.getHandle();
            mTooltipView.getPlacement().density = mTooltipDensity;
            // At the moment we only support showing tooltips on top or bottom of the target view
            if (mTooltipPosition == ViewUtils.TooltipPosition.BOTTOM) {
                mTooltipView.getPlacement().anchorY = 1.0f;
                mTooltipView.getPlacement().parentAnchorY = 0.0f;
                mTooltipView.getPlacement().translationX = (offsetViewBounds.left + UIButton.this.getWidth() / 2.0f) * ratio;
                mTooltipView.getPlacement().translationY = -offsetViewBounds.top * ratio;

            } else {
                mTooltipView.getPlacement().anchorY = 0.0f;
                mTooltipView.getPlacement().parentAnchorY = 1.0f;
                mTooltipView.getPlacement().translationX = (offsetViewBounds.left + UIButton.this.getHeight() / 2.0f) * ratio;
                mTooltipView.getPlacement().translationY = offsetViewBounds.top * ratio;
            }

            mTooltipView.setCurvedMode(false);
            mTooltipView.show(UIWidget.CLEAR_FOCUS);
        }
    };

    private Runnable mHideTooltipRunnable = new Runnable() {
        @Override
        public void run() {
            if (mTooltipView != null) {
                mTooltipView.hide(UIWidget.REMOVE_WIDGET);
            }
        }
    };

    public void setLayoutWidth(@NonNull @Dimension float dimen) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = (int)dimen;
        setLayoutParams(params);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        setHovered(false);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        mDrawable = drawable;
    }

    public boolean setLevel(int level) {
        return mClipDrawable.setLevel(level);
    }
}
