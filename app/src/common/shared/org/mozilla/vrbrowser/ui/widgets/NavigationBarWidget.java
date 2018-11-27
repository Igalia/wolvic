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

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.*;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.ui.widgets.dialogs.VoiceSearchWidget;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.ui.views.CustomUIButton;
import org.mozilla.vrbrowser.ui.views.NavigationURLBar;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private BrowserWidget mBrowserWidget;
    private boolean mIsLoading;
    private boolean mIsInFullScreenMode;
    private boolean mIsResizing;
    private boolean mIsInVRVideo;
    private WidgetPlacement mSizeBeforeFullScreen;
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
    private SearchEngineWrapper mSearchEngineWrapper;
    private VideoProjectionMenuWidget mProjectionMenu;
    private WidgetPlacement mProjectionMenuPlacement;
    private BrightnessMenuWidget mBrightnessWidget;
    private MediaControlsWidget mMediaControlsWidget;
    private BookmarksWidget mBookmarksWidget;
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
        mSizeBeforeFullScreen = new WidgetPlacement(aContext);


        mResizeBackHandler = () -> exitResizeMode(true);

        mFullScreenBackHandler = this::exitFullScreenMode;
        mVRVideoBackHandler = this::exitVRVideo;

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
            exitResizeMode(true);
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

        mSearchEngineWrapper = SearchEngineWrapper.get(getContext());

        SessionStore.get().addSessionChangeListener(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        updateServoButton();
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
        mBrowserWidget = null;
        mBookmarksWidget = null;
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void setBrowserWidget(BrowserWidget aWidget) {
        if (aWidget != null) {
            mWidgetPlacement.parentHandle = aWidget.getHandle();
        }
        mBrowserWidget = aWidget;
    }

    public void setBookmarksWidget(BookmarksWidget aWidget) {
        mBookmarksWidget = aWidget;
    }

    private void setFullScreenSize() {
        SettingsStore settings = SettingsStore.getInstance(getContext());
        mSizeBeforeFullScreen.copyFrom(mBrowserWidget.getPlacement());
        final float oldWidth = settings.getBrowserWorldWidth();
        final float oldHeight = settings.getBrowserWorldHeight();
        // Set browser fullscreen size
        float aspect = SettingsStore.getInstance(getContext()).getWindowAspect();
        Media media = SessionStore.get().getFullScreenVideo();
        if (media != null && media.getWidth() > 0 && media.getHeight() > 0) {
            aspect = (float)media.getWidth() / (float)media.getHeight();
        }
        mBrowserWidget.resizeByMultiplier(aspect,1.75f);
        // Save the old values on settings to prevent the fullscreen size being used on a app restart
        SettingsStore.getInstance(getContext()).setBrowserWorldWidth(oldWidth);
        SettingsStore.getInstance(getContext()).setBrowserWorldHeight(oldHeight);
    }

    private void enterFullScreenMode() {
        if (mIsInFullScreenMode) {
            return;
        }

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
                    mWidgetManager.showVRVideo(mBrowserWidget.getHandle(), projection);
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
        getHandler().postDelayed(() -> {
            if (SessionStore.get().isInFullScreen()) {
                SessionStore.get().exitFullScreen();
            }
        }, 50);

        mBrowserWidget.getPlacement().copyFrom(mSizeBeforeFullScreen);
        mWidgetManager.updateWidget(mBrowserWidget);

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
        startWidgetResize();
        AnimationHelper.fadeIn(mResizeModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        if (mIsInFullScreenMode) {
            AnimationHelper.fadeOut(mFullScreenModeContainer, 0, null);
        } else {
            AnimationHelper.fadeOut(mNavigationContainer, 0, null);
        }
        mWidgetManager.pushBackHandler(mResizeBackHandler);
        closeFloatingMenus();
    }

    private void exitResizeMode(boolean aCommitChanges) {
        if (!mIsResizing) {
            return;
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
        closeFloatingMenus();
    }

    private void enterVRVideo(@VideoProjectionMenuWidget.VideoProjectionFlags int aProjection) {
        if (mIsInVRVideo) {
            return;
        }
        mIsInVRVideo = true;
        mWidgetManager.pushBackHandler(mVRVideoBackHandler);
        // Backup the placement because the same widget is reused in FullScreen & MediaControl menus
        mProjectionMenuPlacement.copyFrom(mProjectionMenu.getPlacement());

        mFullScreenMedia = SessionStore.get().getFullScreenVideo();

        this.setVisible(false);
        if (mFullScreenMedia != null && mFullScreenMedia.getWidth() > 0 && mFullScreenMedia.getHeight() > 0) {
            final boolean resetBorder = aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360 ||
                                        aProjection == VideoProjectionMenuWidget.VIDEO_PROJECTION_360_STEREO;
            mBrowserWidget.enableVRVideoMode(mFullScreenMedia.getWidth(), mFullScreenMedia.getHeight(), resetBorder);
            // Handle video resize while in VR video playback
            mFullScreenMedia.setResizeDelegate((width, height) -> {
                mBrowserWidget.enableVRVideoMode(width, height, resetBorder);
            });
        }
        mBrowserWidget.setVisible(false);

        closeFloatingMenus();
        if (mProjectionMenu.getSelectedProjection() != VideoProjectionMenuWidget.VIDEO_PROJECTION_3D_SIDE_BY_SIDE) {
            mWidgetManager.setControllersVisible(false);
        }

        if (mMediaControlsWidget == null) {
            mMediaControlsWidget = new MediaControlsWidget(getContext());
            mMediaControlsWidget.setParentWidget(mBrowserWidget.getHandle());
            mMediaControlsWidget.getPlacement().visible = false;
            mWidgetManager.addWidget(mMediaControlsWidget);
            mMediaControlsWidget.setBackHandler(this::exitVRVideo);
        }
        mMediaControlsWidget.setProjectionMenuWidget(mProjectionMenu);
        mMediaControlsWidget.setMedia(mFullScreenMedia);
        mMediaControlsWidget.setProjectionSelectorEnabled(mAutoSelectedProjection == null);
        mWidgetManager.updateWidget(mMediaControlsWidget);
        mWidgetManager.showVRVideo(mBrowserWidget.getHandle(), aProjection);
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
        mBrowserWidget.disableVRVideoMode();
        mBrowserWidget.setVisible(true);
        mMediaControlsWidget.setVisible(false);
    }

    private void setResizePreset(float aMultiplier) {
        final float aspect = SettingsStore.getInstance(getContext()).getWindowAspect();
        mBrowserWidget.resizeByMultiplier(aspect, aMultiplier);
        mBookmarksWidget.resizeByMultiplier(aspect, aMultiplier);
    }

    public void showVoiceSearch() {
        mURLBar.showVoiceSearch(true);
    }

    public void updateServoButton() {
        if (SettingsStore.getInstance(mAppContext).isServoEnabled()) {
            mServoButton.setVisibility(View.VISIBLE);
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
        if (mIsInFullScreenMode && !mIsResizing) {
            AnimationHelper.fadeIn(mURLBar, 0, null);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        mIsLoading = false;
        mURLBar.setIsLoading(false);
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_reload);
        }
        if (mIsInFullScreenMode) {
            AnimationHelper.fadeOut(mURLBar, 0, null);
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
                exitResizeMode(false);
            }
            AtomicBoolean autoEnter = new AtomicBoolean(false);
            mAutoSelectedProjection = VideoProjectionMenuWidget.getAutomaticProjection(SessionStore.get().getUriFromSession(session), autoEnter);
            if (mAutoSelectedProjection != null && autoEnter.get()) {
                getHandler().postDelayed(() -> enterVRVideo(mAutoSelectedProjection), 300);
            }
        } else {
            if (mIsInVRVideo) {
                exitVRVideo();
            }
            exitFullScreenMode();
        }
    }

    @Override
    public void onContextMenu(GeckoSession session, int screenX, int screenY, String uri, int elementType, String elementSrc) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }

    @Override
    public void onCrash(GeckoSession session) {

    }

    // WidgetManagerDelegate.UpdateListener
    @Override
    public void onWidgetUpdate(Widget aWidget) {
        if ((aWidget != mBrowserWidget && aWidget != mBookmarksWidget) || mIsResizing) {
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
        boolean isPrivateMode  = aSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        mURLBar.setPrivateMode(isPrivateMode);

        for (CustomUIButton button : mButtons) {
            button.setPrivateMode(isPrivateMode);
        }
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
            mVoiceSearchWidget.show();
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

        mSearchEngineWrapper.getSuggestions(
                originalText,
                (suggestions) -> {
                    ArrayList<SuggestionsWidget.SuggestionItem> items = new ArrayList<>();

                    if (!text.equals(originalText)) {
                        // Completion from browser-domains
                        items.add(SuggestionsWidget.SuggestionItem.create(
                                text,
                                getSearchURLOrDomain(text),
                                null,
                                SuggestionsWidget.SuggestionItem.Type.COMPLETION
                        ));
                    }

                    // Original text
                    items.add(SuggestionsWidget.SuggestionItem.create(
                            originalText,
                            getSearchURLOrDomain(originalText),
                            null,
                            SuggestionsWidget.SuggestionItem.Type.SUGGESTION
                    ));

                    // Suggestions
                    for (String suggestion : suggestions) {
                        String url = mSearchEngineWrapper.getSearchURL(suggestion);
                        items.add(SuggestionsWidget.SuggestionItem.create(
                                suggestion,
                                url,
                                null,
                                SuggestionsWidget.SuggestionItem.Type.SUGGESTION
                        ));
                    }
                    mPopup.setItems(items);
                    mPopup.setHighlightedText(originalText);

                    if (!mPopup.isVisible()) {
                        mPopup.updatePlacement((int)WidgetPlacement.convertPixelsToDp(getContext(), mURLBar.getWidth()));
                        mPopup.show();
                    }
                }
        );
    }

    @Override
    public void onHideSearchPopup() {
        if (mPopup != null) {
            mPopup.hide(UIWidget.KEEP_WIDGET);
        }
    }

    private String getSearchURLOrDomain(String text) {
        if (UrlUtils.isDomain(text)) {
            return text;

        } else {
            return mSearchEngineWrapper.getSearchURL(text);
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
        mURLBar.setBookmarks(true);
        mURLBar.setURL("");
        mURLBar.setHint(R.string.about_bookmarks);
        mURLBar.setIsBookmarkMode(true);
    }

    @Override
    public void onBookmarksHidden() {
        mURLBar.setIsBookmarkMode(false);
        mURLBar.setBookmarks(false);
        mURLBar.setURL(SessionStore.get().getCurrentUri());
        mURLBar.setHint(R.string.search_placeholder);
    }

    // TrayListener

    @Override
    public void onBookmarksClicked() {
        if (mIsResizing) {
            exitResizeMode(false);

        } else if (mIsInFullScreenMode) {
            exitFullScreenMode();

        } else if (mIsInVRVideo) {
            exitVRVideo();
        }
    }

    private void finishWidgetResize() {
        if (mBrowserWidget.isVisible()) {
            mWidgetManager.finishWidgetResize(mBrowserWidget);

        } else if (mBookmarksWidget.isVisible()) {
            mWidgetManager.finishWidgetResize(mBookmarksWidget);
        }
    }

    private void startWidgetResize() {
        if (mBrowserWidget.isVisible()) {
            mWidgetManager.startWidgetResize(mBrowserWidget);

        } else if (mBookmarksWidget.isVisible()) {
            mWidgetManager.startWidgetResize(mBookmarksWidget);
        }
    }

    private void updateWidget() {
        if (mBrowserWidget.isVisible()) {
            onWidgetUpdate(mBrowserWidget);

        } else if (mBookmarksWidget.isVisible()) {
            onWidgetUpdate(mBookmarksWidget);
        }
    }
}
