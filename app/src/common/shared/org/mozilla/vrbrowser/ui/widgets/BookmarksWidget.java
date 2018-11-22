/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.db.entity.BookmarkEntity;
import org.mozilla.vrbrowser.model.Bookmark;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkClickCallback;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.viewmodel.BookmarkListViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import static org.mozilla.gecko.GeckoAppShell.getApplicationContext;


public class BookmarksWidget extends UIWidget implements Application.ActivityLifecycleCallbacks,
        WidgetManagerDelegate.UpdateListener, TrayListener, GeckoSession.NavigationDelegate {

    private static final String ABOUT_BLANK = "about:blank";

    private BookmarksBinding mBinding;
    private BookmarkAdapter mBookmarkAdapter;
    private BookmarkListViewModel mBookmarkListModel;
    private Widget mBrowserWidget;
    private List<BookmarkListener> mBookmarkListeners;
    private AudioEngine mAudio;
    private int mSessionId;
    private int mPreviousSessionId;

    public BookmarksWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public BookmarksWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public BookmarksWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        mBookmarkListeners = new ArrayList<>();

        mAudio = AudioEngine.fromContext(aContext);

        ((Application)getApplicationContext()).registerActivityLifecycleCallbacks(this);
        mWidgetManager.addUpdateListener(this);
        SessionStore.get().addNavigationListener(this);

        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);

        mBookmarkAdapter = new BookmarkAdapter(mBookmarkClickCallback, aContext);
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);

        mBookmarkListModel = new BookmarkListViewModel(((Application)getApplicationContext()));
        subscribeUi(mBookmarkListModel.getBookmarks());
    }

    @Override
    public void setSize(float windowWidth, float windowHeight, float multiplier) {
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        float aspect = windowWidth / windowHeight;
        float worldHeight = worldWidth / aspect;
        float area = worldWidth * worldHeight * multiplier;

        float targetWidth = (float) Math.sqrt(area * aspect);
        float targetHeight = (float) Math.sqrt(area / aspect);

        handleResizeEvent(targetWidth, targetHeight);
    }

    @Override
    public void handleResizeEvent(float aWorldWidth, float aWorldHeight) {
        super.handleResizeEvent(aWorldWidth, aWorldHeight);

        mBrowserWidget.handleResizeEvent(aWorldWidth, aWorldHeight);
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

    @Override
    public void releaseWidget() {
        ((Application)getApplicationContext()).unregisterActivityLifecycleCallbacks(this);
        mWidgetManager.removeUpdateListener(this);
        SessionStore.get().removeNavigationListener(this);

        super.releaseWidget();
    }

    private final BookmarkClickCallback mBookmarkClickCallback = new BookmarkClickCallback() {
        @Override
        public void onClick(Bookmark bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            loadAndHide(bookmark.getUrl());
        }

        @Override
        public void onDelete(Bookmark bookmark) {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            mBookmarkListModel.deleteBookmark(bookmark);
        }
    };

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.visible = false;
        aPlacement.worldWidth =  WidgetPlacement.floatDimension(context, R.dimen.window_world_width);
        aPlacement.width = SettingsStore.getInstance(getContext()).getWindowWidth();
        aPlacement.height = SettingsStore.getInstance(getContext()).getWindowHeight();
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(context, R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.bookmarks_world_z);
    }

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

    public void setBrowserWidget(Widget widget) {
        mBrowserWidget = widget;
    }

    @Override
    public void show() {
        super.show();

        mPreviousSessionId = SessionStore.get().getCurrentSessionId();
        if (SessionStore.get().isCurrentSessionPrivate()) {
            mSessionId = SessionStore.get().createSession(true);

        } else {
            mSessionId = SessionStore.get().createSession();
        }
        SessionStore.get().stackSession(mSessionId);

        notifyBookmarksShown();

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
    }

    public void loadAndHide(String aURL) {
        SessionStore.get().loadUri(aURL);

        hide(UIWidget.REMOVE_WIDGET);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        notifyBookmarksHidden();

        mWidgetManager.popWorldBrightness(this);
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();

        if (SessionStore.get().getCurrentSessionId() == mSessionId) {
            if (SessionStore.get().canGoBack()) {
                SessionStore.get().goBack();

            } else if (SessionStore.get().canUnstackSession()) {
                SessionStore.get().unstackSession();
            }
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

    // UpdateListener

    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget != mBrowserWidget || !mBrowserWidget.isVisible()) {
            return;
        }

        mWidgetPlacement.worldWidth = aWidget.getPlacement().worldWidth;
        mWidgetPlacement.width = aWidget.getPlacement().width;
        mWidgetPlacement.height = aWidget.getPlacement().height;
        mWidgetManager.updateWidget(this);
    }

    // TrayListener

    @Override
    public void onBookmarksClicked() {
        if (isVisible()) {
            onDismiss();
            super.hide(UIWidget.REMOVE_WIDGET);

        } else {
            show();
        }
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        int sessionId = SessionStore.get().getSessionId(session);
        if ((mWidgetPlacement.visible &&
                url != null &&
                !url.equals(ABOUT_BLANK)) ||
                sessionId == mPreviousSessionId) {
            hide(UIWidget.REMOVE_WIDGET);
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
