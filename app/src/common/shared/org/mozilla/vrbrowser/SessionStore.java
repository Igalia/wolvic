/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import org.mozilla.gecko.GeckoAppShell;
import org.mozilla.gecko.GeckoProfile;
import org.mozilla.geckoview.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class SessionStore implements GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate, GeckoSession.TextInputDelegate, GeckoSession.TrackingProtectionDelegate {
    private static SessionStore mInstance;
    private static final String LOGTAG = "VRB";
    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }
    private static final String HOME_WITHOUT_REGION_ORIGIN = "https://webxr.today";
    public static final int NO_SESSION_ID = -1;

    private LinkedList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private LinkedList<GeckoSession.ProgressDelegate> mProgressListeners;
    private LinkedList<GeckoSession.ContentDelegate> mContentListeners;
    private LinkedList<SessionChangeListener> mSessionChangeListeners;
    private LinkedList<GeckoSession.TextInputDelegate> mTextInputListeners;

    public interface SessionChangeListener {
        void onNewSession(GeckoSession aSession, int aId);
        void onRemoveSession(GeckoSession aSession, int aId);
        void onCurrentSessionChange(GeckoSession aSession, int aId);
    }

    class State {
        boolean mCanGoBack;
        boolean mCanGoForward;
        boolean mIsLoading;
        boolean mIsInputActive;
        GeckoSession.ProgressDelegate.SecurityInformation mSecurityInformation;
        String mUri;
        String mTitle;
        boolean mFullScreen;
        GeckoSession mSession;
    }

    private GeckoRuntime mRuntime;
    private GeckoSession mCurrentSession;
    private HashMap<Integer, State> mSessions;
    private Deque<Integer> mSessionsStack;
    private Deque<Integer> mPrivateSessionsStack;
    private GeckoSession.PermissionDelegate mPermissionDelegate;
    private int mPreviousSessionId = SessionStore.NO_SESSION_ID;
    private String mRegion;
    private String mLastUri;
    private Context mContext;

    private SessionStore() {
        mNavigationListeners = new LinkedList<>();
        mProgressListeners = new LinkedList<>();
        mContentListeners = new LinkedList<>();
        mSessionChangeListeners = new LinkedList<>();
        mTextInputListeners = new LinkedList<>();

        mSessions = new LinkedHashMap<>();
        mSessionsStack = new ArrayDeque<>();
        mPrivateSessionsStack = new ArrayDeque<>();
    }

    public void clearListeners() {
        mNavigationListeners.clear();
        mProgressListeners.clear();
        mContentListeners.clear();
        mSessionChangeListeners.clear();
        mTextInputListeners.clear();
    }

    public static final String LOCAL_URI = "data:";

    public static boolean isLocalPage(String aURI) {
        if (aURI.startsWith(LOCAL_URI))
            return true;

        return false;
    }

    private InternalPages.PageResources errorPageResourcesForCategory(@LoadErrorCategory int category) {
        switch (category) {
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_UNKNOWN: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_SECURITY: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_NETWORK: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_CONTENT: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_URI: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_PROXY: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            case GeckoSession.NavigationDelegate.ERROR_CATEGORY_SAFEBROWSING: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
            default: {
                return InternalPages.PageResources.create(R.raw.error_pages, R.raw.error_style);
            }
        }
    }

    public void setContext(Context aContext) {
        if (mRuntime == null) {
            // FIXME: Once GeckoView has a prefs API
            vrPrefsWorkAround(aContext);
            GeckoRuntimeSettings.Builder runtimeSettingsBuilder = new GeckoRuntimeSettings.Builder();
            runtimeSettingsBuilder.javaCrashReportingEnabled(SettingsStore.getInstance(aContext).isCrashReportingEnabled());
            runtimeSettingsBuilder.nativeCrashReportingEnabled(SettingsStore.getInstance(aContext).isCrashReportingEnabled());
            runtimeSettingsBuilder.trackingProtectionCategories(GeckoSession.TrackingProtectionDelegate.CATEGORY_AD | GeckoSession.TrackingProtectionDelegate.CATEGORY_SOCIAL | GeckoSession.TrackingProtectionDelegate.CATEGORY_ANALYTIC);
            runtimeSettingsBuilder.consoleOutput(SettingsStore.getInstance(aContext).isConsoleLogsEnabled());
            runtimeSettingsBuilder.displayDensityOverride(SettingsStore.getInstance(aContext).getDisplayDensity());
            runtimeSettingsBuilder.remoteDebuggingEnabled(SettingsStore.getInstance(aContext).isRemoteDebuggingEnabled());
            runtimeSettingsBuilder.displayDpiOverride(SettingsStore.getInstance(aContext).getDisplayDpi());
            runtimeSettingsBuilder.screenSizeOverride(SettingsStore.getInstance(aContext).getMaxWindowWidth(),
                    SettingsStore.getInstance(aContext).getMaxWindowHeight());

            if (BuildConfig.DEBUG) {
                runtimeSettingsBuilder.arguments(new String[] { "-purgecaches" });
            }

            mRuntime = GeckoRuntime.create(aContext, runtimeSettingsBuilder.build());

        } else {
            mRuntime.attachTo(aContext);
        }

        mContext = aContext;
    }

    public void dumpAllState(Integer sessionId) {
        dumpAllState(getSession(sessionId));
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
            State state = mSessions.get(aSession.hashCode());
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
            State state = mSessions.get(aSession.hashCode());
            if (state != null) {
                isLoading = state.mIsLoading;
                securityInfo = state.mSecurityInformation;
                uri = state.mUri;
            }
        }
        if (isLoading) {
            aListener.onPageStart(aSession, uri);
        }

        if (securityInfo != null) {
            aListener.onSecurityChange(aSession, securityInfo);
        }
    }

    public void dumpState(GeckoSession aSession, GeckoSession.ContentDelegate aListener) {
        String title = "";
        if (aSession != null) {
            State state = mSessions.get(aSession.hashCode());
            if (state != null) {
                title = state.mTitle;
            }
        }

        aListener.onTitleChange(aSession, title);
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

    public class SessionSettings {
        public boolean multiprocess = SettingsStore.getInstance(mContext).isMultiprocessEnabled();
        public boolean privateMode = false;
        public boolean trackingProtection = true;
        public boolean suspendMediaWhenInactive = true;
        public int userAgentMode = SettingsStore.getInstance(mContext).getUaMode();
    }

    public int createSession() {
        return createSession(new SessionSettings());
    }
    public int createSession(SessionSettings aSettings) {
        State state = new State();
        state.mSession = new GeckoSession();

        int result = state.mSession.hashCode();
        mSessions.put(result, state);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, aSettings.multiprocess);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_PRIVATE_MODE, aSettings.privateMode);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_TRACKING_PROTECTION, aSettings.trackingProtection);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.SUSPEND_MEDIA_WHEN_INACTIVE, aSettings.suspendMediaWhenInactive);
        state.mSession.getSettings().setInt(GeckoSessionSettings.USER_AGENT_MODE, aSettings.userAgentMode);
        state.mSession.setNavigationDelegate(this);
        state.mSession.setProgressDelegate(this);
        state.mSession.setContentDelegate(this);
        state.mSession.getTextInput().setDelegate(this);
        state.mSession.setPermissionDelegate(mPermissionDelegate);
        state.mSession.setTrackingProtectionDelegate(this);
        for (SessionChangeListener listener: mSessionChangeListeners) {
            listener.onNewSession(state.mSession, result);
        }

        return result;
    }

    public void removeSession(int aSessionId) {
        GeckoSession session = getSession(aSessionId);
        if (session != null) {
            session.setContentDelegate(null);
            session.setNavigationDelegate(null);
            session.setProgressDelegate(null);
            session.getTextInput().setDelegate(null);
            mSessions.remove(aSessionId);
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onRemoveSession(session, aSessionId);
            }
            session.setActive(false);
            session.stop();
        }
    }

    private void pushSession(int aSessionId) {
        boolean isPrivateMode  = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode)
            mPrivateSessionsStack.push(aSessionId);
        else
            mSessionsStack.push(aSessionId);
    }

    private Integer popSession() {
        boolean isPrivateMode  = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode)
            return mPrivateSessionsStack.pop();
        else
            return mSessionsStack.pop();
    }

    private Integer peekSession() {
        boolean isPrivateMode  = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode)
            return mPrivateSessionsStack.peek();
        else
            return mSessionsStack.peek();
    }

    public GeckoSession getSession(int aId) {
        State result = mSessions.get(aId);
        if (result == null) {
            return null;
        }
        return result.mSession;
    }

    public Integer getSessionId(GeckoSession aSession) {
        for (Map.Entry<Integer, State> entry : mSessions.entrySet()) {
            if (entry.getValue().mSession == aSession) {
                return  entry.getKey();
            }
        }
        return null;
    }

    public String getUriFromSession(GeckoSession aSession) {
        Integer sessionId = getSessionId(aSession);
        if (sessionId == null) {
            return "";
        }
        State state = mSessions.get(sessionId);
        if (state != null) {
            return state.mUri;
        }

        return "";
    }

    public List<Integer> getSessions() {
        return new ArrayList<>(mSessions.keySet());
    }

    public List<Integer> getSessionsByPrivateMode(boolean aUsingPrivateMode) {
        ArrayList<Integer> result = new ArrayList<>();
        for (Integer sessionId : mSessions.keySet()) {
            GeckoSession session = getSession(sessionId);
            if (session != null && session.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE) == aUsingPrivateMode) {
                result.add(sessionId);
            }
        }
        return result;
    }

    public void setCurrentSession(int aId) {
        if (mRuntime == null) {
            Log.e(LOGTAG, "SessionStore failed to set current session, GeckoRuntime is null");
            return;
        }

        Log.d(LOGTAG, "Creating session: " + aId);

        if (mCurrentSession != null) {
            mCurrentSession.setActive(false);
        }

        mCurrentSession = null;
        State state = mSessions.get(aId);
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

        if (mCurrentSession != null)
            mCurrentSession.setActive(true);
    }

    public void setRegion(String aRegion) {
        Log.d(LOGTAG, "SessionStore setRegion: " + aRegion);
        aRegion = aRegion != null ? aRegion.toLowerCase() : "worldwide";
        mRegion = aRegion;
    }

    public String getHomeUri() {
        String result = SessionStore.HOME_WITHOUT_REGION_ORIGIN;
        if (mRegion != null) {
            result = SessionStore.HOME_WITHOUT_REGION_ORIGIN + "/?region=" + mRegion;
        }
        return result;
    }

    public Boolean isHomeUri(String aUri) {
        return aUri != null && aUri.toLowerCase().startsWith(SessionStore.HOME_WITHOUT_REGION_ORIGIN);
    }

    public String getCurrentUri() {
        String result = "";
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mUri;
        }
        return result;
    }

    public boolean isInputActive(int aSessionId) {
        SessionStore.State state = mSessions.get(aSessionId);
        if (state != null) {
            return state.mIsInputActive;
        }
        return false;
    }

    public boolean canGoBack() {
        if (mCurrentSession == null) {
            return false;
        }

        State state = mSessions.get(mCurrentSession.hashCode());
        if (state != null) {
            return state.mCanGoBack;
        }

        return false;
    }

    public void goBack() {
        if (mCurrentSession == null) {
             return;
        }
        if (isInFullScreen()) {
            exitFullScreen();
        } else {
            mCurrentSession.goBack();
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

    public boolean isInFullScreen() {
        if (mCurrentSession == null) {
            return false;
        }

        State state = mSessions.get(mCurrentSession.hashCode());
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
            return NO_SESSION_ID;
        }
        return mCurrentSession.hashCode();
    }

    public void setPermissionDelegate(GeckoSession.PermissionDelegate aDelegate) {
        mPermissionDelegate = aDelegate;
        for (HashMap.Entry<Integer, State> entry : mSessions.entrySet()) {
            entry.getValue().mSession.setPermissionDelegate(aDelegate);
        }
    }

    private void vrPrefsWorkAround(Context aContext) {
        File path = GeckoProfile.initFromArgs(aContext, null).getDir();
        String prefFileName = path.getAbsolutePath() + File.separator + "user.js";
        Log.i(LOGTAG, "Creating file: " + prefFileName);
        try (FileOutputStream out = new FileOutputStream(prefFileName)) {
            out.write("pref(\"dom.vr.enabled\", true);\n".getBytes());
            out.write("pref(\"dom.vr.external.enabled\", true);\n".getBytes());
            out.write("pref(\"webgl.enable-surface-texture\", true);\n".getBytes());
            out.write("pref(\"apz.allow_double_tap_zooming\", false);\n".getBytes());
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "Unable to create file: '" + prefFileName + "' got exception: " + e.toString());
        } catch (IOException e) {
            Log.e(LOGTAG, "Unable to write file: '" + prefFileName + "' got exception: " + e.toString());
        }
    }

    public void switchPrivateMode() {
        if (mCurrentSession == null)
            return;

        boolean isPrivateMode  = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (!isPrivateMode) {
            if (mPreviousSessionId == SessionStore.NO_SESSION_ID) {
                mPreviousSessionId = getCurrentSessionId();

                SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
                settings.privateMode = true;
                int id = createSession(settings);
                setCurrentSession(id);

                mLastUri = "about:privatebrowsing";
                InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
                getCurrentSession().loadData(InternalPages.createAboutPage(mContext, pageResources), "text/html");

            } else {
                int sessionId = getCurrentSessionId();
                setCurrentSession(mPreviousSessionId);
                mPreviousSessionId = sessionId;
            }

        } else {
            int sessionId = getCurrentSessionId();
            setCurrentSession(mPreviousSessionId);
            mPreviousSessionId = sessionId;
        }
    }

    public void setUaMode(int mode) {
        if (mCurrentSession != null) {
            mCurrentSession.getSettings().setInt(GeckoSessionSettings.USER_AGENT_MODE, mode);
            mCurrentSession.reload();
        }
    }

    public void exitPrivateMode() {
        if (mCurrentSession == null)
            return;

        boolean isPrivateMode  = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPrivateMode) {
            int privateSessionId = getCurrentSessionId();
            setCurrentSession(mPreviousSessionId);
            mPreviousSessionId = SessionStore.NO_SESSION_ID;

            // Remove current private_mode session
            removeSession(privateSessionId);

            // Remove all the stacked private_mode sessions
            for (Iterator<Integer> it = mPrivateSessionsStack.iterator(); it.hasNext();) {
                int sessionId = it.next();
                removeSession(sessionId);
            }
            mPrivateSessionsStack.clear();
        }
    }

    public boolean isCurrentSessionPrivate() {
        if (mCurrentSession != null)
            return mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);

        return false;
    }

    public boolean canUnstackSession() {
        Integer prevSessionId = peekSession();

        return prevSessionId != null;
    }

    public void unstackSession() {
        Integer prevSessionId = popSession();
        if (prevSessionId != null) {
            int currentSession = getCurrentSessionId();
            setCurrentSession(prevSessionId);
            removeSession(currentSession);
        }
    }

    public void setConsoleOutputEnabled(boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setConsoleOutputEnabled(enabled);
        }
    }

    public void setMaxWindowSize(int width, int height) {
        GeckoAppShell.setScreenSizeOverride(new Rect(0, 0, width, height));
    }

    public void setMultiprocess(final boolean enabled) {
        if (mCurrentSession != null) {
            final GeckoResult<GeckoSession.SessionState> state = mCurrentSession.saveState();
            state.then(new GeckoResult.OnValueListener<GeckoSession.SessionState, Object>() {
                @Nullable
                @Override
                public GeckoResult<Object> onValue(@Nullable GeckoSession.SessionState value) throws Throwable {
                    if (value != null) {
                        mCurrentSession.stop();
                        mCurrentSession.close();

                        int oldSessionId = getCurrentSessionId();
                        int sessionId = createSession();
                        GeckoSession session = getSession(sessionId);
                        session.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, enabled);
                        session.restoreState(value);
                        setCurrentSession(sessionId);
                        removeSession(oldSessionId);
                    }

                    return null;
                }
            }, new GeckoResult.OnExceptionListener<Object>() {
                @Nullable
                @Override
                public GeckoResult<Object> onException(@NonNull Throwable exception) throws Throwable {
                    Log.e(LOGTAG, "State saving exception while setting multiprocess mode: " + exception.getLocalizedMessage());
                    return null;
                }
            });
        }
    }

    public void setRemoteDebugging(final boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setRemoteDebuggingEnabled(enabled);
        }
    }

    @Override
    public void onLocationChange(GeckoSession aSession, String aUri) {
        Log.d(LOGTAG, "SessionStore onLocationChange: " + aUri);
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            Log.e(LOGTAG, "Unknown session!");
            return;
        }

        if (isLocalPage(aUri))
            aUri = mLastUri;

        state.mUri = aUri;

        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            listener.onLocationChange(aSession, aUri);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession aSession, boolean aCanGoBack) {
        Log.d(LOGTAG, "SessionStore onCanGoBack: " + (aCanGoBack ? "true" : "false"));
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mCanGoBack = aCanGoBack;
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            listener.onCanGoBack(aSession, aCanGoBack);
        }
    }

    @Override
    public void onCanGoForward(GeckoSession aSession, boolean aCanGoForward) {
        Log.d(LOGTAG, "SessionStore onCanGoForward: " + (aCanGoForward ? "true" : "false"));
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mCanGoForward = aCanGoForward;
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            listener.onCanGoForward(aSession, aCanGoForward);
        }
    }

    @Nullable
    public GeckoResult<Boolean> onLoadRequest(@NonNull GeckoSession aSession,
                                       @NonNull String aUri,
                                       @TargetWindow int target,
                                       @LoadRequestFlags int flags) {
        GeckoResult<Boolean> result = new GeckoResult<>();

        if (aUri.equalsIgnoreCase("about:privatebrowsing")) {
            switchPrivateMode();
            result.complete(true);

        } else {
            result.complete(false);
        }

        return result;
    }

    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession aSession, @NonNull String aUri) {
        Log.d(LOGTAG, "SessionStore onNewSession: " + aUri);

        pushSession(getCurrentSessionId());

        int sessionId;
        boolean isPreviousPrivateMode = mCurrentSession.getSettings().getBoolean(GeckoSessionSettings.USE_PRIVATE_MODE);
        if (isPreviousPrivateMode) {
            SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
            settings.privateMode = true;
            sessionId = createSession(settings);

        } else {
            sessionId = createSession();
        }

        mCurrentSession = null;
        State state = mSessions.get(sessionId);
        if (state != null) {
            mCurrentSession = state.mSession;
            for (SessionChangeListener listener : mSessionChangeListeners) {
                listener.onCurrentSessionChange(mCurrentSession, sessionId);
            }
        }
        dumpAllState(mCurrentSession);

        return GeckoResult.fromValue(getSession(sessionId));
    }

    @Override
    public GeckoResult<String> onLoadError(GeckoSession session, String uri, int category, int error) {
        Log.d(LOGTAG, "SessionStore onLoadError: " + uri);

        mLastUri = uri;
        InternalPages.PageResources pageResources = errorPageResourcesForCategory(category);
        session.loadData(InternalPages.createErrorPage(mContext, uri, pageResources, category, error), "text/html");
        return null;
    }

    // Progress Listener
    @Override
    public void onPageStart(GeckoSession aSession, String aUri) {
        Log.d(LOGTAG, "SessionStore onPageStart");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mIsLoading = true;
        for (GeckoSession.ProgressDelegate listener: mProgressListeners) {
            listener.onPageStart(aSession, aUri);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        Log.d(LOGTAG, "SessionStore onPageStop");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mIsLoading = false;
        for (GeckoSession.ProgressDelegate listener: mProgressListeners) {
            listener.onPageStop(aSession, b);
        }
    }

    @Override
    public void onProgressChange(GeckoSession session, int progress) {

    }

    @Override
    public void onSecurityChange(GeckoSession aSession, SecurityInformation aInformation) {
        Log.d(LOGTAG, "SessionStore onPageStop");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mSecurityInformation = aInformation;
        for (GeckoSession.ProgressDelegate listener: mProgressListeners) {
            listener.onSecurityChange(aSession, aInformation);
        }
    }

    // Content Delegate
    @Override
    public void onTitleChange(GeckoSession aSession, String aTitle) {
        Log.d(LOGTAG, "SessionStore onTitleChange");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mTitle = aTitle;
        for (GeckoSession.ContentDelegate listener: mContentListeners) {
            listener.onTitleChange(aSession, aTitle);
        }
    }

    @Override
    public void onFocusRequest(GeckoSession aSession) {
        Log.d(LOGTAG, "SessionStore onFocusRequest");
    }

    @Override
    public void onCloseRequest(GeckoSession aSession) {
        int sessionId = getSessionId(aSession);
        if (getCurrentSessionId() == sessionId) {
            unstackSession();
        }
    }

    @Override
    public void onFullScreen(GeckoSession aSession, boolean aFullScreen) {
        Log.d(LOGTAG, "SessionStore onFullScreen");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mFullScreen = aFullScreen;
        for (GeckoSession.ContentDelegate listener: mContentListeners) {
            listener.onFullScreen(aSession, aFullScreen);
        }
    }

    @Override
    public void onContextMenu(GeckoSession aSession, int i, int i1, String s, int i2, String s1) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }

    @Override
    public void onCrash(GeckoSession session) {

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
        SessionStore.State state = mSessions.get(getSessionId(aSession));
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
        SessionStore.State state = mSessions.get(getSessionId(aSession));
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
    public void notifyAutoFill(GeckoSession session, int notification, int virtualId) {

    }

    @Override
    public void onTrackerBlocked(GeckoSession session, String uri, int categories) {
        if ((categories & GeckoSession.TrackingProtectionDelegate.CATEGORY_AD) != 0) {
          Log.i(LOGTAG, "Blocking Ad: " + uri);
        }

        if ((categories & GeckoSession.TrackingProtectionDelegate.CATEGORY_ANALYTIC) != 0) {
            Log.i(LOGTAG, "Blocking Analytic: " + uri);
        }

        if ((categories & GeckoSession.TrackingProtectionDelegate.CATEGORY_CONTENT) != 0) {
            Log.i(LOGTAG, "Blocking Content: " + uri);
        }

        if ((categories & GeckoSession.TrackingProtectionDelegate.CATEGORY_SOCIAL) != 0) {
            Log.i(LOGTAG, "Blocking Social: " + uri);
        }
    }

}
