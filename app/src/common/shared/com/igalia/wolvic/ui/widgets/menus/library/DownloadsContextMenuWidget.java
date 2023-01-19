package com.igalia.wolvic.ui.widgets.menus.library;

import android.content.Context;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.ui.callbacks.DownloadsContextMenuCallback;

import java.util.EnumSet;

public class DownloadsContextMenuWidget extends LibraryContextMenuWidget {

    public DownloadsContextMenuWidget(Context aContext, LibraryContextMenuItem item,
                                      boolean canOpenWindows, boolean canCopyToContentUri) {
        super(aContext, item, getAdditionalActions(canOpenWindows, canCopyToContentUri));
    }

    private static EnumSet<Action> getAdditionalActions(boolean canOpenWindows, boolean canCopyToContentUri) {
        EnumSet<Action> additionalActions = EnumSet.noneOf(Action.class);
        if (canOpenWindows)
            additionalActions.add(Action.OPEN_WINDOW);
        if (canCopyToContentUri)
            additionalActions.add(Action.CAN_COPY);
        return additionalActions;
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

    @Override
    protected void setupCustomMenuItems(EnumSet<Action> additionalActions) {
        if (additionalActions.contains(Action.CAN_COPY)) {
            mItems.add(new MenuItem(getContext().getString(
                    R.string.download_context_copy_to_content_uri),
                    R.drawable.ic_icon_file_copy,
                    () -> mItemDelegate.ifPresent((present ->
                            ((DownloadsContextMenuCallback) mItemDelegate.get()).onCopyToContentUri((DownloadsContextMenuItem) mItem)))));
        }
        mItems.add(new MenuItem(getContext().getString(
                R.string.download_context_delete),
                R.drawable.ic_icon_library_clearfromlist,
                () -> mItemDelegate.ifPresent((present ->
                        ((DownloadsContextMenuCallback) mItemDelegate.get()).onDelete((DownloadsContextMenuItem) mItem)))));
    }

}
