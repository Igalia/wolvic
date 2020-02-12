package org.mozilla.vrbrowser.ui.viewmodel;

import android.app.Application;
import android.content.res.Resources;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.widgets.Windows;
import org.mozilla.vrbrowser.utils.ServoUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.Executor;

public class WindowViewModel extends AndroidViewModel {

    private Executor mUIThreadExecutor;

    private int mURLProtocolColor;
    private int mURLWebsiteColor;

    private MutableLiveData<Spannable> url;
    private MutableLiveData<String> hint;
    private MutableLiveData<ObservableBoolean> isWindowVisible;
    private MutableLiveData<Windows.WindowPlacement> placement;
    private MutableLiveData<ObservableBoolean> isOnlyWindow;
    private MutableLiveData<ObservableBoolean> isFullscreen;
    private MediatorLiveData<ObservableBoolean> isTopBarVisible;
    private MutableLiveData<ObservableBoolean> isResizeMode;
    private MutableLiveData<ObservableBoolean> isPrivateSession;
    private MediatorLiveData<ObservableBoolean> showClearButton;
    private MutableLiveData<ObservableBoolean> isInsecure;
    private MutableLiveData<ObservableBoolean> isActiveWindow;
    private MediatorLiveData<ObservableBoolean> isTitleBarVisible;
    private MutableLiveData<ObservableBoolean> isBookmarksVisible;
    private MutableLiveData<ObservableBoolean> isHistoryVisible;
    private MediatorLiveData<ObservableBoolean> isLibraryVisible;
    private MutableLiveData<ObservableBoolean> isLoading;
    private MutableLiveData<ObservableBoolean> isMicrophoneEnabled;
    private MutableLiveData<ObservableBoolean> isBookmarked;
    private MutableLiveData<ObservableBoolean> isFocused;
    private MutableLiveData<ObservableBoolean> isUrlEmpty;
    private MutableLiveData<ObservableBoolean> isPopUpAvailable;
    private MutableLiveData<ObservableBoolean> canGoForward;
    private MutableLiveData<ObservableBoolean> canGoBack;
    private MutableLiveData<ObservableBoolean> isInVRVideo;
    private MutableLiveData<ObservableBoolean> autoEnteredVRVideo;
    private MediatorLiveData<ObservableBoolean> isServoAvailable;
    private MediatorLiveData<String> titleBarUrl;
    private MediatorLiveData<ObservableBoolean> isInsecureVisible;
    private MutableLiveData<ObservableBoolean> isMediaAvailable;
    private MutableLiveData<ObservableBoolean> isMediaPlaying;
    private MediatorLiveData<String> navigationBarUrl;


