/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.keyboards.KeyboardInterface.Words;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

import java.util.ArrayList;
import java.util.List;

import static android.view.Gravity.CENTER_VERTICAL;

public class AutoCompletionView extends FrameLayout {
    private LinearLayout mFirstLine;
    private LinearLayout mExtendContent;
    private ScrollView mScrollView;
    private View mSeparator;
    private int mKeyWidth;
    private int mKeyHeight;
    private int mLineWidth;
    private int mLineHeight;
    private UIButton mExtendButton;
    private int mExtendedHeight;
    private ArrayList<Words> mExtraItems = new ArrayList<>();
    private boolean mIsExtended;
    private Delegate mDelegate;

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
        mKeyWidth = mKeyHeight = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_button_size);
        mLineWidth = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_line_width);
        mLineHeight = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_line_height);
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
        key.setBackground(getContext().getDrawable(R.drawable.keyboard_key_background));
        if (aHandler != null) {
            key.setOnClickListener(aHandler);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mKeyHeight);
        key.setMinWidth(mKeyWidth);
        params.gravity = CENTER_VERTICAL;
        key.setLayoutParams(params);
        key.setPadding(10, 20, 10, 0);
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

    public void setItems(List<Words> aItems) {
        mFirstLine.removeAllViews();
        mExtraItems.clear();
        mExtendContent.removeAllViews();
        if (aItems == null || aItems.size() == 0) {
            exitExtend();
            mExtendButton.setVisibility(View.GONE);
            return;
        }

        int n = 0;
        int currentWidth = 0;

        for (Words item : aItems) {
            UITextButton textBtn = createButton(item, clickHandler);
            textBtn.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            currentWidth += textBtn.getMeasuredWidth();

            if (currentWidth < mLineWidth) {
                mFirstLine.addView(textBtn);
            } else {
                mExtraItems.add(item);
            }
            n++;
        }

        mExtendButton.setVisibility(currentWidth >= mLineWidth ? View.VISIBLE : View.GONE);
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

        for (Words item: mExtraItems) {
            UITextButton textBtn = createButton(item, clickHandler);
            textBtn.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            currentWidth += textBtn.getMeasuredWidth();

            if (currentWidth < mLineWidth) {
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
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        params.height = WidgetPlacement.pixelDimension(getContext(), R.dimen.autocompletion_widget_line_height);
        setLayoutParams(params);
        if (mDelegate != null) {
            mDelegate.onAutoCompletionExtendedChanged();
        }
    }
}
