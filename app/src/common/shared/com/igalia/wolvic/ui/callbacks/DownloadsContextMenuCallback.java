package com.igalia.wolvic.ui.callbacks;

import com.igalia.wolvic.ui.widgets.menus.DownloadsContextMenuWidget;

public interface DownloadsContextMenuCallback extends LibraryContextMenuCallback {
    void onDelete(DownloadsContextMenuWidget.DownloadsContextMenuItem item);
}
