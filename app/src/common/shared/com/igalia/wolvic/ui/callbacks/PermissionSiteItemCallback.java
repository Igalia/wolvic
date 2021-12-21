package com.igalia.wolvic.ui.callbacks;

import androidx.annotation.NonNull;

import com.igalia.wolvic.db.SitePermission;

public interface PermissionSiteItemCallback {
    void onDelete(@NonNull SitePermission item);
}
