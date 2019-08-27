package org.mozilla.vrbrowser.ui.callbacks;

import mozilla.components.concept.storage.VisitInfo;

public interface HistoryItemContextMenuClickCallback {
    void onOpenInNewWindowClick(VisitInfo item);
    void onAddToBookmarks(VisitInfo item);
    void onRemoveFromBookmarks(VisitInfo item);
}
