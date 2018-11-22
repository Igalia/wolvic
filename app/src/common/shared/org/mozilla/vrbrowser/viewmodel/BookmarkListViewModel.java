package org.mozilla.vrbrowser.viewmodel;

import android.app.Application;

import org.mozilla.vrbrowser.DataRepository;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.db.entity.BookmarkEntity;
import org.mozilla.vrbrowser.model.Bookmark;

import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

public class BookmarkListViewModel extends AndroidViewModel {

    private final DataRepository mRepository;

    private final MediatorLiveData<List<BookmarkEntity>> mObservableBookmarks;

    public BookmarkListViewModel(Application application) {
        super(application);

        mObservableBookmarks = new MediatorLiveData<>();
        mObservableBookmarks.setValue(null);

        mRepository = ((VRBrowserApplication) application).getRepository();
        LiveData<List<BookmarkEntity>> bookmarks = mRepository.getBookmarks();

        mObservableBookmarks.addSource(bookmarks, mObservableBookmarks::setValue);
    }

    public LiveData<List<BookmarkEntity>> getBookmarks() {
        return mObservableBookmarks;
    }

    public void deleteBookmark(Bookmark bookmark) {
        mRepository.deleteBookmark(bookmark);
    }
}
