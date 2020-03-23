package org.mozilla.vrbrowser.ui.callbacks;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.db.SitePermission;

public interface PermissionSiteItemCallback {
    void onDelete(@NonNull SitePermission item);
}
