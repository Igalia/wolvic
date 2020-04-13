/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets.menus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;

import androidx.annotation.StringRes;

import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.downloads.DownloadJob;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.ArrayList;

public class ContextMenuWidget extends MenuWidget {
    ArrayList<MenuItem> mItems;
    private Runnable mDismissCallback;

    public ContextMenuWidget(Context aContext) {
        super(aContext, R.layout.menu);
        initialize();
    }

    private void initialize() {
        updateUI();
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
    public void updateUI() {
        super.updateUI();

        mAdapter.updateBackgrounds(R.drawable.context_menu_item_background_first,
                R.drawable.context_menu_item_background_last,
                R.drawable.context_menu_item_background,
                R.drawable.context_menu_item_background_single);
        mAdapter.updateLayoutId(R.layout.context_menu_item);
        menuContainer.setBackground(getContext().getDrawable(R.drawable.context_menu_background));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
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

    public void setContextElement(ContextElement aContextElement) {
        mItems = new ArrayList<>();
        mItems.add(new MenuWidget.MenuItem(aContextElement.linkUri, 0, null));
        final WidgetManagerDelegate widgetManager = mWidgetManager;
        if (mWidgetManager.canOpenNewWindow()) {
            mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_new_window_1), 0, () -> {
                if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                    widgetManager.openNewWindow(aContextElement.linkUri);
                }
                onDismiss();
            }));
        }
        mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_new_tab_1), 0, () -> {
            if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                widgetManager.openNewTab(aContextElement.linkUri);
                GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.CONTEXT_MENU);
            }
            onDismiss();
        }));
        if (!StringUtils.isEmpty(aContextElement.linkUri)) {
            mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_download_link), 0, () -> {
                DownloadJob job = DownloadJob.fromLink(aContextElement);
                widgetManager.getFocusedWindow().startDownload(job, false);
                // TODO Add Download from context menu Telemetry
                onDismiss();
            }));
        }
        if (!StringUtils.isEmpty(aContextElement.srcUri)) {
            @StringRes int srcText;
            switch (aContextElement.type) {
                case ContextElement.TYPE_IMAGE:
                    srcText = R.string.context_menu_download_image;
                    break;
                case ContextElement.TYPE_VIDEO:
                    srcText = R.string.context_menu_download_video;
                    break;
                case ContextElement.TYPE_AUDIO:
                    srcText = R.string.context_menu_download_audio;
                    break;
                default:
                    srcText = R.string.context_menu_download_link;
                    break;
            }
            mItems.add(new MenuWidget.MenuItem(getContext().getString(srcText), 0, () -> {
                DownloadJob job = DownloadJob.fromSrc(aContextElement);
                widgetManager.getFocusedWindow().startDownload(job, false);
                // TODO Add Download from context menu Telemetry
                onDismiss();
            }));
        }
        mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_copy_link), 0, () -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri;
            if (aContextElement.linkUri != null) {
                uri = Uri.parse(aContextElement.linkUri);

            } else {
                uri = Uri.parse(aContextElement.srcUri);
            }
            if (uri != null) {
                String label = aContextElement.title;
                if (StringUtils.isEmpty(label)) {
                    label = aContextElement.altText;
                }
                if (StringUtils.isEmpty(label)) {
                    label = aContextElement.altText;
                }
                if (StringUtils.isEmpty(label)) {
                    label = uri.toString();
                }
                ClipData clip = ClipData.newRawUri(label, uri);
                if (clipboard != null) {
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

}
