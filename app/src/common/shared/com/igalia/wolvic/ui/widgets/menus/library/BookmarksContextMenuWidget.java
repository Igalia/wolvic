package com.igalia.wolvic.ui.widgets.menus.library;

import android.content.Context;

import java.util.EnumSet;

public class BookmarksContextMenuWidget extends LibraryContextMenuWidget {

    public BookmarksContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows) {
        super(aContext, item, getAdditionalActions(canOpenWindows));
    }

    private static EnumSet<LibraryContextMenuWidget.Action> getAdditionalActions(boolean canOpenWindows) {
        EnumSet<Action> additionalActions = EnumSet.of(Action.IS_BOOKMARKED);
        if (canOpenWindows)
            additionalActions.add(Action.OPEN_WINDOW);
        return additionalActions;
    }

}
