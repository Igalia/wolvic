package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.ui.widgets.menus.library.LibraryContextMenuWidget;

public interface LibraryContextMenuCallback {
    void onOpenInNewWindowClick(LibraryContextMenuWidget.LibraryContextMenuItem item);
    void onOpenInNewTabClick(LibraryContextMenuWidget.LibraryContextMenuItem item);
}
