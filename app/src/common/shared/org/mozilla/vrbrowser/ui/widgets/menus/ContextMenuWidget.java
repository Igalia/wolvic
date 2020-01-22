/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.menus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.view.View;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.StringUtils;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;

public class ContextMenuWidget extends MenuWidget implements WidgetManagerDelegate.FocusChangeListener {
    ArrayList<MenuItem> mItems;
    private Runnable mDismissCallback;

    public ContextMenuWidget(Context aContext) {
        super(aContext, R.layout.menu);
        initialize();
    }

    private void initialize() {
        mAdapter.updateBackgrounds(R.drawable.context_menu_item_background_first,
                R.drawable.context_menu_item_background_last,
                R.drawable.context_menu_item_background,
                R.drawable.context_menu_item_background_single);
        mAdapter.updateLayoutId(R.layout.context_menu_item);
        menuContainer.setBackground(getContext().getDrawable(R.drawable.context_menu_background));
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.context_menu_row_width) + mBorderWidth * 2;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetManager.addFocusChangeListener(ContextMenuWidget.this);
        super.show(aShowFlags);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.removeFocusChangeListener(this);
    }

    @Override
    protected void onDismiss() {
        if (mDismissCallback != null) {
            mDismissCallback.run();
        }
    }

    public void setDismissCallback(Runnable aCallback) {
        mDismissCallback = aCallback;
    }

    public void setContextElement(GeckoSession.ContentDelegate.ContextElement aContextElement) {
        mItems = new ArrayList<>();
        mItems.add(new MenuWidget.MenuItem(aContextElement.linkUri, 0, null));
        if (mWidgetManager.canOpenNewWindow()) {
            mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_new_window_1), 0, () -> {
                if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                    mWidgetManager.openNewWindow(aContextElement.linkUri);
                }
                onDismiss();
            }));
        }
        mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_new_tab_1), 0, () -> {
            if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                mWidgetManager.openNewTab(aContextElement.linkUri);
                GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.CONTEXT_MENU);
            }
            onDismiss();
        }));
        mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_copy_link), 0, () -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (aContextElement.linkUri != null) {
                Uri uri = Uri.parse(aContextElement.linkUri);
                if (uri != null) {
                    String label = aContextElement.title;
                    if (StringUtils.isEmpty(label)) {
                        label = aContextElement.altText;
                    }
                    if (StringUtils.isEmpty(label)) {
                        label = aContextElement.altText;
                    }
                    if (StringUtils.isEmpty(label)) {
                        label = aContextElement.linkUri;
                    }
                    ClipData clip = ClipData.newRawUri(label, uri);
                    clipboard.setPrimaryClip(clip);
                }
            }
            onDismiss();
        }));
        updateMenuItems(mItems);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.context_menu_row_height);
        mWidgetPlacement.height += mBorderWidth * 2;
        mWidgetPlacement.height += 10.0f; // Link separator
    }

    // WidgetManagerDelegate.FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus) && isVisible()) {
            onDismiss();
        }
    }

}
