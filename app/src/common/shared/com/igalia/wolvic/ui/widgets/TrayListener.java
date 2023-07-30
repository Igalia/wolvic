package com.igalia.wolvic.ui.widgets;

public interface TrayListener {
    default void onPrivateBrowsingClicked() {}
    default void onAddWindowClicked() {}
    default void onTabsClicked() {}
    default void onLibraryClicked() {}
    default void onDownloadsClicked() {}
    default void onWebAppsClicked() {}
}
