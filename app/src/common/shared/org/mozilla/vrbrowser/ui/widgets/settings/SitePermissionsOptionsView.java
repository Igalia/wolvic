/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.settings;

import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.databinding.OptionsExceptionsBinding;
import org.mozilla.vrbrowser.db.SitePermission;
import org.mozilla.vrbrowser.ui.adapters.SitePermissionAdapter;
import org.mozilla.vrbrowser.ui.callbacks.PermissionSiteItemCallback;
import org.mozilla.vrbrowser.ui.viewmodel.SitePermissionViewModel;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.List;

class SitePermissionsOptionsView extends SettingsView {

    private OptionsExceptionsBinding mBinding;
    private SitePermissionAdapter mAdapter;
    private SitePermissionViewModel mViewModel;
    private @SitePermission.Category int mCategory;

    public SitePermissionsOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager, @SitePermission.Category int category) {
        super(aContext, aWidgetManager);
        mCategory = category;
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Preferred languages adapter
        mAdapter = new SitePermissionAdapter(getContext(), mCallback);

        // View Model
        mViewModel = new SitePermissionViewModel(((Application)getContext().getApplicationContext()));
        updateUI();
    }

    @Override
    protected void updateUI() {
        super.updateUI();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.options_exceptions, this, true);

        // Header
        mBinding.headerLayout.setBackClickListener(view -> {
            mDelegate.showView(SettingViewType.PRIVACY);
        });

        // Adapters
        mBinding.siteList.setAdapter(mAdapter);

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mClearAllListener);

        switch (mCategory) {
            case SitePermission.SITE_PERMISSION_POPUP:
                mBinding.headerLayout.setTitle(R.string.settings_privacy_policy_popups_title);
                mBinding.contentText.setText(R.string.privacy_options_popups_list_header_v1);
                mBinding.emptyText.setText(R.string.privacy_options_popups_list_empty_first);
                break;
            case SitePermission.SITE_PERMISSION_WEBXR:
                mBinding.headerLayout.setTitle(R.string.settings_privacy_policy_webxr_title);
                mBinding.contentText.setText(R.string.settings_privacy_policy_webxr_description);
                mBinding.emptyText.setText(R.string.settings_privacy_policy_webxr_empty_description);
                break;
            case SitePermission.SITE_PERMISSION_TRACKING:
                mBinding.headerLayout.setTitle(R.string.settings_privacy_policy_tracking_title);
                mBinding.contentText.setText(R.string.settings_privacy_policy_tracking_description);
                mBinding.emptyText.setText(R.string.settings_privacy_policy_tracking_empty_description);
                mBinding.emptySecondText.setVisibility(GONE);
                break;
        }

        mBinding.executePendingBindings();
    }

    private OnClickListener mClearAllListener = (view) -> {
        reset();
    };

    @Override
    public Point getDimensions() {
        return new Point( WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_width),
                WidgetPlacement.dpDimension(getContext(), R.dimen.settings_dialog_height));
    }


    @Override
    protected boolean reset() {
        List<SitePermission> sites = mAdapter.getSites();
        mViewModel.deleteAll(mCategory);
        if (sites != null) {
            for (SitePermission site: sites) {
                reloadIfSameDomain(site.url);
            }
        }
        return true;
    }

    @Override
    public void onShown() {
        super.onShown();

        mViewModel.getAll(mCategory).observeForever(mObserver);

        mBinding.siteList.post(() -> mBinding.siteList.scrollToPosition(0));
    }

    @Override
    public void onHidden() {
        super.onHidden();

        mViewModel.getAll(mCategory).removeObserver(mObserver);
    }

    @Override
    protected SettingViewType getType() {
        switch (mCategory) {
            case SitePermission.SITE_PERMISSION_WEBXR:
                return SettingViewType.WEBXR_EXCEPTIONS;
            case SitePermission.SITE_PERMISSION_POPUP:
            default:
                return SettingViewType.POPUP_EXCEPTIONS;
        }
    }

    private Observer<List<SitePermission>> mObserver = new Observer<List<SitePermission>>() {
        @Override
        public void onChanged(List<SitePermission> sites) {
            if (sites != null) {
                mAdapter.setSites(sites);
                mBinding.setIsEmpty(sites.isEmpty());

            } else {
                mBinding.setIsEmpty(true);
            }
        }
    };

    private PermissionSiteItemCallback mCallback = new PermissionSiteItemCallback() {
        @Override
        public void onDelete(@NonNull SitePermission item) {
            mViewModel.deleteSite(item);
            reloadIfSameDomain(item.url);
        }
    };

    private void reloadIfSameDomain(String aHost) {
        if (mCategory != SitePermission.SITE_PERMISSION_WEBXR) {
            return;
        }
        for (WindowWidget window: mWidgetManager.getWindows().getCurrentWindows()) {
            Session session = window.getSession();
            if (aHost.equalsIgnoreCase(UrlUtils.getHost(session.getCurrentUri()))) {
                session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }
    }
}
