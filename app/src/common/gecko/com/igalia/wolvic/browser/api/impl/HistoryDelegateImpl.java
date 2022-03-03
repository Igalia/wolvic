package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.List;
import java.util.stream.Collectors;

class HistoryDelegateImpl implements GeckoSession.HistoryDelegate {
    private WSession.HistoryDelegate mDelegate;
    private SessionImpl mSession;

    public HistoryDelegateImpl(WSession.HistoryDelegate delegate, SessionImpl session) {
        mDelegate = delegate;
        mSession = session;
    }

    @Nullable
    @Override
    public GeckoResult<Boolean> onVisited(@NonNull GeckoSession session, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        return ResultImpl.from(mDelegate.onVisited(mSession, url, lastVisitedURL, flags));
    }

    @Nullable
    @Override
    public GeckoResult<boolean[]> getVisited(@NonNull GeckoSession session, @NonNull String[] urls) {
        return ResultImpl.from(mDelegate.getVisited(mSession, urls));
    }

    @Override
    public void onHistoryStateChange(@NonNull GeckoSession session, @NonNull HistoryList historyList) {
        mDelegate.onHistoryStateChange(mSession, new WSession.HistoryDelegate.HistoryList() {
            @Override
            public int getCurrentIndex() {
                return historyList.getCurrentIndex();
            }

            @Override
            public List<WSession.HistoryDelegate.HistoryItem> getItems() {
                return historyList.stream().map(HistoryItemImpl::new).collect(Collectors.toList());
            }
        });
    }

    private static class HistoryItemImpl implements WSession.HistoryDelegate.HistoryItem {
        public HistoryItemImpl(HistoryItem geckoHistoryItem) {
            mGeckoHistoryItem = geckoHistoryItem;
        }

        HistoryItem mGeckoHistoryItem;

        @NonNull
        @Override
        public String getUri() {
            return mGeckoHistoryItem.getUri();
        }

        @NonNull
        @Override
        public String getTitle() {
            return mGeckoHistoryItem.getTitle();
        }
    }
}
