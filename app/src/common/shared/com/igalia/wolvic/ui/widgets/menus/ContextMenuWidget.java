/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.menus;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.webkit.URLUtil;

import androidx.annotation.StringRes;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.downloads.DownloadJob;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.StringUtils;

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

    public boolean hasActions() {
        return mItems.stream().anyMatch(menuItem -> menuItem.mCallback != null);
    }

    public void setDismissCallback(Runnable aCallback) {
        mDismissCallback = aCallback;
    }

    public void setContextElement(WSession.ContentDelegate.ContextElement aContextElement) {
        mItems = new ArrayList<>();
        final WidgetManagerDelegate widgetManager = mWidgetManager;
        if (aContextElement.linkUri != null && !aContextElement.linkUri.isEmpty()) {
            // Link url
            mItems.add(new MenuWidget.MenuItem(aContextElement.linkUri, 0, null));
            // Open link in a new window
            if (mWidgetManager.canOpenNewWindow()) {
                mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_link_new_window_1), 0, () -> {
                    if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                        widgetManager.openNewWindow(aContextElement.linkUri);
                    }
                    onDismiss();
                }));
            }
            // Open link in a new tab
            mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_open_link_new_tab_1), 0, () -> {
                if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                    widgetManager.openNewTabForeground(aContextElement.linkUri);
                    TelemetryService.Tabs.openedCounter(TelemetryService.Tabs.TabSource.CONTEXT_MENU);
                }
                onDismiss();
            }));
            // Download link
            if (!StringUtils.isEmpty(aContextElement.linkUri)) {
                mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_download_link), 0, () -> {
                    DownloadJob job = DownloadJob.fromLink(aContextElement);
                    widgetManager.getFocusedWindow().startDownload(job, false);
                    // TODO Add Download from context menu Telemetry
                    onDismiss();
                }));
            }
            // Copy link uri
            mItems.add(new MenuWidget.MenuItem(getContext().getString(R.string.context_menu_copy_link), 0, () -> {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
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
                        label = uri.toString();
                    }
                    // The clip data contains the URI in two formats: as an URI and as plain text.
                    ClipData clip = new ClipData(label,
                            new String[]{ClipDescription.MIMETYPE_TEXT_URILIST, ClipDescription.MIMETYPE_TEXT_PLAIN},
                            new ClipData.Item(uri));
                    clip.addItem(new ClipData.Item(uri.toString()));
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                    }
                }
                onDismiss();
            }));

        } else {
            // If there is no link, show src uri instead
            mItems.add(new MenuWidget.MenuItem(aContextElement.srcUri, 0, null));
        }

        if (URLUtil.isNetworkUrl(aContextElement.srcUri) && aContextElement.type != WSession.ContentDelegate.ContextElement.TYPE_NONE) {
            @StringRes int copyText = R.string.context_menu_copy_image_location;
            @StringRes int srcText = R.string.context_menu_download_image;
            @StringRes int viewText = R.string.context_menu_view_image;
            if (aContextElement.type == WSession.ContentDelegate.ContextElement.TYPE_VIDEO) {
                srcText = R.string.context_menu_download_video;
                copyText = R.string.context_menu_copy_video_location;
                viewText = R.string.context_menu_view_video;

            } else if(aContextElement.type == WSession.ContentDelegate.ContextElement.TYPE_AUDIO) {
                srcText = R.string.context_menu_download_audio;
                copyText = R.string.context_menu_copy_audio_location;
                viewText = R.string.context_menu_view_audio;
            }
            // View src
            if (aContextElement.baseUri != null && !aContextElement.baseUri.equals(aContextElement.srcUri)) {
                mItems.add(new MenuWidget.MenuItem(getContext().getString(viewText), 0, () -> {
                    widgetManager.getFocusedWindow().getSession().loadUri(aContextElement.srcUri);
                    onDismiss();
                }));
            }
            // Download src
            mItems.add(new MenuWidget.MenuItem(getContext().getString(srcText), 0, () -> {
                DownloadJob job = DownloadJob.fromSrc(aContextElement);
                widgetManager.getFocusedWindow().startDownload(job, false);
                // TODO Add Download from context menu Telemetry
                onDismiss();
            }));
            // Copy src uri
            mItems.add(new MenuWidget.MenuItem(getContext().getString(copyText), 0, () -> {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                Uri uri = Uri.parse(aContextElement.srcUri);
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
        }
        updateMenuItems(mItems);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.context_menu_row_height);
        mWidgetPlacement.height += mBorderWidth * 2;
        mWidgetPlacement.height += 10.0f; // Link separator
    }
}
