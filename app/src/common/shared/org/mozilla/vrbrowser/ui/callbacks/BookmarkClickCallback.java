package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.model.Bookmark;

import mozilla.components.concept.storage.BookmarkNode;

public interface BookmarkClickCallback {
    void onClick(BookmarkNode bookmark);
    void onDelete(BookmarkNode bookmark);
}
