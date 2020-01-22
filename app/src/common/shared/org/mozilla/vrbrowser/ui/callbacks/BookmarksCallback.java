package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.ui.adapters.Bookmark;

public interface BookmarksCallback {
    default void onClearBookmarks(@NonNull View view) {}
    default void onSyncBookmarks(@NonNull View view) {}
    default void onFxALogin(@NonNull View view) {}
    default void onFxASynSettings(@NonNull View view) {}
    default void onShowContextMenu(@NonNull View view, Bookmark item, boolean isLastVisibleItem) {}
    default void onHideContextMenu(@NonNull View view) {}
    default void onClickItem(@NonNull View view, Bookmark item) {}
}
