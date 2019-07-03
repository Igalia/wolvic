/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.databinding.ContextMenuBinding;
import org.mozilla.vrbrowser.ui.adapters.ContextMenuAdapter;
import org.mozilla.vrbrowser.ui.callbacks.ContextMenuClickCallback;

import java.util.Arrays;
import java.util.List;

public class ContextMenu extends FrameLayout {

    private ContextMenuBinding mBinding;
    private ContextMenuAdapter mContextMenuAdapter;
    private ContextMenuClickCallback mCallback;

    public ContextMenu(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ContextMenu(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ContextMenu(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.context_menu, this, true);
        mContextMenuAdapter = new ContextMenuAdapter(mContextMenuClickCallback);
        mBinding.contextMenuList.setAdapter(mContextMenuAdapter);
        mBinding.executePendingBindings();
    }

    public void setContextMenuClickCallback(ContextMenuClickCallback callback) {
        mCallback = callback;
    }

    private final ContextMenuClickCallback mContextMenuClickCallback = contextMenuNode -> {
        if (mCallback != null) {
            mCallback.onClick(contextMenuNode);
        }
    };

    public void createLinkContextMenu() {
        List<ContextMenuAdapter.ContextMenuNode> contextMenuItems = buildBaseContextMenu();
        mContextMenuAdapter.setContextMenuItemList(contextMenuItems);
        mBinding.executePendingBindings();
    }

    public void createAudioContextMenu() {
        List<ContextMenuAdapter.ContextMenuNode> contextMenuItems = buildBaseContextMenu();
        mContextMenuAdapter.setContextMenuItemList(contextMenuItems);
        mBinding.executePendingBindings();
    }

    public void createVideoContextMenu() {
        List<ContextMenuAdapter.ContextMenuNode> contextMenuItems = buildBaseContextMenu();
        mContextMenuAdapter.setContextMenuItemList(contextMenuItems);
        mBinding.executePendingBindings();
    }

    public void createImageContextMenu() {
        List<ContextMenuAdapter.ContextMenuNode> contextMenuItems = buildBaseContextMenu();
        mContextMenuAdapter.setContextMenuItemList(contextMenuItems);
        mBinding.executePendingBindings();
    }

    private List<ContextMenuAdapter.ContextMenuNode> buildBaseContextMenu() {
        return Arrays.asList(
                new ContextMenuAdapter.ContextMenuNode(
                        0,
                        getResources().getDrawable(R.drawable.ic_context_menu_new_window, getContext().getTheme()),
                        "Open in a New Window")
        );
    }

}
