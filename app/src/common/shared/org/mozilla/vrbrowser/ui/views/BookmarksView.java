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
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkClickCallback;
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

    private void initialize(Context aContext) {
        mAudio = AudioEngine.fromContext(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);
        mBookmarkAdapter = new BookmarkAdapter(mBookmarkClickCallback, aContext);
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);
        mBinding.setIsLoading(true);
        mBinding.executePendingBindings();
        syncBookmarks();
        SessionStore.get().getBookmarkStore().addListener(this);

        setVisibility(GONE);
    }

    public void onDestroy() {
        SessionStore.get().getBookmarkStore().removeListener(this);
    }

    private final BookmarkClickCallback mBookmarkClickCallback = new BookmarkClickCallback() {
        @Override
        public void onClick(BookmarkNode bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            SessionStack sessionStack = SessionStore.get().getActiveStore();
            sessionStack.loadUri(bookmark.getUrl());
        }

        @Override
        public void onDelete(BookmarkNode bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mIgnoreNextListener = true;
            SessionStore.get().getBookmarkStore().deleteBookmarkById(bookmark.getGuid());
            mBookmarkAdapter.removeItem(bookmark);
            if (mBookmarkAdapter.itemCount() == 0) {
                mBinding.setIsEmpty(true);
                mBinding.setIsLoading(false);
                mBinding.executePendingBindings();
            }
        }
    };


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
        }
        mBinding.executePendingBindings();
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
}
