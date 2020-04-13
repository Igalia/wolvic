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
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.HistoryBinding;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.adapters.HistoryAdapter;
import org.mozilla.vrbrowser.ui.callbacks.HistoryCallback;
import org.mozilla.vrbrowser.ui.callbacks.HistoryContextMenuCallback;
import org.mozilla.vrbrowser.ui.callbacks.HistoryItemCallback;
import org.mozilla.vrbrowser.ui.viewmodel.HistoryViewModel;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WindowWidget;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.ui.widgets.dialogs.ClearHistoryDialogWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.HistoryContextMenuWidget;
import org.mozilla.vrbrowser.ui.widgets.menus.library.LibraryContextMenuWidget;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import mozilla.components.concept.storage.VisitInfo;
import mozilla.components.concept.storage.VisitType;
import mozilla.components.concept.sync.AccountObserver;
import mozilla.components.concept.sync.AuthType;
import mozilla.components.concept.sync.OAuthAccount;
import mozilla.components.concept.sync.Profile;
import mozilla.components.service.fxa.SyncEngine;
import mozilla.components.service.fxa.sync.SyncReason;
import mozilla.components.service.fxa.sync.SyncStatusObserver;

import static org.mozilla.vrbrowser.ui.widgets.settings.SettingsView.SettingViewType.FXA;

public class HistoryView extends LibraryView implements HistoryStore.HistoryListener {

    private static final String LOGTAG = SystemUtils.createLogtag(HistoryView.class);

    private static final boolean ACCOUNTS_UI_ENABLED = false;

    private HistoryBinding mBinding;
    private Accounts mAccounts;
    private HistoryAdapter mHistoryAdapter;
    private ClearHistoryDialogWidget mClearHistoryDialog;
    private HistoryViewModel mViewModel;

    public HistoryView(Context aContext) {
        super(aContext);
        initialize();
    }

