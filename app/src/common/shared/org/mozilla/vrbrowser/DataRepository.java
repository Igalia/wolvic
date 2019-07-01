package org.mozilla.vrbrowser;

import org.mozilla.vrbrowser.browser.engine.SessionManager;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.db.AppDatabase;
import org.mozilla.vrbrowser.db.entity.BookmarkEntity;
import org.mozilla.vrbrowser.model.Bookmark;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

public class DataRepository implements LifecycleOwner {

    private static DataRepository sInstance;

    private final AppExecutors mExecutors;
    private final AppDatabase mDatabase;
    private final LifecycleRegistry mLifeCycle;
    private MediatorLiveData<List<BookmarkEntity>> mObservableBookmark;

    private DataRepository(final AppDatabase database, final AppExecutors executors) {
        mDatabase = database;
        mExecutors = executors;
        mLifeCycle = new LifecycleRegistry(this);
        mLifeCycle.markState(Lifecycle.State.STARTED);
        mObservableBookmark = new MediatorLiveData<>();

        mObservableBookmark.addSource(mDatabase.bookmarkDao().loadAllBookmarks(),
                bookmarkEntities -> {
                    if (mDatabase.getDatabaseCreated().getValue() != null) {
                        mObservableBookmark.postValue(bookmarkEntities);
                    }
                });
    }

    public static DataRepository getInstance(final AppDatabase database, final AppExecutors executors) {
        if (sInstance == null) {
            synchronized (DataRepository.class) {
                if (sInstance == null) {
                    sInstance = new DataRepository(database, executors);
                }
            }
        }
        return sInstance;
    }

    public LiveData<List<BookmarkEntity>> getBookmarks() {
        return mObservableBookmark;
    }

    public LiveData<BookmarkEntity> getBookmarkByUrl(final String url) {
        return mDatabase.bookmarkDao().getBookmarkByUrlAsync(url);
    }

    public void insertBookmark(BookmarkEntity bookmark) {
        mExecutors.diskIO().execute(() -> mDatabase.bookmarkDao().insertBookmark(bookmark));
    }

    public void deleteBookmark(Bookmark bookmark) {
        mExecutors.diskIO().execute(() -> mDatabase.bookmarkDao().deleteById(bookmark.getId()));
    }

    public void deleteBookmarkByUrl(String url) {
        mExecutors.diskIO().execute(() -> mDatabase.bookmarkDao().deleteByUrl(url));
    }

    public void migrateOldBookmarks() {
        getBookmarks().observe(this, oldBookmarks -> {
            if (oldBookmarks == null || oldBookmarks.size() == 0) {
                return;
            }
            for (BookmarkEntity oldBookmark: oldBookmarks) {
                SessionManager.get().getBookmarkStore().addBookmark(oldBookmark.getUrl(), oldBookmark.getTitle());
            }
            mExecutors.diskIO().execute(() -> mDatabase.bookmarkDao().deleteAll());
        });
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifeCycle;
    }
}
