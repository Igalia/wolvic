package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.downloads.Download;

public interface DownloadsCallback {
    default void onDeleteDownloads(@NonNull View view) {}
    default void onShowContextMenu(@NonNull View view, Download item, boolean isLastVisibleItem) {}
    default void onHideContextMenu(@NonNull View view) {}
    default void onShowSortingContextMenu(@NonNull View view) {}
}
