package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.ui.widgets.menus.LibraryMenuWidget;

public interface LibraryItemContextMenuClickCallback {
    void onOpenInNewWindowClick(LibraryMenuWidget.LibraryContextMenuItem item);
    void onOpenInNewTabClick(LibraryMenuWidget.LibraryContextMenuItem item);
    void onAddToBookmarks(LibraryMenuWidget.LibraryContextMenuItem item);
    void onRemoveFromBookmarks(LibraryMenuWidget.LibraryContextMenuItem item);
}
