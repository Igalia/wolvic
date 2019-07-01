package org.mozilla.vrbrowser.ui.widgets;

public interface TrayListener {
    default void onBookmarksClicked() {};
    default void onPrivateBrowsingClicked() {};
}
