package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.db.PopUpSite;

import java.util.List;

public class PopUpsViewModel extends AndroidViewModel {

    private final DataRepository mRepository;

    private final MediatorLiveData<List<PopUpSite>> mObservableSites;

    public PopUpsViewModel(Application application) {
        super(application);

        mObservableSites = new MediatorLiveData<>();
        mObservableSites.setValue(null);

        mRepository = ((VRBrowserApplication) application).getRepository();
        LiveData<List<PopUpSite>> sites = mRepository.getPopUpSites();

        mObservableSites.addSource(sites, mObservableSites::setValue);
    }

    public LiveData<List<PopUpSite>> getAll() {
        return mObservableSites;
    }

    public void insertSite(@NonNull String url, boolean allowed) {
        mRepository.insertPopUpSite(url, allowed);
    }

    public void insertSite(@NonNull PopUpSite site) {
        mRepository.insertPopUpSite(site);
    }

    public void deleteSite(@NonNull PopUpSite site) {
        mRepository.deletePopUpSite(site);
    }

    public void deleteAll() {
        mRepository.deleteAllPopUpSites();
    }

}
