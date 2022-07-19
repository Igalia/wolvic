package com.igalia.wolvic.browser.api.impl;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;

import org.chromium.weblayer.ContextMenuParams;
import org.chromium.weblayer.TabCallback;

class TabCallbackImpl extends TabCallback {
    private SessionImpl mSession;

    public TabCallbackImpl(SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onVisibleUriChanged(@NonNull Uri uri) {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            delegate.onLocationChange(mSession, uri.toString());
        }
    }

    @Override
    public void onRenderProcessGone() {

    }

    @Override
    public void showContextMenu(@NonNull ContextMenuParams params) {

    }

    @Override
    public void onTabModalStateChanged(boolean isTabModalShowing) {

    }

    @Override
    public void onTitleUpdated(@NonNull String title) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onTitleChange(mSession, title);
        }
    }

    @Override
    public void bringTabToFront() {
        mSession.mTab.getBrowser().setActiveTab(mSession.mTab);
    }

    @Override
    public void onBackgroundColorChanged(int color) {

    }

    @Override
    public void onScrollNotification(int notificationType, float currentScrollRatio) {

    }
}
