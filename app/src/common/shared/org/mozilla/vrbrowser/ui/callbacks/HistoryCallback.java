package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import androidx.annotation.NonNull;

import mozilla.components.concept.storage.VisitInfo;

public interface HistoryCallback {
    void onClearHistory(@NonNull View view);
    void onShowContextMenu(@NonNull View view, @NonNull VisitInfo item, boolean isLastVisibleItem);
}
