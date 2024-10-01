package com.igalia.wolvic.ui.viewmodel;

import android.app.Application;
import android.content.res.Resources;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableInt;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.ui.widgets.Windows;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class WindowViewModel extends AndroidViewModel {

    private static final String LOGTAG = SystemUtils.createLogtag(WindowViewModel.class);

    private int mURLProtocolColor;
    private int mURLWebsiteColor;

    private MutableLiveData<Spannable> url;
    private MutableLiveData<String> hint;
    private MutableLiveData<ObservableBoolean> isWindowVisible;
    private MutableLiveData<Windows.WindowPlacement> placement;
    private MutableLiveData<ObservableBoolean> isOnlyWindow;
    private MutableLiveData<ObservableBoolean> isFullscreen;
    private MutableLiveData<ObservableBoolean> isCurved;
    private MutableLiveData<ObservableBoolean> isKioskMode;
    private MutableLiveData<ObservableBoolean> isDesktopMode;
    private MediatorLiveData<ObservableBoolean> isTopBarVisible;
    private MediatorLiveData<ObservableBoolean> isTabsBarVisible;
    private MutableLiveData<ObservableBoolean> isResizeMode;
    private MutableLiveData<ObservableBoolean> isPrivateSession;
    private MediatorLiveData<ObservableBoolean> showClearButton;
    private MutableLiveData<ObservableBoolean> isInsecure;
    private MutableLiveData<ObservableBoolean> isActiveWindow;
    private MediatorLiveData<ObservableBoolean> isTitleBarVisible;
    private MutableLiveData<Windows.ContentType> currentContentType;
    private MediatorLiveData<ObservableBoolean> isNativeContentVisible;
    private MutableLiveData<ObservableBoolean> isLoading;
    private MutableLiveData<ObservableBoolean> isMicrophoneEnabled;
    private MutableLiveData<ObservableBoolean> isBookmarked;
    private MutableLiveData<ObservableBoolean> isWebApp;
    private MutableLiveData<ObservableBoolean> isFocused;
    private MutableLiveData<ObservableBoolean> isUrlEmpty;
    private MutableLiveData<ObservableBoolean> isFindInPage;
    private MutableLiveData<ObservableBoolean> isPopUpAvailable;
    private MutableLiveData<ObservableBoolean> isPopUpBlocked;
    private MutableLiveData<ObservableBoolean> canGoForward;
    private MutableLiveData<ObservableBoolean> canGoBack;
    private MutableLiveData<ObservableBoolean> isInVRVideo;
    private MutableLiveData<ObservableBoolean> autoEnteredVRVideo;
    private MediatorLiveData<String> titleBarUrl;
    private MediatorLiveData<ObservableBoolean> isInsecureVisible;
    private MutableLiveData<ObservableBoolean> isMediaAvailable;
    private MutableLiveData<ObservableBoolean> isMediaPlaying;
    private MediatorLiveData<String> navigationBarUrl;
    private MutableLiveData<ObservableBoolean> isWebXRUsed;
    private MutableLiveData<ObservableBoolean> isWebXRBlocked;
    private MutableLiveData<ObservableBoolean> isTrackingEnabled;
    private MutableLiveData<ObservableBoolean> isDrmUsed;
    private MediatorLiveData<ObservableBoolean> isUrlBarButtonsVisible;
    private MediatorLiveData<ObservableBoolean> isUrlBarIconsVisible;
    private MutableLiveData<ObservableInt> mWidth;
    private MutableLiveData<ObservableInt> mHeight;

    public WindowViewModel(Application application) {
        super(application);

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
        isCurved = new MutableLiveData<>(new ObservableBoolean(false));
        isKioskMode = new MutableLiveData<>(new ObservableBoolean(false));
        isDesktopMode = new MutableLiveData<>(new ObservableBoolean(false));
        isResizeMode = new MutableLiveData<>(new ObservableBoolean(false));
        isPrivateSession = new MutableLiveData<>(new ObservableBoolean(false));

        isTopBarVisible = new MediatorLiveData<>();
        isTopBarVisible.addSource(isOnlyWindow, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isFullscreen, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isKioskMode, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isResizeMode, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isPrivateSession, mIsTopBarVisibleObserver);
        isTopBarVisible.addSource(isWindowVisible, mIsTopBarVisibleObserver);
        isTopBarVisible.setValue(new ObservableBoolean(true));

        showClearButton = new MediatorLiveData<>();
        showClearButton.addSource(isOnlyWindow, mShowClearButtonObserver);
        showClearButton.addSource(isPrivateSession, mShowClearButtonObserver);
        showClearButton.addSource(isResizeMode, mShowClearButtonObserver);
        showClearButton.addSource(isFullscreen, mShowClearButtonObserver);
        showClearButton.addSource(isKioskMode, mShowClearButtonObserver);
        showClearButton.addSource(isWindowVisible, mShowClearButtonObserver);
        showClearButton.setValue(new ObservableBoolean(false));

        isInsecure = new MutableLiveData<>(new ObservableBoolean(false));
        isActiveWindow = new MutableLiveData<>(new ObservableBoolean(false));

        isTitleBarVisible = new MediatorLiveData<>();
        isTitleBarVisible.addSource(isFullscreen, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isKioskMode, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isResizeMode, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isActiveWindow, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isWindowVisible, mIsTitleBarVisibleObserver);
        isTitleBarVisible.addSource(isOnlyWindow, mIsTitleBarVisibleObserver);
        isTitleBarVisible.setValue(new ObservableBoolean(true));

        currentContentType = new MutableLiveData<>(Windows.ContentType.WEB_CONTENT);
        isNativeContentVisible = new MediatorLiveData<>();
        isNativeContentVisible.addSource(currentContentType, contentType ->
                isNativeContentVisible.setValue(new ObservableBoolean(contentType != Windows.ContentType.WEB_CONTENT))
        );
        isNativeContentVisible.setValue(new ObservableBoolean(currentContentType.getValue() != Windows.ContentType.WEB_CONTENT));

        isLoading = new MutableLiveData<>(new ObservableBoolean(false));
        isMicrophoneEnabled = new MutableLiveData<>(new ObservableBoolean(true));
        isBookmarked = new MutableLiveData<>(new ObservableBoolean(false));
        isWebApp = new MutableLiveData<>(new ObservableBoolean(false));
        isFocused = new MutableLiveData<>(new ObservableBoolean(false));
        isUrlEmpty = new MutableLiveData<>(new ObservableBoolean(true));
        isFindInPage = new MutableLiveData<>(new ObservableBoolean(false));
        isPopUpAvailable = new MutableLiveData<>(new ObservableBoolean(false));
        isPopUpBlocked = new MutableLiveData<>(new ObservableBoolean(false));
        canGoForward = new MutableLiveData<>(new ObservableBoolean(false));
        canGoBack = new MutableLiveData<>(new ObservableBoolean(false));
        isInVRVideo = new MutableLiveData<>(new ObservableBoolean(false));
        autoEnteredVRVideo = new MutableLiveData<>(new ObservableBoolean(false));

        titleBarUrl = new MediatorLiveData<>();
        titleBarUrl.addSource(url, mTitleBarUrlObserver);
        titleBarUrl.setValue("");

        isTabsBarVisible = new MediatorLiveData<>();
        isTabsBarVisible.addSource(isActiveWindow, mIsTabsBarVisibleObserver);
        isTabsBarVisible.addSource(isFullscreen, mIsTabsBarVisibleObserver);
        isTabsBarVisible.addSource(isKioskMode, mIsTabsBarVisibleObserver);
        isTabsBarVisible.addSource(isResizeMode, mIsTabsBarVisibleObserver);
        isTabsBarVisible.addSource(isWindowVisible, mIsTabsBarVisibleObserver);
        isTabsBarVisible.setValue(new ObservableBoolean(true));

        isInsecureVisible = new MediatorLiveData<>();
        isInsecureVisible.addSource(isInsecure, mIsInsecureVisibleObserver);
        isInsecureVisible.addSource(isPrivateSession, mIsInsecureVisibleObserver);
        isInsecureVisible.addSource(isNativeContentVisible, mIsInsecureVisibleObserver);
        isInsecureVisible.setValue(new ObservableBoolean(false));

        isMediaAvailable = new MutableLiveData<>(new ObservableBoolean(false));
        isMediaPlaying = new MutableLiveData<>(new ObservableBoolean(false));

        navigationBarUrl = new MediatorLiveData<>();
        navigationBarUrl.addSource(url, mNavigationBarUrlObserver);
        navigationBarUrl.setValue("");

        isWebXRUsed = new MutableLiveData<>(new ObservableBoolean(false));
        isWebXRBlocked = new MutableLiveData<>(new ObservableBoolean(false));

        isTrackingEnabled = new MutableLiveData<>(new ObservableBoolean(true));
        isDrmUsed = new MutableLiveData<>(new ObservableBoolean(false));

        isUrlBarButtonsVisible = new MediatorLiveData<>();
        isUrlBarButtonsVisible.addSource(isTrackingEnabled, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.addSource(isDrmUsed, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.addSource(isPopUpAvailable, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.addSource(isWebXRUsed, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.addSource(isNativeContentVisible, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.addSource(isFocused, mIsUrlBarButtonsVisibleObserver);
        isUrlBarButtonsVisible.setValue(new ObservableBoolean(false));

        isUrlBarIconsVisible = new MediatorLiveData<>();
        isUrlBarIconsVisible.addSource(isLoading, mIsUrlBarIconsVisibleObserver);
        isUrlBarIconsVisible.addSource(isInsecureVisible, mIsUrlBarIconsVisibleObserver);
        isUrlBarIconsVisible.addSource(isNativeContentVisible, mIsUrlBarIconsVisibleObserver);
        isUrlBarIconsVisible.setValue(new ObservableBoolean(false));

        mWidth = new MutableLiveData<>(new ObservableInt());
        mHeight = new MutableLiveData<>(new ObservableInt());
    }

    private Observer<ObservableBoolean> mIsTopBarVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            if (isFullscreen.getValue().get() || isKioskMode.getValue().get() || isResizeMode.getValue().get() || !isWindowVisible.getValue().get()) {
                isTopBarVisible.postValue(new ObservableBoolean(false));

            } else {
                if (isOnlyWindow.getValue().get()) {
                    isTopBarVisible.postValue(new ObservableBoolean(isPrivateSession.getValue().get()));

                } else {
                    isTopBarVisible.postValue(new ObservableBoolean(true));
                }
            }
        }
    };

    private Observer<ObservableBoolean> mIsTabsBarVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            if (!isActiveWindow.getValue().get() || isFullscreen.getValue().get() || isKioskMode.getValue().get() || isResizeMode.getValue().get() || !isWindowVisible.getValue().get()) {
                isTabsBarVisible.postValue(new ObservableBoolean(false));

            } else {
                isTabsBarVisible.postValue(new ObservableBoolean(true));
            }
        }
    };

    private Observer<ObservableBoolean> mShowClearButtonObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            showClearButton.postValue(new ObservableBoolean(isWindowVisible.getValue().get() &&
                    isPrivateSession.getValue().get() && isOnlyWindow.getValue().get() &&
                    !isResizeMode.getValue().get() && !isFullscreen.getValue().get() &&
                    !isKioskMode.getValue().get()));
        }
    };

    private Observer<ObservableBoolean> mIsTitleBarVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            if (isFullscreen.getValue().get() || isKioskMode.getValue().get() || isResizeMode.getValue().get() || isActiveWindow.getValue().get()) {
                isTitleBarVisible.postValue(new ObservableBoolean(false));

            } else {
                isTitleBarVisible.postValue(new ObservableBoolean(isWindowVisible.getValue().get() && !isOnlyWindow.getValue().get()));
            }
        }
    };

    private Observer<Spannable> mTitleBarUrlObserver = new Observer<Spannable>() {
        @Override
        public void onChanged(Spannable aUrl) {
            String url = aUrl.toString();
            if (isNativeContentVisible.getValue().get()) {
                url = getApplication().getString(R.string.url_library_title);

            } else {
                if (UrlUtils.isPrivateAboutPage(getApplication(), url) ||
                        (UrlUtils.isDataUri(url) && isPrivateSession.getValue().get())) {
                    url = getApplication().getString(R.string.private_browsing_title);

                } else if (UrlUtils.isHomeUri(getApplication(), aUrl.toString())) {
                    url = getApplication().getString(R.string.url_home_title, getApplication().getString(R.string.app_name));

                } else if (UrlUtils.isWebExtensionUrl(aUrl.toString())) {
                    url = getApplication().getString(R.string.web_extensions_title);

                } else if (UrlUtils.isBlankUri(getApplication(), aUrl.toString())) {
                    url = "";
                }
            }

            titleBarUrl.postValue(UrlUtils.titleBarUrl(url));
        }
    };

    private Observer<ObservableBoolean> mIsInsecureVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            String aUrl = url.getValue().toString();
            if (isInsecure.getValue().get()) {
                if (UrlUtils.isPrivateAboutPage(getApplication(), aUrl) ||
                        (UrlUtils.isDataUri(aUrl) && isPrivateSession.getValue().get()) ||
                        UrlUtils.isFileUri(aUrl) ||
                        UrlUtils.isHomeUri(getApplication(), aUrl) ||
                        isNativeContentVisible.getValue().get() ||
                        UrlUtils.isBlankUri(getApplication(), aUrl)) {
                    isInsecureVisible.postValue(new ObservableBoolean(false));

                } else {
                    isInsecureVisible.postValue(new ObservableBoolean(true));
                }

            } else {
                isInsecureVisible.postValue(new ObservableBoolean(false));
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
                    isNativeContentVisible.getValue().get() ||
                    UrlUtils.isBlankUri(getApplication(), aUrl.toString())) {
                navigationBarUrl.postValue("");

            } else {
                navigationBarUrl.postValue(url);
            }
        }
    };

    private Observer<ObservableBoolean> mIsUrlBarButtonsVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            String aUrl = url.getValue().toString();
            isUrlBarButtonsVisible.postValue(new ObservableBoolean(
                    !isFocused.getValue().get() &&
                            !isNativeContentVisible.getValue().get() &&
                            !UrlUtils.isContentFeed(getApplication(), aUrl) &&
                            !UrlUtils.isPrivateAboutPage(getApplication(), aUrl) &&
                            (URLUtil.isHttpUrl(aUrl) || URLUtil.isHttpsUrl(aUrl)) &&
                            (
                                    (SettingsStore.getInstance(getApplication()).getTrackingProtectionLevel() != WContentBlocking.EtpLevel.NONE) ||
                                    isPopUpAvailable.getValue().get() ||
                                    isDrmUsed.getValue().get() ||
                                    isWebXRUsed.getValue().get()
                            )
            ));
            hint.postValue(getHintValue());
        }
    };

    private Observer<ObservableBoolean> mIsUrlBarIconsVisibleObserver = new Observer<ObservableBoolean>() {
        @Override
        public void onChanged(ObservableBoolean o) {
            isUrlBarIconsVisible.postValue(new ObservableBoolean(
                    !isNativeContentVisible.getValue().get() &&
                            (isLoading.getValue().get() ||
                                    isInsecureVisible.getValue().get())
            ));
        }
    };

    public void refresh() {
        url.postValue(url.getValue());
        hint.postValue(getHintValue());
        isWindowVisible.postValue(isWindowVisible.getValue());
        placement.postValue(placement.getValue());
        isOnlyWindow.postValue(isOnlyWindow.getValue());
        isResizeMode.postValue(isResizeMode.getValue());
        isPrivateSession.postValue(isPrivateSession.getValue());
        isInsecure.postValue(isInsecure.getValue());
        isLoading.postValue(isLoading.getValue());
        isMicrophoneEnabled.postValue(isMicrophoneEnabled.getValue());
        isBookmarked.postValue(isBookmarked.getValue());
        isFocused.postValue(isFocused.getValue());
        isUrlEmpty.postValue(isUrlEmpty.getValue());
        isFindInPage.postValue(isFindInPage.getValue());
        isPopUpAvailable.postValue(isPopUpAvailable.getValue());
        isPopUpBlocked.postValue(isPopUpBlocked.getValue());
        canGoForward.postValue(canGoForward.getValue());
        canGoBack.postValue(canGoBack.getValue());
        isInVRVideo.postValue(isInVRVideo.getValue());
        autoEnteredVRVideo.postValue(autoEnteredVRVideo.getValue());
        titleBarUrl.setValue(titleBarUrl.getValue());
        isMediaAvailable.postValue(isMediaAvailable.getValue());
        isMediaPlaying.postValue(isMediaPlaying.getValue());
        isWebXRUsed.postValue(isWebXRUsed.getValue());
        isWebXRBlocked.postValue(isWebXRBlocked.getValue());
        isTrackingEnabled.postValue(isTrackingEnabled.getValue());
        isDrmUsed.postValue(isDrmUsed.getValue());
        mWidth.postValue(mWidth.getValue());
        mHeight.postValue(mHeight.getValue());
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

    private void setUrl(@Nullable Spannable url) {
        if (url == null) {
            return;
        }

        String aURL;
        try {
            aURL = URLDecoder.decode(url.toString(), "UTF-8");

        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            Log.w(LOGTAG, "Unable to decode URL [ " + url + " ] : " + e);
            aURL = "";
        }
        int index = -1;
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
            if (index > 0) {
                SpannableString spannable = new SpannableString(aURL);
                ForegroundColorSpan color1 = new ForegroundColorSpan(mURLProtocolColor);
                ForegroundColorSpan color2 = new ForegroundColorSpan(mURLWebsiteColor);
                spannable.setSpan(color1, 0, index + 3, 0);
                spannable.setSpan(color2, index + 3, aURL.length(), 0);
                this.url.postValue(spannable);

            } else {
                this.url.postValue(url);
            }
        }
    }

    @NonNull
    public MutableLiveData<String> getHint() {
        return hint;
    }

    private String getHintValue() {
        if (isNativeContentVisible.getValue().get()) {
            return getApplication().getString(R.string.url_library_title);

        } else {
            return getApplication().getString(R.string.search_placeholder);
        }
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
    public MutableLiveData<ObservableBoolean> getIsCurved() {
        return isCurved;
    }

    public void setIsCurved(boolean isCurved) {
        this.isCurved.postValue(new ObservableBoolean(isCurved));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsKioskMode() {
        return isKioskMode;
    }

    public void setIsKioskMode(boolean isKioskMode) {
        // setValue changes the data immediately, but must be called from the main thread
        // FIXME rework the execution flow so only postValue() is needed
        if (Looper.getMainLooper().isCurrentThread()) {
            this.isKioskMode.setValue(new ObservableBoolean(isKioskMode));
        } else {
            this.isKioskMode.postValue(new ObservableBoolean(isKioskMode));
        }
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsDesktopMode() {
        return isDesktopMode;
    }

    public void setIsDesktopMode(boolean isDesktopMode) {
        this.isDesktopMode.postValue(new ObservableBoolean(isDesktopMode));
    }

    @NonNull
    public MediatorLiveData<ObservableBoolean> getIsTopBarVisible() {
        return isTopBarVisible;
    }

    public void setIsTopBarVisible(boolean isTopBarVisible) {
        this.isTopBarVisible.postValue(new ObservableBoolean(isTopBarVisible));
    }

    @NonNull
    public MediatorLiveData<ObservableBoolean> getIsTabsBarVisible() {
        return isTabsBarVisible;
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
        this.isActiveWindow.setValue(new ObservableBoolean(isActiveWindow));
    }

    public void setCurrentContentType(Windows.ContentType contentType) {
        currentContentType.postValue(contentType);
    }

    @NonNull
    public MutableLiveData<Windows.ContentType> getCurrentContentType() {
        return currentContentType;
    }

    public MutableLiveData<ObservableBoolean> getIsNativeContentVisible() {
        return isNativeContentVisible;
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
        this.isBookmarked.postValue(new ObservableBoolean(isBookmarked));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsWebApp() {
        return isWebApp;
    }

    public void setIsWebApp(boolean isWebApp) {
        this.isWebApp.postValue(new ObservableBoolean(isWebApp));
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
    public MutableLiveData<ObservableBoolean> getIsFindInPage() {
        return isFindInPage;
    }

    public void setIsFindInPage(boolean isFindInPage) {
        this.isFindInPage.postValue(new ObservableBoolean(isFindInPage));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsWebXRUsed() {
        return isWebXRUsed;
    }

    public void setIsWebXRUsed(boolean used) {
        this.isWebXRUsed.postValue(new ObservableBoolean(used));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsWebXRBlocked() {
        return isWebXRBlocked;
    }

    public void setIsWebXRBlocked(boolean blocked) {
        this.isWebXRBlocked.postValue(new ObservableBoolean(blocked));
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

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsPopUpAvailable() {
        return isPopUpAvailable;
    }

    public void setIsPopUpAvailable(boolean isPopUpAvailable) {
        this.isPopUpAvailable.postValue(new ObservableBoolean(isPopUpAvailable));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsPopUpBlocked() {
        return isPopUpBlocked;
    }

    public void setIsPopUpBlocked(boolean isPopUpBlocked) {
        this.isPopUpBlocked.postValue(new ObservableBoolean(isPopUpBlocked));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsTrackingEnabled() {
        return isTrackingEnabled;
    }

    public void setIsTrackingEnabled(boolean isTrackingEnabled) {
        this.isTrackingEnabled.postValue(new ObservableBoolean(isTrackingEnabled));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsDrmUsed() {
        return isDrmUsed;
    }

    public void setIsDrmUsed(boolean isEnabled) {
        this.isDrmUsed.postValue(new ObservableBoolean(isEnabled));
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsUrlBarButtonsVisible() {
        return isUrlBarButtonsVisible;
    }

    @NonNull
    public MutableLiveData<ObservableBoolean> getIsUrlBarIconsVisible() {
        return isUrlBarIconsVisible;
    }

    @NonNull
    public MutableLiveData<ObservableInt> getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth.setValue(new ObservableInt(width));
    }

    @NonNull
    public MutableLiveData<ObservableInt> getHeight() {
        return mHeight;
    }

    public void setHeight(int height) {
        this.mHeight.setValue(new ObservableInt(height));
    }
}
