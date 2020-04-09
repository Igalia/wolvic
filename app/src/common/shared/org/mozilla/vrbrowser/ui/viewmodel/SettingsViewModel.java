package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.vrbrowser.browser.SettingsStore;

public class SettingsViewModel extends AndroidViewModel {

    private MutableLiveData<ObservableBoolean> isTrackingProtectionEnabled;
    private MutableLiveData<ObservableBoolean> isDRMEnabled;
    private MutableLiveData<ObservableBoolean> isPopupBlockingEnabled;
    private MutableLiveData<ObservableBoolean> isWebXREnabled;

    public SettingsViewModel(@NonNull Application application) {
        super(application);

        isTrackingProtectionEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isDRMEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isPopupBlockingEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isWebXREnabled = new MutableLiveData<>(new ObservableBoolean(false));
    }

    public void refresh() {
        int level = SettingsStore.getInstance(getApplication().getBaseContext()).getTrackingProtectionLevel();
        boolean isEnabled = level != ContentBlocking.EtpLevel.NONE;
        isTrackingProtectionEnabled.setValue(new ObservableBoolean(isEnabled));

        boolean drmEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isDrmContentPlaybackEnabled();
        isDRMEnabled = new MutableLiveData<>(new ObservableBoolean(drmEnabled));

        boolean popupBlockingEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isPopUpsBlockingEnabled();
        isPopupBlockingEnabled = new MutableLiveData<>(new ObservableBoolean(popupBlockingEnabled));

        boolean webxrEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isWebXREnabled();
        isWebXREnabled = new MutableLiveData<>(new ObservableBoolean(webxrEnabled));
    }

    public void setIsTrackingProtectionEnabled(boolean isEnabled) {
        this.isTrackingProtectionEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> getIsTrackingProtectionEnabled() {
        return isTrackingProtectionEnabled;
    }

    public void setIsDrmEnabled(boolean isEnabled) {
        this.isDRMEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> getIsDrmEnabled() {
        return isDRMEnabled;
    }

    public void setIsPopUpBlockingEnabled(boolean isEnabled) {
        this.isPopupBlockingEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> getIsPopUpBlockingEnabled() {
        return isPopupBlockingEnabled;
    }

    public void setIsWebXREnabled(boolean isEnabled) {
        this.isWebXREnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> getIsWebXREnabled() {
        return isWebXREnabled;
    }

}
