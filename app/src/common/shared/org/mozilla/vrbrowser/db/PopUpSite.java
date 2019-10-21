package org.mozilla.vrbrowser.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class PopUpSite {

    public PopUpSite(@NonNull String url, boolean allowed) {
        this.url = url;
        this.allowed = allowed;
    }

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String url;

    @ColumnInfo(name = "allowed")
    public boolean allowed;
}

