package org.mozilla.vrbrowser.browser;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.engine.Session;

public interface SessionChangeListener {
    default void onNewSession(GeckoSession aSession) {};
    default void onRemoveSession(GeckoSession aSession) {};
    default void onCurrentSessionChange(GeckoSession aOldSession, GeckoSession aSession) {};
    default void onNewTab(Session aTab) {};
}
