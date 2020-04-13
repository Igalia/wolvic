/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.BookmarksBinding;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.adapters.Bookmark;
import org.mozilla.vrbrowser.ui.adapters.BookmarkAdapter;
import org.mozilla.vrbrowser.ui.adapters.CustomLinearLayoutManager;
import org.mozilla.vrbrowser.ui.callbacks.BookmarkItemCallback;
import org.mozilla.vrbrowser.ui.callbacks.BookmarksCallback;
import org.mozilla.vrbrowser.ui.callbacks.LibraryContextMenuCallback;
import org.mozilla.vrbrowser.ui.viewmodel.BookmarksViewModel;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.ui.widgets.menus.library.BookmarksContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.LibraryContextMenuWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import mozilla.appservices.places.BookmarkRoot;
import mozilla.components.concept.storage.BookmarkNode;
import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;
import mozilla.components.service.fxa.SyncEngine;
import mozilla.components.service.fxa.sync.SyncReason;
import mozilla.components.service.fxa.sync.SyncStatusObserver;

import static org.mozilla.vrbrowser.ui.widgets.settings.SettingsView.SettingViewType.FXA;

public class BookmarksView extends LibraryView implements BookmarksStore.BookmarkListener {

    private static final String LOGTAG = SystemUtils.createLogtag(BookmarksView.class);

    private static final boolean ACCOUNTS_UI_ENABLED = false;

    private BookmarksBinding mBinding;
    private Accounts mAccounts;
    private BookmarkAdapter mBookmarkAdapter;
    private CustomLinearLayoutManager mLayoutManager;
    private BookmarksViewModel mViewModel;

    public BookmarksView(Context aContext) {
        super(aContext);
        initialize();
    }

    public BookmarksView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public BookmarksView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();

        mAccounts = ((VRBrowserApplication)getContext().getApplicationContext()).getAccounts();
        if (ACCOUNTS_UI_ENABLED) {
            mAccounts.addAccountListener(mAccountListener);
            mAccounts.addSyncListener(mSyncListener);
        }

        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(BookmarksViewModel.class);

        SessionStore.get().getBookmarkStore().addListener(this);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.bookmarks, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setBookmarksViewModel(mViewModel);
        mBinding.setCallback(mBookmarksCallback);
        mBookmarkAdapter = new BookmarkAdapter(mBookmarkItemCallback, getContext());
        mBinding.bookmarksList.setAdapter(mBookmarkAdapter);
        mBinding.bookmarksList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.bookmarksList.addOnScrollListener(mScrollListener);
        mBinding.bookmarksList.setHasFixedSize(true);
        mBinding.bookmarksList.setItemViewCacheSize(20);
        mBinding.bookmarksList.setDrawingCacheEnabled(true);
        mBinding.bookmarksList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        mLayoutManager = (CustomLinearLayoutManager) mBinding.bookmarksList.getLayoutManager();

        mViewModel.setIsLoading(true);

