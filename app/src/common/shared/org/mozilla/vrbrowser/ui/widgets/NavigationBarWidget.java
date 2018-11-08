/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.*;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.browser.SessionStore;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.search.SearchEngineWrapper;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.ui.views.CustomUIButton;
import org.mozilla.vrbrowser.ui.views.NavigationURLBar;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;

public class NavigationBarWidget extends UIWidget implements GeckoSession.NavigationDelegate,
        GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate,
        WidgetManagerDelegate.UpdateListener, SessionStore.SessionChangeListener,
        NavigationURLBar.NavigationURLBarDelegate, VoiceSearchWidget.VoiceSearchDelegate,
        SharedPreferences.OnSharedPreferenceChangeListener, SuggestionsWidget.URLBarPopupDelegate {

    private static final String LOGTAG = "VRB";

    private AudioEngine mAudio;
    private UIButton mBackButton;
    private UIButton mForwardButton;
    private UIButton mReloadButton;
    private UIButton mHomeButton;
    private UIButton mServoButton;
    private NavigationURLBar mURLBar;
    private ViewGroup mNavigationContainer;
    private ViewGroup mFocusModeContainer;
    private ViewGroup mResizeModeContainer;
    private BrowserWidget mBrowserWidget;
    private boolean mIsLoading;
    private boolean mIsInFocusMode;
    private boolean mIsResizing;
    private Runnable mResizeBackHandler;
    private UIButton mResizeEnterButton;
    private UIButton mResizeExitButton;
    private UITextButton mPreset0;
    private UITextButton mPreset1;
    private UITextButton mPreset2;
    private UITextButton mPreset3;
    private ArrayList<CustomUIButton> mButtons;
    private int mURLBarLayoutIndex;
    private VoiceSearchWidget mVoiceSearchWidget;
    private Context mAppContext;
    private SharedPreferences mPrefs;
    private SuggestionsWidget mPopup;
    private SearchEngineWrapper mSearchEngineWrapper;

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
        mFocusModeContainer = findViewById(R.id.focusModeContainer);
        mResizeModeContainer = findViewById(R.id.resizeModeContainer);

        mResizeBackHandler = new Runnable() {
            @Override
            public void run() {
                exitResizeMode(true);
            }
        };

        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocusFromTouch();
                if (SessionStore.get().canGoBack())
                    SessionStore.get().goBack();
                else if (SessionStore.get().canUnstackSession())
                    SessionStore.get().unstackSession();

                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.BACK);
                }
            }
        });

        mForwardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocusFromTouch();
                SessionStore.get().goForward();
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mReloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocusFromTouch();
                if (mIsLoading) {
                    SessionStore.get().stop();
                } else {
                    SessionStore.get().reload();
                }
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mHomeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocusFromTouch();
                SessionStore.get().loadUri(SessionStore.get().getHomeUri());
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mServoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocusFromTouch();
                SessionStore.get().toggleServo();
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mResizeEnterButton = findViewById(R.id.resizeEnterButton);
        mResizeExitButton = findViewById(R.id.resizeExitButton);
        mPreset0 = findViewById(R.id.resizePreset0);
        mPreset1 = findViewById(R.id.resizePreset1);
        mPreset2 = findViewById(R.id.resizePreset2);
        mPreset3 = findViewById(R.id.resizePreset3);

        mResizeEnterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                enterResizeMode();
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mResizeExitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                exitResizeMode(true);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mPreset0.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                setResizePreset(0.5f);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mPreset1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                setResizePreset(1.0f);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mPreset2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                setResizePreset(2.0f);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mPreset3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.requestFocusFromTouch();
                setResizePreset(3.0f);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
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

        mVoiceSearchWidget = createChild(VoiceSearchWidget.class, false);
        mVoiceSearchWidget.setDelegate(this);

        mPopup = createChild(SuggestionsWidget.class);
        mPopup.setURLBarPopupDelegate(this);

        mSearchEngineWrapper = SearchEngineWrapper.get(getContext());

        SessionStore.get().addSessionChangeListener(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        updateServoButton();
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeUpdateListener(this);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        SessionStore.get().removeNavigationListener(this);
        SessionStore.get().removeProgressListener(this);
        SessionStore.get().removeContentListener(this);
        SessionStore.get().removeSessionChangeListener(this);
        mBrowserWidget = null;
        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_width);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.browser_world_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.navigation_bar_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 1.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = -35;
        aPlacement.opaque = false;
    }

    public void setBrowserWidget(BrowserWidget aWidget) {
        if (aWidget != null) {
            mWidgetPlacement.parentHandle = aWidget.getHandle();
        }
        mBrowserWidget = aWidget;
    }

    private void enterFocusMode() {
        if (mIsInFocusMode) {
            return;
        }
        mIsInFocusMode = true;
        AnimationHelper.fadeIn(mFocusModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, new Runnable() {
            @Override
            public void run() {
                // Set up required to show the URLBar while in focus mode
                mURLBarLayoutIndex = mNavigationContainer.indexOfChild(mURLBar);
                mNavigationContainer.removeView(mURLBar);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mURLBar.getLayoutParams();
                params.width = (int)(WidgetPlacement.pixelDimension(getContext(), R.dimen.browser_width_pixels) * 0.8);
                params.weight = 1;
                mURLBar.setLayoutParams(params);
                mFocusModeContainer.addView(mURLBar, 0);
                mURLBar.setVisibility(View.INVISIBLE);
                mURLBar.setClickable(false);
            }
        });
        AnimationHelper.fadeOut(mNavigationContainer, 0, null);

        mWidgetManager.pushWorldBrightness(this, WidgetManagerDelegate.DEFAULT_DIM_BRIGHTNESS);
        mWidgetManager.setTrayVisible(false);
    }

    private void exitFocusMode() {
        if (!mIsInFocusMode) {
            return;
        }
        mIsInFocusMode = false;

        // Restore URL bar to normal mode
        mFocusModeContainer.removeView(mURLBar);
        mNavigationContainer.addView(mURLBar, mURLBarLayoutIndex);
        mURLBar.setVisibility(View.VISIBLE);
        mURLBar.setAlpha(1.0f);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mURLBar.getLayoutParams();
        params.width = LayoutParams.WRAP_CONTENT;
        params.weight = 100;
        mURLBar.setLayoutParams(params);
        mURLBar.setClickable(true);

        AnimationHelper.fadeIn(mNavigationContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        AnimationHelper.fadeOut(mFocusModeContainer, 0, null);
        mWidgetManager.popWorldBrightness(this);

        if (SessionStore.get().isInFullScreen()) {
            SessionStore.get().exitFullScreen();
        }

        mWidgetManager.setTrayVisible(true);
    }

    private void enterResizeMode() {
        if (mIsResizing) {
            return;
        }
        mIsResizing = true;
        mWidgetManager.startWidgetResize(mBrowserWidget);
        AnimationHelper.fadeIn(mResizeModeContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        AnimationHelper.fadeOut(mNavigationContainer, 0, null);
        mWidgetManager.pushBackHandler(mResizeBackHandler);
    }

    private void exitResizeMode(boolean aCommitChanges) {
        if (!mIsResizing) {
            return;
        }
        mIsResizing = false;
        mWidgetManager.finishWidgetResize(mBrowserWidget);
        AnimationHelper.fadeIn(mNavigationContainer, AnimationHelper.FADE_ANIMATION_DURATION, null);
        AnimationHelper.fadeOut(mResizeModeContainer, 0, new Runnable() {
            @Override
            public void run() {
                onWidgetUpdate(mBrowserWidget);
            }
        });
        mWidgetManager.popBackHandler(mResizeBackHandler);
    }

    private void setResizePreset(float aResizeMode) {
        mBrowserWidget.setBrowserSize(
                SettingsStore.getInstance(getContext()).getWindowWidth(),
                SettingsStore.getInstance(getContext()).getWindowHeight(),
                aResizeMode);
    }

    public boolean isInFocusMode() {
        return mIsInFocusMode;
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
        if (mURLBar != null) {
            Log.d(LOGTAG, "Got onLoadUri");
            mURLBar.setURL(aRequest.uri);
        }

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
        if (mIsInFocusMode && !mIsResizing) {
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
        if (mIsInFocusMode) {
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
            if (!mIsInFocusMode) {
                enterFocusMode();
            }
            if (mIsResizing) {
                exitResizeMode(false);
            }

        } else {
            exitFocusMode();
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
        if (aWidget != mBrowserWidget || mIsResizing) {
            return;
        }

        // Browser window may have been resized, adjust the navigation bar
        float targetWidth = aWidget.getPlacement().worldWidth;
        float defaultWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.browser_world_width);
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
    }

    // NavigationURLBarDelegate

    @Override
    public void OnVoiceSearchClicked() {
        if (mVoiceSearchWidget.getPlacement().visible) {
            mVoiceSearchWidget.hide();

        } else {
            mVoiceSearchWidget.show();
        }
    }

    @Override
    public void OnShowSearchPopup() {
        if (mPopup != null) {
            final String text = mURLBar.getText().trim();
            final String originalText = mURLBar.getOriginalText().trim();
            if (originalText.length() > 0) {
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
                                mPopup.getPlacement().width = (int) (WidgetPlacement.convertPixelsToDp(getContext(), mURLBar.getWidth()));
                                mPopup.updatePlacement();
                                mPopup.show();
                            }
                        }
                );

            } else {
                mPopup.hide();
            }
        }
    }

    @Override
    public void OnHideSearchPopup() {
        if (mPopup != null && mPopup.isVisible()) {
            mPopup.hide();
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

    // URLBarPopupWidgetDelegate

    @Override
    public void OnItemClicked(SuggestionsWidget.SuggestionItem item) {
        mURLBar.handleURLEdit(item.url);
    }

    @Override
    public void OnItemDeleted(SuggestionsWidget.SuggestionItem item) {

    }
}
