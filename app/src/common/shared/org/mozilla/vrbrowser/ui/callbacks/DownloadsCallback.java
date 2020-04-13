package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.downloads.Download;
import org.mozilla.vrbrowser.ui.adapters.Bookmark;

public interface DownloadsCallback {
    default void onDeleteDownloads(@NonNull View view) {}
    default void onShowContextMenu(@NonNull View view, Download item, boolean isLastVisibleItem) {}
    default void onHideContextMenu(@NonNull View view) {}
    default void onShowSortingContextMenu(@NonNull View view) {}
}
