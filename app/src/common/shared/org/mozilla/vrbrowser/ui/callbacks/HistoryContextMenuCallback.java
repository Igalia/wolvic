package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.ui.widgets.menus.library.HistoryContextMenuWidget;

public interface HistoryContextMenuCallback extends LibraryContextMenuCallback {
    void onAddToBookmarks(HistoryContextMenuWidget.LibraryContextMenuItem item);
    void onRemoveFromBookmarks(HistoryContextMenuWidget.LibraryContextMenuItem item);
}
