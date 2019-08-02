package org.mozilla.vrbrowser.browser;

import org.mozilla.geckoview.GeckoSession;

public interface SessionChangeListener {
    default void onNewSession(GeckoSession aSession, int aId) {};
    default void onRemoveSession(GeckoSession aSession, int aId) {};
    default void onCurrentSessionChange(GeckoSession aSession, int aId) {};
}
