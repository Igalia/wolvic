/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

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

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.databinding.TopBarBinding;
import org.mozilla.vrbrowser.ui.viewmodel.WindowViewModel;

public class TopBarWidget extends UIWidget {

    private WindowViewModel mViewModel;
    private TopBarBinding mBinding;
    private AudioEngine mAudio;
    private WindowWidget mAttachedWindow;
    private TopBarWidget.Delegate mDelegate;
    private boolean mWidgetAdded = false;

    public TopBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public TopBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    public interface Delegate {
        void onCloseClicked(TopBarWidget aWidget);
        void onMoveLeftClicked(TopBarWidget aWidget);
        void onMoveRightClicked(TopBarWidget aWidget);
    }

    private void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.top_bar_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.top_bar_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) * aPlacement.width/getWorldWidth();
        aPlacement.translationY = WidgetPlacement.dpDimension(context, R.dimen.top_bar_window_margin);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
    }

    private void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.top_bar, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setViewmodel(mViewModel);

        mBinding.closeWindowButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onCloseClicked(TopBarWidget.this);
            }
        });

        mBinding.moveWindowLeftButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onMoveLeftClicked(TopBarWidget.this);
            }
        });

        mBinding.moveWindowRightButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onMoveRightClicked(TopBarWidget.this);
            }
        });

        mBinding.clearButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            if (mDelegate != null) {
                mDelegate.onCloseClicked(TopBarWidget.this);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    public void detachFromWindow() {
        mAttachedWindow = null;

        if (mViewModel != null) {
            mViewModel.getIsTopBarVisible().removeObserver(mIsVisible);
            mViewModel = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (mAttachedWindow == aWindow) {
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

        mViewModel.getIsTopBarVisible().observe((VRBrowserActivity)getContext(), mIsVisible);
    }

    public @Nullable WindowWidget getAttachedWindow() {
        return mAttachedWindow;
    }

    @Override
    public void releaseWidget() {
        detachFromWindow();

        mAttachedWindow = null;
        super.releaseWidget();
    }

    Observer<ObservableBoolean> mIsVisible = isVisible -> {
        mWidgetPlacement.visible = isVisible.get();
        if (!mWidgetAdded) {
            mWidgetManager.addWidget(TopBarWidget.this);
            mWidgetAdded = true;
        } else {
            mWidgetManager.updateWidget(TopBarWidget.this);
        }
    };

    public void setDelegate(TopBarWidget.Delegate aDelegate) {
        mDelegate = aDelegate;
    }

}
