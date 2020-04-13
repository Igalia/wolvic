package org.mozilla.vrbrowser.ui.widgets.menus.library;

import android.content.Context;

public class BookmarksContextMenuWidget extends LibraryContextMenuWidget {

    public BookmarksContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows) {
        super(aContext, item, canOpenWindows, true);
    }

}
