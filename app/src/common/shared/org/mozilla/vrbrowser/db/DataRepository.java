package org.mozilla.vrbrowser.db;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import org.mozilla.vrbrowser.AppExecutors;

import java.util.List;

public class DataRepository implements LifecycleOwner {

    private static DataRepository sInstance;

    private final AppExecutors mExecutors;
    private final AppDatabase mDatabase;
    private final LifecycleRegistry mLifeCycle;
    private MediatorLiveData<List<PopUpSite>> mObservablePopUps;

    private DataRepository(final @NonNull AppDatabase database, final @NonNull AppExecutors executors) {
        mDatabase = database;
        mExecutors = executors;
        mLifeCycle = new LifecycleRegistry(this);
        mLifeCycle.markState(Lifecycle.State.STARTED);
        mObservablePopUps = new MediatorLiveData<>();

        mObservablePopUps.addSource(mDatabase.popUpSiteDao().loadAll(),
                popUpSites -> {
                    if (mDatabase.getDatabaseCreated().getValue() != null) {
                        mObservablePopUps.postValue(popUpSites);
                    }
                });
    }

    public static DataRepository getInstance(final @NonNull AppDatabase database, final AppExecutors executors) {
        if (sInstance == null) {
            synchronized (DataRepository.class) {
                if (sInstance == null) {
                    sInstance = new DataRepository(database, executors);
                }
            }
        }

        return sInstance;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifeCycle;
    }

    public LiveData<List<PopUpSite>> getPopUpSites() {
        return mObservablePopUps;
    }

    public LiveData<PopUpSite> findPopUpSiteByUrl(final @NonNull String url) {
        return mDatabase.popUpSiteDao().findByUrl(url);
    }

    public void insertPopUpSite(final @NonNull String url, boolean allowed) {
        mExecutors.diskIO().execute(() -> mDatabase.popUpSiteDao().insert(new PopUpSite(url, allowed)));
    }

    public void insertPopUpSite(final @NonNull PopUpSite site) {
        mExecutors.diskIO().execute(() -> mDatabase.popUpSiteDao().insert(site));
    }

    public void deletePopUpSite(final @NonNull PopUpSite site) {
        mExecutors.diskIO().execute(() -> mDatabase.popUpSiteDao().delete(site));
    }

    public void deleteAllPopUpSites() {
        mExecutors.diskIO().execute(() -> mDatabase.popUpSiteDao().deleteAll());
    }

}
