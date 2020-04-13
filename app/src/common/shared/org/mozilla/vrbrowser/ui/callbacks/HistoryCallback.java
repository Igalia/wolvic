package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import mozilla.components.concept.storage.VisitInfo;

public interface HistoryCallback {
    default void onClearHistory(@NonNull View view) {}
    default void onSyncHistory(@NonNull View view) {}
    default void onFxALogin(@NonNull View view) {}
    default void onFxASynSettings(@NonNull View view) {}
    default void onShowContextMenu(@NonNull View view, @NonNull VisitInfo item, boolean isLastVisibleItem) {}
    default void onHideContextMenu(@NonNull View view) {}
}
