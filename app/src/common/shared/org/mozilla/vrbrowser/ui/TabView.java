/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;

public class TabView extends LinearLayout {
    private boolean mIsSelected = false;
    private boolean mIsFirst = false;
    private boolean mIsLoading = false;
    private boolean mIsTruncating = false;
    private int mSessionId;
    private TextView mTabTitleView;
    private ImageView mTabFaviconView;
    private ImageView mTabLoadingView;
    private ImageButton mTabCloseButton;
    private Animation mLoadingAnimation;

    public TabView(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TabView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TabView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.tab, this);
        mTabTitleView = findViewById(R.id.tabTitleView);
        mTabFaviconView = findViewById(R.id.tabFaviconView);
        mTabLoadingView = findViewById(R.id.tabLoadingView);
        mTabCloseButton = findViewById(R.id.tabCloseButton);
        mLoadingAnimation = AnimationUtils.loadAnimation(aContext, R.anim.loading);

        mTabCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().removeSession(mSessionId);
            }
        });

        updateBackground();
    }

    public void setSessionId(int aSessionId) {
        mSessionId = aSessionId;
    }

    public int getSessionId() {
        return mSessionId;
    }


    public void setTitle(String aTitle) {
        mTabTitleView.setText(aTitle);
    }

    public void setIsSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            updateBackground();
            updateCloseIcon();
        }
    }

    public void setIsFirst(boolean first) {
        if (mIsFirst != first) {
            mIsFirst = first;
            updateBackground();
        }
    }


    public void setIsLoading(boolean aIsLoading) {
        if (aIsLoading == mIsLoading) {
            return;
        }
        mIsLoading = aIsLoading;
        if (aIsLoading) {
            mTabLoadingView.startAnimation(mLoadingAnimation);
            mTabLoadingView.setVisibility(View.VISIBLE);
            mTabFaviconView.setVisibility(View.GONE);
        } else {
            mTabLoadingView.clearAnimation();
            mTabLoadingView.setVisibility(View.GONE);
            mTabFaviconView.setVisibility(View.VISIBLE);
        }
    }

    public void setIsTruncating(boolean truncating) {
        if (mIsTruncating != truncating) {
            mIsTruncating = truncating;
            updateCloseIcon();
        }
    }

    private void updateCloseIcon() {
        if (mIsSelected || !mIsTruncating) {
            mTabCloseButton.setVisibility(VISIBLE);
        } else {
            mTabCloseButton.setVisibility(GONE);
        }
    }

    private void updateBackground() {
        if (mIsFirst && mIsSelected) {
            setBackgroundResource(R.drawable.tab_active_first);
        } else if (mIsSelected) {
            setBackgroundResource(R.drawable.tab_active);
        } else {
            setBackgroundResource(R.drawable.tab_inactive_with_hover);
        }
    }
}
