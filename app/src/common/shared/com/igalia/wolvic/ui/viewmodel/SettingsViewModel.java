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
import com.igalia.wolvic.utils.Announcement;
import com.igalia.wolvic.utils.Experience;
import com.igalia.wolvic.utils.RemoteAnnouncements;
import com.igalia.wolvic.utils.RemoteExperiences;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.SystemUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsViewModel extends AndroidViewModel {

    private static final String LOGTAG = SystemUtils.createLogtag(SettingsViewModel.class);

    private MutableLiveData<ObservableBoolean> isTrackingProtectionEnabled;
    private MutableLiveData<ObservableBoolean> isDRMEnabled;
    private MutableLiveData<ObservableBoolean> isPopupBlockingEnabled;
    private MutableLiveData<ObservableBoolean> isWebXREnabled;
    private MutableLiveData<String> propsVersionName;
    private MutableLiveData<Map<String, RemoteProperties>> props;
    private MutableLiveData<RemoteAnnouncements> announcements;
    private MutableLiveData<RemoteAnnouncements> visibleAnnouncements;
    private MutableLiveData<RemoteExperiences> experiences;
    private MutableLiveData<ObservableBoolean> isWhatsNewVisible;

    public SettingsViewModel(@NonNull Application application) {
        super(application);

        isTrackingProtectionEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isDRMEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isPopupBlockingEnabled = new MutableLiveData<>(new ObservableBoolean(false));
        isWebXREnabled = new MutableLiveData<>(new ObservableBoolean(false));
        propsVersionName = new MutableLiveData<>();
        props = new MutableLiveData<>(Collections.emptyMap());
        announcements = new MutableLiveData<>(new RemoteAnnouncements());
        visibleAnnouncements = new MutableLiveData<>(new RemoteAnnouncements());
        experiences = new MutableLiveData<>(new RemoteExperiences());
        isWhatsNewVisible = new MutableLiveData<>(new ObservableBoolean(false));

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

    public void setAnnouncements(String json) {
        RemoteAnnouncements updatedAnnouncements = null;
        try {
            Gson gson = new GsonBuilder().create();
            updatedAnnouncements = gson.fromJson(json, RemoteAnnouncements.class);
        } catch (Exception e) {
            Log.w(LOGTAG, String.valueOf(e.getLocalizedMessage()));
        } finally {
            if (updatedAnnouncements != null) {
                this.announcements.postValue(updatedAnnouncements);
                // Filter the upstream list to remove dismissed announcements.
                updateVisibleAnnouncementsInternal(updatedAnnouncements);
            }
        }
    }

    public void updateVisibleAnnouncements() {
        updateVisibleAnnouncementsInternal(announcements.getValue());
    }

    private void updateVisibleAnnouncementsInternal(RemoteAnnouncements updatedAnnouncements) {
        if (updatedAnnouncements == null) {
            return;
        }

        // Filter out announcements that have been already dismissed.
        Set<String> dismissedIds = SettingsStore.getInstance(getApplication().getBaseContext()).getDismissedAnnouncementIds();
        List<Announcement> visibleList = new ArrayList<>();
        for (Announcement announcement : updatedAnnouncements.getAnnouncements()) {
            if (!dismissedIds.contains(announcement.getId())) {
                visibleList.add(announcement);
            }
        }

        RemoteAnnouncements visibleValue = new RemoteAnnouncements();
        visibleValue.setAnnouncements(visibleList);
        visibleAnnouncements.postValue(visibleValue);
    }

    public void setExperiences(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }

        try {
            Gson gson = new GsonBuilder().create();
            RemoteExperiences newExperiences = gson.fromJson(json, RemoteExperiences.class);

            RemoteExperiences currentExperiences = this.experiences.getValue();
            if (currentExperiences == null) {
                // Initialize a new experiences object if one doesn't exist yet.
                this.experiences.postValue(newExperiences);
            } else {
                currentExperiences.setRemoteExperiences(newExperiences);
                this.experiences.postValue(currentExperiences);
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "Error processing experiences data: " + e.getLocalizedMessage());
        }
    }

    public void setHeyVRExperiences(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }

        try {
            Gson gson = new GsonBuilder().create();
            Experience[] experiencesArray = gson.fromJson(json, Experience[].class);
            List<Experience> heyVRExperiences = Arrays.asList(experiencesArray);

            RemoteExperiences currentExperiences = this.experiences.getValue();
            if (currentExperiences == null) {
                // Initialize a new experiences object if one doesn't exist yet.
                currentExperiences = new RemoteExperiences();
            }
            currentExperiences.setHeyVRExperiences(heyVRExperiences);
            this.experiences.postValue(currentExperiences);
        } catch (Exception e) {
            Log.w(LOGTAG, "Error processing HeyVR data: " + e.getLocalizedMessage());
        }
    }

    public MutableLiveData<RemoteAnnouncements> getAnnouncements() {
        return announcements;
    }

    public MutableLiveData<RemoteAnnouncements> getVisibleAnnouncements() {
        return visibleAnnouncements;
    }

    public MutableLiveData<RemoteExperiences> getExperiences() {
        return experiences;
    }

    public MutableLiveData<ObservableBoolean> getIsWhatsNewVisible() {
        return isWhatsNewVisible;
    }

}
