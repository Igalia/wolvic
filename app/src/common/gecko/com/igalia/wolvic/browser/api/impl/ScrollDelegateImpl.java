package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WSession;

import org.mozilla.geckoview.GeckoSession;

class ScrollDelegateImpl implements GeckoSession.ScrollDelegate {
    private WSession.ScrollDelegate mDelegate;
    private SessionImpl mSession;

    public ScrollDelegateImpl(WSession.ScrollDelegate delegate, SessionImpl session) {
        mDelegate = delegate;
        mSession = session;
    }

    @Override
    public void onScrollChanged(@NonNull GeckoSession session, int scrollX, int scrollY) {
        mDelegate.onScrollChanged(mSession, scrollX, scrollY);
    }
}
