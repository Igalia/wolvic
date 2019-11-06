/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.engine;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResponse;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.MediaElement;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.UserAgentOverride;
import org.mozilla.vrbrowser.browser.VideoAvailabilityListener;
import org.mozilla.vrbrowser.geolocation.GeolocationData;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.InternalPages;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.mozilla.vrbrowser.utils.ServoUtils.createServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isInstanceOfServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

public class Session implements ContentBlocking.Delegate, GeckoSession.NavigationDelegate,
        GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate, GeckoSession.TextInputDelegate,
        GeckoSession.PromptDelegate, GeckoSession.MediaDelegate, GeckoSession.HistoryDelegate, GeckoSession.PermissionDelegate,
        GeckoSession.SelectionActionDelegate, SharedPreferences.OnSharedPreferenceChangeListener, SessionChangeListener {

    private static final String LOGTAG = SystemUtils.createLogtag(Session.class);

    private transient LinkedList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private transient LinkedList<GeckoSession.ProgressDelegate> mProgressListeners;
    private transient LinkedList<GeckoSession.ContentDelegate> mContentListeners;
    private transient LinkedList<SessionChangeListener> mSessionChangeListeners;
    private transient LinkedList<GeckoSession.TextInputDelegate> mTextInputListeners;
    private transient LinkedList<VideoAvailabilityListener> mVideoAvailabilityListeners;
    private transient LinkedList<BitmapChangedListener> mBitmapChangedListeners;
    private transient LinkedList<GeckoSession.SelectionActionDelegate> mSelectionActionListeners;
    private transient UserAgentOverride mUserAgentOverride;

    private SessionState mState;
    private LinkedList<Runnable> mQueuedCalls = new LinkedList<>();
    private transient GeckoSession.PermissionDelegate mPermissionDelegate;
    private transient GeckoSession.PromptDelegate mPromptDelegate;
    private transient GeckoSession.HistoryDelegate mHistoryDelegate;
    private transient Context mContext;
    private transient SharedPreferences mPrefs;
    private transient GeckoRuntime mRuntime;
    private boolean mUsePrivateMode;
    private transient byte[] mPrivatePage;
    private boolean mIsActive;


    public interface BitmapChangedListener {
        void onBitmapChanged(Session aSession, Bitmap aBitmap);
    }

    @IntDef(value = { SESSION_OPEN, SESSION_DO_NOT_OPEN})
    public @interface SessionOpenModeFlags {}
    public static final int SESSION_OPEN = 0;
    public static final int SESSION_DO_NOT_OPEN = 1;

    protected Session(Context aContext, GeckoRuntime aRuntime, boolean aUsePrivateMode) {
        this(aContext, aRuntime, aUsePrivateMode, null, SESSION_OPEN);
    }

    protected Session(Context aContext, GeckoRuntime aRuntime, boolean aUsePrivateMode,
                      @Nullable SessionSettings aSettings, @SessionOpenModeFlags int aOpenMode) {
        mContext = aContext;
        mRuntime = aRuntime;
        mUsePrivateMode = aUsePrivateMode;
        initialize();
        if (aSettings != null) {
            mState = createSession(aSettings, aOpenMode);
        } else {
            mState = createSession(aOpenMode);
        }

        setupSessionListeners(mState.mSession);
    }

    protected Session(Context aContext, GeckoRuntime aRuntime, SessionState aRestoreState) {
        mContext = aContext;
        mRuntime = aRuntime;
        mUsePrivateMode = false;
        initialize();
        mState = aRestoreState;
        restore();
        setupSessionListeners(mState.mSession);
    }

    private void initialize() {
        mNavigationListeners = new LinkedList<>();
        mProgressListeners = new LinkedList<>();
        mContentListeners = new LinkedList<>();
        mSessionChangeListeners = new LinkedList<>();
        mTextInputListeners = new LinkedList<>();
        mVideoAvailabilityListeners = new LinkedList<>();
        mSelectionActionListeners = new LinkedList<>();
        mBitmapChangedListeners = new LinkedList<>();

        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        mPrivatePage = InternalPages.createAboutPage(mContext, pageResources);

        if (mUserAgentOverride == null) {
            mUserAgentOverride = new UserAgentOverride();
            mUserAgentOverride.loadOverridesFromAssets((Activity)mContext, mContext.getString(R.string.user_agent_override_file));
        }
    }

    protected void shutdown() {
        if (mState.mSession != null) {
            if (mState.mSession.isOpen()) {
                mState.mSession.close();
            }
            mState.mSession = null;
        }

        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onRemoveSession(this);
        }
        if (mState.mParentId != null) {
            Session parent = SessionStore.get().getSession(mState.mParentId);
            if (parent != null) {
                parent.mSessionChangeListeners.remove(this);
            }
        }

        mQueuedCalls.clear();
        mNavigationListeners.clear();
        mProgressListeners.clear();
        mContentListeners.clear();
        mSessionChangeListeners.clear();
        mTextInputListeners.clear();
        mVideoAvailabilityListeners.clear();
        mSelectionActionListeners.clear();
        mBitmapChangedListeners.clear();

        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    private void dumpAllState() {
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            dumpState(listener);
        }
        for (GeckoSession.ProgressDelegate listener: mProgressListeners) {
            dumpState(listener);
        }
        for (GeckoSession.ContentDelegate listener: mContentListeners) {
            dumpState(listener);
        }

        for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
            dumpState(listener);
        }
    }

    private void dumpState(GeckoSession.NavigationDelegate aListener) {
        if (mState.mSession != null) {
            aListener.onCanGoBack(mState.mSession, mState.mCanGoBack);
            aListener.onCanGoForward(mState.mSession, mState.mCanGoForward);
            aListener.onLocationChange(mState.mSession, mState.mUri);
        }
    }

    private void dumpState(GeckoSession.ProgressDelegate aListener) {
        if (mState.mIsLoading) {
            aListener.onPageStart(mState.mSession, mState.mUri);
        } else {
            aListener.onPageStop(mState.mSession, true);
        }

        if (mState.mSecurityInformation != null) {
            aListener.onSecurityChange(mState.mSession, mState.mSecurityInformation);
        }
    }

    private void dumpState(GeckoSession.ContentDelegate aListener) {
        aListener.onTitleChange(mState.mSession, mState.mTitle);
    }

    private void dumpState(VideoAvailabilityListener aListener) {
        aListener.onVideoAvailabilityChanged(mState.mMediaElements != null && mState.mMediaElements.size() > 0);
    }

    private void flushQueuedEvents() {
        for (Runnable call: mQueuedCalls) {
            call.run();
        }
        mQueuedCalls.clear();
    }

    public void setPermissionDelegate(GeckoSession.PermissionDelegate aDelegate) {
        mPermissionDelegate = aDelegate;
    }

    public void setPromptDelegate(GeckoSession.PromptDelegate aDelegate) {
        mPromptDelegate = aDelegate;
    }

    public void setHistoryDelegate(GeckoSession.HistoryDelegate aDelegate) {
        mHistoryDelegate = aDelegate;
    }

    public void addNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.add(aListener);
        dumpState(aListener);
    }

    public void removeNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.remove(aListener);
    }

    public void addProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.add(aListener);
        dumpState(aListener);
    }

    public void removeProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.remove(aListener);
    }

    public void addContentListener(GeckoSession.ContentDelegate aListener) {
        mContentListeners.add(aListener);
        dumpState(aListener);
    }

    public void removeContentListener(GeckoSession.ContentDelegate aListener) {
        mContentListeners.remove(aListener);
    }

    public void addSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.add(aListener);
    }

    public void removeSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.remove(aListener);
    }

    public void addTextInputListener(GeckoSession.TextInputDelegate aListener) {
        mTextInputListeners.add(aListener);
    }

    public void removeTextInputListener(GeckoSession.TextInputDelegate aListener) {
        mTextInputListeners.remove(aListener);
    }

    public void addVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.add(aListener);
        dumpState(aListener);
    }

    public void removeVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.remove(aListener);
    }

    public void addSelectionActionListener(GeckoSession.SelectionActionDelegate aListener) {
        mSelectionActionListeners.add(aListener);
    }

    public void removeSelectionActionListener(GeckoSession.ContentDelegate aListener) {
        mSelectionActionListeners.remove(aListener);
    }

    public void addBitmapChangedListener(BitmapChangedListener aListener) {
        mBitmapChangedListeners.add(aListener);
    }

    public void removeBitmapChangedListener(BitmapChangedListener aListener) {
        mBitmapChangedListeners.remove(aListener);
    }

    private void setupSessionListeners(GeckoSession aSession) {
        aSession.setNavigationDelegate(this);
        aSession.setProgressDelegate(this);
        aSession.setContentDelegate(this);
        aSession.getTextInput().setDelegate(this);
        aSession.setPermissionDelegate(this);
        aSession.setPromptDelegate(this);
        aSession.setContentBlockingDelegate(this);
        aSession.setMediaDelegate(this);
        aSession.setHistoryDelegate(this);
        aSession.setSelectionActionDelegate(this);
    }

    private void cleanSessionListeners(GeckoSession aSession) {
        aSession.setContentDelegate(null);
        aSession.setNavigationDelegate(null);
        aSession.setProgressDelegate(null);
        aSession.getTextInput().setDelegate(null);
        aSession.setPromptDelegate(null);
        aSession.setPermissionDelegate(null);
        aSession.setContentBlockingDelegate(null);
        aSession.setMediaDelegate(null);
        aSession.setHistoryDelegate(null);
        aSession.setSelectionActionDelegate(null);
    }

    private void restore() {
        SessionSettings settings = mState.mSettings;
        if (settings == null) {
            settings = new SessionSettings.Builder()
                    .withDefaultSettings(mContext)
                    .build();
        }

        mState.mSession = createGeckoSession(settings);
        if (!mState.mSession.isOpen()) {
            mState.mSession.open(mRuntime);
        }
        
        if (mState.mSessionState != null) {
            mState.mSession.restoreState(mState.mSessionState);
        }

        if (mUsePrivateMode) {
            loadPrivateBrowsingPage();
        } else if(mState.mSessionState == null || mState.mUri.equals(mContext.getResources().getString(R.string.about_blank)) ||
                (mState.mSessionState != null && mState.mSessionState.size() == 0)) {
            loadHomePage();
        } else if (mState.mUri != null && mState.mUri.contains(".youtube.com")) {
            mState.mSession.loadUri(mState.mUri);
        }

        dumpAllState();
    }

    private SessionState createSession(@SessionOpenModeFlags int aOpenMode) {
        SessionSettings settings = new SessionSettings.Builder()
                .withDefaultSettings(mContext)
                .build();

        return createSession(settings, aOpenMode);
    }

    private SessionState createSession(@NonNull SessionSettings aSettings, @SessionOpenModeFlags int aOpenMode) {
        SessionState state = new SessionState();
        state.mSettings = aSettings;
        state.mSession = createGeckoSession(aSettings);

        if (aOpenMode == SESSION_OPEN && !state.mSession.isOpen()) {
            state.mSession.open(mRuntime);
        }

        return state;
    }

    private GeckoSession createGeckoSession(@NonNull SessionSettings aSettings) {
        GeckoSessionSettings geckoSettings = new GeckoSessionSettings.Builder()
                .useMultiprocess(aSettings.isMultiprocessEnabled())
                .usePrivateMode(mUsePrivateMode)
                .useTrackingProtection(aSettings.isTrackingProtectionEnabled())
                .userAgentMode(aSettings.getUserAgentMode())
                .viewportMode(aSettings.getViewportMode())
                .suspendMediaWhenInactive(aSettings.isSuspendMediaWhenInactiveEnabled())
                .build();

        GeckoSession session;
        if (aSettings.isServoEnabled() && isServoAvailable()) {
            session = createServoSession(mContext);
        } else {
            session = new GeckoSession(geckoSettings);
        }

        session.getSettings().setUserAgentOverride(aSettings.getUserAgentOverride());

        return session;
    }

    private void recreateSession() {
        SessionState previous = mState;

        mState = createSession(previous.mSettings, SESSION_OPEN);
        if (previous.mSessionState != null)
            mState.mSession.restoreState(previous.mSessionState);
        if (previous.mSession != null) {
            closeSession(previous.mSession);
        }
        setupSessionListeners(mState.mSession);

        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onCurrentSessionChange(previous.mSession, mState.mSession);
        }
    }

    private void closeSession(@NonNull GeckoSession aSession) {
        cleanSessionListeners(aSession);
        aSession.setActive(false);
        aSession.stop();
        aSession.close();
        mIsActive = false;
    }

    public void captureBitmap(@NonNull GeckoDisplay aDisplay) {
        aDisplay.capturePixels().then(bitmap -> {
            if (bitmap != null) {
                BitmapCache.getInstance(mContext).scaleBitmap(bitmap, 500, 280).thenAccept(scaledBitmap -> {
                    BitmapCache.getInstance(mContext).addBitmap(getId(), scaledBitmap);
                    for (BitmapChangedListener listener: mBitmapChangedListeners) {
                        listener.onBitmapChanged(Session.this, scaledBitmap);
                    }

                }).exceptionally(throwable -> {
                    Log.d(LOGTAG, "Error scaling the bitmap: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
                });
            }
            return null;
        });
    }

    public void captureBackgroundBitmap(int displayWidth, int displayHeight) {
        Surface captureSurface = BitmapCache.getInstance(mContext).acquireCaptureSurface(displayWidth, displayHeight);
        if (captureSurface == null) {
            return;
        }
        GeckoSession session = mState.mSession;
        GeckoDisplay display = session.acquireDisplay();
        display.surfaceChanged(captureSurface, displayWidth, displayHeight);
        display.capturePixels().then(bitmap -> {
            if (bitmap != null) {
                BitmapCache.getInstance(mContext).scaleBitmap(bitmap, 500, 280).thenAccept(scaledBitmap -> {
                    BitmapCache.getInstance(mContext).addBitmap(getId(), scaledBitmap);
                    for (BitmapChangedListener listener: mBitmapChangedListeners) {
                        listener.onBitmapChanged(Session.this, scaledBitmap);
                    }

                }).exceptionally(throwable -> {
                    Log.d(LOGTAG, "Error scaling the bitmap: " + throwable.getLocalizedMessage());
                    throwable.printStackTrace();
                    return null;
                });
            }
            display.surfaceDestroyed();
            session.releaseDisplay(display);
            BitmapCache.getInstance(mContext).releaseCaptureSurface();
            return null;
        });
    }

    public boolean hasCapturedBitmap() {
        return BitmapCache.getInstance(mContext).hasBitmap(mState.mId);
    }

    public void purgeHistory() {
        if (mState.mSession != null) {
            mState.mSession.purgeHistory();
        }
    }

    public void setRegion(String aRegion) {
        Log.d(LOGTAG, "Session setRegion: " + aRegion);
        mState.mRegion = aRegion != null ? aRegion.toLowerCase() : "worldwide";

        // There is a region initialize and the home is already loaded
        if (mState.mSession != null && isHomeUri(getCurrentUri())) {
            mState.mSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        }
    }

    public String getHomeUri() {
        String homepage = SettingsStore.getInstance(mContext).getHomepage();
        if (homepage.equals(mContext.getString(R.string.homepage_url)) && mState.mRegion != null) {
            homepage = homepage + "?region=" + mState.mRegion;
        }
        return homepage;
    }

    public Boolean isHomeUri(String aUri) {
        return aUri != null && aUri.toLowerCase().startsWith(
          SettingsStore.getInstance(mContext).getHomepage()
        );
    }

    public String getCurrentUri() {
        return mState.mUri;
    }

    public String getCurrentTitle() {
        return mState.mTitle;
    }

    public boolean isSecure() {
        return mState.mSecurityInformation != null && mState.mSecurityInformation.isSecure;
    }

    public boolean isVideoAvailable() {
        return mState.mMediaElements != null && mState.mMediaElements.size() > 0;
    }

    public Media getFullScreenVideo() {
        for (Media media: mState.mMediaElements) {
            if (media.isFullscreen()) {
                return media;
            }
        }
        if (mState.mMediaElements.size() > 0) {
            return mState.mMediaElements.get(mState.mMediaElements.size() - 1);
        }

        return null;
    }

    public boolean isInputActive() {
        return mState.mIsInputActive;
    }

    public boolean canGoBack() {
        if (mState.mCanGoBack || isInFullScreen()) {
            return true;
        }
        if (mState.mParentId != null) {
            Session parent = SessionStore.get().getSession(mState.mParentId);
            return  parent != null && !parent.isActive();
        }
        return false;
    }

    public void goBack() {
        if (isInFullScreen()) {
            exitFullScreen();
        } else if (mState.mCanGoBack && mState.mSession != null) {
            mState.mSession.goBack();
        } else if (mState.mParentId != null) {
          Session parent = SessionStore.get().getSession(mState.mParentId);
          if (parent != null && !parent.isActive()) {
              for (SessionChangeListener listener: mSessionChangeListeners) {
                  listener.onUnstackSession(this, parent);
              }
          }
        }
    }

    public void goForward() {
        if (mState.mCanGoForward && mState.mSession != null) {
            mState.mSession.goForward();
        }
    }

    public void setActive(boolean aActive) {
        // Flush the events queued while the session was inactive
        if (mState.mSession != null && !mIsActive && aActive) {
            flushQueuedEvents();
        }

        if (mState.mSession != null) {
            mState.mSession.setActive(aActive);
        }

        mIsActive = aActive;

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onActiveStateChange(this, aActive);
        }
    }

    public void reload() {
        if (mState.mSession != null) {
            mState.mSession.reload();
        }
    }

    public void stop() {
        if (mState.mSession != null) {
            mState.mSession.stop();
        }
    }

    public void loadUri(String aUri) {
        if (aUri == null) {
            aUri = getHomeUri();
        }
        if (mState.mSession != null) {
            Log.d(LOGTAG, "Loading URI: " + aUri);
            mState.mSession.loadUri(aUri);
        }
    }

    public void loadHomePage() {
        loadUri(getHomeUri());
    }

    public void loadPrivateBrowsingPage() {
        if (mState.mSession != null) {
            mState.mSession.loadData(mPrivatePage, "text/html");
        }
    }

    public void toggleServo() {
        if (mState.mSession == null) {
            return;
        }

        Log.v("servo", "toggleServo");
        SessionState previous = mState;
        String uri = getCurrentUri();

        SessionSettings settings = new SessionSettings.Builder()
                .withDefaultSettings(mContext)
                .withServo(!isInstanceOfServoSession(mState.mSession))
                .build();

        mState = createSession(settings, SESSION_OPEN);
        closeSession(previous.mSession);
        loadUri(uri);
    }

    public boolean isInFullScreen() {
        return mState.mFullScreen;
    }

    public void exitFullScreen() {
        if (mState.mSession != null) {
            mState.mSession.exitFullScreen();
        }
    }

    public GeckoSession getGeckoSession() {
        return mState.mSession;
    }

    public String getId() {
        return mState.mId;
    }

    public boolean isPrivateMode() {
        if (mState.mSession != null) {
            return mState.mSession.getSettings().getUsePrivateMode();
        }
        return false;
    }

    // Session Settings

    protected void setServo(final boolean enabled) {
        mState.mSettings.setServoEnabled(enabled);
        if (mState.mSession != null && isInstanceOfServoSession(mState.mSession) != enabled) {
           toggleServo();
        }
    }

    public int getUaMode() {
        return mState.mSession.getSettings().getUserAgentMode();
    }

    public boolean isActive() {
        return mIsActive;
    }

    private static final String M_PREFIX = "m.";
    private static final String MOBILE_PREFIX = "mobile.";

    private String checkForMobileSite(String aUri) {
        if (aUri == null) {
            return null;
        }
        String result = null;
        URI uri;
        try {
            uri = new URI(aUri);
        } catch (URISyntaxException | NullPointerException e) {
            Log.d(LOGTAG, "Error parsing URL: " + aUri + " " + e.getMessage());
            return null;
        }
        String authority = uri.getAuthority();
        if (authority == null) {
            return null;
        }
        authority = authority.toLowerCase();
        String foundPrefix = null;
        if (authority.startsWith(M_PREFIX)) {
            foundPrefix= M_PREFIX;
        } else if (authority.startsWith(MOBILE_PREFIX)) {
            foundPrefix = MOBILE_PREFIX;
        }
        if (foundPrefix != null) {
            try {
                uri = new URI(uri.getScheme(), authority.substring(foundPrefix.length()), uri.getPath(), uri.getQuery(), uri.getFragment());
                result = uri.toString();
            } catch (URISyntaxException | NullPointerException e) {
                Log.d(LOGTAG, "Error dropping mobile prefix from: " + aUri + " " + e.getMessage());
            }
        }
        return result;
    }

    public void setUaMode(int mode) {
        if (mState.mSession == null || mState.mSettings.getUserAgentMode() == mode) {
            return;
        }
        mState.mSettings.setUserAgentMode(mode);
        mState.mSession.getSettings().setUserAgentMode(mode);
        String overrideUri = null;
        if (mode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP) {
            mState.mSettings.setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
            overrideUri = checkForMobileSite(mState.mUri);
        } else {
            mState.mSettings.setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }
        mState.mSession.getSettings().setViewportMode(mState.mSettings.getViewportMode());
        mState.mSession.loadUri(overrideUri != null ? overrideUri : mState.mUri, GeckoSession.LOAD_FLAGS_BYPASS_CACHE | GeckoSession.LOAD_FLAGS_REPLACE_HISTORY);
    }

    protected void setMultiprocess(final boolean aEnabled) {
        if (mState.mSettings.isMultiprocessEnabled() != aEnabled) {
            mState.mSettings.setMultiprocessEnabled(aEnabled);
            recreateSession();
        }
    }

    protected void setTrackingProtection(final boolean aEnabled) {
        if (mState.mSettings.isTrackingProtectionEnabled() != aEnabled) {
            mState.mSettings.setTrackingProtectionEnabled(aEnabled);
            mState.mSession.getSettings().setUseTrackingProtection(aEnabled);
        }
    }

    public void clearCache(final long clearFlags) {
        if (mRuntime != null) {
            // Per GeckoView Docs:
            // Note: Any open session may re-accumulate previously cleared data.
            // To ensure that no persistent data is left behind, you need to close all sessions prior to clearing data.
            // https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/StorageController.html#clearData-long-
            if (mState.mSession != null) {
                mState.mSession.stop();
                mState.mSession.close();
            }

            mRuntime.getStorageController().clearData(clearFlags).then(aVoid -> {
                recreateSession();
                return null;
            });
        }
    }

    public void updateLastUse() {
        mState.mLastUse = System.currentTimeMillis();
    }

    public long getLastUse() {
        return mState.mLastUse;
    }

    public @NonNull SessionState getSessionState() {
        return mState;
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession aSession, String aUri) {
        if (mState.mSession != aSession) {
            return;
        }

        mState.mPreviousUri = mState.mUri;
        mState.mUri = aUri;

        for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onLocationChange(aSession, aUri);
        }

        // The homepage finishes loading after the region has been updated
        if (mState.mRegion != null && aUri.equalsIgnoreCase(SettingsStore.getInstance(mContext).getHomepage())) {
            aSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        }
    }

    @Override
    public void onCanGoBack(@NonNull GeckoSession aSession, boolean aCanGoBack) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onCanGoBack: " + (aCanGoBack ? "true" : "false"));
        mState.mCanGoBack = aCanGoBack;

        for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onCanGoBack(aSession, aCanGoBack);
        }
    }

    @Override
    public void onCanGoForward(@NonNull GeckoSession aSession, boolean aCanGoForward) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onCanGoForward: " + (aCanGoForward ? "true" : "false"));
        mState.mCanGoForward = aCanGoForward;

        for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onCanGoForward(aSession, aCanGoForward);
        }
    }

    @Override
    public @Nullable GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession aSession, @NonNull LoadRequest aRequest) {
        String uri = aRequest.uri;

        Log.d(LOGTAG, "onLoadRequest: " + uri);

        String uriOverride = SessionUtils.checkYoutubeOverride(uri);
        if (uriOverride != null) {
            aSession.loadUri(uriOverride);
            return GeckoResult.DENY;
        }

        if (aSession == mState.mSession) {
            Log.d(LOGTAG, "Testing for UA override");

            final String userAgentOverride = mUserAgentOverride.lookupOverride(uri);
            aSession.getSettings().setUserAgentOverride(userAgentOverride);
            if (mState.mSettings != null) {
                mState.mSettings.setUserAgentOverride(userAgentOverride);
            }
        }

        if (mContext.getString(R.string.about_private_browsing).equalsIgnoreCase(uri)) {
            return GeckoResult.DENY;
        }

        if (mNavigationListeners.size() == 0) {
            return GeckoResult.ALLOW;
        }

        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        AtomicInteger count = new AtomicInteger(0);
        AtomicBoolean allowed = new AtomicBoolean(false);
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            GeckoResult<AllowOrDeny> listenerResult = listener.onLoadRequest(aSession, aRequest);
            if (listenerResult != null) {
                listenerResult.then(value -> {
                    if (AllowOrDeny.ALLOW.equals(value)) {
                        allowed.set(true);
                    }
                    if (count.getAndIncrement() == mNavigationListeners.size() - 1) {
                        result.complete(allowed.get() ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                    }

                    return null;
                });

            } else {
                allowed.set(true);
                if (count.getAndIncrement() == mNavigationListeners.size() - 1) {
                    result.complete(allowed.get() ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                }
            }
        }

        return result;
    }

    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession aSession, @NonNull String aUri) {
        Log.d(LOGTAG, "Session onStackSession: " + aUri);

        Session session = SessionStore.get().createSession(mUsePrivateMode, mState.mSettings, SESSION_DO_NOT_OPEN);
        session.mState.mParentId = mState.mId;
        for (SessionChangeListener listener: new LinkedList<>(mSessionChangeListeners)) {
            listener.onStackSession(session);
        }
        mSessionChangeListeners.add(session);
        return GeckoResult.fromValue(session.getGeckoSession());
    }

    @Override
    public GeckoResult<String> onLoadError(@NonNull GeckoSession session, String uri,  @NonNull WebRequestError error) {
        Log.d(LOGTAG, "Session onLoadError: " + uri);

        return GeckoResult.fromValue(InternalPages.createErrorPageDataURI(mContext, uri, error.category, error.code));
    }

    // Progress Listener

    @Override
    public void onPageStart(@NonNull GeckoSession aSession, @NonNull String aUri) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStart");
        mState.mIsLoading = true;
        TelemetryWrapper.startPageLoadTime();

        for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
            listener.onPageStart(aSession, aUri);
        }
    }

    @Override
    public void onPageStop(@NonNull GeckoSession aSession, boolean b) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStop");
        mState.mIsLoading = false;
        if (!SessionUtils.isLocalizedContent(mState.mUri)) {
            TelemetryWrapper.uploadPageLoadToHistogram(mState.mUri);
        }

        for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
            listener.onPageStop(aSession, b);
        }
    }

    @Override
    public void onSecurityChange(@NonNull GeckoSession aSession, @NonNull SecurityInformation aInformation) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStop");
        mState.mSecurityInformation = aInformation;

        for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
            listener.onSecurityChange(aSession, aInformation);
        }
    }

    @Override
    public void onSessionStateChange(@NonNull GeckoSession aSession,
                                     @NonNull GeckoSession.SessionState aSessionState) {
        if (mState.mSession == aSession) {
            mState.mSessionState = aSessionState;
        }
    }

    // Content Delegate

    @Override
    public void onTitleChange(@NonNull GeckoSession aSession, String aTitle) {
        if (mState.mSession != aSession) {
            return;
        }

        mState.mTitle = aTitle;

        for (GeckoSession.ContentDelegate listener : mContentListeners) {
            listener.onTitleChange(aSession, aTitle);
        }
    }

    @Override
    public void onCloseRequest(@NonNull GeckoSession aSession) {

    }

    @Override
    public void onFullScreen(@NonNull GeckoSession aSession, boolean aFullScreen) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onFullScreen");
        mState.mFullScreen = aFullScreen;

        for (GeckoSession.ContentDelegate listener : mContentListeners) {
            listener.onFullScreen(aSession, aFullScreen);
        }
    }

    @Override
    public void onContextMenu(@NonNull GeckoSession session, int screenX, int screenY, @NonNull ContextElement element) {
        if (mState.mSession == session) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onContextMenu(session, screenX, screenY, element);
            }
        }
    }

    @Override
    public void onCrash(@NonNull GeckoSession session) {
        Log.e(LOGTAG,"Child crashed. Creating new session");
        recreateSession();
        loadUri(getHomeUri());
    }

    @Override
    public void onFirstComposite(@NonNull GeckoSession aSession) {
        if (mState.mSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstComposite(aSession);
            }
        }
    }

    @Override
    public void onFirstContentfulPaint(@NonNull GeckoSession aSession) {
        if (mState.mSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstContentfulPaint(aSession);
            }
        }
    }

    // TextInput Delegate

    @Override
    public void restartInput(@NonNull GeckoSession aSession, int reason) {
        if (mState.mSession == aSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.restartInput(aSession, reason);
            }
        }
    }

    @Override
    public void showSoftInput(@NonNull GeckoSession aSession) {
        if (mState.mSession == aSession) {
            mState.mIsInputActive = true;
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.showSoftInput(aSession);
            }
        }
    }

    @Override
    public void hideSoftInput(@NonNull GeckoSession aSession) {
        if (mState.mSession == aSession) {
            mState.mIsInputActive = false;
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.hideSoftInput(aSession);
            }
        }
    }

    @Override
    public void updateSelection(@NonNull GeckoSession aSession, int selStart, int selEnd, int compositionStart, int compositionEnd) {
        if (mState.mSession == aSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateSelection(aSession, selStart, selEnd, compositionStart, compositionEnd);
            }
        }
    }

    @Override
    public void updateExtractedText(@NonNull GeckoSession aSession, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {
        if (mState.mSession == aSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateExtractedText(aSession, request, text);
            }
        }
    }

    @Override
    public void updateCursorAnchorInfo(@NonNull GeckoSession aSession, @NonNull CursorAnchorInfo info) {
        if (mState.mSession == aSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateCursorAnchorInfo(aSession, info);
            }
        }
    }

    @Override
    public void onContentBlocked(@NonNull final GeckoSession session, @NonNull final ContentBlocking.BlockEvent event) {
        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.AD) != 0) {
          Log.i(LOGTAG, "Blocking Ad: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.ANALYTIC) != 0) {
            Log.i(LOGTAG, "Blocking Analytic: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.CONTENT) != 0) {
            Log.i(LOGTAG, "Blocking Content: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.SOCIAL) != 0) {
            Log.i(LOGTAG, "Blocking Social: " + event.uri);
        }
    }

    // PromptDelegate

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(@NonNull GeckoSession geckoSession, @NonNull PopupPrompt popupPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onPopupPrompt(geckoSession, popupPrompt);
        }
        return GeckoResult.fromValue(popupPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession aSession, @NonNull AlertPrompt alertPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onAlertPrompt(aSession, alertPrompt);
        }
        return GeckoResult.fromValue(alertPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession aSession, @NonNull ButtonPrompt buttonPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onButtonPrompt(aSession, buttonPrompt);
        }
        return GeckoResult.fromValue(buttonPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession aSession, @NonNull TextPrompt textPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onTextPrompt(aSession, textPrompt);
        }
        return GeckoResult.fromValue(textPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession aSession, @NonNull AuthPrompt authPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onAuthPrompt(aSession, authPrompt);
        }
        return GeckoResult.fromValue(authPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession aSession, @NonNull ChoicePrompt choicePrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onChoicePrompt(aSession, choicePrompt);
        }
        return GeckoResult.fromValue(choicePrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onColorPrompt(@NonNull GeckoSession aSession, @NonNull ColorPrompt colorPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onColorPrompt(aSession, colorPrompt);
        }
        return GeckoResult.fromValue(colorPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(@NonNull GeckoSession aSession, @NonNull DateTimePrompt dateTimePrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onDateTimePrompt(aSession, dateTimePrompt);
        }
        return GeckoResult.fromValue(dateTimePrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onFilePrompt(@NonNull GeckoSession aSession, @NonNull FilePrompt filePrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onFilePrompt(aSession, filePrompt);
        }
        return GeckoResult.fromValue(filePrompt.dismiss());
    }

    // MediaDelegate

    @Override
    public void onMediaAdd(@NonNull GeckoSession aSession, @NonNull MediaElement element) {
        if (mState.mSession != aSession) {
            return;
        }
        Media media = new Media(element);
        mState.mMediaElements.add(media);

        for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
            listener.onVideoAvailabilityChanged(true);
        }
    }

    @Override
    public void onMediaRemove(@NonNull GeckoSession aSession, @NonNull MediaElement element) {
        if (mState.mSession != aSession) {
            return;
        }
        for (int i = 0; i < mState.mMediaElements.size(); ++i) {
            Media media = mState.mMediaElements.get(i);
            if (media.getMediaElement() == element) {
                media.unload();
                mState.mMediaElements.remove(i);
                if (mState.mMediaElements.size() == 0) {
                    for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
                        listener.onVideoAvailabilityChanged(false);
                    }
                }
                return;
            }
        }
    }

    // HistoryDelegate

    @Override
    public void onHistoryStateChange(@NonNull GeckoSession aSession, @NonNull GeckoSession.HistoryDelegate.HistoryList historyList) {
        if (mState.mSession == aSession) {
            if (mHistoryDelegate != null) {
                mHistoryDelegate.onHistoryStateChange(aSession, historyList);

            } else {
                mQueuedCalls.add(() -> {
                    if (mHistoryDelegate != null) {
                        mHistoryDelegate.onHistoryStateChange(aSession, historyList);
                    }
                });
            }
        }
    }

    @Nullable
    @Override
    public GeckoResult<Boolean> onVisited(@NonNull GeckoSession aSession, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        if (mState.mSession == aSession) {
            if (mHistoryDelegate != null) {
                return mHistoryDelegate.onVisited(aSession, url, lastVisitedURL, flags);

            } else {
                final GeckoResult<Boolean> response = new GeckoResult<>();
                mQueuedCalls.add(() -> {
                    if (mHistoryDelegate != null) {
                        try {
                            requireNonNull(mHistoryDelegate.onVisited(aSession, url, lastVisitedURL, flags)).then(aBoolean -> {
                                response.complete(aBoolean);
                                return null;

                            }).exceptionally(throwable -> {
                                Log.d(LOGTAG, "Null GeckoResult from onVisited");
                                return null;
                            });

                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    }
                });

                return response;
            }
        }

        return GeckoResult.fromValue(false);
    }

    @UiThread
    @Nullable
    public GeckoResult<boolean[]> getVisited(@NonNull GeckoSession aSession, @NonNull String[] urls) {
        if (mState.mSession == aSession) {
            if (mHistoryDelegate != null) {
                return mHistoryDelegate.getVisited(aSession, urls);

            } else {
                final GeckoResult<boolean[]> response = new GeckoResult<>();
                mQueuedCalls.add(() -> {
                    if (mHistoryDelegate != null) {
                        try {
                            requireNonNull(mHistoryDelegate.getVisited(aSession, urls)).then(aBoolean -> {
                                response.complete(aBoolean);
                                return null;

                            }).exceptionally(throwable -> {
                                Log.d(LOGTAG, "Null GeckoResult from getVisited");
                                return null;
                            });

                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    }
                });
                return response;
            }
        }

        return GeckoResult.fromValue(new boolean[]{});
    }

    // PermissionDelegate
    @Override
    public void onAndroidPermissionsRequest(@NonNull GeckoSession aSession, @Nullable String[] strings, @NonNull Callback callback) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            mPermissionDelegate.onAndroidPermissionsRequest(aSession, strings, callback);
        }
    }

    @Override
    public void onContentPermissionRequest(@NonNull GeckoSession aSession, @Nullable String s, int i, @NonNull Callback callback) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            mPermissionDelegate.onContentPermissionRequest(aSession, s, i, callback);
        }
    }

    @Override
    public void onMediaPermissionRequest(@NonNull GeckoSession aSession, @NonNull String s, @Nullable MediaSource[] mediaSources, @Nullable MediaSource[] mediaSources1, @NonNull MediaCallback mediaCallback) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            mPermissionDelegate.onMediaPermissionRequest(aSession, s, mediaSources, mediaSources1, mediaCallback);
        }
    }


    // SharedPreferences.OnSharedPreferenceChangeListener

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mContext != null) {
            if (key.equals(mContext.getString(R.string.settings_key_geolocation_data))) {
                GeolocationData data = GeolocationData.parse(sharedPreferences.getString(key, null));
                setRegion(data.getCountryCode());
            }
        }
    }

    // GeckoSession.SelectionActionDelegate

    @Override
    public void onShowActionRequest(@NonNull GeckoSession aSession, @NonNull Selection selection, @NonNull String[] strings, @NonNull GeckoResponse<String> geckoResponse) {
        if (mState.mSession == aSession) {
            for (GeckoSession.SelectionActionDelegate listener : mSelectionActionListeners) {
                listener.onShowActionRequest(aSession, selection, strings, geckoResponse);
            }
        }
    }

    @Override
    public void onHideAction(@NonNull GeckoSession aSession, int aHideReason) {
        if (mState.mSession == aSession) {
            for (GeckoSession.SelectionActionDelegate listener : mSelectionActionListeners) {
                listener.onHideAction(aSession, aHideReason);
            }
        }
    }

    // SessionChangeListener

    @Override
    public void onRemoveSession(Session aParent) {
        if (mState.mParentId != null) {
            mState.mParentId = null;
            // Parent stack session closed. Notify canGoBack state changed
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getGeckoSession(), canGoBack());
            }
        }
    }

    @Override
    public void onActiveStateChange(Session aSession, boolean aActive) {
        if (mState.mParentId != null) {
            // Parent stack session has been attached/detached. Notify canGoBack state changed
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getGeckoSession(), canGoBack());
            }
        }
    }
}
