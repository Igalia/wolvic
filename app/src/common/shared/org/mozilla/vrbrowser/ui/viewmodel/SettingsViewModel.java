package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.vrbrowser.browser.SettingsStore;

public class SettingsViewModel extends AndroidViewModel {

    private MutableLiveData<ObservableBoolean> isTrackingProtectionEnabled;

    public SettingsViewModel(@NonNull Application application) {
        super(application);

        isTrackingProtectionEnabled = new MutableLiveData<>(new ObservableBoolean(true));
    }

    public void refresh() {
        int level = SettingsStore.getInstance(getApplication().getBaseContext()).getTrackingProtectionLevel();
        boolean isEnabled = level != ContentBlocking.EtpLevel.NONE;
        isTrackingProtectionEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public void setIsTrackingProtectionEnabled(boolean isEnabled) {
        this.isTrackingProtectionEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> getIsTrackingProtectionEnabled() {
        return isTrackingProtectionEnabled;
    }

}