        mBinding.setIsSignedIn(mAccounts.isSignedIn());
        boolean isSyncEnabled = mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE);
        mBinding.setIsSyncEnabled(isSyncEnabled);
        if (isSyncEnabled) {
            mBinding.setLastSync(mAccounts.lastSync());
            mBinding.setIsSyncing(mAccounts.isSyncing());
        }
        mViewModel.setIsNarrow(false);
        mBinding.setIsAccountsUIEnabled(ACCOUNTS_UI_ENABLED);
        mBinding.executePendingBindings();

        updateBookmarks();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onShow() {
        updateLayout();
    }

    @Override
    public void onDestroy() {
        SessionStore.get().getBookmarkStore().removeListener(this);

        mBinding.bookmarksList.removeOnScrollListener(mScrollListener);

        if (ACCOUNTS_UI_ENABLED) {
            mAccounts.removeAccountListener(mAccountListener);
            mAccounts.removeSyncListener(mSyncListener);
        }
    }

    private final BookmarkItemCallback mBookmarkItemCallback = new BookmarkItemCallback() {
        @Override
        public void onClick(@NonNull View view, @NonNull Bookmark item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getUrl());

            WindowWidget window = mWidgetManager.getFocusedWindow();
            window.hidePanel(Windows.PanelType.BOOKMARKS);
        }

        @Override
        public void onDelete(@NonNull View view, @NonNull Bookmark item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            SessionStore.get().getBookmarkStore().deleteBookmarkById(item.getGuid());
        }

        @Override
        public void onMore(@NonNull View view, @NonNull Bookmark item) {
            mBinding.bookmarksList.requestFocusFromTouch();

            int rowPosition = mBookmarkAdapter.getItemPosition(item.getGuid());
            RecyclerView.ViewHolder row = mBinding.bookmarksList.findViewHolderForLayoutPosition(rowPosition);
            boolean isLastVisibleItem = false;
            if (mBinding.bookmarksList.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) mBinding.bookmarksList.getLayoutManager();
                int lastItem = mBookmarkAdapter.getItemCount();
                if ((rowPosition == layoutManager.findLastVisibleItemPosition() || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition() ||
                        rowPosition == layoutManager.findLastVisibleItemPosition()-1 || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition()-1)
                        && rowPosition != lastItem) {
                    isLastVisibleItem = true;
                }
            }

            mBinding.getCallback().onShowContextMenu(
                    row.itemView,
                    item,
                    isLastVisibleItem);
        }

        @Override
        public void onFolderOpened(@NonNull Bookmark item) {
            int position = mBookmarkAdapter.getItemPosition(item.getGuid());
            mLayoutManager.scrollToPositionWithOffset(position, 20);
        }
    };

    private BookmarksCallback mBookmarksCallback = new BookmarksCallback() {

        @Override
        public void onSyncBookmarks(@NonNull View view) {
            view.requestFocusFromTouch();
            mAccounts.syncNowAsync(SyncReason.User.INSTANCE, false);
        }

        @Override
        public void onFxALogin(@NonNull View view) {
            view.requestFocusFromTouch();
            if (mAccounts.getAccountStatus() == Accounts.AccountStatus.SIGNED_IN) {
                mAccounts.logoutAsync();

            } else {
                CompletableFuture<String> result = mAccounts.authUrlAsync();
                if (result != null) {
                    result.thenAcceptAsync((url) -> {
                        if (url == null) {
                            mAccounts.logoutAsync();

                        } else {
                            mAccounts.setLoginOrigin(Accounts.LoginOrigin.BOOKMARKS);
                            WidgetManagerDelegate widgetManager = ((VRBrowserActivity) getContext());
                            widgetManager.openNewTabForeground(url);
                            widgetManager.getFocusedWindow().getSession().setUaMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
                            GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.FXA_LOGIN);

                            WindowWidget window = mWidgetManager.getFocusedWindow();
                            window.hidePanel(Windows.PanelType.BOOKMARKS);
                        }

                    }, mUIThreadExecutor).exceptionally(throwable -> {
                        Log.d(LOGTAG, "Error getting the authentication URL: " + throwable.getLocalizedMessage());
                        throwable.printStackTrace();
                        return null;
                    });
                }
            }
        }

        @Override
        public void onFxASynSettings(@NonNull View view) {
            view.requestFocusFromTouch();
            mWidgetManager.getTray().showSettingsDialog(FXA);
        }

        @Override
        public void onShowContextMenu(@NonNull View view, Bookmark item, boolean isLastVisibleItem) {
            showContextMenu(
                    view,
                    new BookmarksContextMenuWidget(getContext(),
                            new BookmarksContextMenuWidget.LibraryContextMenuItem(
                                    item.getUrl(),
                                    item.getTitle()),
                            mWidgetManager.canOpenNewWindow()),
                    mCallback,
                    isLastVisibleItem);
        }

        @Override
        public void onHideContextMenu(@NonNull View view) {
            hideContextMenu();
        }
    };

    private SyncStatusObserver mSyncListener = new SyncStatusObserver() {
        @Override
        public void onStarted() {
            updateSyncBindings(true);
        }

        @Override
        public void onIdle() {
            updateSyncBindings(false);

            // This shouldn't be necessary but for some reason the buttons stays hovered after the sync.
            // I guess Android restoring it to the latest state (hovered) before being disabled
            // Probably an Android bindings bug.
            mBinding.bookmarksNarrow.syncButton.setHovered(false);
            mBinding.bookmarksWide.syncButton.setHovered(false);
        }

        @Override
        public void onError(@Nullable Exception e) {
            updateSyncBindings(false);
        }
    };

    private void updateSyncBindings(boolean isSyncing) {
        boolean isSyncEnabled = mAccounts.isEngineEnabled(SyncEngine.Bookmarks.INSTANCE);
        mBinding.setIsSyncEnabled(isSyncEnabled);
        if (isSyncEnabled) {
            mBinding.setIsSyncing(isSyncing);
            mBinding.setLastSync(mAccounts.lastSync());
        }
        mBinding.executePendingBindings();
    }

    private AccountObserver mAccountListener = new AccountObserver() {

        @Override
        public void onAuthenticated(@NonNull OAuthAccount oAuthAccount, @NonNull AuthType authType) {
            mBinding.setIsSignedIn(true);
        }

        @Override
        public void onProfileUpdated(@NonNull Profile profile) {
        }

        @Override
        public void onLoggedOut() {
            mBinding.setIsSignedIn(false);
        }

        @Override
        public void onAuthenticationProblems() {
            mBinding.setIsSignedIn(false);
        }
    };

    private void updateBookmarks() {
        SessionStore.get().getBookmarkStore().getTree(BookmarkRoot.Root.getId(), true).
                thenAcceptAsync(this::showBookmarks, mUIThreadExecutor).
                exceptionally(throwable -> {
                    Log.d(LOGTAG, "Error getting bookmarks: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
        });
    }

    private void showBookmarks(List<BookmarkNode> aBookmarks) {
        if (aBookmarks == null || aBookmarks.size() == 0) {
            mViewModel.setIsEmpty(true);
            mViewModel.setIsLoading(false);

        } else {
            mViewModel.setIsEmpty(false);
            mViewModel.setIsLoading(false);
            mBookmarkAdapter.setBookmarkList(aBookmarks);
        }

        mBinding.executePendingBindings();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateLayout();
    }

    @Override
    protected void updateLayout() {
        post(() -> {
            double width = Math.ceil(getWidth()/getContext().getResources().getDisplayMetrics().density);
            boolean isNarrow = width < SettingsStore.WINDOW_WIDTH_DEFAULT;

            if (isNarrow != mViewModel.getIsNarrow().getValue().get()) {
                mBookmarkAdapter.setNarrow(isNarrow);

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                requestLayout();
            }
        });
    }

    private LibraryContextMenuCallback mCallback = new LibraryContextMenuCallback() {
        @Override
        public void onOpenInNewWindowClick(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            mWidgetManager.openNewWindow(item.getUrl());
            hideContextMenu();
        }

        @Override
        public void onOpenInNewTabClick(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            mWidgetManager.openNewTabForeground(item.getUrl());
            GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.BOOKMARKS);
            hideContextMenu();
        }
    };

    // BookmarksStore.BookmarksViewListener

    @Override
    public void onBookmarksUpdated() {
        updateBookmarks();
    }

    @Override
    public void onBookmarkAdded() {
        updateBookmarks();
    }
}
