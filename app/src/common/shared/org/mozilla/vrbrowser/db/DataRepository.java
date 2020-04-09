package org.mozilla.vrbrowser.db;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import org.mozilla.vrbrowser.AppExecutors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DataRepository implements LifecycleOwner {

    private static DataRepository sInstance;

    private final AppExecutors mExecutors;
    private final AppDatabase mDatabase;
    private final LifecycleRegistry mLifeCycle;
    private MediatorLiveData<List<SitePermission>> mObservablePopUps;

    private DataRepository(final @NonNull AppDatabase database, final @NonNull AppExecutors executors) {
        mDatabase = database;
        mExecutors = executors;
        mLifeCycle = new LifecycleRegistry(this);
        mLifeCycle.setCurrentState(Lifecycle.State.STARTED);
        mObservablePopUps = new MediatorLiveData<>();

        mObservablePopUps.addSource(mDatabase.sitePermissionDao().loadAll(),
                sites -> {
                    if (mDatabase.getDatabaseCreated().getValue() != null) {
                        mObservablePopUps.postValue(sites);
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

    public LiveData<List<SitePermission>> getSitePermissions() {
        return mObservablePopUps;
    }

    public CompletableFuture<SitePermission> getSitePermission(String aURL, @SitePermission.Category int category) {
        CompletableFuture<SitePermission> future = new CompletableFuture<>();
        mExecutors.diskIO().execute(() -> mDatabase.sitePermissionDao().findByUrl(aURL, category));
        return future;
    }

    public void insertSitePermission(final @NonNull SitePermission site) {
        mExecutors.diskIO().execute(() -> mDatabase.sitePermissionDao().insert(site));
    }

    public void deleteSitePermission(final @NonNull SitePermission site) {
        mExecutors.diskIO().execute(() -> mDatabase.sitePermissionDao().delete(site));
    }

    public void deleteAllSitePermission(@SitePermission.Category int category) {
        mExecutors.diskIO().execute(() -> mDatabase.sitePermissionDao().deleteAll(category));
    }

}
