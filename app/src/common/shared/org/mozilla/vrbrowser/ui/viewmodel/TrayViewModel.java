package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableInt;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

public class TrayViewModel extends AndroidViewModel {

    private MutableLiveData<ObservableBoolean> isMaxWindows;
    private MutableLiveData<ObservableBoolean> shouldBeVisible;
    private MutableLiveData<ObservableBoolean> isKeyboardVisible;
    private MutableLiveData<ObservableInt> downloadsNumber;
    private MediatorLiveData<ObservableBoolean> isVisible;

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

}