    public WindowViewModel(Application application) {
        super(application);

        mUIThreadExecutor = ((VRBrowserApplication)application).getExecutors().mainThread();

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = application.getTheme();
        theme.resolveAttribute(R.attr.urlProtocolColor, typedValue, true);
        mURLProtocolColor = typedValue.data;
        theme.resolveAttribute(R.attr.urlWebsiteColor, typedValue, true);
        mURLWebsiteColor = typedValue.data;

        url = new MutableLiveData<>(new SpannableString(""));
        hint = new MutableLiveData<>("");
        isWindowVisible = new MutableLiveData<>(new ObservableBoolean(true));
        placement = new MutableLiveData<>(Windows.WindowPlacement.FRONT);
        isOnlyWindow = new MutableLiveData<>(new ObservableBoolean(false));
        isFullscreen = new MutableLiveData<>(new ObservableBoolean(false));
        isResizeMode = new MutableLiveData<>(new ObservableBoolean(false));
        isPrivateSession = new MutableLiveData<>(new ObservableBoolean(false));

        isTopBarVisible = new MediatorLiveData<>();
        isTopBarVisible.addSource(isOnlyWindow, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isFullscreen, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isResizeMode, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isPrivateSession, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isWindowVisible, mIsTopBarVisibleObserver);
        isTopBarVisible.setValue(new ObservableBoolean(false));

        showClearButton = new MediatorLiveData<>();
        showClearButton.addSource(isOnlyWindow, mShowClearButtonObserver);
        showClearButton.addSource(isPrivateSession, mShowClearButtonObserver);
        showClearButton.addSource(isResizeMode, mShowClearButtonObserver);
        showClearButton.addSource(isFullscreen, mShowClearButtonObserver);
        showClearButton.addSource(isWindowVisible, mShowClearButtonObserver);
        showClearButton.setValue(new ObservableBoolean(false));

        isInsecure = new MutableLiveData<>(new ObservableBoolean(false));
        isActiveWindow = new MutableLiveData<>(new ObservableBoolean(false));

        isTitleBarVisible = new MediatorLiveData<>();
        isTitleBarVisible.addSource(isFullscreen, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isResizeMode, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isActiveWindow, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isWindowVisible, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isOnlyWindow, mIsTitleBarVisibleObserver);
        isTitleBarVisible.setValue(new ObservableBoolean(false));

        isBookmarksVisible = new MutableLiveData<>(new ObservableBoolean(false));
        isHistoryVisible = new MutableLiveData<>(new ObservableBoolean(false));

        isLibraryVisible = new MediatorLiveData<>();
        isLibraryVisible.addSource(isBookmarksVisible, mIsLibraryVisibleObserver);
        isLibraryVisible.addSource(isHistoryVisible, mIsLibraryVisibleObserver);
        isLibraryVisible.setValue(new ObservableBoolean(false));

        isLoading = new MutableLiveData<>(new ObservableBoolean(false));
        isMicrophoneEnabled = new MutableLiveData<>(new ObservableBoolean(true));
        isBookmarked = new MutableLiveData<>(new ObservableBoolean(false));
        isFocused = new MutableLiveData<>(new ObservableBoolean(false));
        isUrlEmpty = new MutableLiveData<>(new ObservableBoolean(true));
        isPopUpAvailable = new MutableLiveData<>(new ObservableBoolean(false));
        canGoForward = new MutableLiveData<>(new ObservableBoolean(false));
        canGoBack = new MutableLiveData<>(new ObservableBoolean(false));
        isInVRVideo = new MutableLiveData<>(new ObservableBoolean(false));
        autoEnteredVRVideo = new MutableLiveData<>(new ObservableBoolean(false));

        isServoAvailable = new MediatorLiveData<>();
        isServoAvailable.addSource(url, mIsServoAvailableObserver);
        isServoAvailable.setValue(new ObservableBoolean(false));

        titleBarUrl = new MediatorLiveData<>();
        titleBarUrl.addSource(url, mTitleBarUrlObserver);
        titleBarUrl.setValue("");

        isInsecureVisible = new MediatorLiveData<>();
        isInsecureVisible.addSource(isInsecure, mIsInsecureVisibleObserver);
        isInsecureVisible.addSource(isPrivateSession, mIsInsecureVisibleObserver);
        isInsecureVisible.addSource(isLibraryVisible, mIsInsecureVisibleObserver);
        isInsecureVisible.setValue(new ObservableBoolean(false));

        isMediaAvailable = new MutableLiveData<>(new ObservableBoolean(false));
        isMediaPlaying = new MutableLiveData<>(new ObservableBoolean(false));

        navigationBarUrl = new MediatorLiveData<>();
        navigationBarUrl.addSource(url, mNavigationBarUrlObserver);
        navigationBarUrl.setValue("");
    }

