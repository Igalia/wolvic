/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.engine;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
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
import org.mozilla.vrbrowser.utils.InternalPages;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mozilla.vrbrowser.utils.ServoUtils.createServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isInstanceOfServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

public class SessionStack implements ContentBlocking.Delegate, GeckoSession.NavigationDelegate,
        GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate, GeckoSession.TextInputDelegate,
        GeckoSession.PromptDelegate, GeckoSession.MediaDelegate, GeckoSession.HistoryDelegate,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOGTAG = SystemUtils.createLogtag(SessionStack.class);
    // You can test a local file using: "resource://android/assets/webvr/index.html"
    public static final int NO_SESSION = -1;

    private transient LinkedList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private transient LinkedList<GeckoSession.ProgressDelegate> mProgressListeners;
    private transient LinkedList<GeckoSession.ContentDelegate> mContentListeners;
    private transient LinkedList<SessionChangeListener> mSessionChangeListeners;
    private transient LinkedList<GeckoSession.TextInputDelegate> mTextInputListeners;
    private transient LinkedList<VideoAvailabilityListener> mVideoAvailabilityListeners;
    private transient UserAgentOverride mUserAgentOverride;

    private transient GeckoSession mCurrentSession;
    private LinkedHashMap<Integer, SessionState> mSessions;
    private transient GeckoSession.PermissionDelegate mPermissionDelegate;
    private transient GeckoSession.PromptDelegate mPromptDelegate;
    private transient GeckoSession.HistoryDelegate mHistoryDelegate;
    private int mPreviousGeckoSessionId = NO_SESSION;
    private String mRegion;
    private transient Context mContext;
    private transient SharedPreferences mPrefs;
    private transient GeckoRuntime mRuntime;
    private boolean mUsePrivateMode;
    private transient byte[] mPrivatePage;

    protected SessionStack(Context context, GeckoRuntime runtime, boolean usePrivateMode) {
        mRuntime = runtime;
        mSessions = new LinkedHashMap<>();
        mUsePrivateMode = usePrivateMode;

        mNavigationListeners = new LinkedList<>();
        mProgressListeners = new LinkedList<>();
        mContentListeners = new LinkedList<>();
        mSessionChangeListeners = new LinkedList<>();
        mTextInputListeners = new LinkedList<>();
        mVideoAvailabilityListeners = new LinkedList<>();

        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        mPrivatePage = InternalPages.createAboutPage(mContext, pageResources);

        if (mUserAgentOverride == null) {
            mUserAgentOverride = new UserAgentOverride();
            mUserAgentOverride.loadOverridesFromAssets((Activity)mContext, mContext.getString(R.string.user_agent_override_file));
        }
    }

    protected void shutdown() {
        for (Map.Entry<Integer, SessionState> session : mSessions.entrySet()) {
            session.getValue().mSession.close();
        }

        mSessions.clear();

        mNavigationListeners.clear();
        mProgressListeners.clear();
        mContentListeners.clear();
        mSessionChangeListeners.clear();
        mTextInputListeners.clear();
        mVideoAvailabilityListeners.clear();

        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        mCurrentSession = null;
        mPreviousGeckoSessionId = NO_SESSION;
    }

    private void dumpAllState(GeckoSession aSession) {
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            dumpState(aSession, listener);
        }
        for (GeckoSession.ProgressDelegate listener: mProgressListeners) {
            dumpState(aSession, listener);
        }
        for (GeckoSession.ContentDelegate listener: mContentListeners) {
            dumpState(aSession, listener);
        }
    }

    private void dumpState(GeckoSession aSession, GeckoSession.NavigationDelegate aListener) {
        boolean canGoForward = false;
        boolean canGoBack = false;
        String uri = "";
        if (aSession != null) {
            SessionState state = mSessions.get(aSession.hashCode());
            if (state != null) {
                canGoBack = state.mCanGoBack;
                canGoForward = state.mCanGoForward;
                uri = state.mUri;
            }
        }
        aListener.onCanGoBack(aSession, canGoBack);
        aListener.onCanGoForward(aSession, canGoForward);
        aListener.onLocationChange(aSession, uri);
    }

    private void dumpState(GeckoSession aSession, GeckoSession.ProgressDelegate aListener) {
        boolean isLoading = false;
        GeckoSession.ProgressDelegate.SecurityInformation securityInfo = null;
        String uri = "";
        if (aSession != null) {
            SessionState state = mSessions.get(aSession.hashCode());
            if (state != null) {
                isLoading = state.mIsLoading;
                securityInfo = state.mSecurityInformation;
                uri = state.mUri;
            }
        }
        if (isLoading) {
            aListener.onPageStart(aSession, uri);
        } else {
            aListener.onPageStop(aSession, true);
        }

        if (securityInfo != null) {
            aListener.onSecurityChange(aSession, securityInfo);
        }
    }

    private void dumpState(GeckoSession aSession, GeckoSession.ContentDelegate aListener) {
        String title = "";
        if (aSession != null) {
            SessionState state = mSessions.get(aSession.hashCode());
            if (state != null) {
                title = state.mTitle;
            }
        }

        aListener.onTitleChange(aSession, title);
    }

    public void setPermissionDelegate(GeckoSession.PermissionDelegate aDelegate) {
        mPermissionDelegate = aDelegate;
        for (HashMap.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            entry.getValue().mSession.setPermissionDelegate(aDelegate);
        }
    }

    public void setPromptDelegate(GeckoSession.PromptDelegate aDelegate) {
        mPromptDelegate = aDelegate;
        for (HashMap.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            entry.getValue().mSession.setPromptDelegate(aDelegate);
        }
    }

    public void setHistoryDelegate(GeckoSession.HistoryDelegate aDelegate) {
        mHistoryDelegate = aDelegate;
        for (HashMap.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            entry.getValue().mSession.setHistoryDelegate(aDelegate);
        }
    }

    public void addNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.add(aListener);
        dumpState(mCurrentSession, aListener);
    }

    public void removeNavigationListener(GeckoSession.NavigationDelegate aListener) {
        mNavigationListeners.remove(aListener);
    }

    public void addProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.add(aListener);
        dumpState(mCurrentSession, aListener);
    }

    public void removeProgressListener(GeckoSession.ProgressDelegate aListener) {
        mProgressListeners.remove(aListener);
    }

    public void addContentListener(GeckoSession.ContentDelegate aListener) {
        mContentListeners.add(aListener);
        dumpState(mCurrentSession, aListener);
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
    }

    public void removeVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.remove(aListener);
    }

    public void restore(SessionStack store, int currentSessionId) {
        mSessions.clear();

        mPreviousGeckoSessionId = store.mPreviousGeckoSessionId;
        mRegion = store.mRegion;

        for (Map.Entry<Integer, SessionState> entry : store.mSessions.entrySet()) {
            SessionState state = entry.getValue();

            GeckoSessionSettings geckoSettings = new GeckoSessionSettings.Builder()
                    .useMultiprocess(state.mSettings.isMultiprocessEnabled())
                    .usePrivateMode(mUsePrivateMode)
                    .userAgentMode(state.mSettings.getUserAgentMode())
                    .suspendMediaWhenInactive(state.mSettings.isSuspendMediaWhenInactiveEnabled())
                    .useTrackingProtection(state.mSettings.isTrackingProtectionEnabled())
                    .build();

            if (state.mSettings.isServoEnabled()) {
                if (isServoAvailable()) {
                    state.mSession = createServoSession(mContext);
                } else {
                    Log.e(LOGTAG, "Attempt to create a ServoSession. Servo hasn't been enable at build time. Using a GeckoSession instead.");
                    state.mSession = new GeckoSession(geckoSettings);
                }
            } else {
                state.mSession = new GeckoSession(geckoSettings);
            }

            if (state.mSessionState != null) {
                state.mSession.restoreState(state.mSessionState);
            }

            int newSessionId = state.mSession.hashCode();

            state.mSession.setNavigationDelegate(this);
            state.mSession.setProgressDelegate(this);
            state.mSession.setContentDelegate(this);
            state.mSession.getTextInput().setDelegate(this);
            state.mSession.setPermissionDelegate(mPermissionDelegate);
            state.mSession.setPromptDelegate(mPromptDelegate);
            state.mSession.setContentBlockingDelegate(this);
            state.mSession.setMediaDelegate(this);
            state.mSession.setHistoryDelegate(this);
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onNewSession(state.mSession, newSessionId);
            }

            mSessions.put(newSessionId, state);

            if (entry.getKey() == currentSessionId) {
                setCurrentSession(newSessionId);
            }

            if (mUsePrivateMode) {
                loadPrivateBrowsingPage();

            } else if(state.mSessionState == null || state.mUri.equals(mContext.getResources().getString(R.string.about_blank)) ||
                    (state.mSessionState != null && state.mSessionState.size() == 0)) {
                loadHomePage();
            }

            dumpAllState(state.mSession);
        }
    }

    private int createSession() {
        SessionSettings settings = new SessionSettings.Builder()
                .withDefaultSettings(mContext)
                .build();

        return createSession(settings);
    }

    private int createSession(@NonNull SessionSettings aSettings) {
        SessionState state = new SessionState();
        state.mSettings = aSettings;

        GeckoSessionSettings geckoSettings = new GeckoSessionSettings.Builder()
            .useMultiprocess(aSettings.isMultiprocessEnabled())
            .usePrivateMode(mUsePrivateMode)
            .useTrackingProtection(aSettings.isTrackingProtectionEnabled())
            .build();

        if (aSettings.isServoEnabled()) {
            if (isServoAvailable()) {
                state.mSession = createServoSession(mContext);
            } else {
                Log.e(LOGTAG, "Attempt to create a ServoSession. Servo hasn't been enable at build time. Using a GeckoSession instead.");
                state.mSession = new GeckoSession(geckoSettings);
            }
        } else {
            state.mSession = new GeckoSession(geckoSettings);
        }

        int result = state.mSession.hashCode();
        mSessions.put(result, state);

        state.mSession.getSettings().setSuspendMediaWhenInactive(aSettings.isSuspendMediaWhenInactiveEnabled());
        state.mSession.getSettings().setUserAgentMode(aSettings.getUserAgentMode());
        state.mSession.setNavigationDelegate(this);
        state.mSession.setProgressDelegate(this);
        state.mSession.setContentDelegate(this);
        state.mSession.getTextInput().setDelegate(this);
        state.mSession.setPermissionDelegate(mPermissionDelegate);
        state.mSession.setPromptDelegate(mPromptDelegate);
        state.mSession.setContentBlockingDelegate(this);
        state.mSession.setMediaDelegate(this);
        state.mSession.setHistoryDelegate(this);
        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onNewSession(state.mSession, result);
        }

        return result;
    }

    private void recreateAllSessions() {
        Map<Integer, SessionState> sessions = (Map<Integer, SessionState>) mSessions.clone();
        for (Integer sessionId : sessions.keySet()) {
            recreateSession(sessionId);
        }
    }

    private void recreateSession(int sessionId) {
        SessionState previousSessionState = mSessions.get(sessionId);

        previousSessionState.mSession.stop();
        previousSessionState.mSession.close();

        int newSessionId = createSession(previousSessionState.mSettings);
        GeckoSession session = mSessions.get(newSessionId).mSession;
        if (previousSessionState.mSessionState != null)
            session.restoreState(previousSessionState.mSessionState);
        setCurrentSession(newSessionId);
        removeSession(sessionId);
    }

    private void removeSession(int aSessionId) {
        GeckoSession session = getSession(aSessionId);
        if (session != null) {
            session.setContentDelegate(null);
            session.setNavigationDelegate(null);
            session.setProgressDelegate(null);
            session.getTextInput().setDelegate(null);
            session.setPromptDelegate(null);
            session.setPermissionDelegate(null);
            session.setContentBlockingDelegate(null);
            session.setMediaDelegate(null);
            session.setHistoryDelegate(null);
            mSessions.remove(aSessionId);
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onRemoveSession(session, aSessionId);
            }
            session.setActive(false);
            session.stop();
            session.close();
        }
    }

    public void newSession() {
        SessionSettings settings = new SessionSettings.Builder().withDefaultSettings(mContext).build();
        int id = createSession(settings);
        setCurrentSession(id);
    }

    public void newSessionWithUrl(String url) {
        newSession();
        loadUri(url);
    }

    private void unstackSession() {
        int currentSessionId = getCurrentSessionId();
        ArrayList sessionsStack = new ArrayList<>(mSessions.keySet());
        int index = sessionsStack.indexOf(currentSessionId);
        if (index > 0) {
            int prevSessionId = (Integer)sessionsStack.get(index-1);
            setCurrentSession(prevSessionId);
            removeSession(currentSessionId);
        }
    }

    public GeckoSession getSession(int aId) {
        SessionState state = mSessions.get(aId);
        if (state == null) {
            return null;
        }
        return state.mSession;
    }

    public boolean containsSession(GeckoSession aSession) {
        return getSessionId(aSession) != NO_SESSION;
    }

    private Integer getSessionId(GeckoSession aSession) {
        for (Map.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            if (entry.getValue().mSession == aSession) {
                return  entry.getKey();
            }
        }
        return NO_SESSION;
    }

    public String getUriFromSession(GeckoSession aSession) {
        Integer sessionId = getSessionId(aSession);
        if (sessionId == NO_SESSION) {
            return "";
        }
        SessionState state = mSessions.get(sessionId);
        if (state != null) {
            return state.mUri;
        }

        return "";
    }

    public void setCurrentSession(int aId) {
        Log.d(LOGTAG, "Creating session: " + aId);

        if (mCurrentSession != null) {
            mCurrentSession.setActive(false);
        }

        mCurrentSession = null;
        SessionState state = mSessions.get(aId);
        if (state != null) {
            mCurrentSession = state.mSession;
            if (!mCurrentSession.isOpen()) {
                mCurrentSession.open(mRuntime);
            }
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onCurrentSessionChange(mCurrentSession, aId);
            }
        }
        dumpAllState(mCurrentSession);

        if (mCurrentSession != null) {
            mCurrentSession.setActive(true);
        }
    }

    public void setRegion(String aRegion) {
        Log.d(LOGTAG, "SessionStack setRegion: " + aRegion);
        mRegion = aRegion != null ? aRegion.toLowerCase() : "worldwide";

        // There is a region initialize and the home is already loaded
        if (mCurrentSession != null && isHomeUri(getCurrentUri())) {
            mCurrentSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        }
    }

    public String getHomeUri() {
        String homepage = SettingsStore.getInstance(mContext).getHomepage();
        if (homepage.equals(mContext.getString(R.string.homepage_url)) && mRegion != null) {
            homepage = homepage + "?region=" + mRegion;
        }
        return homepage;
    }

    public Boolean isHomeUri(String aUri) {
        return aUri != null && aUri.toLowerCase().startsWith(
          SettingsStore.getInstance(mContext).getHomepage()
        );
    }

    public String getCurrentUri() {
        String result = "";
        if (mCurrentSession != null) {
            SessionState state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mUri;
        }
        return result;
    }

    public String getCurrentTitle() {
        String result = "";
        if (mCurrentSession != null) {
            SessionState state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mTitle;
        }
        return result;
    }

    public boolean isSecure() {
        boolean isSecure = false;
        if (mCurrentSession != null) {
            SessionState state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return false;
            }
            isSecure = state.mSecurityInformation != null && state.mSecurityInformation.isSecure;
        }
        return isSecure;
    }

    public Media getFullScreenVideo() {
        if (mCurrentSession != null) {
            SessionState state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return null;
            }
            for (Media media: state.mMediaElements) {
                if (media.isFullscreen()) {
                    return media;
                }
            }
            if (state.mMediaElements.size() > 0) {
                return state.mMediaElements.get(state.mMediaElements.size() - 1);
            }
        }

        return null;
    }

    public boolean isInputActive(int aSessionId) {
        SessionState state = mSessions.get(aSessionId);
        if (state != null) {
            return state.mIsInputActive;
        }
        return false;
    }

    public boolean canGoBack() {
        if (mCurrentSession == null) {
            return false;
        }

        SessionState state = mSessions.get(mCurrentSession.hashCode());
        boolean canGoBack = false;
        if (state != null) {
            canGoBack = state.mCanGoBack;
        }

        return canGoBack || mSessions.size() > 1;
    }

    public void goBack() {
        if (mCurrentSession == null) {
             return;
        }
        if (isInFullScreen()) {
            exitFullScreen();

        } else {
            int sessionId = getCurrentSessionId();
            SessionState state = mSessions.get(sessionId);
            if (state.mCanGoBack) {
                getCurrentSession().goBack();

            } else {
                unstackSession();
            }
        }
    }

    public void goForward() {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.goForward();
    }

    public void setActive(boolean aActive) {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.setActive(aActive);
    }


    public void reload() {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.reload();
    }

    public void stop() {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.stop();
    }

    public void loadUri(String aUri) {
        if (mCurrentSession == null) {
            return;
        }

        if (aUri == null) {
            aUri = getHomeUri();
        }
        Log.d(LOGTAG, "Loading URI: " + aUri);
        mCurrentSession.loadUri(aUri);
    }

    public void loadHomePage() {
        if (mCurrentSession == null) {
            return;
        }

        mCurrentSession.loadUri(getHomeUri());
    }

    public void loadPrivateBrowsingPage() {
        if (mCurrentSession == null) {
            return;
        }

        mCurrentSession.loadData(mPrivatePage, "text/html");
    }

    public void toggleServo() {
        if (mCurrentSession == null) {
            return;
        }

        Log.v("servo", "toggleServo");

        if (!isInstanceOfServoSession(mCurrentSession)) {
            if (mPreviousGeckoSessionId == SessionStack.NO_SESSION) {
                mPreviousGeckoSessionId = getCurrentSessionId();
                String uri = getCurrentUri();
                SessionSettings settings = new SessionSettings.Builder()
                        .withDefaultSettings(mContext)
                        .withServo(true)
                        .build();
                int id = createSession(settings);
                setCurrentSession(id);
                loadUri(uri);
            } else {
                Log.e(LOGTAG, "Multiple Servo sessions not supported yet.");
            }
        } else {
            removeSession(getCurrentSessionId());
            setCurrentSession(mPreviousGeckoSessionId);
            mPreviousGeckoSessionId = SessionStack.NO_SESSION;
        }
    }

    public boolean isInFullScreen() {
        if (mCurrentSession == null) {
            return false;
        }

        SessionState state = mSessions.get(mCurrentSession.hashCode());
        if (state != null) {
            return state.mFullScreen;
        }

        return false;
    }

    public boolean isInFullScreen(GeckoSession aSession) {
        Integer sessionId = getSessionId(aSession);
        if (sessionId == NO_SESSION) {
            return false;
        }
        SessionState state = mSessions.get(sessionId);
        if (state != null) {
            return state.mFullScreen;
        }

        return false;
    }

    public void exitFullScreen() {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.exitFullScreen();
    }

    public GeckoSession getCurrentSession() {
        return mCurrentSession;
    }

    public int getCurrentSessionId() {
        if (mCurrentSession == null) {
            return NO_SESSION;
        }
        return mCurrentSession.hashCode();
    }

    public boolean isPrivateMode() {
        if (mCurrentSession != null) {
            return mCurrentSession.getSettings().getUsePrivateMode();
        }
        return false;
    }

    // Session Settings

    protected void setServo(final boolean enabled) {
        SessionState state = mSessions.get(mCurrentSession.hashCode());
        if (state != null) {
            state.mSettings.setServoEnabled(enabled);
        }
        if (!enabled && mCurrentSession != null && isInstanceOfServoSession(mCurrentSession)) {
            String uri = getCurrentUri();
            int id = createSession();
            setCurrentSession(id);
            loadUri(uri);
        }
    }

    public int getUaMode() {
        return mCurrentSession.getSettings().getUserAgentMode();
    }

    private static final String M_PREFIX = "m.";
    private static final String MOBILE_PREFIX = "mobile.";

    private String checkForMobileSite(String aUri) {
        String result = null;
        URI uri;
        try {
            uri = new URI(aUri);
        } catch (URISyntaxException e) {
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
            } catch (URISyntaxException e) {
                Log.d(LOGTAG, "Error dropping mobile prefix from: " + aUri + " " + e.getMessage());
            }
        }
        return result;
    }

    public void setUaMode(int mode) {
        if (mCurrentSession != null) {
            SessionState state = mSessions.get(mCurrentSession.hashCode());
            if (state != null && state.mSettings.getUserAgentMode() != mode) {
                state.mSettings.setUserAgentMode(mode);
                mCurrentSession.getSettings().setUserAgentMode(mode);
                String overrideUri = null;
                if (mode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP) {
                    state.mSettings.setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
                    overrideUri = checkForMobileSite(state.mUri);
                } else {
                    state.mSettings.setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
                }
                mCurrentSession.getSettings().setViewportMode(state.mSettings.getViewportMode());
                mCurrentSession.loadUri(overrideUri != null ? overrideUri : state.mUri, GeckoSession.LOAD_FLAGS_BYPASS_CACHE | GeckoSession.LOAD_FLAGS_REPLACE_HISTORY);
            }
        }
    }

    protected void setMultiprocess(final boolean aEnabled) {
        for (Map.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            SessionState state = entry.getValue();
            if (state != null && state.mSettings.isMultiprocessEnabled() != aEnabled) {
                state.mSettings.setMultiprocessEnabled(aEnabled);
            }
        }

        recreateAllSessions();
    }

    protected void setTrackingProtection(final boolean aEnabled) {
        for (Map.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
            SessionState state = entry.getValue();
            if (state != null && state.mSettings.isTrackingProtectionEnabled() != aEnabled) {
                state.mSettings.setTrackingProtectionEnabled(aEnabled);
            }
        }

        recreateAllSessions();
    }

    public void clearCache(final long clearFlags) {
        if (mRuntime != null) {
            // Per GeckoView Docs:
            // Note: Any open session may re-accumulate previously cleared data.
            // To ensure that no persistent data is left behind, you need to close all sessions prior to clearing data.
            // https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/StorageController.html#clearData-long-
            for (Map.Entry<Integer, SessionState> entry : mSessions.entrySet()) {
                SessionState state = entry.getValue();
                if (state != null) {
                    state.mSession.stop();
                    state.mSession.close();
                }
            }

            mRuntime.getStorageController().clearData(clearFlags).then(aVoid -> {
                recreateAllSessions();
                return null;
            });
        }
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession aSession, String aUri) {
        Log.d(LOGTAG, "SessionStack onLocationChange: " + aUri);
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            Log.e(LOGTAG, "Unknown session!");
            return;
        }

        state.mPreviousUri = state.mUri;
        state.mUri = aUri;

        if (mCurrentSession == aSession) {
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onLocationChange(aSession, aUri);
            }
        }

        // The homepage finishes loading after the region has been updated
        if (mRegion != null && aUri.equalsIgnoreCase(SettingsStore.getInstance(mContext).getHomepage())) {
            aSession.loadUri("javascript:window.location.replace('" + getHomeUri() + "');");
        }
    }

    @Override
    public void onCanGoBack(@NonNull GeckoSession aSession, boolean aCanGoBack) {
        Log.d(LOGTAG, "SessionStack onCanGoBack: " + (aCanGoBack ? "true" : "false"));
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mCanGoBack = aCanGoBack;

        if (mCurrentSession == aSession) {
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoBack(aSession, aCanGoBack);
            }
        }
    }

    @Override
    public void onCanGoForward(@NonNull GeckoSession aSession, boolean aCanGoForward) {
        Log.d(LOGTAG, "SessionStack onCanGoForward: " + (aCanGoForward ? "true" : "false"));
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mCanGoForward = aCanGoForward;

        if (mCurrentSession == aSession) {
            for (GeckoSession.NavigationDelegate listener : mNavigationListeners) {
                listener.onCanGoForward(aSession, aCanGoForward);
            }
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

        if (aSession == mCurrentSession) {
            Log.d(LOGTAG, "Testing for UA override");
            aSession.getSettings().setUserAgentOverride(mUserAgentOverride.lookupOverride(uri));
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
        Log.d(LOGTAG, "SessionStack onNewSession: " + aUri);

        int sessionId;
        SessionState state = mSessions.get(getCurrentSessionId());
        if (state != null) {
            sessionId = createSession(state.mSettings);

        } else {
            SessionSettings settings = new SessionSettings.Builder()
                .withDefaultSettings(mContext)
                .build();
            sessionId = createSession(settings);
        }

        state = mSessions.get(sessionId);
        mCurrentSession = state.mSession;
        if (mCurrentSession != aSession) {
            for (SessionChangeListener listener : mSessionChangeListeners) {
                listener.onCurrentSessionChange(mCurrentSession, sessionId);
            }
        }
        dumpAllState(mCurrentSession);

        return GeckoResult.fromValue(mCurrentSession);
    }

    @Override
    public GeckoResult<String> onLoadError(@NonNull GeckoSession session, String uri,  @NonNull WebRequestError error) {
        Log.d(LOGTAG, "SessionStack onLoadError: " + uri);

        return GeckoResult.fromValue(InternalPages.createErrorPageDataURI(mContext, uri, error.category, error.code));
    }

    // Progress Listener

    @Override
    public void onPageStart(@NonNull GeckoSession aSession, @NonNull String aUri) {
        Log.d(LOGTAG, "SessionStack onPageStart");
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mIsLoading = true;
        TelemetryWrapper.startPageLoadTime();

        if (mCurrentSession == aSession) {
            for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
                listener.onPageStart(aSession, aUri);
            }
        }
    }

    @Override
    public void onPageStop(@NonNull GeckoSession aSession, boolean b) {
        Log.d(LOGTAG, "SessionStack onPageStop");
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mIsLoading = false;
        if (!SessionUtils.isLocalizedContent(state.mUri)) {
            TelemetryWrapper.uploadPageLoadToHistogram(state.mUri);
        }

        if (mCurrentSession == aSession) {
            for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
                listener.onPageStop(aSession, b);
            }
        }
    }

    @Override
    public void onSecurityChange(@NonNull GeckoSession aSession, @NonNull SecurityInformation aInformation) {
        Log.d(LOGTAG, "SessionStack onPageStop");
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mSecurityInformation = aInformation;

        if (mCurrentSession == aSession) {
            for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
                listener.onSecurityChange(aSession, aInformation);
            }
        }
    }

    @Override
    public void onSessionStateChange(@NonNull GeckoSession aSession,
                                     @NonNull GeckoSession.SessionState aSessionState) {
        SessionState state = mSessions.get(aSession.hashCode());
        if (state != null) {
            state.mSessionState = aSessionState;
        }
    }

    // Content Delegate

    @Override
    public void onTitleChange(@NonNull GeckoSession aSession, String aTitle) {
        Log.d(LOGTAG, "SessionStack onTitleChange");
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mTitle = aTitle;

        if (mCurrentSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onTitleChange(aSession, aTitle);
            }
        }
    }

    @Override
    public void onCloseRequest(@NonNull GeckoSession aSession) {
        int sessionId = getSessionId(aSession);
        if (getCurrentSessionId() == sessionId) {
            unstackSession();
        }
    }

    @Override
    public void onFullScreen(@NonNull GeckoSession aSession, boolean aFullScreen) {
        Log.d(LOGTAG, "SessionStack onFullScreen");
        SessionState state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mFullScreen = aFullScreen;

        if (mCurrentSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFullScreen(aSession, aFullScreen);
            }
        }
    }

    @Override
    public void onContextMenu(@NonNull GeckoSession session, int screenX, int screenY, @NonNull ContextElement element) {
        if (mCurrentSession == session) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onContextMenu(session, screenX, screenY, element);
            }
        }
    }

    @Override
    public void onCrash(@NonNull GeckoSession session) {
        Log.e(LOGTAG,"Child crashed. Creating new session");
        int crashedSessionId = getCurrentSessionId();
        int newSessionId = createSession();
        setCurrentSession(newSessionId);
        loadUri(getHomeUri());
        removeSession(crashedSessionId);
    }

    @Override
    public void onFirstComposite(@NonNull GeckoSession aSession) {
        if (mCurrentSession == aSession) {
            for (GeckoSession.ContentDelegate listener : mContentListeners) {
                listener.onFirstComposite(aSession);
            }
        }
    }

    // TextInput Delegate

    @Override
    public void restartInput(@NonNull GeckoSession aSession, int reason) {
        if (aSession == mCurrentSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.restartInput(aSession, reason);
            }
        }
    }

    @Override
    public void showSoftInput(@NonNull GeckoSession aSession) {
        SessionState state = mSessions.get(getSessionId(aSession));
        if (state != null) {
            state.mIsInputActive = true;
        }
        if (aSession == mCurrentSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.showSoftInput(aSession);
            }
        }
    }

    @Override
    public void hideSoftInput(@NonNull GeckoSession aSession) {
        SessionState state = mSessions.get(getSessionId(aSession));
        if (state != null) {
            state.mIsInputActive = false;
        }
        if (aSession == mCurrentSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.hideSoftInput(aSession);
            }
        }
    }

    @Override
    public void updateSelection(@NonNull GeckoSession aSession, int selStart, int selEnd, int compositionStart, int compositionEnd) {
        if (aSession == mCurrentSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateSelection(aSession, selStart, selEnd, compositionStart, compositionEnd);
            }
        }
    }

    @Override
    public void updateExtractedText(@NonNull GeckoSession aSession, @NonNull ExtractedTextRequest request, @NonNull ExtractedText text) {
        if (aSession == mCurrentSession) {
            for (GeckoSession.TextInputDelegate listener : mTextInputListeners) {
                listener.updateExtractedText(aSession, request, text);
            }
        }
    }

    @Override
    public void updateCursorAnchorInfo(@NonNull GeckoSession aSession, @NonNull CursorAnchorInfo info) {
        if (aSession == mCurrentSession) {
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
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession geckoSession, @NonNull AlertPrompt alertPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onAlertPrompt(geckoSession, alertPrompt);
        }
        return GeckoResult.fromValue(alertPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession geckoSession, @NonNull ButtonPrompt buttonPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onButtonPrompt(geckoSession, buttonPrompt);
        }
        return GeckoResult.fromValue(buttonPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession geckoSession, @NonNull TextPrompt textPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onTextPrompt(geckoSession, textPrompt);
        }
        return GeckoResult.fromValue(textPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession geckoSession, @NonNull AuthPrompt authPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onAuthPrompt(geckoSession, authPrompt);
        }
        return GeckoResult.fromValue(authPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession geckoSession, @NonNull ChoicePrompt choicePrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onChoicePrompt(geckoSession, choicePrompt);
        }
        return GeckoResult.fromValue(choicePrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onColorPrompt(@NonNull GeckoSession geckoSession, @NonNull ColorPrompt colorPrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onColorPrompt(geckoSession, colorPrompt);
        }
        return GeckoResult.fromValue(colorPrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(@NonNull GeckoSession geckoSession, @NonNull DateTimePrompt dateTimePrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onDateTimePrompt(geckoSession, dateTimePrompt);
        }
        return GeckoResult.fromValue(dateTimePrompt.dismiss());
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onFilePrompt(@NonNull GeckoSession geckoSession, @NonNull FilePrompt filePrompt) {
        if (mPromptDelegate != null) {
            return mPromptDelegate.onFilePrompt(geckoSession, filePrompt);
        }
        return GeckoResult.fromValue(filePrompt.dismiss());
    }

    // MediaDelegate

    @Override
    public void onMediaAdd(@NonNull GeckoSession session, @NonNull MediaElement element) {
        SessionState state = mSessions.get(getSessionId(session));
        if (state == null) {
            return;
        }
        Media media = new Media(element);
        state.mMediaElements.add(media);

        if (state.mMediaElements.size() == 1) {
            for (VideoAvailabilityListener listener: mVideoAvailabilityListeners) {
                listener.onVideoAvailabilityChanged(true);
            }
        }
    }

    @Override
    public void onMediaRemove(@NonNull GeckoSession session, @NonNull MediaElement element) {
        SessionState state = mSessions.get(getSessionId(session));
        if (state == null) {
            return;
        }
        for (int i = 0; i < state.mMediaElements.size(); ++i) {
            Media media = state.mMediaElements.get(i);
            if (media.getMediaElement() == element) {
                media.unload();
                state.mMediaElements.remove(i);
                if (state.mMediaElements.size() == 0) {
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
    public void onHistoryStateChange(@NonNull GeckoSession geckoSession, @NonNull GeckoSession.HistoryDelegate.HistoryList historyList) {
        if (mHistoryDelegate != null) {
            mHistoryDelegate.onHistoryStateChange(geckoSession, historyList);
        }
    }

    @Nullable
    @Override
    public GeckoResult<Boolean> onVisited(@NonNull GeckoSession geckoSession, @NonNull String url, @Nullable String lastVisitedURL, int flags) {
        if (mHistoryDelegate != null) {
            return mHistoryDelegate.onVisited(geckoSession, url, lastVisitedURL, flags);
        }

        return GeckoResult.fromValue(false);
    }

    @UiThread
    @Nullable
    public GeckoResult<boolean[]> getVisited(@NonNull GeckoSession geckoSession, @NonNull String[] urls) {
        if (mHistoryDelegate != null) {
            return mHistoryDelegate.getVisited(geckoSession, urls);
        }

        return GeckoResult.fromValue(new boolean[]{});
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
}
