package org.mozilla.vrbrowser.db.entity;

import org.mozilla.vrbrowser.model.Bookmark;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks",
        indices = {@Index(value = {"url"}, unique = true)})
public class BookmarkEntity implements Bookmark {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "url")
    private String url;

    @ColumnInfo(name = "added")
    private long added;

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String getTitle() {
        if (title.isEmpty()) {
            return url;
        }

        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public long getAdded() {
        return added;
    }

    public void setAdded(long added) {
        this.added = added;
    }

    public BookmarkEntity() {
    }

    @Ignore
    public BookmarkEntity(int id, String title, String url, long added) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.added = added;
    }

    @Ignore
    public BookmarkEntity(String title, String url, long added) {
        this.title = title;
        this.url = url;
        this.added = added;
    }

    public BookmarkEntity(Bookmark bookmark) {
        this.id = bookmark.getId();
        this.title = bookmark.getTitle();
        this.url = bookmark.getUrl();
        this.added = bookmark.getAdded();
    }
}
