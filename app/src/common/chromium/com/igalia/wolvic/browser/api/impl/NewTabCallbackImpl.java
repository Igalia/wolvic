package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.weblayer.NewTabCallback;
import org.chromium.weblayer.Tab;

class NewTabCallbackImpl extends NewTabCallback {
    @NonNull SessionImpl mSession;

    public NewTabCallbackImpl(@NonNull SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onNewTab(@NonNull Tab tab, int type) {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            RuntimeImpl runtime = mSession.getRuntime();
            assert runtime != null;
            SessionImpl newSession = new SessionImpl(runtime, tab);
            delegate.onNewSession(newSession, tab.getNavigationController().getNavigationEntryDisplayUri(0).toString());
        }
    }
}
