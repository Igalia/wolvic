package com.igalia.wolvic.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.SystemUtils;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

public class SettingsViewModel extends AndroidViewModel {

    private static final String LOGTAG = SystemUtils.createLogtag(SettingsViewModel.class);

    private MutableLiveData<ObservableBoolean> isTrackingProtectionEnabled;
    private MutableLiveData<ObservableBoolean> isDRMEnabled;
    private MutableLiveData<ObservableBoolean> isPopupBlockingEnabled;
    private MutableLiveData<ObservableBoolean> isWebXREnabled;
    private MutableLiveData<String> propsVersionName;
    private MutableLiveData<Map<String, RemoteProperties>> props;
    private MutableLiveData<ObservableBoolean> isWhatsNewVisible;
    private MutableLiveData<ObservableBoolean> isWindowMovementEnabled;

    public SettingsViewModel(@NonNull Application application) {
        super(application);

        isTrackingProtectionEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isDRMEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isPopupBlockingEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isWebXREnabled = new MutableLiveData<>(new ObservableBoolean(false));
        propsVersionName = new MutableLiveData<>();
        props = new MutableLiveData<>(Collections.emptyMap());
        isWhatsNewVisible = new MutableLiveData<>(new ObservableBoolean(false));
        isWindowMovementEnabled = new MutableLiveData<>(new ObservableBoolean(false));

        propsVersionName.observeForever(props -> isWhatsNewVisible());
        props.observeForever(versionName -> isWhatsNewVisible());
    }

    public void refresh() {
        int level = SettingsStore.getInstance(getApplication().getBaseContext()).getTrackingProtectionLevel();
        boolean isEnabled = level != WContentBlocking.EtpLevel.NONE;
        isTrackingProtectionEnabled.postValue(new ObservableBoolean(isEnabled));

        boolean drmEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isDrmContentPlaybackEnabled();
        isDRMEnabled.postValue(new ObservableBoolean(drmEnabled));

        boolean popupBlockingEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isPopUpsBlockingEnabled();
        isPopupBlockingEnabled.postValue(new ObservableBoolean(popupBlockingEnabled));

        boolean webxrEnabled = SettingsStore.getInstance(getApplication().getBaseContext()).isWebXREnabled();
        isWebXREnabled.postValue(new ObservableBoolean(webxrEnabled));

        String appVersionName = SettingsStore.getInstance(getApplication().getBaseContext()).getRemotePropsVersionName();
        propsVersionName.postValue(appVersionName);

        boolean windowMovementVisible = SettingsStore.getInstance(getApplication().getBaseContext()).isWindowMovementEnabled();
        isWindowMovementEnabled.postValue(new ObservableBoolean(windowMovementVisible));
    }

    private void isWhatsNewVisible() {
        boolean value = props.getValue() != null &&
                !BuildConfig.VERSION_NAME.equals(propsVersionName.getValue()) &&
                props.getValue().containsKey(BuildConfig.VERSION_NAME);
        isWhatsNewVisible.postValue(new ObservableBoolean(value));
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

    public void setWindowMovementEnabled(boolean isEnabled) {
        this.isWindowMovementEnabled.setValue(new ObservableBoolean(isEnabled));
    }

    public MutableLiveData<ObservableBoolean> isWindowMovementEnabled() {
        return isWindowMovementEnabled;
    }

    public void setPropsVersionName(String appVersionName) {
        this.propsVersionName.setValue(appVersionName);
    }

    public MutableLiveData<String> getPropsVersionName() {
        return propsVersionName;
    }

    public void setProps(String json) {
        try {
            Gson gson = new GsonBuilder().create();
            Type type = new TypeToken<Map<String, RemoteProperties>>() {}.getType();
            this.props.postValue(gson.fromJson(json, type));

        } catch (Exception e) {
            Log.e(LOGTAG, String.valueOf(e.getLocalizedMessage()));
        }
    }

    public MutableLiveData<Map<String, RemoteProperties>> getProps() {
        return props;
    }

    public MutableLiveData<ObservableBoolean> getIsWhatsNewVisible() {
        return isWhatsNewVisible;
    }

}
