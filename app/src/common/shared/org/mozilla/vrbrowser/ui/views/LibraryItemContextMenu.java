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

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.LibraryItemContextMenuBinding;
import org.mozilla.vrbrowser.ui.callbacks.LibraryItemContextMenuClickCallback;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;

public class LibraryItemContextMenu extends FrameLayout {

    private static final String LOGTAG = LibraryItemContextMenu.class.getSimpleName();

    public enum LibraryItemType {
        BOOKMARKS,
        HISTORY
    }

    public static class LibraryContextMenuItem {

        private String url;
        private String title;
        private LibraryItemType type;

        public LibraryContextMenuItem(@NonNull String url, String title, LibraryItemType type) {
            this.url = url;
            this.title = title;
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public LibraryItemType getType() {
            return type;
        }

    }

    private LibraryItemContextMenuBinding mBinding;

    public LibraryItemContextMenu(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public LibraryItemContextMenu(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public LibraryItemContextMenu(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.library_item_context_menu, this, true);
    }

    public void setItem(@NonNull LibraryContextMenuItem item) {
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

    public void setContextMenuClickCallback(LibraryItemContextMenuClickCallback callback) {
        mBinding.setCallback(callback);
    }

    public int getMenuHeight() {
        switch (mBinding.getItem().getType()) {
            case BOOKMARKS:
                mBinding.bookmarkLayout.setVisibility(GONE);
                mBinding.newWindowLayout.setBackgroundResource(R.drawable.library_context_menu_item_background_single);
                return WidgetPlacement.dpDimension(getContext(), R.dimen.library_item_row_height);
            case HISTORY:
                mBinding.bookmarkLayout.setVisibility(VISIBLE);
                mBinding.newWindowLayout.setBackgroundResource(R.drawable.library_context_menu_item_background_top);
                return WidgetPlacement.dpDimension(getContext(), R.dimen.library_item_row_height) * 2;
        }

        return WidgetPlacement.dpDimension(getContext(), R.dimen.library_item_row_height) * 2;
    }

}
