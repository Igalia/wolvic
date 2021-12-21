package com.igalia.wolvic.ui.callbacks;

import com.igalia.wolvic.ui.widgets.menus.library.LibraryContextMenuWidget;

public interface LibraryContextMenuCallback {
    void onOpenInNewWindowClick(LibraryContextMenuWidget.LibraryContextMenuItem item);
    void onOpenInNewTabClick(LibraryContextMenuWidget.LibraryContextMenuItem item);
}
