package org.mozilla.vrbrowser.ui.widgets;

public interface BookmarkListener {
    default void onBookmarksShown() {};
    default void onBookmarksHidden() {};
}
