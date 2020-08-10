package org.mozilla.vrbrowser.ui.widgets;

public interface TrayListener {
    default void onPrivateBrowsingClicked() {}
    default void onAddWindowClicked() {}
    default void onTabsClicked() {}
    default void onLibraryClicked() {}
}
