package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.ui.views.LibraryItemContextMenu;

public interface LibraryItemContextMenuClickCallback {
    void onOpenInNewWindowClick(LibraryItemContextMenu.LibraryContextMenuItem item);
    void onAddToBookmarks(LibraryItemContextMenu.LibraryContextMenuItem item);
    void onRemoveFromBookmarks(LibraryItemContextMenu.LibraryContextMenuItem item);
}
