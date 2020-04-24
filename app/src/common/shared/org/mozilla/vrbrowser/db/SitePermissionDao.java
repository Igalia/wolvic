package org.mozilla.vrbrowser.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SitePermissionDao {
    @Query("SELECT * FROM SitePermission")
    LiveData<List<SitePermission>> loadAll();

    @Query("SELECT * FROM SitePermission WHERE category = :category AND url LIKE :url LIMIT 1")
    LiveData<SitePermission> findByUrl(String url, @SitePermission.Category int category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SitePermission site);

    @Delete
    void delete(SitePermission site);

    @Delete
    void delete(List<SitePermission> sites);

    @Query("DELETE FROM SitePermission WHERE url = :url AND category = :category")
    void deleteByUrl(String url, @SitePermission.Category int category);

    @Query("DELETE FROM SitePermission WHERE category = :category")
    void deleteAll(@SitePermission.Category int category);
}

