/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.databinding.TopBarBinding;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.utils.DeviceType;

import java.util.Objects;

public class TopBarWidget extends UIWidget implements SharedPreferences.OnSharedPreferenceChangeListener {

    private WindowViewModel mViewModel;
    private TopBarBinding mBinding;
    private AudioEngine mAudio;
    private WindowWidget mAttachedWindow;
    private TopBarWidget.Delegate mDelegate;
    private boolean mWidgetAdded = false;
    private SharedPreferences mPrefs;
    private boolean mUsesHorizontalTabsBar = false;

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
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

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
        // FIXME: we temporarily disable the creation of a layer for this widget in order to
        // limit the amount of layers we create, as Pico's runtime only allows 16 at a given time.
        if (DeviceType.isPicoXR())
            aPlacement.layer = false;
    }

    private void adjustWindowPlacement(boolean isHorizontalTabsVisible) {
        if (isHorizontalTabsVisible) {
            // Move this widget upwards to make space for the horizontal tabs bar.
            getPlacement().verticalOffset = WidgetPlacement.dpDimension(getContext(), R.dimen.horizontal_tabs_bar_height) * WidgetPlacement.worldToDpRatio(getContext());
        } else {
            getPlacement().verticalOffset = 0;
        }
        mWidgetManager.updateWidget(TopBarWidget.this);
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
            mViewModel.getIsTabsBarVisible().removeObserver(mIsTabsBarVisible);
            mViewModel = null;
        }
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
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

        mViewModel.getIsTopBarVisible().observe((VRBrowserActivity) getContext(), mIsVisible);
        mViewModel.getIsTabsBarVisible().observe((VRBrowserActivity) getContext(), mIsTabsBarVisible);

        mPrefs.registerOnSharedPreferenceChangeListener(this);
        int tabsLocation = mPrefs.getInt(getContext().getString(R.string.settings_key_tabs_location), SettingsStore.TABS_LOCATION_DEFAULT);
        mUsesHorizontalTabsBar = (tabsLocation == SettingsStore.TABS_LOCATION_HORIZONTAL);
        adjustWindowPlacement(mViewModel.getIsTabsBarVisible().getValue().get() && mUsesHorizontalTabsBar);
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

    private Observer<ObservableBoolean> mIsVisible = isVisible -> {
        mWidgetPlacement.visible = isVisible.get();
        if (!mWidgetAdded) {
            mWidgetManager.addWidget(TopBarWidget.this);
            mWidgetAdded = true;
        } else {
            mWidgetManager.updateWidget(TopBarWidget.this);
        }
    };

    private Observer<ObservableBoolean> mIsTabsBarVisible = isTabsBarVisible -> {
        adjustWindowPlacement(isTabsBarVisible.get() && mUsesHorizontalTabsBar);
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (Objects.equals(key, getContext().getString(R.string.settings_key_tabs_location))) {
            int tabsLocation = mPrefs.getInt(getContext().getString(R.string.settings_key_tabs_location), SettingsStore.TABS_LOCATION_DEFAULT);
            mUsesHorizontalTabsBar = tabsLocation == SettingsStore.TABS_LOCATION_HORIZONTAL;
            adjustWindowPlacement(mViewModel.getIsTabsBarVisible().getValue().get() && mUsesHorizontalTabsBar);
        }
    }

    public void setDelegate(TopBarWidget.Delegate aDelegate) {
        mDelegate = aDelegate;
    }

}
