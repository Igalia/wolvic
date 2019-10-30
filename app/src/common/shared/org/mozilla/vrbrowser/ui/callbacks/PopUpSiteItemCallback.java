package org.mozilla.vrbrowser.ui.callbacks;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.db.PopUpSite;

public interface PopUpSiteItemCallback {
    void onDelete(@NonNull PopUpSite item);
}
