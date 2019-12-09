package org.mozilla.vrbrowser.browser;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.engine.Session;

public interface SessionChangeListener {
    default void onSessionOpened(Session aSession) {}
    default void onSessionClosed(Session aSession) {}
    default void onRemoveSession(Session aSession) {}
    default void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {}
    default void onStackSession(Session aSession) {}
    default void onUnstackSession(Session aSession, Session aParent) {}
    default void onActiveStateChange(Session aSession, boolean aActive) {}
}
