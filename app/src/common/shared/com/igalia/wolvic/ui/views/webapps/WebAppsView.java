/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views.webapps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.WebAppsStore;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.WebappsBinding;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.ui.adapters.WebAppsAdapter;
import com.igalia.wolvic.ui.callbacks.WebAppItemCallback;
import com.igalia.wolvic.ui.views.library.LibraryView;
import com.igalia.wolvic.ui.viewmodel.WebAppsViewModel;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.utils.SystemUtils;

import java.util.List;
import java.util.stream.Collectors;

public class WebAppsView extends LibraryView implements WebAppsStore.WebAppsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(WebAppsView.class);

    private WebappsBinding mBinding;
    private WebAppsAdapter mWebAppsAdapter;
    private WebAppsViewModel mViewModel;
    private Handler mHandler;
    private WebAppsPanel mWebAppsPanel;

    public WebAppsView(Context aContext, @NonNull WebAppsPanel delegate) {
        super(aContext);
        mWebAppsPanel = delegate;
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();

        mHandler = new Handler(Looper.getMainLooper());
        mViewModel = new ViewModelProvider(
                (VRBrowserActivity) getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(WebAppsViewModel.class);

        SessionStore.get().getWebAppsStore().addListener(this);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.webapps, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mWebAppsAdapter = new WebAppsAdapter(mWebAppItemCallback, getContext());
        mBinding.setWebAppsViewModel(mViewModel);
        mBinding.webAppsList.setAdapter(mWebAppsAdapter);
        mBinding.webAppsList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.webAppsList.addOnScrollListener(mScrollListener);
        mBinding.webAppsList.setHasFixedSize(true);
        mBinding.webAppsList.setItemViewCacheSize(20);
        // Drawing Cache is deprecated in API level 28: https://developer.android.com/reference/android/view/View#getDrawingCache().
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mBinding.webAppsList.setDrawingCacheEnabled(true);
            mBinding.webAppsList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        }

        mViewModel.setIsNarrow(false);
        mViewModel.setIsLoading(true);

        updateWebApps();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void updateSearchFilter(String s) {
        super.updateSearchFilter(s);
        updateWebApps();
    };

    private void updateWebApps() {
        List<WebApp> webApps = SessionStore.get().getWebAppsStore()
                .getWebApps().stream()
                .filter(value -> {
                    if (value.getName() != null && !mSearchFilter.isEmpty()) {
                        return value.getName().toLowerCase().contains(mSearchFilter) ||
                                value.getStartUrl().toLowerCase().contains(mSearchFilter);
                    }
                    return true;
                })
                .collect(Collectors.toList());
        setWebApps(webApps);
    }

    // WebAppsStore.WebAppsListener
    @Override
    public void onWebAppsUpdated(@NonNull List<WebApp> webApps) {
        setWebApps(webApps);
    }

    private void setWebApps(@NonNull List<WebApp> webApps) {
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(() -> {
            mWebAppsAdapter.setWebAppsList(webApps);
            mViewModel.setIsEmpty(webApps.isEmpty());
            mViewModel.setIsLoading(false);
            mBinding.executePendingBindings();
        });
    }

    @Override
    public void onShow() {
        updateLayout();
        mBinding.webAppsList.smoothScrollToPosition(0);
        if (mWebAppsPanel != null) {
            mWebAppsPanel.onViewUpdated(getContext().getString(R.string.web_apps_title));
        }
    }

    @Override
    public void onDestroy() {
        SessionStore.get().getWebAppsStore().removeListener(this);

        mBinding.webAppsList.removeOnScrollListener(mScrollListener);
    }

    private final WebAppItemCallback mWebAppItemCallback = new WebAppItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull WebApp item) {
            mBinding.webAppsList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getStartUrl());

            SessionStore.get().getWebAppsStore().updateWebAppOpenTime(item.getId());

            WindowWidget window = mWidgetManager.getFocusedWindow();
            window.hidePanel();
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull WebApp item) {
            mBinding.webAppsList.requestFocusFromTouch();

            SessionStore.get().getWebAppsStore().removeWebAppById(item.getId());
        }
    };

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateLayout();
    }

    @Override
    protected void updateLayout() {
        post(() -> {
            double width = Math.ceil(getWidth() / getContext().getResources().getDisplayMetrics().density);
            boolean isNarrow = width < SettingsStore.WINDOW_WIDTH_DEFAULT;

            if (isNarrow != mViewModel.getIsNarrow().getValue().get()) {
                mWebAppsAdapter.setNarrow(isNarrow);

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                requestLayout();
            }
        });
    }
}
