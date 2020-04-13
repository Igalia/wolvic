package org.mozilla.vrbrowser.ui.widgets.menus.library;

import android.content.Context;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.callbacks.DownloadsContextMenuCallback;

public class DownloadsContextMenuWidget extends LibraryContextMenuWidget {

    public DownloadsContextMenuWidget(Context aContext, LibraryContextMenuItem item, boolean canOpenWindows) {
        super(aContext, item, canOpenWindows, false);
    }

    public static class DownloadsContextMenuItem extends LibraryContextMenuItem {

        long downloadId;

        public DownloadsContextMenuItem(@NonNull String url, String title, long downloadId) {
            super(url, title);

            this.downloadId = downloadId;
        }

        public long getDownloadsId() {
            return downloadId;
        }

    }

    protected void setupCustomMenuItems(boolean canOpenWindows, boolean isBookmarked) {
        mItems.add(new MenuItem(getContext().getString(
                R.string.download_context_delete),
                R.drawable.ic_icon_library_clearfromlist,
                () -> mItemDelegate.ifPresent((present ->
                        ((DownloadsContextMenuCallback)mItemDelegate.get()).onDelete((DownloadsContextMenuItem)mItem)))));
    }

}