    public HistoryView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize();
    }

    public HistoryView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize();
    }

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
                .get(HistoryViewModel.class);

        SessionStore.get().getHistoryStore().addListener(this);

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.history, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setHistoryViewModel(mViewModel);
        mBinding.setCallback(mHistoryCallback);
        mHistoryAdapter = new HistoryAdapter(mHistoryItemCallback, getContext());
        mBinding.historyList.setAdapter(mHistoryAdapter);
        mBinding.historyList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.historyList.addOnScrollListener(mScrollListener);
        mBinding.historyList.setHasFixedSize(true);
        mBinding.historyList.setItemViewCacheSize(20);
        mBinding.historyList.setDrawingCacheEnabled(true);
        mBinding.historyList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        mViewModel.setIsLoading(true);

        mBinding.setIsSignedIn(mAccounts.isSignedIn());
        boolean isSyncEnabled = mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE);
        mBinding.setIsSyncEnabled(isSyncEnabled);
        if (isSyncEnabled) {
            mBinding.setLastSync(mAccounts.lastSync());
            mBinding.setIsSyncing(mAccounts.isSyncing());
        }
        mViewModel.setIsNarrow(false);
        mBinding.setIsAccountsUIEnabled(ACCOUNTS_UI_ENABLED);
        mBinding.executePendingBindings();

        updateHistory();

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    @Override
    public void onDestroy() {
        SessionStore.get().getHistoryStore().removeListener(this);

        mBinding.historyList.removeOnScrollListener(mScrollListener);

        if (ACCOUNTS_UI_ENABLED) {
            mAccounts.removeAccountListener(mAccountListener);
            mAccounts.removeSyncListener(mSyncListener);
        }
    }

    @Override
    public void onShow() {
        updateLayout();
    }

    private final HistoryItemCallback mHistoryItemCallback = new HistoryItemCallback() {
        @Override
        public void onClick(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getUrl());

            WindowWidget window = mWidgetManager.getFocusedWindow();
            window.hidePanel(Windows.PanelType.HISTORY);
        }

        @Override
        public void onDelete(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            SessionStore.get().getHistoryStore().deleteVisitsFor(item.getUrl());
        }

        @Override
        public void onMore(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            int rowPosition = mHistoryAdapter.getItemPosition(item.getVisitTime());
            RecyclerView.ViewHolder row = mBinding.historyList.findViewHolderForLayoutPosition(rowPosition);
            boolean isLastVisibleItem = false;
            if (mBinding.historyList.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) mBinding.historyList.getLayoutManager();
                int lastItem = mHistoryAdapter.getItemCount();
                if ((rowPosition == layoutManager.findLastVisibleItemPosition() || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition() ||
                        rowPosition == layoutManager.findLastVisibleItemPosition()-1 || rowPosition == layoutManager.findLastCompletelyVisibleItemPosition()-1)
                        && rowPosition != lastItem) {
                    isLastVisibleItem = true;
                }
            }

            if (row != null) {
                mBinding.getCallback().onShowContextMenu(
                        row.itemView,
                        item,
                        isLastVisibleItem);
            }
        }
    };

    private HistoryCallback mHistoryCallback = new HistoryCallback() {
        @Override
        public void onClearHistory(@NonNull View view) {
            view.requestFocusFromTouch();
            showClearCacheDialog();
            hideContextMenu();
        }

        @Override
        public void onSyncHistory(@NonNull View view) {
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
                            mAccounts.setLoginOrigin(Accounts.LoginOrigin.HISTORY);
                            mWidgetManager.openNewTabForeground(url);
                            mWidgetManager.getFocusedWindow().getSession().setUaMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
                            GleanMetricsService.Tabs.openedCounter(GleanMetricsService.Tabs.TabSource.FXA_LOGIN);

                            WindowWidget window = mWidgetManager.getFocusedWindow();
                            window.hidePanel(Windows.PanelType.HISTORY);
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
        public void onShowContextMenu(@NonNull View view, @NonNull VisitInfo item, boolean isLastVisibleItem) {
            SessionStore.get().getBookmarkStore().isBookmarked(item.getUrl()).thenAcceptAsync((isBookmarked -> {
                showContextMenu(
                        view,
                        new HistoryContextMenuWidget(getContext(),
                                new HistoryContextMenuWidget.LibraryContextMenuItem(
                                        item.getUrl(),
                                        item.getTitle()),
                                mWidgetManager.canOpenNewWindow(),
                                isBookmarked),
                        mCallback,
                        isLastVisibleItem);

            }), mUIThreadExecutor).exceptionally(throwable -> {
                Log.d(LOGTAG, "Error getting the bookmarked status: " + throwable.getLocalizedMessage());
                throwable.printStackTrace();
                return null;
            });
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
            mBinding.historyNarrow.syncButton.setHovered(false);
            mBinding.historyWide.syncButton.setHovered(false);
        }

        @Override
        public void onError(@Nullable Exception e) {
            updateSyncBindings(false);
        }
    };

    private void updateSyncBindings(boolean isSyncing) {
        boolean isSyncEnabled = mAccounts.isEngineEnabled(SyncEngine.History.INSTANCE);
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

    @NonNull
    public static <T> Predicate<T> distinctByUrl(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private void updateHistory() {
        Calendar date = new GregorianCalendar();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        long todayLimit = date.getTimeInMillis();
        long yesterdayLimit = todayLimit - SystemUtils.ONE_DAY_MILLIS;
        long oneWeekLimit = todayLimit - SystemUtils.ONE_WEEK_MILLIS;

        SessionStore.get().getHistoryStore().getDetailedHistory().thenAcceptAsync((items) -> {
            List<VisitInfo> orderedItems = items.stream()
                    .sorted(Comparator.comparing(VisitInfo::getVisitTime)
                    .reversed())
                    .filter(distinctByUrl(VisitInfo::getUrl))
                    .collect(Collectors.toList());

            addSection(orderedItems, getResources().getString(R.string.history_section_today), Long.MAX_VALUE, todayLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_yesterday), todayLimit, yesterdayLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_last_week), yesterdayLimit, oneWeekLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_older), oneWeekLimit, 0);

            showHistory(orderedItems);

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, "Error getting history: " + throwable.getLocalizedMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    private void addSection(final @NonNull List<VisitInfo> items, @NonNull String section, long rangeStart, long rangeEnd) {
        for (int i=0; i< items.size(); i++) {
            if (items.get(i).getVisitTime() == rangeStart && items.get(i).getVisitType() == VisitType.NOT_A_VISIT)
                break;

            if (items.get(i).getVisitTime() < rangeStart && items.get(i).getVisitTime() > rangeEnd) {
                items.add(i, new VisitInfo(
                        section,
                        section,
                        rangeStart,
                        VisitType.NOT_A_VISIT
                ));
                break;
            }
        }
    }

    private void showHistory(List<VisitInfo> historyItems) {
        if (historyItems == null || historyItems.size() == 0) {
            mViewModel.setIsEmpty(true);
            mViewModel.setIsLoading(false);

        } else {
            mViewModel.setIsEmpty(false);
            mViewModel.setIsLoading(false);
            mHistoryAdapter.setHistoryList(historyItems);
        }

        mBinding.executePendingBindings();
    }

    @Override
    protected void updateLayout() {
        post(() -> {
            double width = Math.ceil(getWidth()/getContext().getResources().getDisplayMetrics().density);
            boolean isNarrow = width < SettingsStore.WINDOW_WIDTH_DEFAULT;

            if (isNarrow != mViewModel.getIsNarrow().getValue().get()) {
                mHistoryAdapter.setNarrow(isNarrow);

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                mViewModel.setIsNarrow(isNarrow);
                mBinding.executePendingBindings();

                requestLayout();
            }
        });
    }

    private HistoryContextMenuCallback mCallback = new HistoryContextMenuCallback() {
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

        @Override
        public void onAddToBookmarks(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            SessionStore.get().getBookmarkStore().addBookmark(item.getUrl(), item.getTitle());
            hideContextMenu();
        }

        @Override
        public void onRemoveFromBookmarks(LibraryContextMenuWidget.LibraryContextMenuItem item) {
            SessionStore.get().getBookmarkStore().deleteBookmarkByURL(item.getUrl());
            hideContextMenu();
        }
    };

    public void showClearCacheDialog() {
        if (mClearHistoryDialog == null) {
            mClearHistoryDialog = new ClearHistoryDialogWidget(getContext());
        }
        mClearHistoryDialog.show(UIWidget.REQUEST_FOCUS);
    }

    // HistoryStore.HistoryListener

    @Override
    public void onHistoryUpdated() {
        updateHistory();
    }
}
