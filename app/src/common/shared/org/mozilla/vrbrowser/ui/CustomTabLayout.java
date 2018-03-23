/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;

import org.mozilla.vrbrowser.R;

import java.lang.reflect.Field;

public class CustomTabLayout extends TabLayout {
    private int mTabDefaultWidth;
    private int mTabTruncateThreshold;
    private int mTabCurrentWidth;
    private int mTabUsedSpaceWidth;
    private Delegate mDelegate;
    private boolean mIsTruncating = false;
    private int widthBeforeTruncate;
    private Field mScrollableTabMinWidth;
    private Handler mHandler;

    public interface Delegate {
        void onTruncateChange(boolean truncate);
        void onTabUsedSpaceChange(int width);
    }

    public CustomTabLayout(Context aContext) {
        super(aContext);
        initialize();
    }

    public CustomTabLayout(Context aContext, AttributeSet attrs) {
        super(aContext, attrs);
        initialize();
    }

    public CustomTabLayout(Context aContext, AttributeSet attrs, int defStyleAttr) {
        super(aContext, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        mTabDefaultWidth = getResources().getDimensionPixelSize(R.dimen.tab_default_width);
        mTabTruncateThreshold = getResources().getDimensionPixelSize(R.dimen.tab_truncation_threshold);
        mHandler = new Handler();
        updateMinTabWidth(mTabDefaultWidth);
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    public void scrollToTab(TabLayout.Tab aTab) {
        final int tabIndex = getIndexForTab(aTab);
        // Without the delay scrolling doesn't work ok in all situations
        // (e.g scrolling after just creating a new tab)
        if (tabIndex >= 0) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    setScrollX(tabIndex * mTabCurrentWidth);
                }
            }, 10);
        }
    }

    public void scrollLeft() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setScrollX(getScrollX() - mTabCurrentWidth);
            }
        }, 10);
    }

    public void scrollRight() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setScrollX(getScrollX() + mTabCurrentWidth);
            }
        }, 10);
    }

    private int getIndexForTab(TabLayout.Tab aTab) {
        for (int i = 0; i < getTabCount(); ++i) {
            if (getTabAt(i) == aTab) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addTab(@NonNull Tab tab, int position, boolean setSelected) {
        super.addTab(tab, position, setSelected);

        TabView view = (TabView) tab.getCustomView();
        if (view != null) {
            view.setIsTruncating(mIsTruncating);
        }

        checkRelayout();
    }

    @Override
    public void removeTabAt(int position) {
        super.removeTabAt(position);
        checkRelayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkRelayout();
    }

    private void checkRelayout() {
        int tabCount = getTabCount();
        int width = getWidth();
        if (width == 0 || tabCount == 0) {
            return;
        }

        int tabWidth = width / tabCount;
        if (mIsTruncating && widthBeforeTruncate > width) {
            tabWidth = widthBeforeTruncate / tabCount;
        }
        boolean truncate = false;

        if (tabWidth > mTabDefaultWidth) {
            updateMinTabWidth(mTabDefaultWidth);
        }
        else if (tabWidth > mTabTruncateThreshold) {
            updateMinTabWidth(tabWidth);
        }
        else {
            updateMinTabWidth(mTabTruncateThreshold);
            truncate = true;
        }

        if (truncate != mIsTruncating) {
            mIsTruncating = truncate;
            widthBeforeTruncate = truncate ? width : 0;
            for (int i = 0; i < tabCount; ++i) {
                TabLayout.Tab tab = getTabAt(i);
                TabView view = tab != null ? (TabView) tab.getCustomView() : null;
                if (view != null) {
                    view.setIsTruncating(truncate);
                }
            }
            if (mDelegate != null) {
                mDelegate.onTruncateChange(truncate);
            }
        }

        int usedSpace = tabCount * mTabCurrentWidth;
        if (!truncate && mTabUsedSpaceWidth != usedSpace) {
            mTabUsedSpaceWidth = usedSpace;
            if (mDelegate != null) {
                mDelegate.onTabUsedSpaceChange(mTabUsedSpaceWidth);
            }
        }
    }

    // Unfortunately TabLayout doesn't provide a method to update mScrollableTabMinWidth
    // programmatically (only by XML attribute at construction time)
    // Use reflection to dynamically change the value to achieve the dynamic layouts that
    // we want for default tab sizes, tab truncation and scrolling.
    private void updateMinTabWidth(int width) {
        if (mScrollableTabMinWidth == null) {
            try {
                mScrollableTabMinWidth = TabLayout.class.getDeclaredField("mScrollableTabMinWidth");
                mScrollableTabMinWidth.setAccessible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
        }
        try {
            if (mTabCurrentWidth == width) {
                return;
            }
            mTabCurrentWidth = width;
            mScrollableTabMinWidth.set(this, width);
            // Force min tab size layout calculations
            setTabMode(TabLayout.MODE_FIXED);
            setTabMode(TabLayout.MODE_SCROLLABLE);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
