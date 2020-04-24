package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.db.SitePermission;

import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SitePermissionViewModel extends AndroidViewModel {

    private final DataRepository mRepository;

    private final SparseArray<MediatorLiveData<List<SitePermission>>> mObservableSites;

    public SitePermissionViewModel(Application application) {
        super(application);

        mObservableSites = new SparseArray<>();
        mRepository = ((VRBrowserApplication) application).getRepository();
    }

    public LiveData<List<SitePermission>> getAll() {
        return mRepository.getSitePermissions();
    }

    public LiveData<List<SitePermission>> getAll(@SitePermission.Category int category) {
        MediatorLiveData<List<SitePermission>> result = mObservableSites.get(category);
        if (result == null) {
            LiveData<List<SitePermission>> sites = mRepository.getSitePermissions();
            final MediatorLiveData<List<SitePermission>> mediator = new MediatorLiveData<>();
            mediator.setValue(null);
            mediator.addSource(sites, values -> {
                mediator.setValue(values.stream()
                                        .filter(sitePermission -> sitePermission.category == category)
                                        .collect(Collectors.toList()));
            });
            mObservableSites.put(category, mediator);
            result = mediator;
        }
        return result;
    }

    public void insertSite(@NonNull SitePermission site) {
        mRepository.insertSitePermission(site);
    }

    public void deleteSite(@NonNull SitePermission site) {
        mRepository.deleteSitePermission(site);
    }

    public void deleteSites(@NonNull List<SitePermission> sites) {
        mRepository.deleteSites(sites);
    }

    public void deleteAll(@SitePermission.Category int category) {
        mRepository.deleteAllSitePermission(category);
    }
}
