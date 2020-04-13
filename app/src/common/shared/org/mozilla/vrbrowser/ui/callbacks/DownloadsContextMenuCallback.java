package org.mozilla.vrbrowser.ui.callbacks;

import org.mozilla.vrbrowser.ui.widgets.menus.library.DownloadsContextMenuWidget;

public interface DownloadsContextMenuCallback extends LibraryContextMenuCallback {
    void onDelete(DownloadsContextMenuWidget.DownloadsContextMenuItem item);
}
