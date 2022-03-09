package com.igalia.wolvic.browser;

import com.igalia.wolvic.browser.engine.Session;

import org.mozilla.geckoview.GeckoSession;

public interface SessionChangeListener {
    default void onSessionAdded(Session aSession) {}
    default void onSessionOpened(Session aSession) {}
    default void onSessionClosed(Session aSession) {}
    default void onSessionRemoved(String aId) {}
    default void onSessionStateChanged(Session aSession, boolean aActive) {}
    default void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {}
    default void onStackSession(Session aSession) {}
    default void onUnstackSession(Session aSession, Session aParent) {}
}
