package org.mozilla.vrbrowser.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PopUpSiteDao {
    @Query("SELECT * FROM PopUpSite")
    LiveData<List<PopUpSite>> loadAll();

    @Query("SELECT * FROM PopUpSite WHERE url LIKE :url LIMIT 1")
    LiveData<PopUpSite> findByUrl(String url);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PopUpSite site);

    @Delete
    void delete(PopUpSite site);

    @Query("DELETE FROM PopUpSite WHERE url = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM PopUpSite")
    void deleteAll();
}

