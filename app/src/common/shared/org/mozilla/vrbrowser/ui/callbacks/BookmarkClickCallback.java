package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.model.Bookmark;

public interface BookmarkClickCallback {
    void onClick(Bookmark bookmark);
    void onDelete(Bookmark bookmark);
}
