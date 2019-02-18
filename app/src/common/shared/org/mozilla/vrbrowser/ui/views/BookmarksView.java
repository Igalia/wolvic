/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
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
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.db.entity.BookmarkEntity;
import org.mozilla.vrbrowser.model.Bookmark;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkClickCallback;
import org.mozilla.vrbrowser.ui.widgets.BookmarkListener;
import org.mozilla.vrbrowser.viewmodel.BookmarkListViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

public class BookmarksView extends FrameLayout implements Application.ActivityLifecycleCallbacks,
        GeckoSession.NavigationDelegate {

    private static final String ABOUT_BLANK = "about:blank";

    private BookmarksBinding mBinding;
    private BookmarkAdapter mBookmarkAdapter;
    private BookmarkListViewModel mBookmarkListModel;
    private List<BookmarkListener> mBookmarkListeners;
    private AudioEngine mAudio;

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

        ((Application)getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        SessionStore.get().addNavigationListener(this);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);

        mBookmarkAdapter = new BookmarkAdapter(mBookmarkClickCallback, aContext);
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);

        mBookmarkListModel = new BookmarkListViewModel(((Application)getContext().getApplicationContext()));
        subscribeUi(mBookmarkListModel.getBookmarks());

        setVisibility(GONE);
    }

    public void addListeners(BookmarkListener... listeners) {
        mBookmarkListeners.addAll(Arrays.asList(listeners));
    }

    public void removeAllListeners() {
        mBookmarkListeners.clear();
    }

    private void notifyBookmarksShown() {
        mBookmarkListeners.forEach(bookmarkListener -> bookmarkListener.onBookmarksShown());
    }

    private void notifyBookmarksHidden() {
        mBookmarkListeners.forEach(bookmarkListener -> bookmarkListener.onBookmarksHidden());
    }

    private final BookmarkClickCallback mBookmarkClickCallback = new BookmarkClickCallback() {
        @Override
        public void onClick(Bookmark bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            SessionStore.get().loadUri(bookmark.getUrl());
        }

        @Override
        public void onDelete(Bookmark bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mBookmarkListModel.deleteBookmark(bookmark);
        }
    };

    private void subscribeUi(LiveData<List<BookmarkEntity>> liveData) {
        // Update the list when the data changes
        liveData.observeForever(mBookmarkListObserver);
    }

    private void unsubscribeUi(LiveData<List<BookmarkEntity>> liveData) {
        // Update the list when the data changes
        liveData.removeObserver(mBookmarkListObserver);
    }

    private Observer<List<BookmarkEntity>> mBookmarkListObserver = new Observer<List<BookmarkEntity>>() {

        @Override
        public void onChanged(List<BookmarkEntity> bookmarkEntities) {
            if (bookmarkEntities != null) {
                if (bookmarkEntities.size() == 0) {
                    mBinding.setIsEmpty(true);
                    mBinding.setIsLoading(false);

                } else {
                    mBinding.setIsEmpty(false);
                    mBinding.setIsLoading(false);
                    mBookmarkAdapter.setBookmarkList(bookmarkEntities);
                }

            } else {
                mBinding.setIsLoading(true);
            }
            
            mBinding.executePendingBindings();
        }
    };

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (visibility == VISIBLE) {
            notifyBookmarksShown();

        } else {
            notifyBookmarksHidden();
        }
    }

    // ActivityLifecycleCallbacks

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {
        subscribeUi(mBookmarkListModel.getBookmarks());
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        unsubscribeUi(mBookmarkListModel.getBookmarks());
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

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
}
