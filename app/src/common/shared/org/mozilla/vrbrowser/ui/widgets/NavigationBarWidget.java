/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.suggestions.SuggestionsProvider;
import org.mozilla.vrbrowser.ui.views.CustomUIButton;
import org.mozilla.vrbrowser.ui.views.NavigationURLBar;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;
import org.mozilla.vrbrowser.ui.widgets.dialogs.VoiceSearchWidget;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.ServoUtils;
import org.mozilla.vrbrowser.utils.UIThreadExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import mozilla.components.concept.storage.VisitType;

public class NavigationBarWidget extends UIWidget implements GeckoSession.NavigationDelegate,
        GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate, WidgetManagerDelegate.WorldClickListener,
        WidgetManagerDelegate.UpdateListener, SessionStore.SessionChangeListener,
        NavigationURLBar.NavigationURLBarDelegate, VoiceSearchWidget.VoiceSearchDelegate,
        SharedPreferences.OnSharedPreferenceChangeListener, SuggestionsWidget.URLBarPopupDelegate,
        BookmarkListener, TrayListener {

    private static final String LOGTAG = "VRB";

    private AudioEngine mAudio;
    private UIButton mBackButton;
    private UIButton mForwardButton;
    private UIButton mReloadButton;
    private UIButton mHomeButton;
    private UIButton mServoButton;
    private NavigationURLBar mURLBar;
    private ViewGroup mNavigationContainer;
    private ViewGroup mFullScreenModeContainer;
    private ViewGroup mResizeModeContainer;
    private WindowWidget mWindowWidget;
    private boolean mIsLoading;
    private boolean mIsInFullScreenMode;
    private boolean mIsResizing;
    private boolean mIsInVRVideo;
    private boolean mAutoEnteredVRVideo;
    private WidgetPlacement mPlacementBeforeResize;
    private Runnable mResizeBackHandler;
    private Runnable mFullScreenBackHandler;
    private Runnable mVRVideoBackHandler;
    private UIButton mResizeEnterButton;
    private UIButton mResizeExitButton;
    private UIButton mFullScreenExitButton;
    private UIButton mBrightnessButton;
    private UIButton mFullScreenResizeButton;
    private UIButton mProjectionButton;
    private UITextButton mPreset0;
    private UITextButton mPreset1;
    private UITextButton mPreset2;
    private UITextButton mPreset3;
    private ArrayList<CustomUIButton> mButtons;
    private VoiceSearchWidget mVoiceSearchWidget;
    private Context mAppContext;
    private SharedPreferences mPrefs;
    private SuggestionsWidget mPopup;
    private SuggestionsProvider mSuggestionsProvider;
    private VideoProjectionMenuWidget mProjectionMenu;
    private WidgetPlacement mProjectionMenuPlacement;
    private BrightnessMenuWidget mBrightnessWidget;
    private MediaControlsWidget mMediaControlsWidget;
    private Media mFullScreenMedia;
    private @VideoProjectionMenuWidget.VideoProjectionFlags Integer mAutoSelectedProjection;

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

    private void initialize(Context aContext) {
        mAppContext = aContext.getApplicationContext();
        inflate(aContext, R.layout.navigation_bar, this);

        mAudio = AudioEngine.fromContext(aContext);
        mBackButton = findViewById(R.id.backButton);
        mForwardButton = findViewById(R.id.forwardButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mHomeButton = findViewById(R.id.homeButton);
        mServoButton = findViewById(R.id.servoButton);
        mURLBar = findViewById(R.id.urlBar);
        mNavigationContainer = findViewById(R.id.navigationBarContainer);
        mFullScreenModeContainer = findViewById(R.id.fullScreenModeContainer);
        mResizeModeContainer = findViewById(R.id.resizeModeContainer);
        mFullScreenExitButton = findViewById(R.id.fullScreenExitButton);
        mBrightnessButton = findViewById(R.id.brightnessButton);
        mFullScreenResizeButton = findViewById(R.id.fullScreenResizeEnterButton);
        mProjectionButton = findViewById(R.id.projectionButton);
        mPlacementBeforeResize = new WidgetPlacement(aContext);


        mResizeBackHandler = () -> exitResizeMode(ResizeAction.RESTORE_SIZE);

        mFullScreenBackHandler = this::exitFullScreenMode;
        mVRVideoBackHandler = () -> {
            exitVRVideo();
            if (mAutoEnteredVRVideo) {
                exitFullScreenMode();
            }
        };

        mBackButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (SessionStore.get().canGoBack())
                SessionStore.get().goBack();
            else if (SessionStore.get().canUnstackSession())
                SessionStore.get().unstackSession();

            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.BACK);
            }
        });

        mForwardButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            SessionStore.get().goForward();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mReloadButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            SessionStore.get().getHistoryStore().addHistory(
                    SessionStore.get().getCurrentUri(),
                    VisitType.RELOAD);
            if (mIsLoading) {
                SessionStore.get().stop();
            } else {
                SessionStore.get().reload();
            }
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mHomeButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            SessionStore.get().loadUri(SessionStore.get().getHomeUri());
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mServoButton.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            SessionStore.get().toggleServo();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mResizeEnterButton = findViewById(R.id.resizeEnterButton);
        mResizeExitButton = findViewById(R.id.resizeExitButton);
        mPreset0 = findViewById(R.id.resizePreset0);
        mPreset1 = findViewById(R.id.resizePreset1);
        mPreset2 = findViewById(R.id.resizePreset2);
        mPreset3 = findViewById(R.id.resizePreset3);

        mResizeEnterButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            enterResizeMode();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mResizeExitButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            exitResizeMode(ResizeAction.KEEP_SIZE);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mFullScreenResizeButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            enterResizeMode();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mFullScreenExitButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            exitFullScreenMode();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mProjectionButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mAutoSelectedProjection != null) {
                enterVRVideo(mAutoSelectedProjection);
                return;
            }
            boolean wasVisible = mProjectionMenu.isVisible();
            closeFloatingMenus();

            if (!wasVisible) {
                mProjectionMenu.setVisible(true);
            }
        });

        mBrightnessButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
            boolean wasVisible = mBrightnessWidget.isVisible();
            closeFloatingMenus();
            if (!wasVisible) {
                float anchor = 0.5f + (float)mBrightnessButton.getMeasuredWidth() / (float)NavigationBarWidget.this.getMeasuredWidth();
                mBrightnessWidget.getPlacement().parentAnchorX = anchor;
                mBrightnessWidget.setVisible(true);
            }
        });


        mPreset0.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(0.5f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mPreset1.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(1.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mPreset2.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(2.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mPreset3.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            setResizePreset(3.0f);
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }
        });

        mButtons = new ArrayList<>();
        mButtons.addAll(Arrays.<CustomUIButton>asList(
                mBackButton, mForwardButton, mReloadButton, mHomeButton, mResizeEnterButton, mResizeExitButton,
                mServoButton, mPreset0, mPreset1, mPreset2, mPreset3));

        mURLBar.setDelegate(this);

        SessionStore.get().addNavigationListener(this);
        SessionStore.get().addProgressListener(this);
        SessionStore.get().addContentListener(this);
        mWidgetManager.addUpdateListener(this);
        mWidgetManager.addWorldClickListener(this);

        mVoiceSearchWidget = createChild(VoiceSearchWidget.class, false);
        mVoiceSearchWidget.setDelegate(this);

        mSuggestionsProvider = new SuggestionsProvider(getContext());

        SessionStore.get().addSessionChangeListener(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        updateServoButton();

        handleSessionState();
    }

    @Override
    public void onPause() {
        super.onPause();
        mURLBar.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mURLBar.onResume();
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);
        mWidgetManager.removeWorldClickListener(this);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        SessionStore.get().removeNavigationListener(this);
        SessionStore.get().removeProgressListener(this);
        SessionStore.get().removeContentListener(this);
        SessionStore.get().removeSessionChangeListener(this);
        mWindowWidget = null;
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
        aPlacement.opaque = false;
        aPlacement.cylinder = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void setBrowserWidget(WindowWidget aWidget) {
        if (aWidget != null) {
            mWidgetPlacement.parentHandle = aWidget.getHandle();
        }
        mWindowWidget = aWidget;
    }

    private void setFullScreenSize() {
        mPlacementBeforeResize.copyFrom(mWindowWidget.getPlacement());
        final float minScale = WidgetPlacement.floatDimension(getContext(), R.dimen.window_fullscreen_min_scale);
        // Set browser fullscreen size
        float aspect = SettingsStore.getInstance(getContext()).getWindowAspect();
        Media media = SessionStore.get().getFullScreenVideo();
        if (media != null && media.getWidth() > 0 && media.getHeight() > 0) {
            aspect = (float)media.getWidth() / (float)media.getHeight();
        }
        float scale = mWindowWidget.getCurrentScale();
        // Enforce min fullscreen size.
        // If current window area is larger only resize if the aspect changes (e.g. media).
        if (scale < minScale || aspect != mWindowWidget.getCurrentAspect()) {
            mWindowWidget.resizeByMultiplier(aspect, Math.max(scale, minScale));
        }
    }

    private void enterFullScreenMode() {
        if (mIsInFullScreenMode) {
            return;
        }

        mWindowWidget.setSaveResizeChanges(false);
        setFullScreenSize();
        mWidgetManager.pushBackHandler(mFullScreenBackHandler);
        mIsInFullScreenMode = true;
        AnimationHelper.fadeIn(mFullScreenModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);

        AnimationHelper.fadeOut(mNavigationContainer, 0, null);

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);

        mWidgetManager.setTrayVisible(false);

        if (mProjectionMenu == null) {
            mProjectionMenu = new VideoProjectionMenuWidget(getContext());
            mProjectionMenu.setParentWidget(this);
            mProjectionMenuPlacement = new WidgetPlacement(getContext());
            mWidgetManager.addWidget(mProjectionMenu);
            mProjectionMenu.setDelegate((projection )-> {
                if (mIsInVRVideo) {
                    // Reproject while reproducing VRVideo
                    mWidgetManager.showVRVideo(mWindowWidget.getHandle(), projection);
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
        if (!mIsInFullScreenMode) {
            return;
        }

        // We need to add a delay for the exitFullScreen() call to solve some viewport scaling issues,
        // See https://github.com/MozillaReality/FirefoxReality/issues/833 for more info.
        postDelayed(() -> {
            if (SessionStore.get().isInFullScreen()) {
                SessionStore.get().exitFullScreen();
            }
        }, 50);

        mWindowWidget.getPlacement().copyFrom(mPlacementBeforeResize);
        mWidgetManager.updateWidget(mWindowWidget);
        mWindowWidget.setSaveResizeChanges(true);

        mIsInFullScreenMode = false;
        mWidgetManager.popBackHandler(mFullScreenBackHandler);

        AnimationHelper.fadeIn(mNavigationContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);

        mWidgetManager.popWorldBrightness(this);
        AnimationHelper.fadeOut(mFullScreenModeContainer, 0, null);

        mWidgetManager.setTrayVisible(true);
        closeFloatingMenus();
        mWidgetManager.popWorldBrightness(mBrightnessWidget);
    }

    private void enterResizeMode() {
        if (mIsResizing) {
            return;
        }
        mIsResizing = true;
        mPlacementBeforeResize.copyFrom(mWindowWidget.getPlacement());
        startWidgetResize();
        AnimationHelper.fadeIn(mResizeModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        if (mIsInFullScreenMode) {
            AnimationHelper.fadeOut(mFullScreenModeContainer, 0, null);
        } else {
            AnimationHelper.fadeOut(mNavigationContainer, 0, null);
        }
        mWidgetManager.pushBackHandler(mResizeBackHandler);
        mWidgetManager.setTrayVisible(false);
        closeFloatingMenus();
    }


    enum ResizeAction {
        KEEP_SIZE,
        RESTORE_SIZE
    }

    private void exitResizeMode(ResizeAction aResizeAction) {
        if (!mIsResizing) {
            return;
        }
        if (aResizeAction == ResizeAction.RESTORE_SIZE) {
            mWindowWidget.getPlacement().copyFrom(mPlacementBeforeResize);
            mWidgetManager.updateWidget(mWindowWidget);
            mWindowWidget.saveCurrentSize();
        }
        mIsResizing = false;
        finishWidgetResize();
        if (mIsInFullScreenMode) {
            AnimationHelper.fadeIn(mFullScreenModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        } else {
            AnimationHelper.fadeIn(mNavigationContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        }
        AnimationHelper.fadeOut(mResizeModeContainer, 0, () -> updateWidget());
        mWidgetManager.popBackHandler(mResizeBackHandler);
        mWidgetManager.setTrayVisible(!mIsInFullScreenMode);
        closeFloatingMenus();
    }

    private void enterVRVideo(@VideoProjectionMenuWidget.VideoProjectionFlags int aProjection) {
        if (mIsInVRVideo) {
            return;
        }
        mIsInVRVideo = true;
        mWidgetManager.pushBackHandler(mVRVideoBackHandler);
        mProjectionMenu.setSelectedProjection(aProjection);
        // Backup the placement because the same widget is reused in FullScreen & MediaControl menus
        mProjectionMenuPlacement.copyFrom(mProjectionMenu.getPlacement());

        mFullScreenMedia = SessionStore.get().getFullScreenVideo();

        this.setVisible(false);
        if (mFullScreenMedia != null && mFullScreenMedia.getWidth() > 0 && mFullScreenMedia.getHeight() > 0) {
            final boolean resetBorder = aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360 ||
                                        aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360_STEREO;
            mWindowWidget.enableVRVideoMode(mFullScreenMedia.getWidth(), mFullScreenMedia.getHeight(), resetBorder);
            // Handle video resize while in VR video playback
            mFullScreenMedia.setResizeDelegate((width, height) -> {
                mWindowWidget.enableVRVideoMode(width, height, resetBorder);
            });
        }
        mWindowWidget.setVisible(false);

        closeFloatingMenus();
        if (aProjection != VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
            mWidgetManager.setControllersVisible(false);
        }

        if (mMediaControlsWidget == null) {
            mMediaControlsWidget = new MediaControlsWidget(getContext());
            mMediaControlsWidget.setParentWidget(mWindowWidget.getHandle());
            mMediaControlsWidget.getPlacement().visible = false;
            mWidgetManager.addWidget(mMediaControlsWidget);
            mMediaControlsWidget.setBackHandler(this::exitVRVideo);
        }
        mMediaControlsWidget.setProjectionMenuWidget(mProjectionMenu);
        mMediaControlsWidget.setMedia(mFullScreenMedia);
        mMediaControlsWidget.setProjectionSelectorEnabled(true);
        mWidgetManager.updateWidget(mMediaControlsWidget);
        mWidgetManager.showVRVideo(mWindowWidget.getHandle(), aProjection);
    }

    private void exitVRVideo() {
        if (!mIsInVRVideo) {
            return;
        }
        if (mFullScreenMedia != null) {
            mFullScreenMedia.setResizeDelegate(null);
        }
        mIsInVRVideo = false;
        mWidgetManager.popBackHandler(mVRVideoBackHandler);
        mWidgetManager.hideVRVideo();
        mProjectionMenu.getPlacement().copyFrom(mProjectionMenuPlacement);
        closeFloatingMenus();
        mWidgetManager.setControllersVisible(true);

        this.setVisible(true);
        mWindowWidget.disableVRVideoMode();
        mWindowWidget.setVisible(true);
        mMediaControlsWidget.setVisible(false);
    }

    private void setResizePreset(float aMultiplier) {
        final float aspect = SettingsStore.getInstance(getContext()).getWindowAspect();
        mWindowWidget.resizeByMultiplier(aspect, aMultiplier);
    }

    public void showVoiceSearch() {
        mURLBar.showVoiceSearch(true);
    }

    public void updateServoButton() {
        // We show the Servo button if:
        // 1. the current session is using Servo. No matter what, we need the toggle button to go back to Gecko.
        // 2. Or, if the pref is enabled and the current url is white listed.
        boolean show = false;
        boolean isServoSession = false;
        GeckoSession currentSession = SessionStore.get().getCurrentSession();
        if (currentSession != null) {
            String currentUri = SessionStore.get().getCurrentUri();
            boolean isPrefEnabled = SettingsStore.getInstance(mAppContext).isServoEnabled();
            boolean isUrlWhiteListed = ServoUtils.isUrlInServoWhiteList(mAppContext, currentUri);
            isServoSession = ServoUtils.isInstanceOfServoSession(currentSession);
            show = isServoSession || (isPrefEnabled && isUrlWhiteListed);
        }
        if (show) {
            mServoButton.setVisibility(View.VISIBLE);
            mServoButton.setImageResource(isServoSession ? R.drawable.ic_icon_gecko : R.drawable.ic_icon_servo);
        } else {
            mServoButton.setVisibility(View.GONE);
        }
    }

    private void closeFloatingMenus() {
        if (mProjectionMenu != null) {
            mProjectionMenu.setVisible(false);
        }
        if (mBrightnessWidget != null) {
            mBrightnessWidget.setVisible(false);
        }
    }

    private void handleSessionState() {
        boolean isPrivateMode  = SessionStore.get().isCurrentSessionPrivate();

        mURLBar.setPrivateMode(isPrivateMode);
        for (CustomUIButton button : mButtons) {
            button.setPrivateMode(isPrivateMode);
        }
    }

    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession aSession, @NonNull String aUri) {
        return null;
    }

    @Override
    public GeckoResult<String> onLoadError(GeckoSession session, String uri, WebRequestError error) {
        return null;
    }

    public void release() {
        SessionStore.get().removeNavigationListener(this);
        SessionStore.get().removeProgressListener(this);
    }

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (mURLBar != null) {
            Log.d(LOGTAG, "Got location change");
            mURLBar.setURL(url);
            mReloadButton.setEnabled(true);
        }
        updateServoButton();
    }

    @Override
    public void onCanGoBack(GeckoSession aSession, boolean canGoBack) {
        if (mBackButton != null) {
            boolean enableBackButton = SessionStore.get().canUnstackSession() | canGoBack;

            Log.d(LOGTAG, "Got onCanGoBack: " + (enableBackButton ? "true" : "false"));
            mBackButton.setEnabled(enableBackButton);
            mBackButton.setHovered(false);
            mBackButton.setClickable(enableBackButton);
        }
    }

    @Override
    public void onCanGoForward(GeckoSession aSession, boolean canGoForward) {
        if (mForwardButton != null) {
            Log.d(LOGTAG, "Got onCanGoForward: " + (canGoForward ? "true" : "false"));
            mForwardButton.setEnabled(canGoForward);
            mForwardButton.setHovered(false);
            mForwardButton.setClickable(canGoForward);
        }
    }

    @Override
    public @Nullable GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession aSession, @NonNull LoadRequest aRequest) {
        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();

        Uri uri = Uri.parse(aRequest.uri);
        if ("file".equalsIgnoreCase(uri.getScheme())
                && !mWidgetManager.isPermissionGranted(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            mWidgetManager.requestPermission(
                    aRequest.uri,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    new GeckoSession.PermissionDelegate.Callback() {
                        @Override
                        public void grant() {
                            result.complete(AllowOrDeny.ALLOW);
                        }

                        @Override
                        public void reject() {
                            result.complete(AllowOrDeny.DENY);
                        }
                    });
            return result;
        }

        result.complete(AllowOrDeny.ALLOW);
        return result;
    }

    // Progress Listener
    @Override
    public void onPageStart(GeckoSession aSession, String aUri) {
        if (mURLBar != null) {
            Log.d(LOGTAG, "Got onPageStart");
            mURLBar.setURL(aUri);
        }
        mIsLoading = true;
        mURLBar.setIsLoading(true);
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_exit);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        mIsLoading = false;
        mURLBar.setIsLoading(false);
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_reload);
        }
    }

    @Override
    public void onProgressChange(GeckoSession session, int progress) {

    }

    @Override
    public void onSecurityChange(GeckoSession geckoSession, SecurityInformation securityInformation) {
        if (mURLBar != null) {
            boolean isSecure = securityInformation.isSecure;
            mURLBar.setIsInsecure(!isSecure);
        }
    }

    // Content delegate

    @Override
    public void onTitleChange(GeckoSession session, String title) {

    }

    @Override
    public void onFocusRequest(GeckoSession session) {

    }

    @Override
    public void onCloseRequest(GeckoSession session) {

    }

    @Override
    public void onFullScreen(GeckoSession session, boolean aFullScreen) {
        if (aFullScreen) {
            if (!mIsInFullScreenMode) {
                enterFullScreenMode();
            }
            if (mIsResizing) {
                exitResizeMode(ResizeAction.KEEP_SIZE);
            }
            AtomicBoolean autoEnter = new AtomicBoolean(false);
            mAutoSelectedProjection = VideoProjectionMenuWidget.getAutomaticProjection(SessionStore.get().getUriFromSession(session), autoEnter);
            if (mAutoSelectedProjection != null && autoEnter.get()) {
                mAutoEnteredVRVideo = true;
                postDelayed(() -> enterVRVideo(mAutoSelectedProjection), 300);
            } else {
                mAutoEnteredVRVideo = false;
            }
        } else {
            if (mIsInVRVideo) {
                exitVRVideo();
            }
            exitFullScreenMode();
        }
    }

    @Override
    public void onContextMenu(GeckoSession session, int screenX, int screenY, ContextElement element) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }

    @Override
    public void onCrash(GeckoSession session) {

    }

    @Override
    public void onFirstComposite(GeckoSession session) {

    }

    // WidgetManagerDelegate.UpdateListener
    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if (aWidget != mWindowWidget || mIsResizing) {
            return;
        }

        // Browser window may have been resized, adjust the navigation bar
        float targetWidth = aWidget.getPlacement().worldWidth;
        float defaultWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        targetWidth = Math.max(defaultWidth, targetWidth);
        targetWidth = Math.min(targetWidth, defaultWidth * 1.5f);

        float ratio = targetWidth / defaultWidth;
        mWidgetPlacement.worldWidth = targetWidth;
        mWidgetPlacement.width = (int) (WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width) * ratio);
        mWidgetManager.updateWidget(this);
    }

    // SessionStore.SessionChangeListener
    @Override
    public void onNewSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onRemoveSession(GeckoSession aSession, int aId) {

    }

    @Override
    public void onCurrentSessionChange(GeckoSession aSession, int aId) {
        handleSessionState();

        boolean isFullScreen = SessionStore.get().isInFullScreen(aSession);
        if (isFullScreen && !mIsInFullScreenMode) {
            enterFullScreenMode();
        } else if (!isFullScreen && mIsInFullScreenMode) {
            exitVRVideo();
            exitFullScreenMode();
        }
    }

    // NavigationURLBarDelegate

    @Override
    public void OnVoiceSearchClicked() {
        if (mVoiceSearchWidget.isVisible()) {
            mVoiceSearchWidget.hide(REMOVE_WIDGET);

        } else {
            mVoiceSearchWidget.show(REQUEST_FOCUS);
        }
    }


    @Override
    public void OnShowSearchPopup() {
        if (mPopup == null) {
            mPopup = createChild(SuggestionsWidget.class);
            mPopup.setURLBarPopupDelegate(this);
        }

        final String text = mURLBar.getText().trim();
        final String originalText = mURLBar.getOriginalText().trim();
        if (originalText.length() <= 0) {
            mPopup.hide(UIWidget.KEEP_WIDGET);
            return;
        }

        mSuggestionsProvider.setText(text);
        mSuggestionsProvider.setFilterText(originalText);
        mSuggestionsProvider.getSuggestions()
                .whenCompleteAsync((items, ex) -> {
                    mPopup.updateItems(items);
                    mPopup.setHighlightedText(originalText);

                    if (!mPopup.isVisible()) {
                        mPopup.updatePlacement((int)WidgetPlacement.convertPixelsToDp(getContext(), mURLBar.getWidth()));
                        mPopup.show(CLEAR_FOCUS);
                    }

                }, new UIThreadExecutor());
    }

    @Override
    public void onHideSearchPopup() {
        if (mPopup != null) {
            mPopup.hide(UIWidget.KEEP_WIDGET);
        }
    }

    // VoiceSearch Delegate

    @Override
    public void OnVoiceSearchResult(String transcription, float confidance) {
        mURLBar.handleURLEdit(transcription);
    }

    @Override
    public void OnVoiceSearchCanceled() {
        // Nothing to do yet
    }

    @Override
    public void OnVoiceSearchError() {
        // Nothing to do yet
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == mAppContext.getString(R.string.settings_key_servo)) {
            updateServoButton();

        } else if (key == mAppContext.getString(R.string.settings_key_user_agent_version)) {
            mURLBar.setUAMode(SettingsStore.getInstance(getContext()).getUaMode());
        }
    }

    // WorldClickListener
    @Override
    public void onWorldClick() {
        if (mIsInVRVideo && mMediaControlsWidget != null) {
            mMediaControlsWidget.setVisible(!mMediaControlsWidget.isVisible());
            if (mProjectionMenu.getSelectedProjection() != VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
                if (mMediaControlsWidget.isVisible()) {
                    // Reorient the MediaControl UI when the users clicks to show it.
                    // So you can look at any point of the 180/360 video and the UI always shows in front of you.
                    mWidgetManager.resetUIYaw();
                }
            }

            if (mMediaControlsWidget.isVisible()) {
                mWidgetManager.setControllersVisible(true);
            } else if (mProjectionMenu.getSelectedProjection() != VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
                mWidgetManager.setControllersVisible(false);
            }
        }
        closeFloatingMenus();
    }

    // URLBarPopupWidgetDelegate

    @Override
    public void OnItemClicked(SuggestionsWidget.SuggestionItem item) {
        mURLBar.handleURLEdit(item.url);
    }

    @Override
    public void OnItemDeleted(SuggestionsWidget.SuggestionItem item) {

    }

    // BookmarkListener

    @Override
    public void onBookmarksShown() {
        mURLBar.setURL("");
        mURLBar.setHint(R.string.about_bookmarks);
        mURLBar.setIsBookmarkMode(true);
    }

    @Override
    public void onBookmarksHidden() {
        mURLBar.setIsBookmarkMode(false);
        mURLBar.setURL(SessionStore.get().getCurrentUri());
        mURLBar.setHint(R.string.search_placeholder);
    }

    // TrayListener

    @Override
    public void onBookmarksClicked() {
        if (mIsResizing) {
            exitResizeMode(ResizeAction.RESTORE_SIZE);

        } else if (mIsInFullScreenMode) {
            exitFullScreenMode();

        } else if (mIsInVRVideo) {
            exitVRVideo();
        }
    }

    @Override
    public void onPrivateBrowsingClicked() {

    }

    private void finishWidgetResize() {
        mWidgetManager.finishWidgetResize(mWindowWidget);
    }

    private void startWidgetResize() {
        mWidgetManager.startWidgetResize(mWindowWidget);
    }

    private void updateWidget() {
        onWidgetUpdate(mWindowWidget);
    }
}
