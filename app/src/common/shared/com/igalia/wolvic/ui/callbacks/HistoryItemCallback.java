package com.igalia.wolvic.ui.callbacks;

import android.view.View;

import mozilla.components.concept.storage.VisitInfo;

public interface HistoryItemCallback {
    void onClick(View view, VisitInfo item);
    void onDelete(View view, VisitInfo item);
    void onMore(View view, VisitInfo item);
}
