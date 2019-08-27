package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import mozilla.components.concept.storage.VisitInfo;

public interface HistoryCallback {
    void onClearHistory(View view);
    void onShowContextMenu(View view, VisitInfo item, boolean isLastVisibleItem);
}
