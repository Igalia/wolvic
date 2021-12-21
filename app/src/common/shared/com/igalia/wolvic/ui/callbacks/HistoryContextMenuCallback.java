package com.igalia.wolvic.ui.callbacks;

import com.igalia.wolvic.ui.widgets.menus.library.HistoryContextMenuWidget;

public interface HistoryContextMenuCallback extends LibraryContextMenuCallback {
    void onAddToBookmarks(HistoryContextMenuWidget.LibraryContextMenuItem item);
    void onRemoveFromBookmarks(HistoryContextMenuWidget.LibraryContextMenuItem item);
}
