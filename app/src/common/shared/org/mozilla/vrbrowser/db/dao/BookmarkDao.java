package org.mozilla.vrbrowser.db.dao;

import org.mozilla.vrbrowser.db.entity.BookmarkEntity;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface BookmarkDao {

    @Query("SELECT * FROM bookmarks")
    LiveData<List<BookmarkEntity>> loadAllBookmarks();

    @Query("SELECT * FROM bookmarks WHERE url LIKE :url LIMIT 1")
    LiveData<BookmarkEntity> getBookmarkByUrlAsync(String url);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(BookmarkEntity bookmark);

    @Delete
    void delete(BookmarkEntity bookmark);

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    void deleteById(long bookmarkId);

    @Query("DELETE FROM bookmarks WHERE url LIKE :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM bookmarks")
    void deleteAll();
}