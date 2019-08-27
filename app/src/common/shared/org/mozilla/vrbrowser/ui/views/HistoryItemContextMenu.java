/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.HistoryItemContextMenuBinding;
import org.mozilla.vrbrowser.ui.callbacks.HistoryItemContextMenuClickCallback;

import mozilla.components.concept.storage.VisitInfo;

public class HistoryItemContextMenu extends FrameLayout {

    private static final String LOGTAG = HistoryItemContextMenu.class.getSimpleName();

    private HistoryItemContextMenuBinding mBinding;

    public HistoryItemContextMenu(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public HistoryItemContextMenu(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public HistoryItemContextMenu(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.history_item_context_menu, this, true);
    }

    public void setItem(VisitInfo item) {
        SessionStore.get().getBookmarkStore().isBookmarked(item.getUrl()).thenAccept((isBookmarked -> {
            mBinding.setItem(item);
            mBinding.setIsBookmarked(isBookmarked);
            mBinding.bookmark.setText(isBookmarked ? R.string.history_context_remove_bookmarks : R.string.history_context_add_bookmarks);
            invalidate();

        })).exceptionally(throwable -> {
            Log.d(LOGTAG, "Couldn't get the bookmarked status of the history item");
            return null;
        });
    }

    public void setContextMenuClickCallback(HistoryItemContextMenuClickCallback callback) {
        mBinding.setCallback(callback);
    }

}
