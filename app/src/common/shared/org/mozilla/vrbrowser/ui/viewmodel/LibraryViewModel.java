package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class LibraryViewModel extends AndroidViewModel {

    private MutableLiveData<ObservableBoolean> isLoading;
    private MutableLiveData<ObservableBoolean> isEmpty;
    private MutableLiveData<ObservableBoolean> isNarrow;

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        isLoading = new MutableLiveData<>(new ObservableBoolean(false));
        isEmpty = new MutableLiveData<>(new ObservableBoolean(false));
        isNarrow = new MutableLiveData<>(new ObservableBoolean(false));
    }

    public MutableLiveData<ObservableBoolean> getIsLoading() {
        return isLoading;
    }

    public void setIsLoading(boolean isLoading) {
        this.isLoading.setValue(new ObservableBoolean(isLoading));
    }

    public MutableLiveData<ObservableBoolean> getIsEmpty() {
        return isEmpty;
    }

    public void setIsEmpty(boolean isEmpty) {
        this.isEmpty.setValue(new ObservableBoolean(isEmpty));
    }

    public MutableLiveData<ObservableBoolean> getIsNarrow() {
        return isNarrow;
    }

    public void setIsNarrow(boolean isNarrow) {
        this.isNarrow.setValue(new ObservableBoolean(isNarrow));
    }
}
