/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.engine.SessionManager;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkClickCallback;
import org.mozilla.vrbrowser.ui.widgets.BookmarkListener;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import mozilla.components.concept.storage.BookmarkNode;

public class BookmarksView extends FrameLayout implements GeckoSession.NavigationDelegate, BookmarksStore.BookmarkListener {

    private static final String ABOUT_BLANK = "about:blank";

    private BookmarksBinding mBinding;
    private BookmarkAdapter mBookmarkAdapter;
    private List<BookmarkListener> mBookmarkListeners;
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
        mBookmarkListeners = new ArrayList<>();

        mAudio = AudioEngine.fromContext(aContext);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);
        mBookmarkAdapter = new BookmarkAdapter(mBookmarkClickCallback, aContext);
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);
        mBinding.setIsLoading(true);
        mBinding.executePendingBindings();
        syncBookmarks();
        SessionManager.get().getBookmarkStore().addListener(this);

        setVisibility(GONE);
    }

    public void addListeners(BookmarkListener... listeners) {
        mBookmarkListeners.addAll(Arrays.asList(listeners));
    }

    public void removeListeners(BookmarkListener... listeners) {
        mBookmarkListeners.removeAll(Arrays.asList(listeners));
    }

    public void onDestroy() {
        mBookmarkListeners.clear();
        SessionManager.get().getBookmarkStore().removeListener(this);
    }

    private void notifyBookmarksShown() {
        mBookmarkListeners.forEach(BookmarkListener::onBookmarksShown);
    }

    private void notifyBookmarksHidden() {
        mBookmarkListeners.forEach(BookmarkListener::onBookmarksHidden);
    }

    private final BookmarkClickCallback mBookmarkClickCallback = new BookmarkClickCallback() {
        @Override
        public void onClick(BookmarkNode bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            SessionStore sessionStore = SessionManager.get().getActiveStore();
            sessionStore.loadUri(bookmark.getUrl());
        }

        @Override
        public void onDelete(BookmarkNode bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mIgnoreNextListener = true;
            SessionManager.get().getBookmarkStore().deleteBookmarkById(bookmark.getGuid());
            mBookmarkAdapter.removeItem(bookmark);
            if (mBookmarkAdapter.itemCount() == 0) {
                mBinding.setIsEmpty(true);
                mBinding.setIsLoading(false);
                mBinding.executePendingBindings();
            }
        }
    };


    private void syncBookmarks() {
        SessionManager.get().getBookmarkStore().getBookmarks().thenAcceptAsync(this::showBookmarks, new UIThreadExecutor());
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

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        SessionStore sessionStore = SessionManager.get().getActiveStore();
        if (visibility == VISIBLE) {
            if (sessionStore != null)
                sessionStore.addNavigationListener(this);
            notifyBookmarksShown();

        } else {
            if (sessionStore != null)
                sessionStore.removeNavigationListener(this);
            notifyBookmarksHidden();
        }
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (getVisibility() == View.VISIBLE &&
                url != null &&
                !url.equals(ABOUT_BLANK)) {
            notifyBookmarksHidden();
        }
    }

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {

    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {

    }

    @Nullable
    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession session, @NonNull LoadRequest request) {
        return GeckoResult.ALLOW;
    }

    @Nullable
    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
        return null;
    }

    @Override
    public GeckoResult<String> onLoadError(GeckoSession session, String uri, WebRequestError error) {
        return null;
    }

    // BookmarksStore.BookmarkListener
    @Override
    public void onBookmarksUpdated() {
        if (mIgnoreNextListener) {
            mIgnoreNextListener = false;
            return;
        }
        syncBookmarks();
    }
}
