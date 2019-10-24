/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.HistoryBinding;
import org.mozilla.vrbrowser.ui.adapters.HistoryAdapter;
import org.mozilla.vrbrowser.ui.callbacks.HistoryCallback;
import org.mozilla.vrbrowser.ui.callbacks.HistoryItemCallback;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

import mozilla.components.concept.storage.VisitInfo;
import mozilla.components.concept.storage.VisitType;

public class HistoryView extends FrameLayout implements HistoryStore.HistoryListener {

    private static final String LOGTAG = SystemUtils.createLogtag(HistoryView.class);

    private HistoryBinding mBinding;
    private HistoryAdapter mHistoryAdapter;
    private boolean mIgnoreNextListener;

    public HistoryView(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public HistoryView(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public HistoryView(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context aContext) {
        LayoutInflater inflater = LayoutInflater.from(aContext);

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.history, this, true);
        mHistoryAdapter = new HistoryAdapter(mHistoryItemCallback, aContext);
        mBinding.historyList.setAdapter(mHistoryAdapter);
        mBinding.historyList.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        mBinding.setIsLoading(true);
        mBinding.executePendingBindings();
        syncHistory();
        SessionStore.get().getHistoryStore().addListener(this);

        setVisibility(GONE);

        setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    public void onDestroy() {
        SessionStore.get().getHistoryStore().removeListener(this);
    }

    private final HistoryItemCallback mHistoryItemCallback = new HistoryItemCallback() {
        @Override
        public void onClick(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            Session session = SessionStore.get().getActiveSession();
            session.loadUri(item.getUrl());
        }

        @Override
        public void onDelete(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            mIgnoreNextListener = true;
            SessionStore.get().getHistoryStore().deleteHistory(item.getUrl(), item.getVisitTime());
            mHistoryAdapter.removeItem(item);
            if (mHistoryAdapter.itemCount() == 0) {
                mBinding.setIsEmpty(true);
                mBinding.setIsLoading(false);
                mBinding.executePendingBindings();
            }
        }

        @Override
        public void onMore(View view, VisitInfo item) {
            mBinding.historyList.requestFocusFromTouch();

            int rowPosition = mHistoryAdapter.getItemPosition(item.getVisitTime());
            RecyclerView.ViewHolder row = mBinding.historyList.findViewHolderForLayoutPosition(rowPosition);
            boolean isLastVisibleItem = false;
            if (mBinding.historyList.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) mBinding.historyList.getLayoutManager();
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

    public void setHistoryCallback(@NonNull HistoryCallback callback) {
        mBinding.setCallback(callback);
    }

    private void syncHistory() {
        Calendar date = new GregorianCalendar();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        long currentTime = System.currentTimeMillis();
        long todayLimit = date.getTimeInMillis();
        long yesterdayLimit = todayLimit - SystemUtils.ONE_DAY_MILLIS;
        long oneWeekLimit = todayLimit - SystemUtils.ONE_WEEK_MILLIS;

        SessionStore.get().getHistoryStore().getDetailedHistory().thenAcceptAsync((items) -> {
            List<VisitInfo> orderedItems = items.stream()
                    .sorted((o1, o2) -> Long.valueOf(o2.getVisitTime() - o1.getVisitTime()).intValue())
                    .collect(Collectors.toList());

            addSection(orderedItems, getResources().getString(R.string.history_section_today), Long.MAX_VALUE, todayLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_yesterday), todayLimit, yesterdayLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_last_week), yesterdayLimit, oneWeekLimit);
            addSection(orderedItems, getResources().getString(R.string.history_section_older), oneWeekLimit, 0);

            showHistory(orderedItems);

        }, new UIThreadExecutor()).exceptionally(throwable -> {
            Log.d(LOGTAG, "Can get the detailed history");
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
            mBinding.setIsEmpty(true);
            mBinding.setIsLoading(false);

        } else {
            mBinding.setIsEmpty(false);
            mBinding.setIsLoading(false);
            mHistoryAdapter.setHistoryList(historyItems);
            mBinding.historyList.post(() -> mBinding.historyList.smoothScrollToPosition(0));
        }
        mBinding.executePendingBindings();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        double width = Math.ceil(getWidth()/getContext().getResources().getDisplayMetrics().density);
        mHistoryAdapter.setNarrow(width < SettingsStore.WINDOW_WIDTH_DEFAULT);
    }

    // HistoryStore.HistoryListener
    @Override
    public void onHistoryUpdated() {
        if (mIgnoreNextListener) {
            mIgnoreNextListener = false;
            return;
        }
        syncHistory();
    }
}
