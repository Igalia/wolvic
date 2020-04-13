package org.mozilla.vrbrowser.ui.widgets;

public interface TrayListener {
    default void onBookmarksClicked() {}
    default void onPrivateBrowsingClicked() {}
    default void onAddWindowClicked() {}
    default void onHistoryClicked() {}
    default void onTabsClicked() {}
    default void onDownloadsClicked() {}
}
