package org.mozilla.vrbrowser.viewmodel;

import android.app.Application;

import org.mozilla.vrbrowser.DataRepository;
import org.mozilla.vrbrowser.db.entity.BookmarkEntity;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class BookmarkViewModel extends AndroidViewModel {

    private final LiveData<BookmarkEntity> mObservableBookmark;

    public ObservableField<BookmarkEntity> bookmark = new ObservableField<>();

    private final String mBookmarkUrl;

    public BookmarkViewModel(@NonNull Application application, DataRepository repository,
                             final String bookmarkUrl) {
        super(application);
        mBookmarkUrl = bookmarkUrl;

        mObservableBookmark = repository.getBookmarkByUrl(mBookmarkUrl);
    }

    public LiveData<BookmarkEntity> getObservableBookmark() {
        return mObservableBookmark;
    }

    public void setBookmark(BookmarkEntity bookmark) {
        this.bookmark.set(bookmark);
    }
}
