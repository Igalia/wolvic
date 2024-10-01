package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;

import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.engine.Session;

public abstract class AbstractTabsBar extends UIWidget implements SessionChangeListener {

    public AbstractTabsBar(Context aContext) {
        super(aContext);
    }

    public AbstractTabsBar(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
    }

    public AbstractTabsBar(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
    }

    // TODO Use more fine-grained updates.
    public abstract void refreshTabs();

    @Override
    public void onSessionAdded(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionOpened(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionClosed(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onSessionRemoved(String aId) {
        refreshTabs();
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        refreshTabs();
    }

    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        refreshTabs();
    }

    @Override
    public void onStackSession(Session aSession) {
        refreshTabs();
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        refreshTabs();
    }
}
