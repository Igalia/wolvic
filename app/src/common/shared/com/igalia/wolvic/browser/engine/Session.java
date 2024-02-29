/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.engine;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.UriOverride;
import com.igalia.wolvic.browser.VideoAvailabilityListener;
import com.igalia.wolvic.browser.api.WAllowOrDeny;
import com.igalia.wolvic.browser.api.WAutocomplete;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WFactory;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.browser.api.WSlowScriptResponse;
import com.igalia.wolvic.browser.api.WWebRequestError;
import com.igalia.wolvic.browser.api.WWebResponse;
import com.igalia.wolvic.browser.content.TrackingProtectionPolicy;
import com.igalia.wolvic.browser.content.TrackingProtectionStore;
import com.igalia.wolvic.geolocation.GeolocationData;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.InternalPages;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Session implements WContentBlocking.Delegate, WSession.NavigationDelegate,
        WSession.ProgressDelegate, WSession.ContentDelegate, WSession.TextInputDelegate,
        WSession.PromptDelegate, WSession.HistoryDelegate, WSession.PermissionDelegate,
        WSession.SelectionActionDelegate, SharedPreferences.OnSharedPreferenceChangeListener, SessionChangeListener {

    private static final String LOGTAG = SystemUtils.createLogtag(Session.class);
    private static UriOverride sUserAgentOverride;
    private static UriOverride sDesktopModeOverrides;
    private static final long KEEP_ALIVE_DURATION_MS = 1000; // 1 second.

    private transient CopyOnWriteArrayList<WSession.NavigationDelegate> mNavigationListeners;
    private transient CopyOnWriteArrayList<WSession.ProgressDelegate> mProgressListeners;
    private transient CopyOnWriteArrayList<WSession.ContentDelegate> mContentListeners;
    private transient CopyOnWriteArrayList<SessionChangeListener> mSessionChangeListeners;
    private transient CopyOnWriteArrayList<WSession.TextInputDelegate> mTextInputListeners;
    private transient CopyOnWriteArrayList<VideoAvailabilityListener> mVideoAvailabilityListeners;
    private transient CopyOnWriteArrayList<BitmapChangedListener> mBitmapChangedListeners;
    private transient CopyOnWriteArrayList<WSession.SelectionActionDelegate> mSelectionActionListeners;
    private transient CopyOnWriteArrayList<WebXRStateChangedListener> mWebXRStateListeners;
    private transient CopyOnWriteArrayList<PopUpStateChangedListener> mPopUpStateStateListeners;
    private transient CopyOnWriteArrayList<DrmStateChangedListener> mDrmStateStateListeners;

    private SessionState mState;
    private transient CopyOnWriteArrayList<Runnable> mQueuedCalls = new CopyOnWriteArrayList<>();
    private transient WSession.PermissionDelegate mPermissionDelegate;
    private transient WSession.PromptDelegate mPromptDelegate;
    private transient WSession.HistoryDelegate mHistoryDelegate;
    private transient ExternalRequestDelegate mExternalRequestDelegate;
    private transient Context mContext;
    private transient SharedPreferences mPrefs;
    private transient WRuntime mRuntime;
    private transient byte[] mPrivatePage;
    private transient boolean mFirstContentfulPaint;
    private transient long mKeepAlive;
    private transient Media mMedia;

    private static final List<String> FORCE_MOBILE_VIEWPORT = Collections.singletonList(".youtube.com");

    public interface BitmapChangedListener {
        void onBitmapChanged(Session aSession, Bitmap aBitmap);
    }

    public interface WebXRStateChangedListener {
        void onWebXRStateChanged(Session aSession, @SessionState.WebXRState int aWebXRState);
    }

    public interface PopUpStateChangedListener {
        void onPopUpStateChanged(Session aSession, @SessionState.PopupState int aPopUpState);
    }

    public interface DrmStateChangedListener {
        void onDrmStateChanged(Session aSession, @SessionState.DrmState int aDrmState);
    }

    public interface ExternalRequestDelegate {
        boolean onHandleExternalRequest(@NonNull String url);
    }

    @IntDef(value = { SESSION_OPEN, SESSION_DO_NOT_OPEN})
    @interface SessionOpenModeFlags {}
    static final int SESSION_OPEN = 0;
    static final int SESSION_DO_NOT_OPEN = 1;

    @NonNull
    static Session createWebExtensionSession(Context aContext, WRuntime aRuntime, @NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode, @NonNull SessionChangeListener listener) {
        Session session = new Session(aContext, aRuntime, aSettings);
        session.mState.mIsWebExtensionSession = true;
        session.addSessionChangeListener(listener);
        listener.onSessionAdded(session);
        if (aOpenMode == Session.SESSION_OPEN) {
            session.openSession();
            session.setActive(true);
        }

        return session;
    }

    @NonNull
    static Session createSession(Context aContext, WRuntime aRuntime, @NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode, @NonNull SessionChangeListener listener) {
        Session session = new Session(aContext, aRuntime, aSettings);
        session.addSessionChangeListener(listener);
        listener.onSessionAdded(session);
        if (aOpenMode == Session.SESSION_OPEN) {
            session.openSession();
            session.setActive(true);
        }

        return session;
    }

    @NonNull
    static Session createSuspendedSession(Context aContext, WRuntime aRuntime, @NonNull SessionState aRestoreState, @NonNull SessionChangeListener listener) {
        Session session = new Session(aContext, aRuntime, aRestoreState);
        session.addSessionChangeListener(listener);

        return session;
    }

    private Session(Context aContext, WRuntime aRuntime, @NonNull SessionSettings aSettings) {
        mContext = aContext;
        mRuntime = aRuntime;
        initialize();
        mState = createSessionState(aSettings);
    }

    private Session(Context aContext, WRuntime aRuntime, @NonNull SessionState aRestoreState) {
        mContext = aContext;
        mRuntime = aRuntime;
        initialize();
        mState = aRestoreState;
    }

    private void initialize() {
        mNavigationListeners = new CopyOnWriteArrayList<>();
        mProgressListeners = new CopyOnWriteArrayList<>();
        mContentListeners = new CopyOnWriteArrayList<>();
        mSessionChangeListeners = new CopyOnWriteArrayList<>();
        mTextInputListeners = new CopyOnWriteArrayList<>();
        mVideoAvailabilityListeners = new CopyOnWriteArrayList<>();
        mSelectionActionListeners = new CopyOnWriteArrayList<>();
        mBitmapChangedListeners = new CopyOnWriteArrayList<>();
        mWebXRStateListeners = new CopyOnWriteArrayList<>();
        mPopUpStateStateListeners = new CopyOnWriteArrayList<>();
        mDrmStateStateListeners = new CopyOnWriteArrayList<>();
        mMedia = new Media();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        mPrivatePage = InternalPages.createAboutPage(mContext, pageResources);

        if (sUserAgentOverride == null) {
            sUserAgentOverride = new UriOverride("user agent");
            sUserAgentOverride.loadOverridesFromAssets((Activity)mContext, mContext.getString(R.string.user_agent_override_file));
        }
        if (sDesktopModeOverrides == null) {
            sDesktopModeOverrides = new UriOverride("desktop mode");
            sDesktopModeOverrides.loadOverridesFromAssets((Activity)mContext, "desktopModeOverrides.json");
        }
    }

    protected void shutdown() {
        if (mState.mSession != null) {
            setActive(false);
            suspend();
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
        mWebXRStateListeners.clear();
        mPopUpStateStateListeners.clear();
        mDrmStateStateListeners.clear();

        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    private void dumpAllState() {
        for (WSession.NavigationDelegate listener: mNavigationListeners) {
            dumpState(listener);
        }
        for (WSession.ProgressDelegate listener: mProgressListeners) {
            dumpState(listener);
        }
        for (WSession.ContentDelegate listener: mContentListeners) {
            dumpState(listener);
        }

        for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
            dumpState(listener);
        }

        for (WebXRStateChangedListener listener: mWebXRStateListeners) {
            dumpState(listener);
        }

        for (PopUpStateChangedListener listener: mPopUpStateStateListeners) {
            dumpState(listener);
        }

        for (DrmStateChangedListener listener: mDrmStateStateListeners) {
            dumpState(listener);
        }
    }

    private void dumpState(WSession.NavigationDelegate aListener) {
        if (mState.mSession != null) {
            aListener.onCanGoBack(mState.mSession, canGoBack());
            aListener.onCanGoForward(mState.mSession, mState.mCanGoForward);
            aListener.onLocationChange(mState.mSession, mState.mUri);
        }
    }

    private void dumpState(WSession.ProgressDelegate aListener) {
        if (mState.mSession == null)
            return;
        if (mState.mIsLoading) {
            aListener.onPageStart(mState.mSession, mState.mUri);
            aListener.onProgressChange(mState.mSession, 0);
        } else {
            aListener.onProgressChange(mState.mSession, 100);
            aListener.onPageStop(mState.mSession, true);
        }

        if (mState.mSecurityInformation != null) {
            aListener.onSecurityChange(mState.mSession, mState.mSecurityInformation);
        }
    }

    private void dumpState(WSession.ContentDelegate aListener) {
        aListener.onTitleChange(mState.mSession, mState.mTitle);
    }

    private void dumpState(VideoAvailabilityListener aListener) {
        Media activeMedia = getActiveVideo();
        if (activeMedia != null)
            aListener.onVideoAvailabilityChanged(activeMedia,true);
    }

    private void dumpState(WebXRStateChangedListener aListener) {
        aListener.onWebXRStateChanged(this, mState.mWebXRState);
    }

    private void dumpState(PopUpStateChangedListener aListener) {
        aListener.onPopUpStateChanged(this, mState.mPopUpState);
    }

    private void dumpState(DrmStateChangedListener aListener) {
        aListener.onDrmStateChanged(this, mState.mDrmState);
    }

    private void flushQueuedEvents() {
        for (Runnable call: mQueuedCalls) {
            call.run();
        }
        mQueuedCalls.clear();
    }

    public void setPermissionDelegate(WSession.PermissionDelegate aDelegate) {
        mPermissionDelegate = aDelegate;
    }

    public void setPromptDelegate(WSession.PromptDelegate aDelegate) {
        mPromptDelegate = aDelegate;
    }

    public void setHistoryDelegate(WSession.HistoryDelegate aDelegate) {
        mHistoryDelegate = aDelegate;
    }

    public void setExternalRequestDelegate(ExternalRequestDelegate aDelegate) {
        mExternalRequestDelegate = aDelegate;
    }

    public void setVideoAvailabilityDelegate(VideoAvailabilityListener aDelegate) {
        mMedia.setAvailabilityDelegate(aDelegate);
    }

    public void addNavigationListener(WSession.NavigationDelegate aListener) {
        mNavigationListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeNavigationListener(WSession.NavigationDelegate aListener) {
        mNavigationListeners.remove(aListener);
    }

    public void addProgressListener(WSession.ProgressDelegate aListener) {
        mProgressListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeProgressListener(WSession.ProgressDelegate aListener) {
        mProgressListeners.remove(aListener);
    }

    public void addContentListener(WSession.ContentDelegate aListener) {
        mContentListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeContentListener(WSession.ContentDelegate aListener) {
        mContentListeners.remove(aListener);
    }

    public void addSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.addIfAbsent(aListener);
    }

    public void removeSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.remove(aListener);
    }

    public void addTextInputListener(WSession.TextInputDelegate aListener) {
        mTextInputListeners.addIfAbsent(aListener);
    }

    public void removeTextInputListener(WSession.TextInputDelegate aListener) {
        mTextInputListeners.remove(aListener);
    }

    public void addVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.remove(aListener);
    }

    public void addSelectionActionListener(WSession.SelectionActionDelegate aListener) {
        mSelectionActionListeners.addIfAbsent(aListener);
    }

    public void removeSelectionActionListener(WSession.ContentDelegate aListener) {
        mSelectionActionListeners.remove(aListener);
    }

    public void addBitmapChangedListener(BitmapChangedListener aListener) {
        mBitmapChangedListeners.addIfAbsent(aListener);
    }

    public void removeBitmapChangedListener(BitmapChangedListener aListener) {
        mBitmapChangedListeners.remove(aListener);
    }

    public void addWebXRStateChangedListener(WebXRStateChangedListener aListener) {
        mWebXRStateListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeWebXRStateChangedListener(WebXRStateChangedListener aListener) {
        mWebXRStateListeners.remove(aListener);
    }

    public void addPopUpStateChangedListener(PopUpStateChangedListener aListener) {
        mPopUpStateStateListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removePopUpStateChangedListener(PopUpStateChangedListener aListener) {
        mPopUpStateStateListeners.remove(aListener);
    }

    public void addDrmStateChangedListener(DrmStateChangedListener aListener) {
        mDrmStateStateListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeDrmStateChangedListener(DrmStateChangedListener aListener) {
        mDrmStateStateListeners.remove(aListener);
    }

    private void setupSessionListeners(WSession aSession) {
        aSession.setNavigationDelegate(this);
        aSession.setProgressDelegate(this);
        aSession.setContentDelegate(this);
        aSession.getTextInput().setDelegate(this);
        aSession.setPermissionDelegate(this);
        aSession.setPromptDelegate(this);
        aSession.setContentBlockingDelegate(this);
        aSession.setMediaSessionDelegate(mMedia);
        aSession.setHistoryDelegate(this);
        aSession.setSelectionActionDelegate(this);
        aSession.setContentBlockingDelegate(this);
    }

    private void cleanSessionListeners(WSession aSession) {
        aSession.setContentDelegate(null);
        aSession.setNavigationDelegate(null);
        aSession.setProgressDelegate(null);
        aSession.getTextInput().setDelegate(null);
        aSession.setPromptDelegate(null);
        aSession.setPermissionDelegate(null);
        aSession.setContentBlockingDelegate(null);
        aSession.setMediaSessionDelegate(null);
        aSession.setHistoryDelegate(null);
        aSession.setSelectionActionDelegate(null);
        aSession.setContentBlockingDelegate(null);
    }

    public void updateTrackingProtection() {
        if ((mState != null) && (mState.mSettings != null)) {
            TrackingProtectionPolicy policy = TrackingProtectionStore.getTrackingProtectionPolicy(mContext);
            mState.mSettings.setTrackingProtectionEnabled(mState.mSettings.isPrivateBrowsingEnabled() || policy.shouldBlockContent());
            if (mState.mSession != null) {
                mState.mSession.getSettings().setUseTrackingProtection(mState.mSettings.isTrackingProtectionEnabled());
            }
        }
    }

    public void suspend() {
        if (mState.isActive()) {
            Log.e(LOGTAG, "Active Sessions can not be suspended");
            return;
        }
        if (mState.mSession == null) {
            return;
        }
        if (mKeepAlive > System.currentTimeMillis()) {
            Log.e(LOGTAG, "Unable to suspend activity with active keep alive time.");
            return;
        }

        Log.d(LOGTAG, "Suspending Session: " + mState.mId);
        closeSession(mState);
        mState.mSession = null;

        mSessionChangeListeners.forEach(listener -> listener.onSessionRemoved(mState.mId));
    }

    private boolean shouldLoadDefaultPage(@NonNull SessionState aState) {
        // data:text URLs can not be restored.
        if (mState.mSessionState != null && ((mState.mUri == null) || mState.mUri.startsWith("data:text"))) {
            return true;
        }

        if (aState.mUri != null && aState.mUri.length() != 0 && !aState.mUri.equals(mContext.getString(R.string.about_blank))) {
            return false;
        }
        if (aState.mSessionState != null && !aState.mSessionState.isEmpty()) {
            return false;
        }
        return true;
    }

    private void loadDefaultPage() {
        if (mState.mSettings.isPrivateBrowsingEnabled()) {
            loadPrivateBrowsingPage();
        } else {
            loadHomePage();
        }
    }

    private void restore() {
        SessionSettings settings = mState.mSettings;
        if (settings == null) {
            settings = new SessionSettings.Builder()
                    .withDefaultSettings(mContext)
                    .build();
        } else {
            updateTrackingProtection();
        }

        mState.mSession = createWSession(settings);

        mSessionChangeListeners.forEach(listener -> listener.onSessionAdded(this));

        openSession();

        if (shouldLoadDefaultPage(mState)) {
            loadDefaultPage();
        } else if (mState.mSessionState != null) {
            mState.mSession.restoreState(mState.mSessionState);
            if (mState.mUri != null && mState.mUri.contains(".youtube.com")) {
                mState.mSession.loadUri(mState.mUri, WSession.LOAD_FLAGS_REPLACE_HISTORY);
            }
        } else if (mState.mUri != null) {
            mState.mSession.loadUri(mState.mUri);
        } else {
            loadDefaultPage();
        }

        dumpAllState();

        mState.setActive(true);

        if (!mState.mIsWebExtensionSession) {
            mRuntime.getWebExtensionController().setTabActive(mState.mSession, true);
        }
    }


    private SessionState createSessionState(@NonNull SessionSettings aSettings) {
        SessionState state = new SessionState();
        state.mSettings = aSettings;
        state.mSession = createWSession(aSettings);

        return state;
    }

    private WSession createWSession(@NonNull SessionSettings aSettings) {
        WSessionSettings settings = WSessionSettings.create(aSettings.isPrivateBrowsingEnabled());
        settings.setUseTrackingProtection(aSettings.isTrackingProtectionEnabled());
        settings.setUserAgentMode(aSettings.getUserAgentMode());
        settings.setViewportMode(aSettings.getViewportMode());
        settings.setSuspendMediaWhenInactive(aSettings.isSuspendMediaWhenInactiveEnabled());
        settings.setUserAgentOverride(aSettings.getUserAgentOverride());

        WSession session = WFactory.createSession(settings);
        setupSessionListeners(session);

        return session;
    }

    void recreateSession() {
        boolean wasFullScreen = mState.mFullScreen;

        WSession previousWSession = null;
        if (mState.mSession != null) {
            previousWSession = mState.mSession;
            closeSession(mState);
        }

        mState = mState.recreate();

        mSessionChangeListeners.forEach(listener -> listener.onSessionRemoved(mState.mId));

        restore();

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onSessionStateChanged(this, true);
        }

        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onCurrentSessionChange(previousWSession, mState.mSession);
        }

        if (wasFullScreen != mState.mFullScreen) {
            for (WSession.ContentDelegate listener : mContentListeners) {
                listener.onFullScreen(mState.mSession, mState.mFullScreen);
            }
        }
    }

    void openSession() {
        if (!mState.mSession.isOpen()) {
            mState.mSession.open(mRuntime);
        }

        mSessionChangeListeners.forEach(listener -> listener.onSessionOpened(this));
    }

    private void closeSession(@NonNull SessionState aState) {
        if (aState.mSession == null || !aState.mSession.isOpen()) {
            return;
        }
        cleanSessionListeners(aState.mSession);
        aState.mSession.setActive(false);
        aState.mSession.stop();
        if (aState.mDisplay != null) {
            aState.mDisplay.surfaceDestroyed();
            aState.mSession.releaseDisplay(aState.mDisplay);
            aState.mDisplay = null;
        }
        aState.mSession.close();
        aState.setActive(false);
        mFirstContentfulPaint = false;

        mSessionChangeListeners.forEach(listener -> listener.onSessionClosed(this));
    }

    public void captureBitmap() {
        if (mState.mDisplay == null || !mFirstContentfulPaint) {
            return;
        }
        try {
            mState.mDisplay.capturePixelsWithAspectPreservingSize(500).then(bitmap -> {
                if (bitmap != null) {
                    BitmapCache.getInstance(mContext).addBitmap(getId(), bitmap);
                    for (BitmapChangedListener listener: mBitmapChangedListeners) {
                        listener.onBitmapChanged(Session.this, bitmap);
                    }
                }
                return null;
            }).exceptionally(throwable -> {
                Log.e(LOGTAG, "Error capturing session bitmap");
                throwable.printStackTrace();
                return null;
            });
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error capturing session bitmap");
            ex.printStackTrace();
        }

    }

    public CompletableFuture<Void> captureBackgroundBitmap(int displayWidth, int displayHeight) {
        // FIXME: calling acquireDisplay() is not well supported in the Chromium backend because
        // that method incorrectly does some extra work handling widgets. Disable the bitmap
        // capture in the meantime (it was not working anyway yet).
        if (mState.mSession == null || !mFirstContentfulPaint || BuildConfig.FLAVOR_backend == "chromium") {
            return CompletableFuture.completedFuture(null);
        }
        Surface captureSurface = BitmapCache.getInstance(mContext).acquireCaptureSurface(displayWidth, displayHeight);
        if (captureSurface == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        WDisplay display = mState.mSession.acquireDisplay();
        display.surfaceChanged(captureSurface, displayWidth, displayHeight);

        Runnable cleanResources = () -> {
            display.surfaceDestroyed();
            if (mState.mSession != null) {
                mState.mSession.releaseDisplay(display);
            }
            BitmapCache.getInstance(mContext).releaseCaptureSurface();
        };

        try {
            display.capturePixelsWithAspectPreservingSize(500).then(bitmap -> {
                if (bitmap != null) {
                    BitmapCache.getInstance(mContext).addBitmap(getId(), bitmap);
                    for (BitmapChangedListener listener : mBitmapChangedListeners) {
                        listener.onBitmapChanged(Session.this, bitmap);
                    }
                }
                cleanResources.run();
                result.complete(null);
                return null;
            }).exceptionally(throwable -> {
                Log.e(LOGTAG, "Error capturing session background bitmap");
                throwable.printStackTrace();
                cleanResources.run();
                result.complete(null);
                return null;
            });
        }
        catch (Exception ex) {
            Log.e(LOGTAG, "Error capturing session background bitmap");
            ex.printStackTrace();
            cleanResources.run();
            result.complete(null);
        }
        return result;
    }

    public boolean hasCapturedBitmap() {
        return BitmapCache.getInstance(mContext).hasBitmap(mState.mId);
    }

    public boolean hasDisplay() {
        return mState != null && mState.mDisplay != null;
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
        if (homepage.equals(mContext.getString(R.string.HOMEPAGE_URL)) && mState.mRegion != null) {
            homepage = homepage + "?region=" + mState.mRegion;
        }
        return homepage;
    }

    public Boolean isHomeUri(String aUri) {
        return UrlUtils.isHomeUri(mContext, aUri);
    }

    public String getCurrentUri() {
        if (mState.mUri == null) {
            return "";
        }
        return mState.mUri;
    }

    public String getCurrentTitle() {
        if (mState.mTitle == null) {
            return "";
        }
        return mState.mTitle;
    }

    public boolean isSecure() {
        return mState.mSecurityInformation != null && mState.mSecurityInformation.isSecure;
    }

    public boolean isFirstContentfulPaint() {
        return mFirstContentfulPaint;
    }

    public boolean isWebExtensionSession() {
        return mState.mIsWebExtensionSession;
    }

    @Nullable
    public Media getFullScreenVideo() {
        if (mMedia.isActive() && mMedia.isFullscreen()) {
            return mMedia;
        }

        return null;
    }

    @Nullable
    public Media getActiveVideo() {
        return mMedia.isActive() ? mMedia : null;
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
            return  parent != null && parent.mState.mDisplay == null;
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
          if (parent != null && parent.mState.mDisplay == null) {
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
        if (!aActive && mState.mSession != null && !mState.isActive()) {
            // Prevent duplicated setActive(false) calls. There is a GV
            // bug that makes the session not to be resumed correctly.
            // See https://github.com/MozillaReality/FirefoxReality/issues/3375.
            return;
        }
        // Flush the events queued while the session was inactive
        if (mState.mSession != null && !mState.isActive() && aActive) {
            flushQueuedEvents();
        }

        if (mState.mSession != null) {
            mState.mSession.setActive(aActive);
            mState.setActive(aActive);
            if (!mState.mIsWebExtensionSession) {
                mRuntime.getWebExtensionController().setTabActive(mState.mSession, aActive);
            }

        } else if (aActive) {
            restore();

        } else {
            Log.e(LOGTAG, "ERROR: Setting null session to inactive!");
        }

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onSessionStateChanged(this, aActive);
        }
    }

    public void reload() {
        reload(WSession.LOAD_FLAGS_NONE);
    }

    public void reload(final int flags) {
        if (mState.mSession != null) {
            mState.mSession.reload(flags);
        }
    }

    public void stop() {
        if (mState.mSession != null) {
            mState.mSession.stop();
        }
    }

    public void loadUri(String aUri) {
        loadUri(aUri, WSession.LOAD_FLAGS_NONE);
    }

    public void loadUri(String aUri, int flags) {
        if (aUri == null) {
            aUri = getHomeUri();
        }
        if (mState.mSession != null) {
            Log.d(LOGTAG, "Loading URI: " + aUri);
            if (mExternalRequestDelegate == null || !mExternalRequestDelegate.onHandleExternalRequest(aUri)) {
                mState.mSession.loadUri(aUri, flags);
            }
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

    public boolean isInFullScreen() {
        return mState.mFullScreen;
    }

    public void exitFullScreen() {
        if (mState.mSession != null) {
            mState.mSession.exitFullScreen();
        }
    }

    public WSession getWSession() {
        return mState.mSession;
    }

    public String getId() {
        return mState.mId;
    }

    public boolean isPrivateMode() {
        if (mState.mSession != null) {
            return mState.mSession.getSettings().getUsePrivateMode();
        } else if (mState.mSettings != null) {
            return mState.mSettings.isPrivateBrowsingEnabled();
        }
        return false;
    }

    public void setWebXRState(@SessionState.WebXRState int aWebXRState) {
        if (aWebXRState != mState.mWebXRState) {
            mState.mWebXRState = aWebXRState;
            for (WebXRStateChangedListener listener: mWebXRStateListeners) {
                dumpState(listener);
            }
        }
    }

    public @SessionState.WebXRState int getWebXRState() {
        return mState.mWebXRState;
    }

    public void setPopUpState(@SessionState.PopupState int aPopUpstate) {
        mState.mPopUpState = aPopUpstate;
        for (PopUpStateChangedListener listener: mPopUpStateStateListeners) {
            dumpState(listener);
        }
    }

    public @SessionState.PopupState int getPopUpState() {
        return mState.mPopUpState;
    }

    public void setDrmState(@SessionState.DrmState int aDrmState) {
        mState.mDrmState = aDrmState;
        for (DrmStateChangedListener listener: mDrmStateStateListeners) {
            dumpState(listener);
        }
    }

    public @SessionState.DrmState int getDrmState() {
        return mState.mDrmState;
    }

    public WebApp getWebAppManifest() {
        return mState.mWebAppManifest;
    }

    // Session Settings

    public int getUaMode() {
        return mState.mSession.getSettings().getUserAgentMode();
    }

    public boolean isActive() {
        return mState.isActive();
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

    private boolean trySetUaMode(int mode) {
        if (mState.mSession == null || mState.mSettings.getUserAgentMode() == mode) {
            return false;
        }
        mState.mSettings.setUserAgentMode(mode);
        mState.mSession.getSettings().setUserAgentMode(mode);
        mState.mSession.getSettings().setViewportMode(mState.mSettings.getViewportMode());
        return true;
    }

    public void setUaMode(int mode, boolean reload) {
        // the UA mode value did not change
        if (!trySetUaMode(mode))
            return;

        // the value did change, but we don't need to force a reload
        if (!reload)
            return;

        String overrideUri = mode == WSessionSettings.USER_AGENT_MODE_DESKTOP ? checkForMobileSite(mState.mUri) : null;
        if (overrideUri != null) {
            mState.mSession.loadUri(overrideUri, WSession.LOAD_FLAGS_BYPASS_CACHE | WSession.LOAD_FLAGS_REPLACE_HISTORY);
        } else {
            mState.mSession.reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
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

    public void setParentSession(@NonNull Session parentSession) {
        mState.mParentId = parentSession.getId();
    }

    public void pageZoomIn() {
        if (mState.mSession != null) {
            mState.mSession.pageZoomIn();
        }
    }

    public void pageZoomOut() {
        if (mState.mSession != null) {
            mState.mSession.pageZoomOut();
        }
    }

    public int getCurrentZoomLevel() {
        if (mState.mSession != null) {
            return mState.mSession.getCurrentZoomLevel();
        }
        return 0;
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull WSession aSession, String aUri) {
        if (mState.mSession != aSession) {
            return;
        }

        setPopUpState(SessionState.POPUP_UNUSED);
        setDrmState(SessionState.DRM_UNUSED);

        mState.mIsWebExtensionSession = aUri.startsWith(UrlUtils.WEB_EXTENSION_URL);

        mState.mPreviousUri = mState.mUri;
        mState.mUri = aUri;

        boolean forceMobileViewport = FORCE_MOBILE_VIEWPORT.stream().anyMatch(aUri::contains);
        if (forceMobileViewport) {
            mState.mSession.getSettings().setViewportMode(WSessionSettings.VIEWPORT_MODE_MOBILE);
        } else {
            mState.mSession.getSettings().setViewportMode(mState.mSettings.getViewportMode());
        }

        for (WSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onLocationChange(aSession, aUri);
        }

        // TODO Check that this is the correct place to clear the stored manifest. Update the UI if needed.
        if (mState.mWebAppManifest != null) {
            Log.d(LOGTAG, "onLocationChange: clear stored Web app manifest");
            mState.mWebAppManifest = null;
        }

        // The homepage finishes loading after the region has been updated
        if (mState.mRegion != null && aUri.equalsIgnoreCase(SettingsStore.getInstance(mContext).getHomepage())) {
            aSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        } else if ((getUaMode() != WSessionSettings.USER_AGENT_MODE_DESKTOP) && !Objects.equals(mState.mPreviousUri, mState.mUri)) {
            // The URL check above allows users to switch to mobile mode even for overriding sites.
            if (sDesktopModeOverrides.lookupOverride(aUri) != null) {
                trySetUaMode(WSessionSettings.USER_AGENT_MODE_DESKTOP);
                String overrideUri = checkForMobileSite(aUri);
                if (overrideUri == null)
                    overrideUri = aUri;
                aSession.loadUri(overrideUri);
            }
        }
    }

    @Override
    public void onCanGoBack(@NonNull WSession aSession, boolean aISessionCanGoBack) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onCanGoBack: " + (aISessionCanGoBack ? "true" : "false"));
        mState.mCanGoBack = aISessionCanGoBack;

        for (WSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onCanGoBack(aSession, canGoBack());
        }
    }

    @Override
    public void onCanGoForward(@NonNull WSession aSession, boolean aCanGoForward) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onCanGoForward: " + (aCanGoForward ? "true" : "false"));
        mState.mCanGoForward = aCanGoForward;

        for (WSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onCanGoForward(aSession, aCanGoForward);
        }
    }

    @Override
    public @Nullable
    WResult<WAllowOrDeny> onLoadRequest(@NonNull WSession aSession, @NonNull LoadRequest aRequest) {
        String uri = aRequest.uri;

        Log.d(LOGTAG, "onLoadRequest: " + uri);

        if (aSession == mState.mSession) {
            Log.d(LOGTAG, "Testing for UA override");

            String userAgentOverride = sUserAgentOverride.lookupOverride(uri);

            // Set the User-Agent according to the current UA settings
            // unless we are in Desktop mode, which uses its own User-Agent value.
            int mode = mState.mSettings.getUserAgentMode();
            if (userAgentOverride == null) {
                userAgentOverride = mState.mSession.getDefaultUserAgent(mode);
            }

            aSession.getSettings().setUserAgentOverride(userAgentOverride);
            if (mState.mSettings != null) {
                mState.mSettings.setUserAgentOverride(userAgentOverride);
            }
        }

        if (mContext.getString(R.string.about_private_browsing).equalsIgnoreCase(uri)) {
            return WResult.deny();
        }

        if (mNavigationListeners.size() == 0) {
            return WResult.allow();
        }

        // If this request is externally handled we just deny
        if (mExternalRequestDelegate != null) {
            if (mExternalRequestDelegate.onHandleExternalRequest(uri)) {
                return WResult.deny();
            }
        }

        final WResult<WAllowOrDeny> result = WResult.create();
        AtomicInteger count = new AtomicInteger(0);
        AtomicBoolean allowed = new AtomicBoolean(true);
        final int listenerCount = mNavigationListeners.size() - 1;
        for (WSession.NavigationDelegate listener: mNavigationListeners) {
            WResult<WAllowOrDeny> listenerResult = listener.onLoadRequest(aSession, aRequest);
            if (listenerResult != null) {
                listenerResult.then(value -> {
                    if (WAllowOrDeny.DENY.equals(value)) {
                        allowed.set(false);
                    }
                    if (count.getAndIncrement() == listenerCount) {
                        result.complete(allowed.get() ? WAllowOrDeny.ALLOW : WAllowOrDeny.DENY);
                    }

                    return null;
                });

            } else {
                allowed.set(true);
                if (count.getAndIncrement() == listenerCount) {
                    result.complete(allowed.get() ? WAllowOrDeny.ALLOW : WAllowOrDeny.DENY);
                }
            }
        }

        if (UrlUtils.isAboutPage(aRequest.uri)) {
            return WResult.deny();
        }

        return result;
    }

    @Override
    public WResult<WSession> onNewSession(@NonNull WSession aSession, @NonNull String aUri, OnNewSessionCallback callback) {
        mKeepAlive = System.currentTimeMillis() + KEEP_ALIVE_DURATION_MS;
        Log.d(LOGTAG, "onNewSession: " + aUri);

        Session session = SessionStore.get().createSession(mState.mSettings, SESSION_DO_NOT_OPEN);
        session.mState.mParentId = mState.mId;
        session.mKeepAlive = mKeepAlive;

        if (callback != null) {
            callback.onNewSession((WSession) session.mState.mSession);
        }

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onStackSession(session);
        }
        mSessionChangeListeners.add(session);
        return WResult.fromValue(session.getWSession());
    }

    @Override
    public WResult<String> onLoadError(@NonNull WSession session, @Nullable String uri, @NonNull WWebRequestError error) {
        Log.d(LOGTAG, "Session onLoadError: " + uri);

        return WResult.fromValue(InternalPages.createErrorPageDataURI(mContext, uri, error.code()));
    }

    // Progress Listener

    @Override
    public void onPageStart(@NonNull WSession aSession, @NonNull String aUri) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStart");
        mState.mIsLoading = true;
        TelemetryService.startPageLoadTime(aUri);

        setWebXRState(SessionState.WEBXR_UNUSED);
        for (WSession.ProgressDelegate listener : mProgressListeners) {
            listener.onPageStart(aSession, aUri);
        }
    }

    @Override
    public void onPageStop(@NonNull WSession aSession, boolean b) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStop");
        mState.mIsLoading = false;
        if (!SessionUtils.isLocalizedContent(mState.mUri)) {
            TelemetryService.stopPageLoadTimeWithURI(mState.mUri);
        }

        for (WSession.ProgressDelegate listener : mProgressListeners) {
            listener.onPageStop(aSession, b);
        }
    }

    @Override
    public void onProgressChange(@NonNull WSession aSession, int progress) {
        if (mState.mSession != aSession) {
            return;
        }
        for (WSession.ProgressDelegate listener : mProgressListeners) {
            listener.onProgressChange(aSession, progress);
        }
    }

    @Override
    public void onSecurityChange(@NonNull WSession aSession, @NonNull SecurityInformation aInformation) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStop");
        mState.mSecurityInformation = aInformation;

        for (WSession.ProgressDelegate listener : mProgressListeners) {
            listener.onSecurityChange(aSession, aInformation);
        }
    }

    @Override
    public void onSessionStateChange(@NonNull WSession aSession,
                                     @NonNull WSessionState aSessionState) {
        if (mState.mSession == aSession) {
            mState.mSessionState = aSessionState;
        }
    }

    // Content Delegate

    @Override
    public void onTitleChange(@NonNull WSession aSession, String aTitle) {
        if (mState.mSession != aSession) {
            return;
        }

        mState.mTitle = aTitle;

        for (WSession.ContentDelegate listener : mContentListeners) {
            listener.onTitleChange(aSession, aTitle);
        }
    }

    @Override
    public void onCloseRequest(@NonNull WSession aSession) {
        for (WSession.ContentDelegate listener : mContentListeners) {
            listener.onCloseRequest(aSession);
        }
    }

    @Override
    public void onFullScreen(@NonNull WSession aSession, boolean aFullScreen) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onFullScreen");
        mState.mFullScreen = aFullScreen;

        for (WSession.ContentDelegate listener : mContentListeners) {
            listener.onFullScreen(aSession, aFullScreen);
        }
    }

    @Override
    public void onContextMenu(@NonNull WSession session, int screenX, int screenY, @NonNull ContextElement element) {
        if (mState.mSession == session) {
            for (WSession.ContentDelegate listener : mContentListeners) {
                listener.onContextMenu(session, screenX, screenY, element);
            }
        }
    }

    @Override
    public void onCrash(@NonNull WSession session) {
        Log.e(LOGTAG,"Child crashed. Recreating session");
        recreateSession();
    }

    @Override
    public void onKill(@NonNull WSession session) {
        Log.e(LOGTAG,"Child killed. Recreating session");
        recreateSession();
    }

    @Override
    public void onFirstComposite(@NonNull WSession aSession) {
        if (mState.mSession == aSession) {
            for (WSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstComposite(aSession);
            }
            if (mFirstContentfulPaint) {
                // onFirstContentfulPaint is only called once after a session is opened.
                // Notify onFirstContentfulPaint after a session is reattached before
                // being closed ((e.g. tab selected)
                for (WSession.ContentDelegate listener : mContentListeners) {
                    listener.onFirstContentfulPaint(aSession);
                }
            }
        }
    }

    @Override
    public void onFirstContentfulPaint(@NonNull WSession aSession) {
        mFirstContentfulPaint = true;
        if (mState.mSession == aSession) {
            for (WSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstContentfulPaint(aSession);
            }
        }
    }

    @Override
    public void onWebAppManifest(@NonNull WSession aSession, @NonNull WebApp webAppManifest) {
        if (mState.mSession == aSession) {
            mState.mWebAppManifest = webAppManifest;
            Log.d(LOGTAG, "onWebAppManifest: received Web app manifest from " + mState.mUri);
            for (WSession.ContentDelegate listener : mContentListeners) {
                listener.onWebAppManifest(aSession, webAppManifest);
            }
        }
    }

    @Nullable
    @Override
    public WResult<WSlowScriptResponse> onSlowScript(@NonNull WSession aSession, @NonNull String aScriptFileName) {
        if (mState.mSession == aSession) {
            for (WSession.ContentDelegate listener : mContentListeners) {
                WResult<WSlowScriptResponse> result = listener.onSlowScript(aSession, aScriptFileName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public void onExternalResponse(@NonNull WSession aSession, @NonNull WWebResponse webResponseInfo) {
        for (WSession.ContentDelegate listener : mContentListeners) {
            listener.onExternalResponse(aSession, webResponseInfo);
        }
    }

    // TextInput Delegate

    @Override
    public void restartInput(@NonNull WSession aSession, int reason) {
        if (mState.mSession == aSession) {
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.restartInput(aSession, reason);
            }
        }
    }

    @Override
    public void showSoftInput(@NonNull WSession aSession, @Nullable View requestView) {
        if (mState.mSession == aSession) {
            mState.mIsInputActive = true;
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.showSoftInput(aSession, requestView);
            }
        }
    }

    @Override
    public void hideSoftInput(@NonNull WSession aSession) {
        if (mState.mSession == aSession) {
            mState.mIsInputActive = false;
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.hideSoftInput(aSession);
            }
        }
    }

    @Override
    public void updateSelection(@NonNull WSession aSession, int selStart, int selEnd, int compositionStart, int compositionEnd) {
        if (mState.mSession == aSession) {
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateSelection(aSession, selStart, selEnd, compositionStart, compositionEnd);
            }
        }
    }

    @Override
    public void updateExtractedText(@NonNull WSession aSession, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {
        if (mState.mSession == aSession) {
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateExtractedText(aSession, request, text);
            }
        }
    }

    @Override
    public void updateCursorAnchorInfo(@NonNull WSession aSession, @NonNull CursorAnchorInfo info) {
        if (mState.mSession == aSession) {
            for (WSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateCursorAnchorInfo(aSession, info);
            }
        }
    }

    @Override
    public void onContentBlocked(@NonNull final WSession session, @NonNull final WContentBlocking.BlockEvent event) {
        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.AD) != 0) {
            Log.d(LOGTAG, "Blocking Ad: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.ANALYTIC) != 0) {
            Log.d(LOGTAG, "Blocking Analytic: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.CONTENT) != 0) {
            Log.d(LOGTAG, "Blocking Content: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.SOCIAL) != 0) {
            Log.d(LOGTAG, "Blocking Social: " + event.uri);
        }
    }

    @Override
    public void onContentLoaded(@NonNull WSession session, @NonNull WContentBlocking.BlockEvent event) {
        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.AD) != 0) {
            Log.d(LOGTAG, "Loading Ad: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.ANALYTIC) != 0) {
            Log.d(LOGTAG, "Loading Analytic: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.CONTENT) != 0) {
            Log.d(LOGTAG, "Loading Content: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & WContentBlocking.AntiTracking.SOCIAL) != 0) {
            Log.d(LOGTAG, "Loading Social: " + event.uri);
        }
    }

    // PromptDelegate

    @Nullable
    @Override
    public WResult<PromptResponse> onPopupPrompt(@NonNull WSession aSession, @NonNull PopupPrompt popupPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onPopupPrompt(aSession, popupPrompt);
        }
        return WResult.fromValue(popupPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onAlertPrompt(@NonNull WSession aSession, @NonNull AlertPrompt alertPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onAlertPrompt(aSession, alertPrompt);
        }
        return WResult.fromValue(alertPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onButtonPrompt(@NonNull WSession aSession, @NonNull ButtonPrompt buttonPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onButtonPrompt(aSession, buttonPrompt);
        }
        return WResult.fromValue(buttonPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onTextPrompt(@NonNull WSession aSession, @NonNull TextPrompt textPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onTextPrompt(aSession, textPrompt);
        }
        return WResult.fromValue(textPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onAuthPrompt(@NonNull WSession aSession, @NonNull AuthPrompt authPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onAuthPrompt(aSession, authPrompt);
        }
        return WResult.fromValue(authPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onChoicePrompt(@NonNull WSession aSession, @NonNull ChoicePrompt choicePrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onChoicePrompt(aSession, choicePrompt);
        }
        return WResult.fromValue(choicePrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onColorPrompt(@NonNull WSession aSession, @NonNull ColorPrompt colorPrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onColorPrompt(aSession, colorPrompt);
        }
        return WResult.fromValue(colorPrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onDateTimePrompt(@NonNull WSession aSession, @NonNull DateTimePrompt dateTimePrompt) {
        if (mState.mSession == aSession && mPromptDelegate != null) {
            return mPromptDelegate.onDateTimePrompt(aSession, dateTimePrompt);
        }
        return WResult.fromValue(dateTimePrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onFilePrompt(@NonNull WSession aSession, @NonNull FilePrompt filePrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onFilePrompt(aSession, filePrompt);
        }
        return WResult.fromValue(filePrompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onBeforeUnloadPrompt(@NonNull WSession aSession, @NonNull BeforeUnloadPrompt prompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onBeforeUnloadPrompt(aSession, prompt);
        }
        return WResult.fromValue(prompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onLoginSelect(@NonNull WSession aSession, @NonNull AutocompleteRequest<WAutocomplete.LoginSelectOption> autocompleteRequest) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onLoginSelect(aSession, autocompleteRequest);
        }
        return WResult.fromValue(autocompleteRequest.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onLoginSave(@NonNull WSession aSession, @NonNull AutocompleteRequest<WAutocomplete.LoginSaveOption> autocompleteRequest) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onLoginSave(aSession, autocompleteRequest);
        }
        return WResult.fromValue(autocompleteRequest.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onSharePrompt(@NonNull WSession aSession, @NonNull SharePrompt prompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onSharePrompt(aSession, prompt);
        }
        return WResult.fromValue(prompt.dismiss());
    }

    @Nullable
    @Override
    public WResult<PromptResponse> onRepostConfirmPrompt(@NonNull WSession aSession, @NonNull RepostConfirmPrompt prompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onRepostConfirmPrompt(aSession, prompt);
        }
        return WResult.fromValue(prompt.dismiss());
    }

    // HistoryDelegate
    @Override
    public void onHistoryStateChange(@NonNull WSession aSession, @NonNull WSession.HistoryDelegate.HistoryList historyList) {
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
    public WResult<Boolean> onVisited(@NonNull WSession aSession, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        if (mState.mSession == aSession) {
            if (mHistoryDelegate != null) {
                return mHistoryDelegate.onVisited(aSession, url, lastVisitedURL, flags);

            } else {
                final WResult<Boolean> response = WResult.create();
                mQueuedCalls.add(() -> {
                    if (mHistoryDelegate != null) {
                        try {
                            requireNonNull(mHistoryDelegate.onVisited(aSession, url, lastVisitedURL, flags)).then(aBoolean -> {
                                response.complete(aBoolean);
                                return null;

                            }).exceptionally(throwable -> {
                                Log.d(LOGTAG, "Null IResult from onVisited");
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

        return WResult.fromValue(false);
    }

    @UiThread
    @Nullable
    public WResult<boolean[]> getVisited(@NonNull WSession aSession, @NonNull String[] urls) {
        if (mState.mSession == aSession) {
            if (mHistoryDelegate != null) {
                return mHistoryDelegate.getVisited(aSession, urls);

            } else {
                final WResult<boolean[]> response = WResult.create();
                mQueuedCalls.add(() -> {
                    if (mHistoryDelegate != null) {
                        try {
                            requireNonNull(mHistoryDelegate.getVisited(aSession, urls)).then(aBoolean -> {
                                response.complete(aBoolean);
                                return null;

                            }).exceptionally(throwable -> {
                                Log.d(LOGTAG, "Null IResult from getVisited");
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

        return WResult.fromValue(new boolean[]{});
    }

    // PermissionDelegate
    @Override
    public void onAndroidPermissionsRequest(@NonNull WSession aSession, @Nullable String[] strings, @NonNull Callback callback) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            mPermissionDelegate.onAndroidPermissionsRequest(aSession, strings, callback);
        }
    }

    @Override
    public WResult<Integer> onContentPermissionRequest(@NonNull WSession aSession, ContentPermission perm) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            return mPermissionDelegate.onContentPermissionRequest(aSession, perm);
        }
        return WResult.fromValue(0);
    }

    @Override
    public void onMediaPermissionRequest(@NonNull WSession aSession, @NonNull String s, @Nullable MediaSource[] mediaSources, @Nullable MediaSource[] mediaSources1, @NonNull MediaCallback mediaCallback) {
        if (mState.mSession == aSession && mPermissionDelegate != null) {
            mPermissionDelegate.onMediaPermissionRequest(aSession, s, mediaSources, mediaSources1, mediaCallback);
        }
    }


    // SharedPreferences.OnSharedPreferenceChangeListener

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mContext == null)
            return;

        if (key.equals(mContext.getString(R.string.settings_key_geolocation_data))) {
            GeolocationData data = GeolocationData.parse(sharedPreferences.getString(key, null));
            if (data != null) {
                setRegion(data.getCountryCode());
            }
        } else if (key.equals(mContext.getString(R.string.settings_key_user_agent_version))) {
            if (mState.mSettings.getUserAgentMode() != WSessionSettings.USER_AGENT_MODE_DESKTOP) {
                setUaMode(SettingsStore.getInstance(mContext).getUaMode(), false);
            }
        }
    }

    // ISession.SelectionActionDelegate

    @Override
    public void onShowActionRequest(@NonNull WSession aSession, @NonNull Selection selection) {
        if (mState.mSession == aSession) {
            for (WSession.SelectionActionDelegate listener : mSelectionActionListeners) {
                listener.onShowActionRequest(aSession, selection);
            }
        }
    }

    @Override
    public void onHideAction(@NonNull WSession aSession, int aHideReason) {
        if (mState.mSession == aSession) {
            for (WSession.SelectionActionDelegate listener : mSelectionActionListeners) {
                listener.onHideAction(aSession, aHideReason);
            }
        }
    }

    // SessionChangeListener

    @Override
    public void onSessionRemoved(String aId) {
        if (mState.mParentId != null) {
            mState.mParentId = null;
            // Parent stack session closed. Notify canGoBack state changed
            for (WSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getWSession(), canGoBack());
            }
        }
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        if (mState.mParentId != null) {
            // Parent stack session has been attached/detached. Notify canGoBack state changed
            for (WSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getWSession(), canGoBack());
            }
        }
    }

    // Display functions
    public void releaseDisplay() {
        surfaceDestroyed();
        if (mState.mDisplay != null) {
            if (mState.mSession != null) {
                mState.mSession.releaseDisplay(mState.mDisplay);
            }
            mState.mDisplay = null;
        }
    }

    public void surfaceDestroyed() {
        if (mState.mDisplay != null) {
            mState.mDisplay.surfaceDestroyed();
        }
    }

    public void surfaceChanged(@NonNull final Surface surface, final int left, final int top,
                               final int width, final int height) {
        if (mState.mSession == null) {
            return;
        }
        if (mState.mDisplay == null) {
            mState.mDisplay = mState.mSession.acquireDisplay();
        }
        mState.mDisplay.surfaceChanged(surface, left, top, width, height);
    }

    public void logState() {
        if (mState == null) {
            Log.d(LOGTAG, "Session state is null");
            return;
        }
        Log.d(LOGTAG, "Session: " + (mState.mSession != null ? mState.mSession.hashCode() : "null"));
        Log.d(LOGTAG, "\tActive: " + mState.isActive());
        Log.d(LOGTAG, "\tUri: " + (mState.mUri != null ? mState.mUri : "null"));
        Log.d(LOGTAG, "\tFullscreen: " + mState.mFullScreen);
        Log.d(LOGTAG, "\tKiosk mode: " + mState.mInKioskMode);
        Log.d(LOGTAG, "\tCan go back: " + mState.mCanGoBack);
        Log.d(LOGTAG, "\tCan go forward: " + mState.mCanGoForward);
        if (mState.mSettings != null) {
            Log.d(LOGTAG, "\tPrivate Browsing: " + mState.mSettings.isPrivateBrowsingEnabled());
        }
    }
}
