/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.mozilla.vrbrowser.R;

public class NavigationURLBar extends FrameLayout {
    private EditText mURL;
    private ImageButton mMicrophoneButton;
    private ImageView mLoadingView;
    private Animation mLoadingAnimation;
    private ImageView mInsecureIcon;
    private RelativeLayout mURLLeftContainer;
    private boolean mIsLoading = false;
    private boolean mIsInsecure = false;
    private int mDefaultURLLeftPadding = 0;
    private int mURLProtocolColor;

    public NavigationURLBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.navigation_url, this);
        mURL = findViewById(R.id.urlEditText);
        mMicrophoneButton = findViewById(R.id.microphoneButton);
        mURLLeftContainer = findViewById(R.id.urlLeftContainer);
        mLoadingView = findViewById(R.id.loadingView);
        mLoadingAnimation = AnimationUtils.loadAnimation(aContext, R.anim.loading);
        mInsecureIcon = findViewById(R.id.insecureIcon);
        mDefaultURLLeftPadding = mURL.getPaddingLeft();

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = aContext.getTheme();
        theme.resolveAttribute(R.attr.urlProtocolColor, typedValue, true);
        mURLProtocolColor = typedValue.data;
    }

    public void setURL(String aURL) {
        int index = -1;
        if (aURL != null) {
            index = aURL.indexOf("://");
        }
        if (index > 0) {
            SpannableString spannable = new SpannableString(aURL);
            ForegroundColorSpan color = new ForegroundColorSpan(mURLProtocolColor);
            spannable.setSpan(color, 0, index + 3, 0);
            mURL.setText(spannable);
        } else {
            mURL.setText(aURL);
        }
    }

    public void setIsLoading(boolean aIsLoading) {
        if (aIsLoading == mIsLoading) {
            return;
        }
        mIsLoading = aIsLoading;
        if (aIsLoading) {
            mLoadingView.startAnimation(mLoadingAnimation);
        } else {
            mLoadingView.clearAnimation();
        }
        syncViews();
    }

    public void setIsInsecure(boolean aIsInsecure) {
        if (mIsInsecure != aIsInsecure) {
            mIsInsecure = aIsInsecure;
            syncViews();
        }
    }

    private void syncViews() {
        if (mIsLoading) {
            mLoadingView.setVisibility(View.VISIBLE);
        } else {
            mLoadingView.setVisibility(View.GONE);
        }

        if (!mIsLoading && mIsInsecure) {
            mInsecureIcon.setVisibility(View.VISIBLE);
        } else {
            mInsecureIcon.setVisibility(View.GONE);
        }

        boolean showContainer = mIsLoading || mIsInsecure;
        int leftPadding = mDefaultURLLeftPadding;
        if (showContainer) {
            mURLLeftContainer.setVisibility(View.VISIBLE);
            mURLLeftContainer.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            leftPadding += mURLLeftContainer.getMeasuredWidth();
        }
        else {
            mURLLeftContainer.setVisibility(View.GONE);
        }

        mURL.setPadding(leftPadding, mURL.getPaddingTop(), mURL.getPaddingRight(), mURL.getPaddingBottom());
    }
}
