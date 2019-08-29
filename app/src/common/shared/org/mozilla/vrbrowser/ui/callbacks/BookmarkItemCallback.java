package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import mozilla.components.concept.storage.BookmarkNode;

public interface BookmarkItemCallback {
    void onClick(View view, BookmarkNode item);
    void onDelete(View view, BookmarkNode item);
    void onMore(View view, BookmarkNode item);
}
