/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkItemCallback;
import org.mozilla.vrbrowser.ui.callbacks.BookmarksCallback;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;

import java.util.List;

import mozilla.components.concept.storage.BookmarkNode;

public class BookmarksView extends FrameLayout implements BookmarksStore.BookmarkListener {

    private BookmarksBinding mBinding;
    private BookmarkAdapter mBookmarkAdapter;
    private AudioEngine mAudio;
    private boolean mIgnoreNextListener;

    public BookmarksView(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public BookmarksView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public BookmarksView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);
        mBookmarkAdapter = new BookmarkAdapter(mBookmarkItemCallback, aContext);
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);
        mBinding.bookmarksList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.setIsLoading(true);
        mBinding.executePendingBindings();
        syncBookmarks();
        SessionStore.get().getBookmarkStore().addListener(this);

        setVisibility(GONE);

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    public void onDestroy() {
        SessionStore.get().getBookmarkStore().removeListener(this);
    }

    private final BookmarkItemCallback mBookmarkItemCallback = new BookmarkItemCallback() {
        @Override
        public void onClick(View view, BookmarkNode item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getUrl());
        }

        @Override
        public void onDelete(View view, BookmarkNode item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            mIgnoreNextListener = true;
            SessionStore.get().getBookmarkStore().deleteBookmarkById(item.getGuid());
            mBookmarkAdapter.removeItem(item);
            if (mBookmarkAdapter.itemCount() == 0) {
                mBinding.setIsEmpty(true);
                mBinding.setIsLoading(false);
                mBinding.executePendingBindings();
            }
        }

        @Override
        public void onMore(View view, BookmarkNode item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            int rowPosition = mBookmarkAdapter.getItemPosition(item.getGuid());
            RecyclerView.ViewHolder row = mBinding.bookmarksList.findViewHolderForLayoutPosition(rowPosition);
            boolean isLastVisibleItem = false;
            if (mBinding.bookmarksList.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) mBinding.bookmarksList.getLayoutManager();
                int lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition();
                if (rowPosition == layoutManager.findLastVisibleItemPosition() && rowPosition != lastVisibleItem) {
                    isLastVisibleItem = true;
                }
            }

            mBinding.getCallback().onShowContextMenu(
                    row.itemView,
                    item,
                    isLastVisibleItem);
        }
    };

    public void setBookmarksCallback(@NonNull BookmarksCallback callback) {
        mBinding.setCallback(callback);
    }

    private void syncBookmarks() {
        SessionStore.get().getBookmarkStore().getBookmarks().thenAcceptAsync(this::showBookmarks, new UIThreadExecutor());
    }

    private void showBookmarks(List<BookmarkNode> aBookmarks) {
        if (aBookmarks == null || aBookmarks.size() == 0) {
            mBinding.setIsEmpty(true);
            mBinding.setIsLoading(false);

        } else {
            mBinding.setIsEmpty(false);
            mBinding.setIsLoading(false);
            mBookmarkAdapter.setBookmarkList(aBookmarks);
            mBinding.bookmarksList.post(() -> mBinding.bookmarksList.smoothScrollToPosition(
                    mBookmarkAdapter.getItemCount() > 0 ? mBookmarkAdapter.getItemCount() - 1 : 0));
        }
        mBinding.executePendingBindings();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        double width = Math.ceil(getWidth()/getContext().getResources().getDisplayMetrics().density);
        mBookmarkAdapter.setNarrow(width < SettingsStore.WINDOW_WIDTH_DEFAULT);
    }

    // BookmarksStore.BookmarksViewListener
    @Override
    public void onBookmarksUpdated() {
        if (mIgnoreNextListener) {
            mIgnoreNextListener = false;
            return;
        }
        syncBookmarks();
    }

    @Override
    public void onBookmarkAdded() {
        if (mIgnoreNextListener) {
            mIgnoreNextListener = false;
            return;
        }
        syncBookmarks();
    }
}
