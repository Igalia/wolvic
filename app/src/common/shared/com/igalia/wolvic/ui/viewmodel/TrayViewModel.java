package com.igalia.wolvic.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableInt;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.igalia.wolvic.R;

public class TrayViewModel extends AndroidViewModel {

    private MutableLiveData<ObservableBoolean> isMaxWindows;
    private MutableLiveData<ObservableBoolean> shouldBeVisible;
    private MutableLiveData<ObservableBoolean> isKeyboardVisible;
    private MutableLiveData<ObservableInt> downloadsNumber;
    private MediatorLiveData<ObservableBoolean> isVisible;
    private MutableLiveData<ObservableBoolean> isTabsWidgetVisible;
    private MutableLiveData<String> time;
    private MutableLiveData<String> pm;
    private MutableLiveData<ObservableBoolean> wifiConnected;
    private MutableLiveData<ObservableInt> headsetIcon;
    private MutableLiveData<ObservableInt> headsetBatteryLevel;
    private MutableLiveData<ObservableInt> leftControllerIcon;
    private MutableLiveData<ObservableInt> leftControllerBatteryLevel;
    private MutableLiveData<ObservableInt> rightControllerIcon;
    private MutableLiveData<ObservableInt> rightControllerBatteryLevel;


    public TrayViewModel(@NonNull Application application) {
        super(application);

        isMaxWindows = new MutableLiveData<>(new ObservableBoolean(true));
        shouldBeVisible = new MutableLiveData<>(new ObservableBoolean(true));
        isKeyboardVisible = new MutableLiveData<>(new ObservableBoolean(false));
        downloadsNumber = new MutableLiveData<>(new ObservableInt(0));
        isVisible = new MediatorLiveData<>();
        isVisible.addSource(shouldBeVisible, mIsVisibleObserver);
        isVisible.addSource(isKeyboardVisible, mIsVisibleObserver);
        isVisible.setValue(new ObservableBoolean(false));
        isTabsWidgetVisible = new MutableLiveData<>(new ObservableBoolean(false));
        time = new MutableLiveData<>();
        pm = new MutableLiveData<>();
        pm = new MutableLiveData<>();
        wifiConnected = new MutableLiveData<>(new ObservableBoolean(true));
        headsetIcon = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_headset_normal));
        headsetBatteryLevel = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_indicator));
        leftControllerIcon = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_controller_generic));
        leftControllerBatteryLevel = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_indicator));
        rightControllerIcon = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_controller_generic));
        rightControllerBatteryLevel = new MutableLiveData<>(new ObservableInt(R.drawable.ic_icon_statusbar_indicator));
    }

    Observer<ObservableBoolean> mIsVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean observableBoolean) {
            boolean shouldShow = shouldBeVisible.getValue().get() && !isKeyboardVisible.getValue().get();
            if (shouldShow != isVisible.getValue().get()) {
                isVisible.setValue(new ObservableBoolean(shouldShow));
            }
        }
    };

    public void refresh() {
        isMaxWindows.setValue(isMaxWindows.getValue());
        shouldBeVisible.setValue(shouldBeVisible.getValue());
        isKeyboardVisible.setValue(isKeyboardVisible.getValue());
        isTabsWidgetVisible.postValue(isTabsWidgetVisible.getValue());
        time.postValue(time.getValue());
        pm.postValue(pm.getValue());
        wifiConnected.postValue(wifiConnected.getValue());
        headsetIcon.setValue(headsetIcon.getValue());
        headsetBatteryLevel.setValue(headsetBatteryLevel.getValue());
        leftControllerIcon.setValue(leftControllerIcon.getValue());
        leftControllerBatteryLevel.setValue(leftControllerBatteryLevel.getValue());
        rightControllerIcon.setValue(rightControllerIcon.getValue());
        rightControllerBatteryLevel.setValue(rightControllerBatteryLevel.getValue());
    }

    public void setIsMaxWindows(boolean isMaxWindows) {
        this.isMaxWindows.setValue(new ObservableBoolean(isMaxWindows));
    }

    public MutableLiveData<ObservableBoolean> getIsMaxWindows() {
        return isMaxWindows;
    }


    public void setShouldBeVisible(boolean shouldBeVisible) {
        this.shouldBeVisible.setValue(new ObservableBoolean(shouldBeVisible));
    }

    public void setIsKeyboardVisible(boolean isVisible) {
        this.isKeyboardVisible.setValue(new ObservableBoolean(isVisible));
    }

    public void setIsTabsWidgetVisible(boolean isTabsWidgetVisible) {
        this.isTabsWidgetVisible.setValue(new ObservableBoolean(isTabsWidgetVisible));
    }

    public MutableLiveData<ObservableBoolean> getIsTabsWidgetVisible() {
        return isTabsWidgetVisible;
    }

    public void setIsVisible(boolean isVisible) {
        this.isVisible.setValue(new ObservableBoolean(isVisible));
    }

    public MutableLiveData<ObservableBoolean> getIsVisible() {
        return isVisible;
    }

    public void setDownloadsNumber(int number) {
        this.downloadsNumber.setValue(new ObservableInt(number));
    }

    public MutableLiveData<ObservableInt> getDownloadsNumber() {
        return downloadsNumber;
    }

    public void setTime(String time) {
        this.time.setValue(time);
    }

    public MutableLiveData<String> getTime() {
        return time;
    }

    public void setPm(String pm) {
        this.pm.setValue(pm);
    }

    public MutableLiveData<String> getPm() {
        return pm;
    }

    public void setWifiConnected(boolean connected) {
        this.wifiConnected.setValue(new ObservableBoolean(connected));
    }

    public MutableLiveData<ObservableBoolean> getWifiConnected() {
        return wifiConnected;
    }

    public void setHeadsetIcon(int image) {
        this.headsetIcon.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getHeadsetIcon() {
        return headsetIcon;
    }

    public void setHeadsetBatteryLevel(int image) {
        this.headsetBatteryLevel.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getHeadsetBatteryLevel() {
        return headsetBatteryLevel;
    }

    public void setLeftControllerIcon(int image) {
        this.leftControllerIcon.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getLeftControllerIcon() {
        return leftControllerIcon;
    }

    public void setLeftControllerBatteryLevel(int image) {
        this.leftControllerBatteryLevel.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getLeftControllerBatteryLevel() {
        return leftControllerBatteryLevel;
    }

    public void setRightControllerIcon(int image) {
        this.rightControllerIcon.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getRightControllerIcon() {
        return rightControllerIcon;
    }

    public void setRightControllerBatteryLevel(int image) {
        this.rightControllerBatteryLevel.setValue(new ObservableInt(image));
    }

    public MutableLiveData<ObservableInt> getRightControllerBatteryLevel() {
        return rightControllerBatteryLevel;
    }
}
