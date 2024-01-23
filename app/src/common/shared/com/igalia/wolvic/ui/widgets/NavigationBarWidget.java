/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import static com.igalia.wolvic.db.SitePermission.SITE_PERMISSION_DRM;
import static com.igalia.wolvic.db.SitePermission.SITE_PERMISSION_POPUP;
import static com.igalia.wolvic.db.SitePermission.SITE_PERMISSION_TRACKING;
import static com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget.VIDEO_PROJECTION_NONE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.URLUtil;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.WebAppsStore;
import com.igalia.wolvic.browser.api.WMediaSession;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.content.TrackingProtectionStore;
import com.igalia.wolvic.browser.engine.Session;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.databinding.NavigationBarBinding;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.search.suggestions.SuggestionsProvider;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.viewmodel.TrayViewModel;
import com.igalia.wolvic.ui.viewmodel.WindowViewModel;
import com.igalia.wolvic.ui.views.NavigationURLBar;
import com.igalia.wolvic.ui.views.UIButton;
import com.igalia.wolvic.ui.views.UITextButton;
import com.igalia.wolvic.ui.widgets.NotificationManager.Notification.NotificationPosition;
import com.igalia.wolvic.ui.widgets.dialogs.InstallWebAppDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.PromptDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.QuickPermissionWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SelectionActionWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SendTabDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.VoiceSearchWidget;
import com.igalia.wolvic.ui.widgets.menus.BrightnessMenuWidget;
import com.igalia.wolvic.ui.widgets.menus.HamburgerMenuWidget;
import com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget;
import com.igalia.wolvic.utils.AnimationHelper;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class NavigationBarWidget extends UIWidget implements WSession.NavigationDelegate,
        WSession.ContentDelegate, WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.UpdateListener, SessionChangeListener,
        NavigationURLBar.NavigationURLBarDelegate, VoiceSearchWidget.VoiceSearchDelegate,
        SharedPreferences.OnSharedPreferenceChangeListener, SuggestionsWidget.URLBarPopupDelegate,
        TrayListener, WindowWidget.WindowListener {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    private static final int TAB_ADDED_NOTIFICATION_ID = 0;
    private static final int TAB_SENT_NOTIFICATION_ID = 1;
    private static final int BOOKMARK_ADDED_NOTIFICATION_ID = 2;
    private static final int POPUP_NOTIFICATION_ID = 3;
    private static final int WEB_APP_ADDED_NOTIFICATION_ID = 4;

    public interface NavigationListener {
        void onBack();
        void onForward();
        void onReload();
        void onStop();
        void onHome();
    }

    private WindowViewModel mViewModel;
    private TrayViewModel mTrayViewModel;
    private SettingsViewModel mSettingsViewModel;
    private NavigationBarBinding mBinding;
    private AudioEngine mAudio;
    private WindowWidget mAttachedWindow;
    private Runnable mResizeBackHandler;
    private Runnable mFullScreenBackHandler;
    private Runnable mVRVideoBackHandler;
    private VoiceSearchWidget mVoiceSearchWidget;
    private Context mAppContext;
    private SharedPreferences mPrefs;
    private SuggestionsWidget mAwesomeBar;
    private SuggestionsProvider mSuggestionsProvider;
    private VideoProjectionMenuWidget mProjectionMenu;
    private WidgetPlacement mProjectionMenuPlacement;
    private BrightnessMenuWidget mBrightnessWidget;
    private MediaControlsWidget mMediaControlsWidget;
    private Media mFullScreenMedia;
    private @VideoProjectionMenuWidget.VideoProjectionFlags int mAutoSelectedProjection = VIDEO_PROJECTION_NONE;
    private HamburgerMenuWidget mHamburgerMenu;
    private QuickPermissionWidget mQuickPermissionWidget;
    private SendTabDialogWidget mSendTabDialog;
    private int mBlockedCount;
    private Executor mUIThreadExecutor;
    private ArrayList<NavigationListener> mNavigationListeners;
    private TrackingProtectionStore mTrackingDelegate;
    private WidgetPlacement mBeforeFullscreenPlacement;
    private float mSavedCylinderDensity = 0.0f;
    private Animation mAnimation;

    private class MoveTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mWidgetManager.startWidgetMove(mWidgetManager.getWindows().getFrontWindow(), WidgetManagerDelegate.WIDGET_MOVE_BEHAVIOUR_WINDOW);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mWidgetManager.finishWidgetMove();
                    break;
                default:
                    return false;

            }
            return true;
        }
    }

    public NavigationBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public NavigationBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public NavigationBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);

        initialize(aContext);
    }

    private void initialize(@NonNull Context aContext) {
        mTrayViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(TrayViewModel.class);
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(SettingsViewModel.class);

        updateUI();

        mAppContext = aContext.getApplicationContext();

        mAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.rotate);

        mUIThreadExecutor = ((VRBrowserApplication)aContext.getApplicationContext()).getExecutors().mainThread();

        mAudio = AudioEngine.fromContext(aContext);

        mResizeBackHandler = () -> exitResizeMode(ResizeAction.RESTORE_SIZE);

        mNavigationListeners = new ArrayList<>();

        mFullScreenBackHandler = () -> {
            if (mAttachedWindow != null) {
                mAttachedWindow.setIsFullScreen(false);
            }
        };
        mVRVideoBackHandler = () -> {
            exitVRVideo();
            if (mAttachedWindow != null &&
                    mViewModel.getAutoEnteredVRVideo().getValue().get()) {
                mAttachedWindow.setIsFullScreen(false);
            } else {
                mAttachedWindow.reCenterFrontWindow();
            }
        };

        mWidgetManager.addUpdateListener(this);
        mWidgetManager.addWorldClickListener(this);
        mWidgetManager.getServicesProvider().getConnectivityReceiver().addListener(mConnectivityDelegate);

        mSuggestionsProvider = new SuggestionsProvider(getContext());

        mTrackingDelegate = SessionStore.get().getTrackingProtectionStore();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    private void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.navigation_bar, this, true);
        mBinding.setLifecycleOwner((VRBrowserActivity)getContext());
        mBinding.setViewmodel(mViewModel);
        mBinding.setSettingsmodel(mSettingsViewModel);

        mBinding.navigationBarFullscreen.fullScreenModeContainer.setVisibility(View.GONE);

        mBinding.navigationBarNavigation.backButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();

            if (getSession().canGoBack()) {
                getSession().goBack();
            }

            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.BACK);
            }
            mNavigationListeners.forEach(NavigationListener::onBack);
        });

        mBinding.navigationBarNavigation.forwardButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            getSession().goForward();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mNavigationListeners.forEach(NavigationListener::onForward);
        });

        mBinding.navigationBarNavigation.reloadButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (mViewModel.getIsLoading().getValue().get()) {
                getSession().stop();
            } else {
                int flags = SettingsStore.getInstance(mAppContext).isBypassCacheOnReloadEnabled() ? WSession.LOAD_FLAGS_BYPASS_CACHE : WSession.LOAD_FLAGS_NONE;
                getSession().reload(flags);
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mNavigationListeners.forEach(NavigationListener::onReload);
        });

        mBinding.navigationBarNavigation.reloadButton.setOnLongClickListener(v -> {
            v.requestFocusFromTouch();
            if (mViewModel.getIsLoading().getValue().get()) {
                getSession().stop();
            } else {
                getSession().reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mNavigationListeners.forEach(NavigationListener::onReload);
            return true;
        });

        mBinding.navigationBarNavigation.homeButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            getSession().loadUri(getSession().getHomeUri());
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            mNavigationListeners.forEach(NavigationListener::onHome);
        });

        mBinding.navigationBarNavigation.whatsNew.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            SettingsStore.getInstance(getContext()).setRemotePropsVersionName(BuildConfig.VERSION_NAME);
            RemoteProperties props = mSettingsViewModel.getProps().getValue().get(BuildConfig.VERSION_NAME);
            if (props != null) {
                mWidgetManager.openNewTabForeground(props.getWhatsNewUrl());
            }
        });

        mBinding.navigationBarNavigation.userFeedbackButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            String uri = getResources().getString(R.string.feedback_link, BuildConfig.VERSION_NAME, DeviceType.getType());
            mWidgetManager.openNewPageNoInterrupt(uri);
        });

        mBinding.navigationBarNavigation.desktopModeButton.setOnClickListener(view -> {
            final int defaultUaMode = SettingsStore.getInstance(mAppContext).getUaMode();
            if (mHamburgerMenu != null) {
                mHamburgerMenu.setUAMode(defaultUaMode);
            }
            if (mAttachedWindow.getSession() != null) {
                mAttachedWindow.getSession().setUaMode(defaultUaMode, true);
            }

            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarNavigation.moveButton.setOnTouchListener(new MoveTouchListener());
        mBinding.navigationBarFullscreen.fullScreenMoveButton.setOnTouchListener(new MoveTouchListener());

        mBinding.navigationBarNavigation.menuButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();

            showMenu();

            updateKioskModeFlipper();

            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarMenu.resizeExitButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            exitResizeMode(ResizeAction.KEEP_SIZE);

            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarFullscreen.fullScreenResizeEnterButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            enterResizeMode();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarFullscreen.fullScreenExitButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAttachedWindow != null) {
                mAttachedWindow.setIsFullScreen(false);
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarFullscreen.projectionButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            boolean wasVisible = mProjectionMenu != null ? mProjectionMenu.isVisible() : false;
            closeFloatingMenus();

            if (mProjectionMenu != null) {
                mProjectionMenu.mWidgetPlacement.cylinder = SettingsStore.getInstance(getContext()).isCurvedModeEnabled();

                if (!wasVisible) {
                    mProjectionMenu.show(REQUEST_FOCUS);
                }

                if (!mProjectionMenu.isVisible()) {
                    view.requestFocusFromTouch();
                }
            }
        });

        mBinding.navigationBarFullscreen.brightnessButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mBrightnessWidget == null) {
                return;
            }

            boolean wasVisible = mBrightnessWidget.isVisible();
            closeFloatingMenus();

            mBrightnessWidget.mWidgetPlacement.cylinder = SettingsStore.getInstance(getContext()).isCurvedModeEnabled();

            if (!wasVisible) {
                float anchor = 0.5f + (float)mBinding.navigationBarFullscreen.brightnessButton.getMeasuredWidth() / (float)NavigationBarWidget.this.getMeasuredWidth();
                mBrightnessWidget.getPlacement().parentAnchorX = anchor;
                mBrightnessWidget.setVisible(true);
            }

            if (!mBrightnessWidget.isVisible()) {
                view.requestFocusFromTouch();
            }
        });


        mBinding.navigationBarMenu.resizePreset0.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(0.5f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarMenu.resizePreset1.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(1.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarMenu.resizePreset15.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(1.5f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarMenu.resizePreset2.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(2.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarMenu.resizePreset3.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(3.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mBinding.navigationBarNavigation.urlBar.setDelegate(this);

        if (mAttachedWindow != null) {
            mBinding.navigationBarNavigation.urlBar.attachToWindow(mAttachedWindow);
        }

        setOnTouchListener((v, event) -> {
            closeFloatingMenus();
            v.performClick();
            return true;
        });
    }

    TrackingProtectionStore.TrackingProtectionListener mTrackingListener = new TrackingProtectionStore.TrackingProtectionListener() {
        @Override
        public void onExcludedTrackingProtectionChange(@NonNull String url, boolean excluded, boolean isPrivate) {
            Session currentSession = getSession();
            if (currentSession != null) {
                String currentSessionHost = UrlUtils.getHost(currentSession.getCurrentUri());
                String sessionHost = UrlUtils.getHost(url);
                if (currentSessionHost.equals(sessionHost) && currentSession.isPrivateMode() == isPrivate) {
                    mViewModel.setIsTrackingEnabled(!excluded);
                }
            }
        }
    };

    public MediaControlsWidget getMediaControlsWidget() {
        return mMediaControlsWidget;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();

        mSettingsViewModel.refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mViewModel.getIsLoading().getValue().get()) {
            mBinding.navigationBarNavigation.progressBar.clearAnimation();
        }
        exitFullScreenMode();
        exitVRVideo();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mViewModel.getIsLoading().getValue().get()) {
            mBinding.navigationBarNavigation.progressBar.startAnimation(mAnimation);
        }
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);
        mWidgetManager.removeWorldClickListener(this);
        mWidgetManager.getServicesProvider().getConnectivityReceiver().removeListener(mConnectivityDelegate);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);

        if (mAttachedWindow != null && mAttachedWindow.isFullScreen()) {
            // Workaround for https://issuetracker.google.com/issues/37123764
            // exitFullScreenMode() may animate some views that are then released
            // so use a custom way to exit fullscreen here without triggering view updates.
            if (getSession().isInFullScreen()) {
                getSession().exitFullScreen();
            }
            mAttachedWindow.restoreBeforeFullscreenPlacement();
            mWidgetManager.popBackHandler(mFullScreenBackHandler);
        }

        detachFromWindow();
        mAttachedWindow = null;

        if (mSendTabDialog != null && !mSendTabDialog.isReleased()) {
            mSendTabDialog.releaseWidget();
        }
        mSendTabDialog = null;

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 1.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = -35;
        aPlacement.cylinder = true;
    }

    public void addNavigationBarListener(@Nullable NavigationListener listener) {
        mNavigationListeners.add(listener);
    }

    public void removeNavigationBarListener(@Nullable NavigationListener listener) {
        mNavigationListeners.remove(listener);
    }

    @Override
    public void detachFromWindow() {
        hideMenu();
        hideAllNotifications();

        if (mAttachedWindow != null && mAttachedWindow.isResizing()) {
            exitResizeMode(ResizeAction.RESTORE_SIZE);
        }
        if (mAttachedWindow != null && mAttachedWindow.isFullScreen()) {
            mAttachedWindow.setIsFullScreen(false);
        }

        if (getSession() != null) {
            cleanSession(getSession());
        }
        if (mAttachedWindow != null) {
            mAttachedWindow.removeWindowListener(this);
        }
        mAttachedWindow = null;
        if (mAwesomeBar != null && mAwesomeBar.isVisible()) {
            mAwesomeBar.hideNoAnim(UIWidget.REMOVE_WIDGET);
        }

        mBinding.navigationBarNavigation.urlBar.detachFromWindow();

        mTrackingDelegate.removeListener(mTrackingListener);

        if (mViewModel != null) {
            mViewModel.getIsLoading().removeObserver(mIsLoadingObserver);
            mViewModel.getIsActiveWindow().removeObserver(mIsActiveWindowObserver);
            mViewModel.getIsPopUpBlocked().removeObserver(mIsPopUpBlockedListener);
            mViewModel = null;
        }
    }

    @Override
    public void attachToWindow(@NonNull WindowWidget aWindow) {
        if (aWindow == mAttachedWindow) {
            return;
        }
        detachFromWindow();

        mAttachedWindow = aWindow;
        mWidgetPlacement.parentHandle = aWindow.getHandle();

        mViewModel = new ViewModelProvider(
                (VRBrowserActivity)getContext(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) getContext()).getApplication()))
                .get(String.valueOf(mAttachedWindow.hashCode()), WindowViewModel.class);

        mBinding.setViewmodel(mViewModel);

        mViewModel.getIsLoading().observe((VRBrowserActivity)getContext(), mIsLoadingObserver);
        mViewModel.getIsActiveWindow().observeForever(mIsActiveWindowObserver);
        mViewModel.getIsPopUpBlocked().observeForever(mIsPopUpBlockedListener);
        mBinding.navigationBarNavigation.urlBar.attachToWindow(mAttachedWindow);

        mTrackingDelegate.addListener(mTrackingListener);

        mAttachedWindow.addWindowListener(this);

        mBeforeFullscreenPlacement = mWidgetPlacement;

        requestFocusFromTouch();

        if (getSession() != null) {
            setUpSession(getSession());
        }
        handleWindowResize();
    }

    private Session getSession() {
        if (mAttachedWindow != null) {
            return mAttachedWindow.getSession();
        }
        return null;
    }

    private void setUpSession(@NonNull Session aSession) {
        aSession.addSessionChangeListener(this);
        aSession.addNavigationListener(this);
        aSession.addContentListener(this);
        mBinding.navigationBarNavigation.urlBar.setSession(getSession());
    }

    private void cleanSession(@NonNull Session aSession) {
        aSession.removeSessionChangeListener(this);
        aSession.removeNavigationListener(this);
        aSession.removeContentListener(this);
    }

    @Override
    public void onSessionChanged(@NonNull Session aOldSession, @NonNull Session aSession) {
        cleanSession(aOldSession);
        setUpSession(aSession);
        mAttachedWindow.setIsFullScreen(false);
    }

    @Override
    public void onMediaFullScreen(@NonNull WMediaSession mediaSession, boolean aFullScreen) {
        if (aFullScreen) {
            // The content fullscreen event might have arrived before the media fullscreen event
            if (!mAttachedWindow.isFullScreen())
                enterFullScreenMode();

            AtomicBoolean autoEnter = new AtomicBoolean(false);
            if (getSession().getFullScreenVideo() == null) {
                mAutoSelectedProjection = VIDEO_PROJECTION_NONE;
                autoEnter.set(false);
            } else {
                mAutoSelectedProjection = VideoProjectionMenuWidget.getAutomaticProjection(getSession().getCurrentUri(), autoEnter);
            }

            if (mAutoSelectedProjection != VIDEO_PROJECTION_NONE && autoEnter.get()) {
                mViewModel.setAutoEnteredVRVideo(true);
                postDelayed(() -> enterVRVideo(mAutoSelectedProjection), 300);
            } else {
                mViewModel.setAutoEnteredVRVideo(false);
                if (mProjectionMenu != null) {
                    mProjectionMenu.setSelectedProjection(mAutoSelectedProjection);
                }
            }
            mAttachedWindow.reCenterFrontWindow();
        } else {
            // This can be called by content's fullscreen event later but will be a noop.
            exitFullScreenMode();

            if (isInVRVideo()) {
                exitVRVideo();
            }
        }
   }

    @Override
    public void onContentFullScreen(@NonNull WindowWidget aWindow, boolean aFullScreen) {
        if (aFullScreen)
            enterFullScreenMode();
        else
            exitFullScreenMode();
    }

    public boolean isInVRVideo() {
        return Objects.requireNonNull(mViewModel.getIsInVRVideo().getValue()).get();
    }

    @Override
    public void onKioskMode(WindowWidget aWindow, boolean isKioskMode) {
        mTrayViewModel.setShouldBeVisible(!isKioskMode);
        updateUI();
        mSettingsViewModel.refresh();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    private void enterFullScreenMode() {
        assert !mAttachedWindow.isFullScreen();
        hideMenu();
        hideAllNotifications();

        mBeforeFullscreenPlacement = mWidgetPlacement.clone();
        mWidgetPlacement.cylinder = SettingsStore.getInstance(getContext()).isCurvedModeEnabled();
        updateWidget();

        if (mAttachedWindow.isResizing()) {
            exitResizeMode(ResizeAction.KEEP_SIZE);
        }

        mBinding.navigationBarFullscreen.brightnessButton.setVisibility(mWidgetManager.isPassthroughEnabled() ? GONE : VISIBLE);
        mWidgetManager.pushBackHandler(mFullScreenBackHandler);

        AnimationHelper.fadeOut(mBinding.navigationBarNavigation.navigationBarContainer, 0, null);

        mTrayViewModel.setShouldBeVisible(false);

        if (mProjectionMenu == null) {
            mProjectionMenu = new VideoProjectionMenuWidget(getContext());
            mProjectionMenu.setParentWidget(this);
            mProjectionMenuPlacement = new WidgetPlacement(getContext());
            mWidgetManager.addWidget(mProjectionMenu);
            mProjectionMenu.setDelegate((projection)-> {
                if (mViewModel.getIsInVRVideo().getValue().get()) {
                    if (projection == VIDEO_PROJECTION_NONE) {
                        exitVRVideo();
                        mAttachedWindow.reCenterFrontWindow();
                    } else {
                        // Reproject while reproducing VRVideo
                        mWidgetManager.showVRVideo(mAttachedWindow.getHandle(), projection);
                    }
                    closeFloatingMenus();
                } else {
                    enterVRVideo(projection);
                }
            });
        }
        if (mBrightnessWidget == null) {
            mBrightnessWidget = new BrightnessMenuWidget(getContext());
            mBrightnessWidget.setParentWidget(this);
            mWidgetManager.addWidget(mBrightnessWidget);
        }
        closeFloatingMenus();
        mWidgetManager.pushWorldBrightness(mBrightnessWidget, mBrightnessWidget.getSelectedBrightness());
    }

    private void exitFullScreenMode() {
        mWidgetPlacement = mBeforeFullscreenPlacement;
        updateWidget();

        hideMenu();
        hideAllNotifications();

        if (mAttachedWindow == null || !mAttachedWindow.isFullScreen()) {
            return;
        }

        mWidgetManager.setControllersVisible(true);

        // We need to add a delay for the exitFullScreen() call to solve some viewport scaling issues,
        // See https://github.com/MozillaReality/FirefoxReality/issues/833 for more info.
        postDelayed(() -> {
            if (getSession().isInFullScreen()) {
                getSession().exitFullScreen();
            }
        }, 50);

        mWidgetManager.updateWidget(mAttachedWindow);

        mWidgetManager.popBackHandler(mFullScreenBackHandler);

        AnimationHelper.fadeIn(mBinding.navigationBarNavigation.navigationBarContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        AnimationHelper.fadeOut(mBinding.navigationBarFullscreen.fullScreenModeContainer, 0, null);
        // if we are in kiosk mode, don't show the tray
        mTrayViewModel.setShouldBeVisible(!mAttachedWindow.isKioskMode());
        closeFloatingMenus();
        mWidgetManager.popWorldBrightness(mBrightnessWidget);
        mAttachedWindow.centerFrontWindowIfNeeded();
    }

    private void enterResizeMode() {
        hideMenu();
        hideAllNotifications();

        if (mAttachedWindow.isResizing()) {
            return;
        }
        mAttachedWindow.mWidgetPlacement.tintColor = Windows.GRAY;
        mAttachedWindow.setIsResizing(true);
        mAttachedWindow.saveBeforeResizePlacement();
        startWidgetResize();
        AnimationHelper.fadeIn(mBinding.navigationBarMenu.resizeModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        if (mAttachedWindow.isFullScreen()) {
            AnimationHelper.fadeOut(mBinding.navigationBarFullscreen.fullScreenModeContainer, 0, null);
        } else {
            AnimationHelper.fadeOut(mBinding.navigationBarNavigation.navigationBarContainer, 0, null);
        }
        mWidgetManager.pushBackHandler(mResizeBackHandler);
        mTrayViewModel.setShouldBeVisible(false);
        closeFloatingMenus();

        float maxScale = 3.0f;
        if (mAttachedWindow != null) {
            maxScale = mAttachedWindow.getMaxWindowScale();
        }

        mBinding.navigationBarMenu.resizePreset3.setVisibility(maxScale >= 3.0f ? View.VISIBLE : View.GONE);
        mBinding.navigationBarMenu.resizePreset2.setVisibility(maxScale >= 2.0f ? View.VISIBLE : View.GONE);
        mBinding.navigationBarMenu.resizePreset15.setVisibility(maxScale == 1.5f ? View.VISIBLE: View.GONE);

        // Update visible presets
        UITextButton[] presets = {
                mBinding.navigationBarMenu.resizePreset3,
                mBinding.navigationBarMenu.resizePreset2,
                mBinding.navigationBarMenu.resizePreset15,
                mBinding.navigationBarMenu.resizePreset1};
        boolean fistVisible = true;
        for (UITextButton preset: presets) {
            if (fistVisible) {
                if (preset.getVisibility() != View.VISIBLE) {
                    continue;
                }
                fistVisible = false;
                preset.updateBackground(getContext().getDrawable(R.drawable.fullscreen_button_first),
                        getContext().getDrawable(R.drawable.fullscreen_button_private_first));
            } else {
                preset.updateBackground(getContext().getDrawable(R.drawable.fullscreen_button),
                        getContext().getDrawable(R.drawable.fullscreen_button_private));
            }
        }

        mWidgetManager.updateWidget(mAttachedWindow);

        // Update preset styles
    }

    enum ResizeAction {
        KEEP_SIZE,
        RESTORE_SIZE
    }

    private void exitResizeMode(ResizeAction aResizeAction) {
        if (!mAttachedWindow.isResizing()) {
            return;
        }
        if (aResizeAction == ResizeAction.RESTORE_SIZE) {
            mAttachedWindow.restoreBeforeResizePlacement();
            mWidgetManager.updateVisibleWidgets();
        }
        mAttachedWindow.setIsResizing(false);
        finishWidgetResize();
        if (mAttachedWindow.isFullScreen()) {
            AnimationHelper.fadeIn(mBinding.navigationBarFullscreen.fullScreenModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        } else {
            AnimationHelper.fadeIn(mBinding.navigationBarNavigation.navigationBarContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        }
        AnimationHelper.fadeOut(mBinding.navigationBarMenu.resizeModeContainer, 0, () -> onWidgetUpdate(mAttachedWindow));
        mWidgetManager.popBackHandler(mResizeBackHandler);
        mTrayViewModel.setShouldBeVisible(!mAttachedWindow.isFullScreen() && !mAttachedWindow.isKioskMode());
        closeFloatingMenus();

        if (aResizeAction == ResizeAction.KEEP_SIZE) {
            TelemetryService.windowsResizeEvent();
        }

        mAttachedWindow.mWidgetPlacement.tintColor = Windows.WHITE;
        mWidgetManager.updateWidget(mAttachedWindow);
    }

    private void enterVRVideo(@VideoProjectionMenuWidget.VideoProjectionFlags int aProjection) {
        if (mViewModel.getIsInVRVideo().getValue().get() || aProjection == VIDEO_PROJECTION_NONE) {
            return;
        }

        // Remember the cylinder density before we enter VR video
        mSavedCylinderDensity = mWidgetManager.getCylinderDensity();
        // Disable curved display temporary
        mWidgetManager.setCylinderDensityForce(0.0f);

        mViewModel.setIsInVRVideo(true);
        mWidgetManager.pushBackHandler(mVRVideoBackHandler);
        mProjectionMenu.setSelectedProjection(aProjection);
        // Backup the placement because the same widget is reused in FullScreen & MediaControl menus
        mProjectionMenuPlacement.copyFrom(mProjectionMenu.getPlacement());
        this.setVisible(false);

        mFullScreenMedia = getSession().getFullScreenVideo();
        // This should not happen, but Gecko does not notify about fullscreen changes in media if
        // the web content is already in fullscreen state.
        if (mFullScreenMedia == null)
            mFullScreenMedia = getSession().getActiveVideo();

        // mFullScreenMedia may still be null at this point.
        // For example, this can happen if the page does not provide the media playback events
        // that we use to recognize when a video is playing and obtain its metadata.

        int mediaWidth;
        int mediaHeight;
        if (mFullScreenMedia != null && mFullScreenMedia.getWidth() > 0 && mFullScreenMedia.getHeight() > 0) {
            mediaWidth = (int) mFullScreenMedia.getWidth();
            mediaHeight = (int) mFullScreenMedia.getHeight();
        } else {
            // Fallback to window sizes if the engine does not provide valid fullscreen sizes.
            mediaWidth = mAttachedWindow.getWindowWidth();
            mediaHeight = mAttachedWindow.getWindowHeight();
        }
        final boolean resetBorder = aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360 ||
                aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360_STEREO;
        mAttachedWindow.enableVRVideoMode(mediaWidth, mediaHeight, resetBorder);
        // Handle video resize while in VR video playback
        if (mFullScreenMedia != null) {
            mFullScreenMedia.setResizeDelegate((width, height) -> {
                mAttachedWindow.enableVRVideoMode(width, height, resetBorder);
            });
        }

        mAttachedWindow.setVisible(false);

        closeFloatingMenus();
        if (!isFrontFacingVRProjection(aProjection)) {
            mWidgetManager.setControllersVisible(false);
        }

        if (mMediaControlsWidget == null) {
            mMediaControlsWidget = new MediaControlsWidget(getContext());
            mMediaControlsWidget.setParentWidget(mAttachedWindow.getHandle());
            mMediaControlsWidget.getPlacement().visible = false;
            mWidgetManager.addWidget(mMediaControlsWidget);
            mMediaControlsWidget.setBackHandler(mVRVideoBackHandler);
            mMediaControlsWidget.setOnClickListener(v -> v.requestFocusFromTouch());
        }
        mMediaControlsWidget.setProjectionMenuWidget(mProjectionMenu);
        mMediaControlsWidget.setMedia(mFullScreenMedia);
        mMediaControlsWidget.setParentWidget(mAttachedWindow.getHandle());
        mMediaControlsWidget.setProjectionSelectorEnabled(true);
        mAttachedWindow.reCenterFrontWindow();
        mWidgetManager.updateWidget(mMediaControlsWidget);
        mWidgetManager.showVRVideo(mAttachedWindow.getHandle(), aProjection);
    }

    private void exitVRVideo() {
        if (!mViewModel.getIsInVRVideo().getValue().get()) {
            return;
        }
        if (mFullScreenMedia != null) {
            mFullScreenMedia.setResizeDelegate(null);
        }
        mViewModel.setIsInVRVideo(false);
        mWidgetManager.popBackHandler(mVRVideoBackHandler);
        mWidgetManager.hideVRVideo();
        boolean composited = mProjectionMenu.getPlacement().composited;
        mProjectionMenu.getPlacement().copyFrom(mProjectionMenuPlacement);
        mProjectionMenu.getPlacement().composited = composited;
        mProjectionMenu.setSelectedProjection(VIDEO_PROJECTION_NONE);
        mWidgetManager.updateWidget(mProjectionMenu);
        closeFloatingMenus();
        mWidgetManager.setControllersVisible(true);

        this.setVisible(!mAttachedWindow.isKioskMode());
        mAttachedWindow.disableVRVideoMode();
        mAttachedWindow.setVisible(true);
        mMediaControlsWidget.setVisible(false);
        mTrayViewModel.setShouldBeVisible(!mAttachedWindow.isFullScreen() && !mAttachedWindow.isKioskMode());
        mViewModel.setAutoEnteredVRVideo(false);
        AnimationHelper.fadeIn(mBinding.navigationBarFullscreen.fullScreenModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);

        mWidgetManager.setCylinderDensityForce(mSavedCylinderDensity);
        // Reposition UI in front of the user when exiting a VR video.
        mWidgetManager.recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL);
    }

    private void setResizePreset(float aMultiplier) {
        final float aspect = (float) mAttachedWindow.getWindowWidth() / (float) mAttachedWindow.getWindowHeight();
        mAttachedWindow.resizeByMultiplier(aspect, aMultiplier);
    }

    public void showVoiceSearch() {
        mViewModel.setIsMicrophoneEnabled(true);
    }

    private void closeFloatingMenus() {
        if (mProjectionMenu != null) {
            mProjectionMenu.hide(KEEP_WIDGET);
        }
        if (mBrightnessWidget != null) {
            mBrightnessWidget.hide(KEEP_WIDGET);
        }
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull WSession session, @Nullable String url) {
        if (getSession() != null && getSession().getWSession() == session) {
            updateTrackingProtection();
        }

        mBinding.navigationBarNavigation.reloadButton.setEnabled(!UrlUtils.isPrivateAboutPage(getContext(), url));
    }

    // Content delegate

    private Observer<ObservableBoolean> mIsLoadingObserver = aBoolean -> {
        // Although the animation does nothing here, it's still needed to trigger the progress bar native animation
        // Work around to fix the progress bar animation stucks
        if (aBoolean.get()) {
            mBinding.navigationBarNavigation.progressBar.startAnimation(mAnimation);
        } else {
            mBinding.navigationBarNavigation.progressBar.clearAnimation();
        }
    };

    private Observer<ObservableBoolean> mIsActiveWindowObserver = aIsActiveWindow -> updateTrackingProtection();

    private Observer<ObservableBoolean> mIsPopUpBlockedListener = observableBoolean -> {
        if (observableBoolean.get()) {
            showPopUpsBlockedNotification();

        } else {
            hidePopUpsBlockedNotification();
        }
    };

    private void updateTrackingProtection() {
        if (getSession() != null) {
            mTrackingDelegate.contains(getSession(), isExcluded -> {
                if (isExcluded != null) {
                    mViewModel.setIsTrackingEnabled(!isExcluded);
                }

                return null;
            });

            mTrackingDelegate.fetchAll(sitePermissions -> {
                Log.d(LOGTAG, "Start");
                sitePermissions.forEach(site -> Log.d(LOGTAG, site.url));
                Log.d(LOGTAG, "End");

                return null;
            });
        }
    }

    // WidgetManagerDelegate.UpdateListener
    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget == mAttachedWindow && !mAttachedWindow.isResizing()) {
            handleWindowResize();
        }
    }

    private void handleWindowResize() {
        // Browser window may have been resized, adjust the navigation bar
        float targetWidth = mAttachedWindow.getPlacement().worldWidth;
        float defaultWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        float maxWidth = defaultWidth * 1.5f;
        float minWidth = defaultWidth * 0.5f;
        targetWidth = Math.max(targetWidth, minWidth);
        targetWidth = Math.min(targetWidth, maxWidth);

        float ratio = targetWidth / defaultWidth;
        mWidgetPlacement.worldWidth = targetWidth;
        mWidgetPlacement.width = (int) (WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width) * ratio);
        mWidgetManager.updateWidget(this);
        postInvalidate();
    }

    // Session.SessionChangeListener

    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        boolean isFullScreen = getSession().isInFullScreen();
        if (isFullScreen && !mAttachedWindow.isFullScreen()) {
            enterFullScreenMode();
        } else if (!isFullScreen && mAttachedWindow.isFullScreen()) {
            exitVRVideo();
            exitFullScreenMode();
        }
    }

    // NavigationURLBarDelegate

    @Override
    public void onVoiceSearchClicked() {
        if (mVoiceSearchWidget == null) {
            mVoiceSearchWidget = new VoiceSearchWidget(getContext());
            mVoiceSearchWidget.setDelegate(this);
            mVoiceSearchWidget.setDelegate(() -> {
                mVoiceSearchWidget.hide(UIWidget.REMOVE_WIDGET);
                mVoiceSearchWidget.releaseWidget();
                mVoiceSearchWidget = null;
            });
        }

        mVoiceSearchWidget.setPlacement(mAttachedWindow.getHandle());
        mVoiceSearchWidget.show(REQUEST_FOCUS);
    }


    @Override
    public void onShowAwesomeBar() {
        if (mAwesomeBar == null) {
            mAwesomeBar = createChild(SuggestionsWidget.class);
            mAwesomeBar.setURLBarPopupDelegate(this);
        }

        final String text = mBinding.navigationBarNavigation.urlBar.getText().trim();
        final String originalText = mBinding.navigationBarNavigation.urlBar.getNonAutocompleteText().trim();
        if (originalText.length() <= 0) {
            mAwesomeBar.hide(UIWidget.KEEP_WIDGET);
            return;
        }

        mSuggestionsProvider.setText(text);
        mSuggestionsProvider.setFilterText(originalText);
        mSuggestionsProvider.getSuggestions()
                .whenCompleteAsync((items, ex) -> {
                    if (mBinding.navigationBarNavigation.urlBar.hasFocus()) {
                        mAwesomeBar.updateItems(items);
                        mAwesomeBar.setHighlightedText(mBinding.navigationBarNavigation.urlBar.getNonAutocompleteText().trim());

                        if (!mAwesomeBar.isVisible()) {
                            mAwesomeBar.updatePlacement((int) WidgetPlacement.convertPixelsToDp(getContext(), mBinding.navigationBarNavigation.urlBar.getWidth()));
                            mAwesomeBar.show(CLEAR_FOCUS);
                        }
                    }

                }, mUIThreadExecutor).exceptionally(throwable -> {
                    Log.d(LOGTAG, "Error getting suggestions: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
        });
    }

    @Override
    public void onHideAwesomeBar() {
        if (mAwesomeBar != null) {
            mAwesomeBar.hide(UIWidget.KEEP_WIDGET);
        }
    }

    @Override
    public void onURLSelectionAction(EditText aURLEdit, float centerX, SelectionActionWidget actionMenu) {
        actionMenu.getPlacement().parentHandle = this.getHandle();
        actionMenu.getPlacement().parentAnchorY = 1.0f;
        actionMenu.getPlacement().anchorY = 0.44f;
        Rect offsetViewBounds = new Rect();
        aURLEdit.getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(aURLEdit, offsetViewBounds);
        float x = aURLEdit.getPaddingLeft() + offsetViewBounds.left + centerX;
        actionMenu.getPlacement().parentAnchorX = x / getWidth();
    }

    @Override
    public void onPopUpButtonClicked() {
        toggleQuickPermission(mBinding.navigationBarNavigation.urlBar.getPopUpButton(),
                SitePermission.SITE_PERMISSION_POPUP,
                !mViewModel.getIsPopUpBlocked().getValue().get());
    }

    @Override
    public void onWebXRButtonClicked() {
        toggleQuickPermission(mBinding.navigationBarNavigation.urlBar.getWebXRButton(),
                SitePermission.SITE_PERMISSION_WEBXR,
                mViewModel.getIsWebXRBlocked().getValue().get());
    }

    @Override
    public void onTrackingButtonClicked() {
        toggleQuickPermission(mBinding.navigationBarNavigation.urlBar.getTrackingButton(),
                SitePermission.SITE_PERMISSION_TRACKING,
                !mViewModel.getIsTrackingEnabled().getValue().get());
    }

    @Override
    public void onDrmButtonClicked() {
        toggleQuickPermission(mBinding.navigationBarNavigation.urlBar.getDrmButton(),
                SitePermission.SITE_PERMISSION_DRM,
                !SettingsStore.getInstance(getContext()).isDrmContentPlaybackEnabled());
    }

    @Override
    public boolean onHandleExternalRequest(@NonNull String uri) {
        return mWidgetManager.getFocusedWindow().onHandleExternalRequest(uri);
    }

    @Override
    public void onWebAppButtonClicked() {
        showSaveWebAppDialog();
    }

    // VoiceSearch Delegate

    @Override
    public void OnVoiceSearchResult(String transcription, float confidence) {
        mBinding.navigationBarNavigation.urlBar.handleURLEdit(transcription);
    }

    @Override
    public void onSharedPreferenceChanged(@NonNull SharedPreferences sharedPreferences, String key) {
        if (key.equals(mAppContext.getString(R.string.settings_key_user_agent_version))) {
            if (mHamburgerMenu != null) {
                mHamburgerMenu.setUAMode(SettingsStore.getInstance(getContext()).getUaMode());
            }
        }
    }

    private boolean isFrontFacingVRProjection(int projection){
        switch (projection) {
            case VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_SIDE_BY_SIDE:
            case VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_TOP_BOTTOM:
                return true;
            default:
                return false;
        }
    }

    // WorldClickListener
    @Override
    public void onWorldClick() {
        if (mViewModel.getIsInVRVideo().getValue().get() && mMediaControlsWidget != null) {
            mMediaControlsWidget.setVisible(!mMediaControlsWidget.isVisible());
            if (!isFrontFacingVRProjection(mProjectionMenu.getSelectedProjection())) {
                if (mMediaControlsWidget.isVisible()) {
                    // Reorient the MediaControl UI when the users clicks to show it.
                    // So you can look at any point of the 180/360 video and the UI always shows in front of you.
                    mWidgetManager.recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_WIDGETS);
                }
            }

            if (mMediaControlsWidget.isVisible()) {
                mWidgetManager.setControllersVisible(true);
            } else if (!isFrontFacingVRProjection(mProjectionMenu.getSelectedProjection())) {
                mWidgetManager.setControllersVisible(false);
            }
        } else if (mViewModel.getIsFullscreen().getValue().get()) {
            if (!mAttachedWindow.isResizing()) {
                if (mBinding.navigationBarFullscreen.fullScreenModeContainer.getVisibility() == View.VISIBLE) {
                    mWidgetManager.setControllersVisible(false);
                    AnimationHelper.fadeOut(mBinding.navigationBarFullscreen.fullScreenModeContainer, 0, null);
                } else {
                    mWidgetManager.setControllersVisible(true);
                    AnimationHelper.fadeIn(mBinding.navigationBarFullscreen.fullScreenModeContainer, 0, null);
                }
            }
        }
        closeFloatingMenus();
    }

    // URLBarPopupWidgetDelegate

    @Override
    public void OnItemClicked(SuggestionsWidget.SuggestionItem item) {
        mBinding.navigationBarNavigation.urlBar.handleURLEdit(item.url);
    }

    // TrayListener

    @Override
    public void onPrivateBrowsingClicked() {

    }

    @Override
    public void onLibraryClicked() {
        if (mAttachedWindow.isResizing()) {
            exitResizeMode(ResizeAction.RESTORE_SIZE);

        } else if (mAttachedWindow.isFullScreen()) {
            exitFullScreenMode();

        } else if (mViewModel.getIsInVRVideo().getValue().get()) {
            exitVRVideo();
        }
    }

    private void finishWidgetResize() {
        mWidgetManager.finishWidgetResize(mAttachedWindow);
    }

    private void startWidgetResize() {
        if (mAttachedWindow != null) {
            final float aspect = (float) mAttachedWindow.getWindowWidth() / (float) mAttachedWindow.getWindowHeight();
            Pair<Float, Float> maxSize = mAttachedWindow.getSizeForScale(mAttachedWindow.getMaxWindowScale(), aspect);
            Pair<Float, Float> minSize = mAttachedWindow.getSizeForScale(0.5f, aspect);
            mWidgetManager.startWidgetResize(mAttachedWindow, maxSize.first, maxSize.second, minSize.first, minSize.second);
        }
    }

    private void showMenu() {
        if (mAttachedWindow.getSession() == null) {
            return;
        }

        if (mHamburgerMenu != null && mHamburgerMenu.isVisible()) {
            // Release current selection menu to recreate it with different actions.
            hideMenu();
            return;
        }

        mHamburgerMenu = new HamburgerMenuWidget(getContext());
        mHamburgerMenu.getPlacement().parentHandle = getHandle();
        mHamburgerMenu.setMenuDelegate(new HamburgerMenuWidget.MenuDelegate() {
            @Override
            public void onSendTab() {
                hideMenu();

                showSendTabDialog();
            }

            @Override
            public void onFindInPage() {
                hideMenu();
                mAttachedWindow.hidePanel();

                mViewModel.setIsFindInPage(true);
            }

            @Override
            public void onResize() {
                hideMenu();

                enterResizeMode();
            }

            @Override
            public void onPassthrough() {
                mWidgetManager.togglePassthrough();

                hideMenu();
            }

            @Override
            public boolean isPassthroughEnabled() {
                return mWidgetManager.isPassthroughEnabled();
            }

            @Override
            public void onSwitchMode() {
                int uaMode = mAttachedWindow.getSession().getUaMode();
                if (uaMode == WSessionSettings.USER_AGENT_MODE_DESKTOP) {
                    final int defaultUaMode = SettingsStore.getInstance(mAppContext).getUaMode();
                    mHamburgerMenu.setUAMode(defaultUaMode);
                    mAttachedWindow.getSession().setUaMode(defaultUaMode, true);

                } else {
                    mHamburgerMenu.setUAMode(WSessionSettings.USER_AGENT_MODE_DESKTOP);
                    mAttachedWindow.getSession().setUaMode(WSessionSettings.USER_AGENT_MODE_DESKTOP, true);
                }

                hideMenu();
            }

            @Override
            public void onAddons() {
                hideMenu();

                if (!mAttachedWindow.isLibraryVisible()) {
                    mAttachedWindow.switchPanel(Windows.ADDONS);

                } else if (mAttachedWindow.getSelectedPanel() != Windows.ADDONS) {
                    mAttachedWindow.showPanel(Windows.ADDONS);
                }
            }

            @Override
            public void onSaveWebApp() {
                hideMenu();

                showSaveWebAppDialog();
            }
        });
        boolean isSendTabEnabled = false;
        if (URLUtil.isHttpUrl(mAttachedWindow.getSession().getCurrentUri()) ||
                URLUtil.isHttpsUrl(mAttachedWindow.getSession().getCurrentUri())) {
            isSendTabEnabled = true;
        }
        mHamburgerMenu.setSendTabEnabled(isSendTabEnabled);
        mHamburgerMenu.setUAMode(mAttachedWindow.getSession().getUaMode());
        mHamburgerMenu.show(UIWidget.KEEP_FOCUS);
    }

    private void hideMenu() {
        if (mHamburgerMenu != null) {
            mHamburgerMenu.hide(UIWidget.REMOVE_WIDGET, false);
        }
    }

    public void showSendTabDialog() {
        mSendTabDialog = SendTabDialogWidget.getInstance(getContext());
        mSendTabDialog.mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();
        mSendTabDialog.setSessionId(mAttachedWindow.getSession().getId());
        mSendTabDialog.setDelegate(null);
        mSendTabDialog.show(UIWidget.REQUEST_FOCUS);
    }

    public void showSaveWebAppDialog() {
        if (getSession() == null || getSession().getWebAppManifest() == null) {
            Log.w(LOGTAG, "showSaveWebAppDialog: missing Session or Web app manifest");
            return;
        }

        WebApp webApp = getSession().getWebAppManifest();
        InstallWebAppDialogWidget installWebAppDialog =
                new InstallWebAppDialogWidget(getContext(), webApp);
        installWebAppDialog.setButtonsDelegate((index, isChecked) -> {
            if (index == PromptDialogWidget.POSITIVE) {
                WebAppsStore webAppsStore = SessionStore.get().getWebAppsStore();
                webAppsStore.addWebApp(webApp);
                mWidgetManager.getWindows().showWebAppAddedNotification();
            }
            installWebAppDialog.onDismiss();
        });
        installWebAppDialog.getPlacement().parentHandle = mWidgetManager.getFocusedWindow().getHandle();
        installWebAppDialog.show(UIWidget.REQUEST_FOCUS);
    }

    public void showPopUpsBlockedNotification() {
        final int POP_UP_NOTIFICATION_DELAY = 800;
        mBlockedCount++;
        final int currentCount = mBlockedCount;
        postDelayed(() -> {
            if (currentCount == mBlockedCount && !mViewModel.getIsLibraryVisible().getValue().get()) {
                showNotification(POPUP_NOTIFICATION_ID,
                        mBinding.navigationBarNavigation.urlBar.getPopUpButton(),
                        NotificationManager.Notification.TOP,
                        R.string.popup_blocked_tooltip);
            }
        }, POP_UP_NOTIFICATION_DELAY);
    }

    public void hidePopUpsBlockedNotification() {
        mBlockedCount++;
        final int currentCount = mBlockedCount;
        post(() -> {
            if (currentCount == mBlockedCount) {
                hideNotification(POPUP_NOTIFICATION_ID);
            }
        });
    }

    public void showTabAddedNotification() {
        showNotification(TAB_ADDED_NOTIFICATION_ID,
                NotificationManager.Notification.BOTTOM,
                R.string.tab_added_notification);
    }

    public void showTabSentNotification() {
        showNotification(TAB_SENT_NOTIFICATION_ID,
                NotificationManager.Notification.BOTTOM,
                R.string.tab_sent_notification);
    }

    public void showBookmarkAddedNotification() {
        showNotification(BOOKMARK_ADDED_NOTIFICATION_ID,
                NotificationManager.Notification.BOTTOM,
                R.string.bookmarks_saved_notification);
    }

    public void showWebAppAddedNotification() {
        showNotification(WEB_APP_ADDED_NOTIFICATION_ID,
                NotificationManager.Notification.BOTTOM,
                R.string.web_apps_saved_notification);
    }

    private void showNotification(int notificationId, UIButton button, @NotificationPosition int position, int stringRes) {
        NotificationManager.Notification notification = new NotificationManager.Builder(this)
                .withView(button)
                .withString(stringRes)
                .withPosition(position)
                .withMargin(20.0f).build();
        NotificationManager.show(notificationId, notification);
    }

    private void showNotification(int notificationId, @NotificationPosition int position, int stringRes) {
        NotificationManager.Notification notification = new NotificationManager.Builder(this)
                .withString(stringRes)
                .withPosition(position)
                .withMargin(20.0f).build();
        NotificationManager.show(notificationId, notification);
    }

    public void hideAllNotifications() {
        NotificationManager.hideAll();
        if (mQuickPermissionWidget != null && mQuickPermissionWidget.isVisible()) {
            mQuickPermissionWidget.hide(KEEP_WIDGET);
        }
    }

    private void hideNotification(int notificationId) {
        NotificationManager.hide(notificationId);
    }

    private ConnectivityReceiver.Delegate mConnectivityDelegate = connected -> {
        if (mMediaControlsWidget != null) {
            mMediaControlsWidget.setVisible(connected && mMediaControlsWidget.isVisible());
        }
    };

    private void toggleQuickPermission(UIButton target, @SitePermission.Category int aCategory, boolean aBlocked) {
        if (mQuickPermissionWidget == null) {
            mQuickPermissionWidget = new QuickPermissionWidget(getContext());
        }

        if (mQuickPermissionWidget.isVisible()) {
            mQuickPermissionWidget.hide(KEEP_WIDGET);
            if (mQuickPermissionWidget.getCategory() == aCategory) {
                return;
            }
        }

        String uri = UrlUtils.getHost(mAttachedWindow.getSession().getCurrentUri());
        mQuickPermissionWidget.setData(uri, aCategory, aBlocked);
        mQuickPermissionWidget.setDelegate(new QuickPermissionWidget.Delegate() {
            @Override
            public void onBlock() {
                if (aCategory == SITE_PERMISSION_TRACKING) {
                    if (getSession() != null) {
                        mTrackingDelegate.add(getSession());
                    }

                } else if (aCategory == SITE_PERMISSION_DRM) {
                    SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(false);

                } else if (aCategory == SITE_PERMISSION_POPUP) {
                    SessionStore.get().addPermissionException(uri, aCategory);

                } else {
                    SessionStore.get().addPermissionException(uri, aCategory);
                }
                mQuickPermissionWidget.onDismiss();
            }

            @Override
            public void onAllow() {
                if (aCategory == SITE_PERMISSION_TRACKING) {
                    if (getSession() != null) {
                        mTrackingDelegate.remove(getSession());
                    }

                } else if (aCategory == SITE_PERMISSION_DRM) {
                    SettingsStore.getInstance(getContext()).setDrmContentPlaybackEnabled(true);

                } else if (aCategory == SITE_PERMISSION_POPUP) {
                    SessionStore.get().removePermissionException(uri, aCategory);

                } else {
                    SessionStore.get().removePermissionException(uri, aCategory);
                }
                mQuickPermissionWidget.onDismiss();
            }
        });
        mQuickPermissionWidget.getPlacement().parentHandle = getHandle();
        // Place the dialog on top of the target button
        Rect offsetViewBounds = new Rect();
        target.getDrawingRect(offsetViewBounds);
        offsetDescendantRectToMyCoords(target, offsetViewBounds);
        float x = offsetViewBounds.left + (offsetViewBounds.right - offsetViewBounds.left) * 0.5f;
        mQuickPermissionWidget.getPlacement().parentAnchorX = x / getWidth();
        mQuickPermissionWidget.show(REQUEST_FOCUS);
    }

    private long kioskModeFlipperTimer;
    private int kioskModeFlipperCounter;

    private void updateKioskModeFlipper() {
        if (!mViewModel.getIsKioskMode().getValue().get()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - kioskModeFlipperTimer < 3000) {
            kioskModeFlipperCounter++;
        } else {
            kioskModeFlipperTimer = now;
            kioskModeFlipperCounter = 1;
        }

        if (kioskModeFlipperCounter >= 7) {
            mViewModel.setIsKioskMode(false);
        }
    }
}
