package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import com.igalia.wolvic.ui.adapters.Bookmark;

public interface BookmarksCallback {
    default void onSyncBookmarks(@NonNull View view) {}
    default void onFxALogin(@NonNull View view) {}
    default void onFxASynSettings(@NonNull View view) {}
    default void onShowContextMenu(@NonNull View view, Bookmark item, boolean isLastVisibleItem) {}
    default void onHideContextMenu(@NonNull View view) {}
}
