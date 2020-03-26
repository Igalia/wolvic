package org.mozilla.vrbrowser.db;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class SitePermission {
    @IntDef(value = { SITE_PERMISSION_POPUP, SITE_PERMISSION_WEBXR, SITE_PERMISSION_TRACKING})
    public @interface Category {}
    public static final int SITE_PERMISSION_POPUP = 0;
    public static final int SITE_PERMISSION_WEBXR = 1;
    public static final int SITE_PERMISSION_TRACKING = 2;

    public SitePermission(@NonNull String url, @NonNull String principal, boolean allowed, @Category int category) {
        this.url = url;
        this.principal = principal;
        this.allowed = allowed;
        this.category = category;
    }

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String url;

    @NonNull
    @ColumnInfo(name = "principal")
    public String principal;

    @ColumnInfo(name = "allowed")
    public boolean allowed;

    @ColumnInfo(name = "category")
    public @Category int category;
}

