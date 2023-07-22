package com.igalia.wolvic.ui.widgets.menus.library;

import android.content.Context;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.callbacks.HistoryContextMenuCallback;

import java.util.EnumSet;

public class HistoryContextMenuWidget extends LibraryContextMenuWidget {

    public HistoryContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows, boolean isBookmarked) {
        super(aContext, item, getAdditionalActions(canOpenWindows, isBookmarked));
    }

    private static EnumSet<Action> getAdditionalActions(boolean canOpenWindows, boolean isBookmarked) {
        EnumSet<Action> additionalActions = EnumSet.noneOf(Action.class);
        if (canOpenWindows)
            additionalActions.add(Action.OPEN_WINDOW);
        if (isBookmarked)
            additionalActions.add(Action.IS_BOOKMARKED);
        return additionalActions;
    }

    @Override
    protected void setupCustomMenuItems(EnumSet<Action> additionalActions) {
        boolean isBookmarked = additionalActions.contains(Action.IS_BOOKMARKED);
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
