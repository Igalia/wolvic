/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.views.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.SystemNotificationsBinding;
import com.igalia.wolvic.ui.adapters.SystemNotification;
import com.igalia.wolvic.ui.adapters.SystemNotificationAdapter;
import com.igalia.wolvic.ui.callbacks.SystemNotificationItemCallback;
import com.igalia.wolvic.ui.viewmodel.SystemNotificationsViewModel;
import com.igalia.wolvic.ui.widgets.SystemNotificationsManager;
import com.igalia.wolvic.ui.widgets.WindowWidget;

import java.util.List;

public class SystemNotificationsView extends LibraryView implements SystemNotificationItemCallback,
        SystemNotificationsManager.ChangeListener {

    private SystemNotificationsBinding mBinding;
    private SystemNotificationsViewModel mViewModel;
    private SystemNotificationAdapter mAdapter;

    public SystemNotificationsView(Context aContext, @NonNull LibraryPanel delegate) {
        super(aContext, delegate);
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();

        mViewModel = new ViewModelProvider(
                (VRBrowserActivity) getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(SystemNotificationsViewModel.class);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.system_notifications, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity) getContext());
        mBinding.setNotificationsViewModel(mViewModel);

        mAdapter = new SystemNotificationAdapter(this);
        mBinding.notificationsList.setAdapter(mAdapter);
        mViewModel.setIsEmpty(mAdapter.getItemCount() == 0);

        mBinding.executePendingBindings();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onDataChanged(List<SystemNotification> newData) {
        mAdapter.setItems(newData);
        mViewModel.setIsEmpty(mAdapter.getItemCount() == 0);
    }

    @Override
    public void onItemAdded(int index, SystemNotification newItem) {
        mAdapter.addItem(index, newItem);
        mViewModel.setIsEmpty(mAdapter.getItemCount() == 0);
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onShow() {
        updateLayout();
        mBinding.notificationsList.smoothScrollToPosition(0);
        if (mRootPanel != null) {
            mRootPanel.onViewUpdated(getContext().getString(R.string.notifications_title));
        }
        mAdapter.setItems(SystemNotificationsManager.getInstance().getSystemNotifications());
        SystemNotificationsManager.getInstance().addChangeListener(this);
        mViewModel.setIsEmpty(mAdapter.getItemCount() == 0);
    }

    @Override
    public void onHide() {
        super.onHide();

        SystemNotificationsManager.getInstance().removeChangeListener(this);
    }

    @Override
    public void onClick(View view, SystemNotification item) {
        if (item.getAction() != null) {
            SystemNotification.Action action = item.getAction();
            if (action.getType() == SystemNotification.Action.OPEN_URL && action.getUrl() != null) {
                Session session = SessionStore.get().getActiveSession();
                session.loadUri(action.getUrl());
            } else if (action.getType() == SystemNotification.Action.OPEN_APP_PAGE) {
                Intent intent;
                if (action.getAction() == null) {
                    intent = new Intent(Intent.ACTION_MAIN);
                } else {
                    intent = new Intent(action.getAction());
                }

                intent.setData(Uri.parse(action.getIntent()));
                getContext().startActivity(intent);
            }
            // No need to handle OPEN_APP_PAGE, this would usually just open Wolvic but it is already open
        }

    }

    @Override
    public void onDelete(View view, SystemNotification item) {
        // TODO implement
    }
}