    private Observer<ObservableBoolean> mIsTopBarVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            if (isFullscreen.getValue().get() || isResizeMode.getValue().get() || !isWindowVisible.getValue().get()) {
                isTopBarVisible.setValue(new ObservableBoolean(false));

            } else {
                if (isOnlyWindow.getValue().get()) {
                    isTopBarVisible.setValue(new ObservableBoolean(isPrivateSession.getValue().get()));

                } else {
                    isTopBarVisible.setValue(new ObservableBoolean(true));
                }
            }
        }
    };

    private Observer<ObservableBoolean> mShowClearButtonObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            showClearButton.setValue(new ObservableBoolean(isWindowVisible.getValue().get() &&
                    isPrivateSession.getValue().get() && isOnlyWindow.getValue().get() &&
                    !isResizeMode.getValue().get() && !isFullscreen.getValue().get()));
        }
    };

    private Observer<ObservableBoolean> mIsTitleBarVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            if (isFullscreen.getValue().get() || isResizeMode.getValue().get() || isActiveWindow.getValue().get()) {
                isTitleBarVisible.setValue(new ObservableBoolean(false));

            } else {
                isTitleBarVisible.setValue(new ObservableBoolean(isWindowVisible.getValue().get() && !isOnlyWindow.getValue().get()));
            }
        }
    };

    private Observer<ObservableBoolean> mIsLibraryVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            isLibraryVisible.setValue(new ObservableBoolean(isBookmarksVisible.getValue().get() || isHistoryVisible.getValue().get()));

            // We use this to force dispatch a title bar and navigation bar URL refresh when library is opened
            url.setValue(url.getValue());
        }
    };

    private Observer<Spannable> mIsServoAvailableObserver = new Observer<Spannable>() {
        @Override
        public void onChanged(Spannable url) {
            boolean isPrefEnabled = SettingsStore.getInstance(getApplication()).isServoEnabled();
            boolean isUrlWhiteListed = ServoUtils.isUrlInServoWhiteList(getApplication(), url.toString());
            isServoAvailable.postValue(new ObservableBoolean(isPrefEnabled && isUrlWhiteListed));
        }
    };

    private Observer<Spannable> mTitleBarUrlObserver = new Observer<Spannable>() {
        @Override
        public void onChanged(Spannable aUrl) {
            String url = aUrl.toString();
            if (isBookmarksVisible.getValue().get()) {
                url = getApplication().getString(R.string.url_bookmarks_title);

            } else if (isHistoryVisible.getValue().get()) {
                url = getApplication().getString(R.string.url_history_title);

            } else {
                if (UrlUtils.isPrivateAboutPage(getApplication(), url) ||
                        (UrlUtils.isDataUri(url) && isPrivateSession.getValue().get())) {
                    url = getApplication().getString(R.string.private_browsing_title);

                } else if (UrlUtils.isHomeUri(getApplication(), aUrl.toString())) {
                    url = getApplication().getString(R.string.url_home_title, getApplication().getString(R.string.app_name));

                } else if (UrlUtils.isBlankUri(getApplication(), aUrl.toString())) {
                    url = "";
                }
            }

            titleBarUrl.setValue(UrlUtils.titleBarUrl(url));
        }
    };

    private Observer<ObservableBoolean> mIsInsecureVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            String aUrl = url.getValue().toString();
            if (isInsecure.getValue().get()) {
                if (UrlUtils.isPrivateAboutPage(getApplication(), aUrl) ||
                        (UrlUtils.isDataUri(aUrl) && isPrivateSession.getValue().get()) ||
                        UrlUtils.isHomeUri(getApplication(), aUrl) ||
                        isLibraryVisible.getValue().get() ||
                        UrlUtils.isBlankUri(getApplication(), aUrl)) {
                    isInsecureVisible.setValue(new ObservableBoolean(false));

                } else {
                    isInsecureVisible.setValue(new ObservableBoolean(true));
                }

            } else {
                isInsecureVisible.setValue(new ObservableBoolean(false));
            }
        }
    };

    private Observer<Spannable> mNavigationBarUrlObserver = new Observer<Spannable>() {
        @Override
        public void onChanged(Spannable aUrl) {
            String url = aUrl.toString();
            if (UrlUtils.isPrivateAboutPage(getApplication(), url) ||
                    (UrlUtils.isDataUri(url) && isPrivateSession.getValue().get()) ||
                    UrlUtils.isHomeUri(getApplication(), aUrl.toString()) ||
                    isLibraryVisible.getValue().get() ||
                    UrlUtils.isBlankUri(getApplication(), aUrl.toString())) {
                navigationBarUrl.setValue("");

            } else {
                navigationBarUrl.setValue(url);
            }

            if (isBookmarksVisible.getValue().get()) {
                hint.setValue(getApplication().getString(R.string.url_bookmarks_title));

            } else if (isHistoryVisible.getValue().get()) {
                hint.setValue(getApplication().getString(R.string.url_history_title));

            } else {
                hint.setValue(getApplication().getString(R.string.search_placeholder));
            }
        }
    };

    public void refresh() {
        url.postValue(url.getValue());
        isWindowVisible.postValue(isWindowVisible.getValue());
        placement.postValue(placement.getValue());
        isOnlyWindow.postValue(isOnlyWindow.getValue());
        isFullscreen.postValue(isFullscreen.getValue());
        isResizeMode.postValue(isResizeMode.getValue());
        isPrivateSession.postValue(isPrivateSession.getValue());
        isInsecure.postValue(isInsecure.getValue());
        isLoading.postValue(isLoading.getValue());
        isMicrophoneEnabled.postValue(isMicrophoneEnabled.getValue());
        isBookmarked.postValue(isBookmarked.getValue());
        isFocused.postValue(isFocused.getValue());
        isUrlEmpty.postValue(isUrlEmpty.getValue());
        isPopUpAvailable.postValue(isPopUpAvailable.getValue());
        canGoForward.postValue(canGoForward.getValue());
        canGoBack.postValue(canGoBack.getValue());
        isInVRVideo.postValue(isInVRVideo.getValue());
        autoEnteredVRVideo.postValue(autoEnteredVRVideo.getValue());
        isMediaAvailable.postValue(isMediaAvailable.getValue());
        isMediaPlaying.postValue(isMediaPlaying.getValue());
    }

    @NonNull
    public MutableLiveData<Spannable> getUrl() {
        if (url == null) {
            url = new MutableLiveData<>(new SpannableString(""));
        }
        return url;
    }

    public void setUrl(@Nullable String url) {
        if (url == null) {
            return;
        }
        setUrl(new SpannableString(url));
    }

    public void setUrl(@Nullable Spannable url) {
        if (url == null) {
            return;
        }

        String aURL = url.toString();

        if (isLibraryVisible.getValue().get()) {
            return;
        }

        int index = -1;
        try {
            aURL = URLDecoder.decode(aURL, "UTF-8");

        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            e.printStackTrace();
            aURL = "";
        }
        if (aURL.startsWith("jar:")) {
            return;

        } else if (aURL.startsWith("resource:") || UrlUtils.isHomeUri(getApplication().getBaseContext(), aURL)) {
            aURL = "";

        } else if (aURL.startsWith("data:") && isPrivateSession.getValue().get()) {
            aURL = "";

        } else if (aURL.startsWith(getApplication().getBaseContext().getString(R.string.about_blank))) {
            aURL = "";

        } else {
            index = aURL.indexOf("://");
        }

        // Update the URL bar only if the URL is different than the current one and
        // the URL bar is not focused to avoid override user input
        if (!getUrl().getValue().toString().equalsIgnoreCase(aURL) && !getIsFocused().getValue().get()) {
            this.url.setValue(new SpannableString(aURL));
            if (index > 0) {
                SpannableString spannable = new SpannableString(aURL);
                ForegroundColorSpan color1 = new ForegroundColorSpan(mURLProtocolColor);
                ForegroundColorSpan color2 = new ForegroundColorSpan(mURLWebsiteColor);
                spannable.setSpan(color1, 0, index + 3, 0);
                spannable.setSpan(color2, index + 3, aURL.length(), 0);
                this.url.setValue(url);

            } else {
                this.url.setValue(url);
            }
        }

        this.url.setValue(url);
    }

    @NonNull
    public MutableLiveData<String> getHint() {
        if (hint == null) {
            hint = new MutableLiveData<>("");
        }
        return hint;
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsWindowVisible() {
        return isWindowVisible;
    }

    public void setIsWindowVisible(boolean isWindowVisible) {
        this.isWindowVisible.postValue(new ObservableBoolean(isWindowVisible));
    }

    @NonNull
    public MutableLiveData<Windows.WindowPlacement> getPlacement() {
        return placement;
    }

    public void setPlacement(Windows.WindowPlacement placement) {
        this.placement.postValue(placement);
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsOnlyWindow() {
        return isOnlyWindow;
    }

    public void setIsOnlyWindow(boolean isOnlyWindow) {
        this.isOnlyWindow.postValue(new ObservableBoolean(isOnlyWindow));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsFullscreen() {
        return isFullscreen;
    }

    public void setIsFullscreen(boolean isFullscreen) {
        this.isFullscreen.postValue(new ObservableBoolean(isFullscreen));
    }

    @NonNull
    public MediatorLiveData<ObservableBoolean> getIsTopBarVisible() {
        return isTopBarVisible;
    }

    public void setIsTopBarVisible(boolean isTopBarVisible) {
        this.isTopBarVisible.postValue(new ObservableBoolean(isTopBarVisible));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsResizeMode() {
        return isResizeMode;
    }

    public void setIsResizeMode(boolean isResizeMode) {
        this.isResizeMode.postValue(new ObservableBoolean(isResizeMode));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsPrivateSession() {
        return isPrivateSession;
    }

    public void setIsPrivateSession(boolean isPrivateSession) {
        this.isPrivateSession.postValue(new ObservableBoolean(isPrivateSession));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getShowClearButton() {
        return showClearButton;
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsInsecure() {
        return isInsecure;
    }

    public void setIsInsecure(boolean isInsecure) {
        this.isInsecure.postValue(new ObservableBoolean(isInsecure));
    }

    @NonNull
    public MediatorLiveData<ObservableBoolean> getIsTitleBarVisible() {
        return isTitleBarVisible;
    }

    public void setIsTitleBarVisible(boolean isTitleBarVisible) {
        this.isTitleBarVisible.postValue(new ObservableBoolean(isTitleBarVisible));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsActiveWindow() {
        return isActiveWindow;
    }

    public void setIsActiveWindow(boolean isActiveWindow) {
        this.isActiveWindow.postValue(new ObservableBoolean(isActiveWindow));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsBookmraksVisible() {
        return isBookmarksVisible;
    }

    public void setIsBookmarksVisible(boolean isBookmarksVisible) {
        this.isBookmarksVisible.postValue(new ObservableBoolean(isBookmarksVisible));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsHistoryVisible() {
        return isHistoryVisible;
    }

    public void setIsHistoryVisible(boolean isHistoryVisible) {
        this.isHistoryVisible.postValue(new ObservableBoolean(isHistoryVisible));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsLibraryVisible() {
        return isLibraryVisible;
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsLoading() {
        return isLoading;
    }

    public void setIsLoading(boolean isLoading) {
        this.isLoading.postValue(new ObservableBoolean(isLoading));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsMicrophoneEnabled() {
        return isMicrophoneEnabled;
    }

    public void setIsMicrophoneEnabled(boolean isMicrophoneEnabled) {
        this.isMicrophoneEnabled.postValue(new ObservableBoolean(isMicrophoneEnabled));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsBookmarked() {
        return isBookmarked;
    }

    public void setIsBookmarked(boolean isBookmarked) {
        this.isBookmarked.setValue(new ObservableBoolean(isBookmarked));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsFocused() {
        return isFocused;
    }

    public void setIsFocused(boolean isFocused) {
        this.isFocused.postValue(new ObservableBoolean(isFocused));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsUrlEmpty() {
        return isUrlEmpty;
    }

    public void setIsUrlEmpty(boolean isUrlEmpty) {
        this.isUrlEmpty.postValue(new ObservableBoolean(isUrlEmpty));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsPopUpAvailable() {
        return isPopUpAvailable;
    }

    public void setIsPopUpAvailable(boolean isPopUpAvailable) {
        this.isPopUpAvailable.postValue(new ObservableBoolean(isPopUpAvailable));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getCanGoForward() {
        return canGoForward;
    }

    public void setCanGoForward(boolean canGoForward) {
        this.canGoForward.postValue(new ObservableBoolean(canGoForward));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getCanGoBack() {
        return canGoBack;
    }

    public void setCanGoBack(boolean canGoBack) {
        this.canGoBack.postValue(new ObservableBoolean(canGoBack));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsInVRVideo() {
        return isInVRVideo;
    }

    public void setIsInVRVideo(boolean isInVRVideo) {
        this.isInVRVideo.postValue(new ObservableBoolean(isInVRVideo));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getAutoEnteredVRVideo() {
        return autoEnteredVRVideo;
    }

    public void setAutoEnteredVRVideo(boolean autoEnteredVRVideo) {
        this.autoEnteredVRVideo.postValue(new ObservableBoolean(autoEnteredVRVideo));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsServoAvailable() {
        return isServoAvailable;
    }

    @NonNull
    public MediatorLiveData<String> getTitleBarUrl() {
        return titleBarUrl;
    }

    @NonNull
    public MediatorLiveData<ObservableBoolean> getIsInsecureVisible() {
        return isInsecureVisible;
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsMediaAvailable() {
        return isMediaAvailable;
    }

    public void setIsMediaAvailable(boolean isMediaAvailable) {
        this.isMediaAvailable.postValue(new ObservableBoolean(isMediaAvailable));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsMediaPlaying() {
        return isMediaPlaying;
    }

    public void setIsMediaPlaying(boolean isMediaPlaying) {
        this.isMediaPlaying.postValue(new ObservableBoolean(isMediaPlaying));
    }

    @NonNull
    public MutableLiveData<String> getNavigationBarUrl() {
        return navigationBarUrl;
    }
}
