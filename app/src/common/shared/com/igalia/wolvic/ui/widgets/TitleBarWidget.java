/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.databinding.TitleBarBinding;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.UrlUtils;

public class TitleBarWidget extends UIWidget {

    public interface Delegate {
        void onTitleClicked(@NonNull TitleBarWidget titleBar);
        void onMediaPlayClicked(@NonNull TitleBarWidget titleBar);
        void onMediaPauseClicked(@NonNull TitleBarWidget titleBar);
    }

    private WindowViewModel mViewModel;
    private TitleBarBinding mBinding;
    private WindowWidget mAttachedWindow;
    private boolean mWidgetAdded = false;
    private Delegate mDelegate;

    public TitleBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TitleBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TitleBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(@NonNull Context aContext) {
        updateUI();
    }

    private void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mBinding = DataBindingUtil.inflate(inflater, R.layout.title_bar, this, true);
        mBinding.setWidget(this);
        mBinding.setDelegate(mDelegate);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setViewmodel(mViewModel);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
        mBinding.setDelegate(mDelegate);
    }

    public @Nullable
    WindowWidget getAttachedWindow() {
        return mAttachedWindow;
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();

        mAttachedWindow = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.title_bar_width);
        float ratio = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) /
                      WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width);

        aPlacement.worldWidth = aPlacement.width * ratio;
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 1.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = -35;
        aPlacement.cylinder = true;
        aPlacement.visible = true;
        // FIXME: we temporarily disable the creation of a layer for this widget in order to
        // limit the amount of layers we create, as Pico's runtime only allows 16 at a given time.
        if (DeviceType.isPicoXR())
            aPlacement.layer = false;
    }

    @Override
    public void detachFromWindow() {
        mAttachedWindow = null;

        if (mViewModel != null) {
            mViewModel.getIsTitleBarVisible().removeObserver(mIsVisibleObserver);
            mViewModel = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (aWindow == mAttachedWindow) {
            return;
        }
        detachFromWindow();

        mWidgetPlacement.parentHandle = aWindow.getHandle();
        mAttachedWindow = aWindow;

        // ModelView creation and observers setup
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(mAttachedWindow.hashCode()), WindowViewModel.class);

        mBinding.setViewmodel(mViewModel);

        mViewModel.getIsTitleBarVisible().observe((VRBrowserActivity)getContext(), mIsVisibleObserver);
    }

    Observer<ObservableBoolean> mIsVisibleObserver = isVisible -> {
        mWidgetPlacement.visible = isVisible.get();
        if (!mWidgetAdded) {
            mWidgetManager.addWidget(TitleBarWidget.this);
            mWidgetAdded = true;
        } else {
            mWidgetManager.updateWidget(TitleBarWidget.this);
        }

        if (mAttachedWindow.getSession() != null) {
            String url = mAttachedWindow.getSession().getCurrentUri();
            mViewModel.setUrl(url);
            mViewModel.setCurrentContentType(UrlUtils.getContentType(url));
        }
    };

}
