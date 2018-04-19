/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
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
    private boolean mIsPrivate = false;
    private int mSessionId;
    private TextView mTabTitleView;
    private ImageView mTabFaviconView;
    private ImageView mTabLoadingView;
    private ImageButton mTabCloseButton;
    private Animation mLoadingAnimation;
    private TabCloseCallback mTabCloseCallback;
    public static final int TAB_ANIMATION_DURATION = 100;

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
                if (mTabCloseCallback != null) {
                    mTabCloseCallback.onTabClose(TabView.this);
                }
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

    public interface TabCloseCallback {
        void onTabClose(TabView aTab);
    }
    public void setTabCloseCallback(TabCloseCallback aCallback) {
        mTabCloseCallback = aCallback;
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

    public void setIsPrivate(boolean aPrivate) {
        if (mIsPrivate != aPrivate) {
            mIsPrivate = aPrivate;
            updateBackground();
            updateFavicon();
        }
    }

    public void animateAdd() {
        Animation anim = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF,-1.0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0.0f,
                TranslateAnimation.RELATIVE_TO_SELF,0.0f,
                TranslateAnimation.RELATIVE_TO_SELF,0.0f);
        anim.setFillAfter(false);
        anim.setDuration(TAB_ANIMATION_DURATION);
        this.startAnimation(anim);
    }

    public void animateSiblingRemove() {
        final ViewGroup parent = (ViewGroup) getParent();
        Animation anim = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF,1.0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0.0f,
                TranslateAnimation.RELATIVE_TO_SELF,0.0f,
                TranslateAnimation.RELATIVE_TO_SELF,0.0f);
        anim.setFillAfter(false);
        anim.setDuration(TAB_ANIMATION_DURATION);
        parent.startAnimation(anim);
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
            setBackgroundResource(mIsPrivate ? R.drawable.tab_active_private_first : R.drawable.tab_active_first);
        } else if (mIsSelected) {
            setBackgroundResource(mIsPrivate ? R.drawable.tab_active_private : R.drawable.tab_active);
        } else {
            setBackgroundResource(R.drawable.tab_inactive_with_hover);
        }
    }

    private void updateFavicon() {
        if (mIsPrivate) {
            mTabFaviconView.setImageResource(R.drawable.ic_private_browsing);
        } else {
            mTabFaviconView.setImageResource(R.drawable.ic_icon_favicon_placeholder);
        }
    }
}
