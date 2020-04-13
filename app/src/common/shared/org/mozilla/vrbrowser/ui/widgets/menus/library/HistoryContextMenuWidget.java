package org.mozilla.vrbrowser.ui.widgets.menus.library;

import android.content.Context;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.callbacks.HistoryContextMenuCallback;

public class HistoryContextMenuWidget extends LibraryContextMenuWidget {

    public HistoryContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows, boolean isBookmarked) {
        super(aContext, item, canOpenWindows, isBookmarked);
    }

    protected void setupCustomMenuItems(boolean canOpenWindows, boolean isBookmarked) {
        mItems.add(new MenuItem(getContext().getString(
                isBookmarked ? R.string.history_context_remove_bookmarks : R.string.history_context_add_bookmarks),
                isBookmarked ? R.drawable.ic_icon_bookmarked_active : R.drawable.ic_icon_bookmarked,
                () -> mItemDelegate.ifPresent((present -> {
                    if (isBookmarked) {
                        ((HistoryContextMenuCallback)mItemDelegate.get()).onRemoveFromBookmarks(mItem);

                    } else {
                        ((HistoryContextMenuCallback)mItemDelegate.get()).onAddToBookmarks(mItem);
                    }
                }))));
    }

}
