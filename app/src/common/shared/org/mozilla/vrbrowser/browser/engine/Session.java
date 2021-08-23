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
import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.MediaElement;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.UserAgentOverride;
import org.mozilla.vrbrowser.browser.VideoAvailabilityListener;
import org.mozilla.vrbrowser.browser.content.TrackingProtectionPolicy;
import org.mozilla.vrbrowser.browser.content.TrackingProtectionStore;
import org.mozilla.vrbrowser.geolocation.GeolocationData;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.InternalPages;
import org.mozilla.vrbrowser.utils.SystemUtils;
import org.mozilla.vrbrowser.utils.UrlUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static UserAgentOverride sUserAgentOverride;
    private static final long KEEP_ALIVE_DURATION_MS = 1000; // 1 second.

    private transient CopyOnWriteArrayList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private transient CopyOnWriteArrayList<GeckoSession.ProgressDelegate> mProgressListeners;
    private transient CopyOnWriteArrayList<GeckoSession.ContentDelegate> mContentListeners;
    private transient CopyOnWriteArrayList<SessionChangeListener> mSessionChangeListeners;
    private transient CopyOnWriteArrayList<GeckoSession.TextInputDelegate> mTextInputListeners;
    private transient CopyOnWriteArrayList<VideoAvailabilityListener> mVideoAvailabilityListeners;
    private transient CopyOnWriteArrayList<BitmapChangedListener> mBitmapChangedListeners;
    private transient CopyOnWriteArrayList<GeckoSession.SelectionActionDelegate> mSelectionActionListeners;
    private transient CopyOnWriteArrayList<WebXRStateChangedListener> mWebXRStateListeners;
    private transient CopyOnWriteArrayList<PopUpStateChangedListener> mPopUpStateStateListeners;
    private transient CopyOnWriteArrayList<DrmStateChangedListener> mDrmStateStateListeners;

    private SessionState mState;
    private transient CopyOnWriteArrayList<Runnable> mQueuedCalls = new CopyOnWriteArrayList<>();
    private transient GeckoSession.PermissionDelegate mPermissionDelegate;
    private transient GeckoSession.PromptDelegate mPromptDelegate;
    private transient GeckoSession.HistoryDelegate mHistoryDelegate;
    private transient ExternalRequestDelegate mExternalRequestDelegate;
    private transient Context mContext;
    private transient SharedPreferences mPrefs;
    private transient GeckoRuntime mRuntime;
    private transient byte[] mPrivatePage;
    private transient boolean mFirstContentfulPaint;
    private transient long mKeepAlive;

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
    static Session createWebExtensionSession(Context aContext, GeckoRuntime aRuntime, @NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode, @NonNull SessionChangeListener listener) {
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
    static Session createSession(Context aContext, GeckoRuntime aRuntime, @NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode, @NonNull SessionChangeListener listener) {
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
    static Session createSuspendedSession(Context aContext, GeckoRuntime aRuntime, @NonNull SessionState aRestoreState, @NonNull SessionChangeListener listener) {
        Session session = new Session(aContext, aRuntime, aRestoreState);
        session.addSessionChangeListener(listener);

        return session;
    }

    private Session(Context aContext, GeckoRuntime aRuntime, @NonNull SessionSettings aSettings) {
        mContext = aContext;
        mRuntime = aRuntime;
        initialize();
        mState = createSessionState(aSettings);
    }

    private Session(Context aContext, GeckoRuntime aRuntime, @NonNull SessionState aRestoreState) {
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

        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        mPrivatePage = InternalPages.createAboutPage(mContext, pageResources);

        if (sUserAgentOverride == null) {
            sUserAgentOverride = new UserAgentOverride();
            sUserAgentOverride.loadOverridesFromAssets((Activity)mContext, mContext.getString(R.string.user_agent_override_file));
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

    private void dumpState(GeckoSession.NavigationDelegate aListener) {
        if (mState.mSession != null) {
            aListener.onCanGoBack(mState.mSession, canGoBack());
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
        mState.mMediaElements.forEach(element -> {
            aListener.onVideoAvailabilityChanged(element,true);
        });
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

    public void setPermissionDelegate(GeckoSession.PermissionDelegate aDelegate) {
        mPermissionDelegate = aDelegate;
    }

    public void setPromptDelegate(GeckoSession.PromptDelegate aDelegate) {
        mPromptDelegate = aDelegate;
    }

    public void setHistoryDelegate(GeckoSession.HistoryDelegate aDelegate) {
        mHistoryDelegate = aDelegate;
    }

    public void setExternalRequestDelegate(ExternalRequestDelegate aDelegate) {
        mExternalRequestDelegate = aDelegate;
    }

    public void addNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.remove(aListener);
    }

    public void addProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.remove(aListener);
    }

    public void addContentListener(GeckoSession.ContentDelegate aListener) {
        mContentListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeContentListener(GeckoSession.ContentDelegate aListener) {
        mContentListeners.remove(aListener);
    }

    public void addSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.addIfAbsent(aListener);
    }

    public void removeSessionChangeListener(SessionChangeListener aListener) {
        mSessionChangeListeners.remove(aListener);
    }

    public void addTextInputListener(GeckoSession.TextInputDelegate aListener) {
        mTextInputListeners.addIfAbsent(aListener);
    }

    public void removeTextInputListener(GeckoSession.TextInputDelegate aListener) {
        mTextInputListeners.remove(aListener);
    }

    public void addVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.addIfAbsent(aListener);
        dumpState(aListener);
    }

    public void removeVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.remove(aListener);
    }

    public void addSelectionActionListener(GeckoSession.SelectionActionDelegate aListener) {
        mSelectionActionListeners.addIfAbsent(aListener);
    }

    public void removeSelectionActionListener(GeckoSession.ContentDelegate aListener) {
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
        aSession.setContentBlockingDelegate(this);
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
        if (aState.mSessionState != null && aState.mSessionState.size() != 0) {
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

        mState.mSession = createGeckoSession(settings);

        mSessionChangeListeners.forEach(listener -> listener.onSessionAdded(this));

        openSession();

        if (shouldLoadDefaultPage(mState)) {
            loadDefaultPage();
        } else if (mState.mSessionState != null) {
            mState.mSession.restoreState(mState.mSessionState);
            if (mState.mUri != null && mState.mUri.contains(".youtube.com")) {
                mState.mSession.loadUri(mState.mUri, GeckoSession.LOAD_FLAGS_REPLACE_HISTORY);
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
        state.mSession = createGeckoSession(aSettings);

        return state;
    }

    private GeckoSession createGeckoSession(@NonNull SessionSettings aSettings) {
        GeckoSessionSettings geckoSettings = new GeckoSessionSettings.Builder()
                .usePrivateMode(aSettings.isPrivateBrowsingEnabled())
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

        if (session != null) {
            session.getSettings().setUserAgentOverride(aSettings.getUserAgentOverride());
        }
        setupSessionListeners(session);

        return session;
    }

    void recreateSession() {
        boolean wasFullScreen = mState.mFullScreen;

        GeckoSession previousGeckoSession = null;
        if (mState.mSession != null) {
            previousGeckoSession = mState.mSession;
            closeSession(mState);
        }

        mState = mState.recreate();

        mSessionChangeListeners.forEach(listener -> listener.onSessionRemoved(mState.mId));

        restore();

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onSessionStateChanged(this, true);
        }

        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onCurrentSessionChange(previousGeckoSession, mState.mSession);
        }

        if (wasFullScreen != mState.mFullScreen) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
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
            mState.mDisplay.screenshot().aspectPreservingSize(500).capture().then(bitmap -> {
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
        if (mState.mSession == null || !mFirstContentfulPaint) {
            return CompletableFuture.completedFuture(null);
        }
        Surface captureSurface = BitmapCache.getInstance(mContext).acquireCaptureSurface(displayWidth, displayHeight);
        if (captureSurface == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        GeckoDisplay display = mState.mSession.acquireDisplay();
        display.surfaceChanged(captureSurface, displayWidth, displayHeight);

        Runnable cleanResources = () -> {
            display.surfaceDestroyed();
            if (mState.mSession != null) {
                mState.mSession.releaseDisplay(display);
            }
            BitmapCache.getInstance(mContext).releaseCaptureSurface();
        };

        try {
            display.screenshot().aspectPreservingSize(500).capture().then(bitmap -> {
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
        if (homepage.equals(mContext.getString(R.string.homepage_url)) && mState.mRegion != null) {
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

    public boolean isVideoAvailable() {
        return mState.mMediaElements != null && mState.mMediaElements.size() > 0;
    }

    public boolean isFirstContentfulPaint() {
        return mFirstContentfulPaint;
    }

    public boolean isWebExtensionSession() {
        return mState.mIsWebExtensionSession;
    }

    @Nullable
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

    @Nullable
    public Media getActiveVideo() {
        for (Media media: mState.mMediaElements) {
            if (media.isFullscreen()) {
                return media;
            }
        }
        return mState.mMediaElements.stream()
                .sorted((o1, o2) -> (int)o2.getLastStateUpdate() - (int)o1.getLastStateUpdate())
                .filter(Media::isPlayed)
                .findFirst().orElse(null);
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
            Log.e(LOGTAG, "ERROR: Setting null GeckoView to inactive!");
        }

        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onSessionStateChanged(this, aActive);
        }
    }

    public void reload() {
        reload(GeckoSession.LOAD_FLAGS_NONE);
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
        loadUri(aUri, GeckoSession.LOAD_FLAGS_NONE);
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

        mState = createSessionState(settings);
        openSession();
        closeSession(previous);

        mState.setActive(true);
        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onSessionStateChanged(this, true);
        }

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
        if (overrideUri != null) {
            mState.mSession.loadUri(overrideUri, GeckoSession.LOAD_FLAGS_BYPASS_CACHE | GeckoSession.LOAD_FLAGS_REPLACE_HISTORY);
        } else {
            mState.mSession.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
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

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession aSession, String aUri) {
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
            mState.mSession.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        } else {
            mState.mSession.getSettings().setViewportMode(mState.mSettings.getViewportMode());
        }

        for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onLocationChange(aSession, aUri);
        }

        // The homepage finishes loading after the region has been updated
        if (mState.mRegion != null && aUri.equalsIgnoreCase(SettingsStore.getInstance(mContext).getHomepage())) {
            aSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        }
    }

    @Override
    public void onCanGoBack(@NonNull GeckoSession aSession, boolean aGeckoSessionCanGoBack) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onCanGoBack: " + (aGeckoSessionCanGoBack ? "true" : "false"));
        mState.mCanGoBack = aGeckoSessionCanGoBack;

        for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
            listener.onCanGoBack(aSession, canGoBack());
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

        if (aSession == mState.mSession) {
            Log.d(LOGTAG, "Testing for UA override");

            final String userAgentOverride = sUserAgentOverride.lookupOverride(uri);
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

        // If this request is externally handled we just deny
        if (mExternalRequestDelegate != null) {
            if (mExternalRequestDelegate.onHandleExternalRequest(uri)) {
                return GeckoResult.DENY;
            }
        }

        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        AtomicInteger count = new AtomicInteger(0);
        AtomicBoolean allowed = new AtomicBoolean(true);
        final int listenerCount = mNavigationListeners.size() - 1;
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            GeckoResult<AllowOrDeny> listenerResult = listener.onLoadRequest(aSession, aRequest);
            if (listenerResult != null) {
                listenerResult.then(value -> {
                    if (AllowOrDeny.DENY.equals(value)) {
                        allowed.set(false);
                    }
                    if (count.getAndIncrement() == listenerCount) {
                        result.complete(allowed.get() ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                    }

                    return null;
                });

            } else {
                allowed.set(true);
                if (count.getAndIncrement() == listenerCount) {
                    result.complete(allowed.get() ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                }
            }
        }

        if (UrlUtils.isAboutPage(aRequest.uri)) {
            return GeckoResult.DENY;
        }

        return result;
    }

    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession aSession, @NonNull String aUri) {
        mKeepAlive = System.currentTimeMillis() + KEEP_ALIVE_DURATION_MS;
        Log.d(LOGTAG, "onNewSession: " + aUri);

        Session session = SessionStore.get().createSession(mState.mSettings, SESSION_DO_NOT_OPEN);
        session.mState.mParentId = mState.mId;
        session.mKeepAlive = mKeepAlive;
        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onStackSession(session);
        }
        mSessionChangeListeners.add(session);
        return GeckoResult.fromValue(session.getGeckoSession());
    }

    @Override
    public GeckoResult<String> onLoadError(@NonNull GeckoSession session, @Nullable String uri,  @NonNull WebRequestError error) {
        Log.d(LOGTAG, "Session onLoadError: " + uri);

        return GeckoResult.fromValue(InternalPages.createErrorPageDataURI(mContext, uri, error.code));
    }

    // Progress Listener

    @Override
    public void onPageStart(@NonNull GeckoSession aSession, @NonNull String aUri) {
        if (mState.mSession != aSession) {
            return;
        }
        Log.d(LOGTAG, "Session onPageStart");
        mState.mIsLoading = true;
        GleanMetricsService.startPageLoadTime(aUri);

        setWebXRState(SessionState.WEBXR_UNUSED);
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
            GleanMetricsService.stopPageLoadTimeWithURI(mState.mUri);
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
        for (GeckoSession.ContentDelegate listener : mContentListeners) {
            listener.onCloseRequest(aSession);
        }
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
        Log.e(LOGTAG,"Child crashed. Recreating session");
        recreateSession();
    }

    @Override
    public void onKill(@NonNull GeckoSession session) {
        Log.e(LOGTAG,"Child killed. Recreating session");
        recreateSession();
    }

    @Override
    public void onFirstComposite(@NonNull GeckoSession aSession) {
        if (mState.mSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstComposite(aSession);
            }
            if (mFirstContentfulPaint) {
                // onFirstContentfulPaint is only called once after a session is opened.
                // Notify onFirstContentfulPaint after a session is reattached before
                // being closed ((e.g. tab selected)
                for (GeckoSession.ContentDelegate listener : mContentListeners) {
                    listener.onFirstContentfulPaint(aSession);
                }
            }
        }
    }

    @Override
    public void onFirstContentfulPaint(@NonNull GeckoSession aSession) {
        mFirstContentfulPaint = true;
        if (mState.mSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstContentfulPaint(aSession);
            }
        }
    }

    @Nullable
    @Override
    public GeckoResult<SlowScriptResponse> onSlowScript(@NonNull GeckoSession aSession, @NonNull String aScriptFileName) {
        if (mState.mSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                GeckoResult<SlowScriptResponse> result = listener.onSlowScript(aSession, aScriptFileName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public void onExternalResponse(@NonNull GeckoSession geckoSession, @NonNull GeckoSession.WebResponseInfo webResponseInfo) {
        for (GeckoSession.ContentDelegate listener : mContentListeners) {
            listener.onExternalResponse(geckoSession, webResponseInfo);
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
            Log.d(LOGTAG, "Blocking Ad: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.ANALYTIC) != 0) {
            Log.d(LOGTAG, "Blocking Analytic: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.CONTENT) != 0) {
            Log.d(LOGTAG, "Blocking Content: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.SOCIAL) != 0) {
            Log.d(LOGTAG, "Blocking Social: " + event.uri);
        }
    }

    @Override
    public void onContentLoaded(@NonNull GeckoSession geckoSession, @NonNull ContentBlocking.BlockEvent event) {
        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.AD) != 0) {
            Log.d(LOGTAG, "Loading Ad: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.ANALYTIC) != 0) {
            Log.d(LOGTAG, "Loading Analytic: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.CONTENT) != 0) {
            Log.d(LOGTAG, "Loading Content: " + event.uri);
        }

        if ((event.getAntiTrackingCategory() & ContentBlocking.AntiTracking.SOCIAL) != 0) {
            Log.d(LOGTAG, "Loading Social: " + event.uri);
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

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(@NonNull GeckoSession aSession, @NonNull BeforeUnloadPrompt prompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onBeforeUnloadPrompt(aSession, prompt);
        }
        return GeckoResult.fromValue(prompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSelect(@NonNull GeckoSession geckoSession, @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> autocompleteRequest) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onLoginSelect(geckoSession, autocompleteRequest);
        }
        return GeckoResult.fromValue(autocompleteRequest.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSave(@NonNull GeckoSession geckoSession, @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> autocompleteRequest) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onLoginSave(geckoSession, autocompleteRequest);
        }
        return GeckoResult.fromValue(autocompleteRequest.dismiss());
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
            listener.onVideoAvailabilityChanged(media, true);
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
                for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
                    listener.onVideoAvailabilityChanged(media, false);
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
                if (data != null) {
                    setRegion(data.getCountryCode());
                }
            }
        }
    }

    // GeckoSession.SelectionActionDelegate

    @Override
    public void onShowActionRequest(@NonNull GeckoSession aSession, @NonNull Selection selection) {
        if (mState.mSession == aSession) {
            for (GeckoSession.SelectionActionDelegate listener : mSelectionActionListeners) {
                listener.onShowActionRequest(aSession, selection);
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
    public void onSessionRemoved(String aId) {
        if (mState.mParentId != null) {
            mState.mParentId = null;
            // Parent stack session closed. Notify canGoBack state changed
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getGeckoSession(), canGoBack());
            }
        }
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        if (mState.mParentId != null) {
            // Parent stack session has been attached/detached. Notify canGoBack state changed
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(this.getGeckoSession(), canGoBack());
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
        Log.d(LOGTAG, "\tCan go back: " + mState.mCanGoBack);
        Log.d(LOGTAG, "\tCan go forward: " + mState.mCanGoForward);
        if (mState.mSettings != null) {
            Log.d(LOGTAG, "\tPrivate Browsing: " + mState.mSettings.isPrivateBrowsingEnabled());
        }
    }
}
