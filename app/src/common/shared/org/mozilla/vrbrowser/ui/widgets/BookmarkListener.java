package org.mozilla.vrbrowser.ui.widgets;

public interface BookmarkListener {
    default void onBookmarksShown(WindowWidget aWindow) {};
    default void onBookmarksHidden(WindowWidget aWindow) {};
}
