/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.settings;

import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.databinding.OptionsExceptionsBinding;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.ui.adapters.SitePermissionAdapter;
import com.igalia.wolvic.ui.callbacks.PermissionSiteItemCallback;
import com.igalia.wolvic.ui.viewmodel.SitePermissionViewModel;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.List;

class SitePermissionsOptionsView extends SettingsView {

    private OptionsExceptionsBinding mBinding;
    protected SitePermissionAdapter mAdapter;
    protected SitePermissionViewModel mViewModel;
    private @SitePermission.Category int mCategory;

    public SitePermissionsOptionsView(Context aContext, WidgetManagerDelegate aWidgetManager, @SitePermission.Category int category) {
        super(aContext, aWidgetManager);
        mCategory = category;
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
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
        mBinding.headerLayout.setBackClickListener(view -> onDismiss());

        // Adapters
        mBinding.siteList.setAdapter(mAdapter);

        // Footer
        mBinding.footerLayout.setFooterButtonClickListener(mClearAllListener);

        switch (mCategory) {
            case SitePermission.SITE_PERMISSION_POPUP:
                mBinding.headerLayout.setTitle(R.string.settings_privacy_policy_popups_title_v1);
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
            case SitePermission.SITE_PERMISSION_AUTOFILL:
                mBinding.headerLayout.setTitle(R.string.settings_privacy_policy_login_exceptions_title);
                mBinding.contentText.setText(R.string.settings_privacy_policy_login_exceptions_description);
                mBinding.emptyText.setText(R.string.settings_privacy_policy_login_exceptions_empty_description);
                mBinding.emptySecondText.setVisibility(GONE);
                break;
        }

        mBinding.executePendingBindings();
    }

    protected OnClickListener mClearAllListener = (view) -> {
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
                return SettingViewType.POPUP_EXCEPTIONS;
            case SitePermission.SITE_PERMISSION_AUTOFILL:
                return SettingViewType.LOGIN_EXCEPTIONS;
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

    protected PermissionSiteItemCallback mCallback = new PermissionSiteItemCallback() {
        @Override
        public void onDelete(@NonNull SitePermission item) {
            mViewModel.deleteSite(item);
            reloadIfSameDomain(item.url);
        }
    };

    protected void reloadIfSameDomain(String aHost) {
        if (mCategory != SitePermission.SITE_PERMISSION_WEBXR) {
            return;
        }
        for (WindowWidget window: mWidgetManager.getWindows().getCurrentWindows()) {
            Session session = window.getSession();
            if (aHost.equalsIgnoreCase(UrlUtils.getHost(session.getCurrentUri()))) {
                session.reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
            }
        }
    }
}
