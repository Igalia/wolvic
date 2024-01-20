/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views;

import static android.view.Gravity.CENTER_VERTICAL;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.keyboards.KeyboardInterface.Words;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;

public class AutoCompletionView extends FrameLayout {
    private LinearLayout mFirstLine;
    private LinearLayout mExtendContent;
    private ScrollView mScrollView;
    private View mSeparator;
    private View mExtendButtonSeparator;
    private int mMinKeyWidth;
    private int mKeyHeight;
    private int mLineWidth;
    private int mLineHeight;
    private int mItemPadding;
    private UIButton mExtendButton;
    private int mExtendedHeight;
    private ArrayList<Words> mExtraItems = new ArrayList<>();
    private boolean mIsExtended;
    private Delegate mDelegate;
    private List<Words> mItems;

    public interface Delegate {
        void onAutoCompletionItemClick(Words aItem);
        void onAutoCompletionExtendedChanged();
    }

    public AutoCompletionView(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public AutoCompletionView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public AutoCompletionView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.autocompletion_bar, this);
        mFirstLine = findViewById(R.id.autoCompletionFirstLine);
        mSeparator = findViewById(R.id.autoCompletionSeparator);
        mExtendButtonSeparator = findViewById(R.id.extendButtonSeparator);
        mScrollView = findViewById(R.id.autoCompletionScroll);
        mExtendButton = findViewById(R.id.extendButton);
        mExtendButton.setTintColorList(R.drawable.main_button_icon_color);
        mExtendContent = findViewById(R.id.extendContent);
        mExtendButton.setOnClickListener(v -> {
            if (mIsExtended) {
                exitExtend();
            } else {
                enterExtend();
            }
        });
        mMinKeyWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_min_item_width);
        mKeyHeight = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_item_height);
        mLineWidth = getMeasuredWidth();
        mLineHeight = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_line_height);
        mItemPadding = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_item_padding);
        mExtendedHeight = mLineHeight * 6;
        setFocusable(false);
    }

    public void setExtendedHeight(int aHeight) {
        mExtendedHeight = aHeight;
    }

    public void setDelegate(AutoCompletionView.Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    private UITextButton createButton(Words aWords, OnClickListener aHandler) {
        UITextButton key = new UITextButton(getContext());
        key.setTintColorList(R.drawable.main_button_icon_color);
        key.setBackground(getContext().getDrawable(R.drawable.autocompletion_item_background));
        if (aHandler != null) {
            key.setOnClickListener(aHandler);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mKeyHeight);
        key.setMinWidth(mMinKeyWidth);
        params.gravity = CENTER_VERTICAL;
        key.setLayoutParams(params);
        key.setPadding(mItemPadding, 20, mItemPadding, 0);
        key.setIncludeFontPadding(false);
        key.setText(aWords.value);
        key.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        key.setTag(aWords);

        return key;
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mLineHeight);
        row.setLayoutParams(params);
        return row;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int width = getMeasuredWidth();
        if (mLineWidth != width && width > 0) {
            mLineWidth = width;
            layoutItems();
            if (mIsExtended) {
                layoutExtendedItems();
            }
        }
    }

    public void setItems(List<Words> aItems) {
        mItems = aItems;
        if (mLineWidth == 0) {
            mLineWidth = getMeasuredWidth();
        }
        if (mLineWidth > 0) {
            layoutItems();
            if (mIsExtended) {
                layoutExtendedItems();
            }
        }
    }

    private void layoutItems() {
        mFirstLine.removeAllViews();
        mExtraItems.clear();
        mExtendContent.removeAllViews();
        if (mItems == null || mItems.size() == 0) {
            exitExtend();
            mExtendButton.setVisibility(View.GONE);
            mExtendButtonSeparator.setVisibility(View.GONE);
            return;
        }

        int n = 0;
        int currentWidth = 0;
        int extendButtonWidth =  mExtendButton.getWidth();

        for (Words item : mItems) {
            UITextButton textBtn = createButton(item, clickHandler);
            if (n == 0) {
                textBtn.setBackground(getContext().getDrawable(R.drawable.autocompletion_item_background_first));
                textBtn.setTintColorList(R.drawable.autocompletion_item_active_color);
            }
            textBtn.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            currentWidth += textBtn.getMeasuredWidth();

            if (currentWidth < (mLineWidth - extendButtonWidth)) {
                mFirstLine.addView(textBtn);
            } else {
                mExtraItems.add(item);
            }
            n++;
        }

        mExtendButton.setVisibility(currentWidth >= mLineWidth ? View.VISIBLE : View.GONE);
        mExtendButtonSeparator.setVisibility(mExtendButton.getVisibility());
    }

    public boolean isExtended() {
        return mIsExtended;
    }

    private OnClickListener clickHandler = v -> {
        UITextButton button = (UITextButton) v;
        if (mIsExtended) {
            exitExtend();
        }
        if (mDelegate != null) {
            mDelegate.onAutoCompletionItemClick((Words)button.getTag());
        }
    };

    private void layoutExtendedItems() {
        int index = 0;
        int currentWidth = 0;
        LinearLayout current = createRow();
        int padding = mScrollView.getPaddingStart() + mScrollView.getPaddingEnd();

        for (Words item: mExtraItems) {
            UITextButton textBtn = createButton(item, clickHandler);
            textBtn.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            currentWidth += textBtn.getMeasuredWidth();

            if (currentWidth < (mLineWidth - padding)) {
                current.addView(textBtn);
                index++;
            } else {
                mExtendContent.addView(current);
                current = createRow();
                index = 0;
                currentWidth = 0;
            }
        }
        if (index > 0) {
            mExtendContent.addView(current);
        }
    }

    private void enterExtend() {
        if (mIsExtended) {
            return;
        }
        mIsExtended = true;
        mScrollView.setVisibility(View.VISIBLE);
        mSeparator.setVisibility(View.VISIBLE);
        if (mExtendContent.getChildCount() == 0) {
            layoutExtendedItems();
        }

        mExtendButton.setScaleY(-1);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        params.height = mExtendedHeight;
        setLayoutParams(params);
        if (mDelegate != null) {
            mDelegate.onAutoCompletionExtendedChanged();
        }
    }

    private void exitExtend() {
        if (!mIsExtended) {
            return;
        }
        mIsExtended = false;
        mScrollView.setVisibility(View.GONE);
        mSeparator.setVisibility(View.GONE);
        mExtendButton.setScaleY(1);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        params.height = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_line_height);
        setLayoutParams(params);
        if (mDelegate != null) {
            mDelegate.onAutoCompletionExtendedChanged();
        }
    }
}
