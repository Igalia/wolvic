/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import org.mozilla.gecko.GeckoProfile;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.SessionState;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.MediaElement;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;
import org.mozilla.vrbrowser.geolocation.GeolocationData;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.utils.InternalPages;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.mozilla.vrbrowser.utils.ServoUtils.createServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isInstanceOfServoSession;
import static org.mozilla.vrbrowser.utils.ServoUtils.isServoAvailable;

public class SessionStore implements ContentBlocking.Delegate, GeckoSession.NavigationDelegate,
        GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate, GeckoSession.TextInputDelegate,
        GeckoSession.PromptDelegate, GeckoSession.MediaDelegate, SharedPreferences.OnSharedPreferenceChangeListener {

    private static SessionStore mInstance;
    private static final String LOGTAG = "VRB";
    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }
    // You can test a local file using: "resource://android/assets/webvr/index.html"
    public static final String PRIVATE_BROWSING_URI = "about:privatebrowsing";
    public static final int NO_SESSION_ID = -1;
    private static final String[] WEB_EXTENSIONS = new String[] {
      "webcompat_vimeo",
      "webcompat_youtube"
    };

    private LinkedList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private LinkedList<GeckoSession.ProgressDelegate> mProgressListeners;
    private LinkedList<GeckoSession.ContentDelegate> mContentListeners;
    private LinkedList<SessionChangeListener> mSessionChangeListeners;
    private LinkedList<GeckoSession.TextInputDelegate> mTextInputListeners;
    private LinkedList<GeckoSession.PromptDelegate> mPromptListeners;
    private LinkedList<VideoAvailabilityListener> mVideoAvailabilityListeners;
    private UserAgentOverride mUserAgentOverride;

    public interface SessionChangeListener {
        void onNewSession(GeckoSession aSession, int aId);
        void onRemoveSession(GeckoSession aSession, int aId);
        void onCurrentSessionChange(GeckoSession aSession, int aId);
    }

    public interface VideoAvailabilityListener {
        void onVideoAvailabilityChanged(boolean aVideosAvailable);
    }

    class SessionSettings {
        boolean multiprocess = SettingsStore.getInstance(mContext).isMultiprocessEnabled();
        boolean privateMode = false;
        boolean trackingProtection = SettingsStore.getInstance(mContext).isTrackingProtectionEnabled();
        boolean suspendMediaWhenInactive = true;
        int userAgentMode = SettingsStore.getInstance(mContext).getUaMode();
        boolean servo = false;
    }

    class State {
        boolean mCanGoBack;
        boolean mCanGoForward;
        boolean mIsLoading;
        boolean mIsInputActive;
        GeckoSession.ProgressDelegate.SecurityInformation mSecurityInformation;
        String mUri;
        String mPreviousUri;
        String mTitle;
        boolean mFullScreen;
        GeckoSession mSession;
        SessionSettings mSettings;
        ArrayList<Media> mMediaElements = new ArrayList<>();
        SessionState mSessionState;
    }

    private GeckoRuntime mRuntime;
    private GeckoSession mCurrentSession;
    private HashMap<Integer, State> mSessions;
    private Deque<Integer> mSessionsStack;
    private Deque<Integer> mPrivateSessionsStack;
    private GeckoSession.PermissionDelegate mPermissionDelegate;
    private int mPreviousSessionId = SessionStore.NO_SESSION_ID;
    private int mPreviousGeckoSessionId = SessionStore.NO_SESSION_ID;
    private String mRegion;
    private Context mContext;
    private SharedPreferences mPrefs;
    private BookmarksStore mBookmarksStore;

    private SessionStore() {
        mSessions = new LinkedHashMap<>();
        mSessionsStack = new ArrayDeque<>();
        mPrivateSessionsStack = new ArrayDeque<>();
    }

    public void registerListeners() {
        mNavigationListeners = new LinkedList<>();
        mProgressListeners = new LinkedList<>();
        mContentListeners = new LinkedList<>();
        mSessionChangeListeners = new LinkedList<>();
        mTextInputListeners = new LinkedList<>();
        mPromptListeners = new LinkedList<>();
        mVideoAvailabilityListeners = new LinkedList<>();

        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }
    }

    public void unregisterListeners() {
        mNavigationListeners.clear();
        mProgressListeners.clear();
        mContentListeners.clear();
        mSessionChangeListeners.clear();
        mTextInputListeners.clear();
        mVideoAvailabilityListeners.clear();

        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        if (mBookmarksStore != null) {
            mBookmarksStore.removeAllListeners();
        }
    }

    public void setContext(Context aContext, Bundle aExtras) {
        if (mRuntime == null) {
            // FIXME: Once GeckoView has a prefs API
            vrPrefsWorkAround(aContext, aExtras);
            GeckoRuntimeSettings.Builder runtimeSettingsBuilder = new GeckoRuntimeSettings.Builder();
            runtimeSettingsBuilder.crashHandler(CrashReporterService.class);
            runtimeSettingsBuilder.contentBlocking((new ContentBlocking.Settings.Builder())
                .categories(ContentBlocking.AT_AD | ContentBlocking.AT_SOCIAL | ContentBlocking.AT_ANALYTIC)
                .build());
            runtimeSettingsBuilder.consoleOutput(SettingsStore.getInstance(aContext).isConsoleLogsEnabled());
            runtimeSettingsBuilder.displayDensityOverride(SettingsStore.getInstance(aContext).getDisplayDensity());
            runtimeSettingsBuilder.remoteDebuggingEnabled(SettingsStore.getInstance(aContext).isRemoteDebuggingEnabled());
            runtimeSettingsBuilder.displayDpiOverride(SettingsStore.getInstance(aContext).getDisplayDpi());
            runtimeSettingsBuilder.screenSizeOverride(SettingsStore.getInstance(aContext).getMaxWindowWidth(),
                    SettingsStore.getInstance(aContext).getMaxWindowHeight());
            if (SettingsStore.getInstance(aContext).getTransparentBorderWidth() > 0) {
                runtimeSettingsBuilder.useMaxScreenDepth(true);
            }

            if (BuildConfig.DEBUG) {
                runtimeSettingsBuilder.arguments(new String[] { "-purgecaches" });
            }

            mRuntime = GeckoRuntime.create(aContext, runtimeSettingsBuilder.build());
            for (String extension: WEB_EXTENSIONS) {
                String path = "resource://android/assets/web_extensions/" + extension + "/";
                mRuntime.registerWebExtension(new WebExtension(path));
            }

        } else {
            mRuntime.attachTo(aContext);
        }

        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mBookmarksStore = new BookmarksStore(mContext);
        if (mUserAgentOverride == null) {
            mUserAgentOverride = new UserAgentOverride();
            mUserAgentOverride.loadOverridesFromAssets((Activity)aContext, aContext.getString(R.string.user_agent_override_file));
        }
    }

    public BookmarksStore getBookmarkStore() {
        return mBookmarksStore;
    }

    public void dumpAllState(Integer sessionId) {
        dumpAllState(getSession(sessionId));
    }

    private boolean isLocalizedContent(@Nullable String url) {
        return url != null && (url.startsWith("about:") || url.startsWith("data:"));
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
        } else {
            aListener.onPageStop(aSession, true);
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

    public void addPromptListener(GeckoSession.PromptDelegate aListener) {
        mPromptListeners.add(aListener);
    }

    public void removePromptListener(GeckoSession.PromptDelegate aListener) {
        mPromptListeners.remove(aListener);
    }

    public void addVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.add(aListener);
    }

    public void removeVideoAvailabilityListener(VideoAvailabilityListener aListener) {
        mVideoAvailabilityListeners.remove(aListener);
    }

    public int createSession() {
        return createSession(false);
    }

    public int createSession(boolean isPrivate) {
        SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
        if (isPrivate) {
            settings.privateMode = true;
        }

        return createSession(settings);
    }

    int createSession(SessionSettings aSettings) {
        State state = new State();
        state.mSettings = aSettings;

        GeckoSessionSettings geckoSettings = new GeckoSessionSettings.Builder()
            .useMultiprocess(aSettings.multiprocess)
            .usePrivateMode(aSettings.privateMode)
            .useTrackingProtection(aSettings.trackingProtection)
            .build();

        if (aSettings.servo) {
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

        state.mSession.getSettings().setSuspendMediaWhenInactive(aSettings.suspendMediaWhenInactive);
        state.mSession.getSettings().setUserAgentMode(aSettings.userAgentMode);
        state.mSession.setNavigationDelegate(this);
        state.mSession.setProgressDelegate(this);
        state.mSession.setPromptDelegate(this);
        state.mSession.setContentDelegate(this);
        state.mSession.getTextInput().setDelegate(this);
        state.mSession.setPermissionDelegate(mPermissionDelegate);
        state.mSession.setContentBlockingDelegate(this);
        state.mSession.setMediaDelegate(this);
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
            session.setPromptDelegate(null);
            session.setPermissionDelegate(null);
            session.setContentBlockingDelegate(null);
            session.setMediaDelegate(null);
            mSessions.remove(aSessionId);
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onRemoveSession(session, aSessionId);
            }
            session.setActive(false);
            session.stop();
            session.close();
        }
    }

    private void pushSession(int aSessionId) {
        boolean isPrivateMode  = mCurrentSession.getSettings().getUsePrivateMode();
        if (isPrivateMode)
            mPrivateSessionsStack.push(aSessionId);
        else
            mSessionsStack.push(aSessionId);
    }

    private Integer popSession() {
        boolean isPrivateMode  = mCurrentSession.getSettings().getUsePrivateMode();
        if (isPrivateMode)
            return mPrivateSessionsStack.pop();
        else
            return mSessionsStack.pop();
    }

    private Integer peekSession() {
      boolean isPrivateMode  = mCurrentSession.getSettings().getUsePrivateMode();
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
            if (session != null && session.getSettings().getUsePrivateMode() == aUsingPrivateMode) {
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
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mUri;
        }
        return result;
    }

    public String getPreviousUri() {
        String result = "";
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mPreviousUri;
        }
        return result;
    }

    public String getCurrentTitle() {
        String result = "";
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return result;
            }
            result = state.mTitle;
        }
        return result;
    }

    public Media getFullScreenVideo() {
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
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

    public void toggleServo() {
        if (mCurrentSession == null) {
            return;
        }

        Log.v("servo", "toggleServo");

        if (!isInstanceOfServoSession(mCurrentSession)) {
            if (mPreviousGeckoSessionId == SessionStore.NO_SESSION_ID) {
                mPreviousGeckoSessionId = getCurrentSessionId();
                String uri = getCurrentUri();
                SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
                settings.servo = true;
                int id = createSession(settings);
                setCurrentSession(id);
                loadUri(uri);
            } else {
                Log.e(LOGTAG, "Multiple Servo sessions not supported yet.");
            }
        } else {
            removeSession(getCurrentSessionId());
            setCurrentSession(mPreviousGeckoSessionId);
            mPreviousGeckoSessionId = SessionStore.NO_SESSION_ID;
        }
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

    public boolean isInFullScreen(GeckoSession aSession) {
        Integer sessionId = getSessionId(aSession);
        if (sessionId == null) {
            return false;
        }
        State state = mSessions.get(sessionId);
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

    private void vrPrefsWorkAround(Context aContext, Bundle aExtras) {
        File path = GeckoProfile.initFromArgs(aContext, null).getDir();
        String prefFileName = path.getAbsolutePath() + File.separator + "user.js";
        Log.i(LOGTAG, "Creating file: " + prefFileName);
        try (FileOutputStream out = new FileOutputStream(prefFileName)) {
            out.write("pref(\"dom.vr.enabled\", true);\n".getBytes());
            out.write("pref(\"dom.vr.external.enabled\", true);\n".getBytes());
            out.write("pref(\"webgl.enable-surface-texture\", true);\n".getBytes());
            // Enable MultiView draft extension
            out.write("pref(\"webgl.enable-draft-extensions\", true);\n".getBytes());
            out.write("pref(\"apz.allow_double_tap_zooming\", false);\n".getBytes());
            out.write("pref(\"dom.webcomponents.customelements.enabled\", true);\n".getBytes());
            out.write("pref(\"javascript.options.ion\", true);\n".getBytes());
            out.write("pref(\"media.webspeech.synth.enabled\", false);\n".getBytes());
            // Prevent autozoom when giving a form field focus.
            out.write("pref(\"formhelper.autozoom\", false);\n".getBytes());
            // Uncomment this to enable WebRender. WARNING NOT READY FOR USAGE.
            // out.write("pref(\"gfx.webrender.all\", true);\n".getBytes());
            int msaa = SettingsStore.getInstance(aContext).getMSAALevel();
            if (msaa > 0) {
                int msaaLevel = msaa == 2 ? 4 : 2;
                out.write(("pref(\"gl.msaa-level\"," + msaaLevel + ");\n").getBytes());
            }
            addOptionalPref(out, "dom.vr.require-gesture", aExtras);
            addOptionalPref(out, "privacy.reduceTimerPrecision", aExtras);
            if (aExtras != null && aExtras.getBoolean("media.autoplay.enabled", false)) {
                // Enable playing audios without gesture (used for gfx automated testing)
                out.write("pref(\"media.autoplay.enabled.user-gestures-needed\", false);\n".getBytes());
                out.write("pref(\"media.autoplay.enabled.ask-permission\", false);\n".getBytes());
                out.write("pref(\"media.autoplay.default\", 0);\n".getBytes());
            }
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "Unable to create file: '" + prefFileName + "' got exception: " + e.toString());
        } catch (IOException e) {
            Log.e(LOGTAG, "Unable to write file: '" + prefFileName + "' got exception: " + e.toString());
        }
    }

    private void addOptionalPref(FileOutputStream out, String aKey, Bundle aExtras) throws IOException {
        if (aExtras != null && aExtras.containsKey(aKey)) {
            boolean value = aExtras.getBoolean(aKey);
            out.write(String.format("pref(\"%s\", %s);\n", aKey, value ? "true" : "false").getBytes());
        }
    }

    public void switchPrivateMode() {
        if (mCurrentSession == null)
            return;

        boolean isPrivateMode = mCurrentSession.getSettings().getUsePrivateMode();
        if (!isPrivateMode) {
            if (mPreviousSessionId == SessionStore.NO_SESSION_ID) {
                mPreviousSessionId = getCurrentSessionId();

                SessionStore.SessionSettings settings = new SessionStore.SessionSettings();
                settings.privateMode = true;
                int id = createSession(settings);
                setCurrentSession(id);

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
            mCurrentSession.getSettings().setUserAgentMode(mode);
            mCurrentSession.reload();
        }
    }

    public void exitPrivateMode() {
        if (mCurrentSession == null)
            return;

        boolean isPrivateMode  = mCurrentSession.getSettings().getUsePrivateMode();
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
            return mCurrentSession.getSettings().getUsePrivateMode();

        return false;
    }

    public boolean canUnstackSession() {
        Integer prevSessionId = peekSession();

        return prevSessionId != null;
    }

    public void stackSession(int sessionId) {
        pushSession(getCurrentSessionId());
        setCurrentSession(sessionId);

        mCurrentSession = null;
        State state = mSessions.get(sessionId);
        if (state != null) {
            mCurrentSession = state.mSession;
            for (SessionChangeListener listener : mSessionChangeListeners) {
                listener.onCurrentSessionChange(mCurrentSession, sessionId);
            }
        }
        dumpAllState(mCurrentSession);
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
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(
                mContext.getString(R.string.settings_key_max_window_height),
                height);
        editor.putInt(
                mContext.getString(R.string.settings_key_max_window_width),
                width);
        editor.commit();
    }

    public void setServo(final boolean enabled) {
      if (!enabled && mCurrentSession != null && isInstanceOfServoSession(mCurrentSession)) {
        String uri = getCurrentUri();
        int id = createSession();
        setCurrentSession(id);
        loadUri(uri);
      }
    }

    private void recreateSession(SessionStore.SessionSettings aSettings) {
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state == null) {
                return;
            }
            mCurrentSession.stop();
            mCurrentSession.close();

            int oldSessionId = getCurrentSessionId();
            int sessionId = createSession(aSettings);
            GeckoSession session = getSession(sessionId);
            if (state.mSessionState != null) {
                session.restoreState(state.mSessionState);
            }
            setCurrentSession(sessionId);
            removeSession(oldSessionId);
        }
    }

    private State getCurrentState() {
        if (mCurrentSession != null) {
            return mSessions.get(mCurrentSession.hashCode());
        }
        return null;
    }

    public void setMultiprocess(final boolean aEnabled) {
        State state = getCurrentState();
        if (state != null && state.mSettings.multiprocess != aEnabled) {
            state.mSettings.multiprocess = aEnabled;
            recreateSession(state.mSettings);
        }
    }

    public void setTrackingProtection(final boolean aEnabled) {
        State state = getCurrentState();
        if (state != null && state.mSettings.trackingProtection != aEnabled) {
            state.mSettings.trackingProtection = aEnabled;
            recreateSession(state.mSettings);
        }
    }

    public void setRemoteDebugging(final boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setRemoteDebuggingEnabled(enabled);
        }
    }

    public void setAutoplayEnabled(final boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setAutoplayDefault(enabled ?
                    GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED :
                    GeckoRuntimeSettings.AUTOPLAY_DEFAULT_BLOCKED);
        }
    }

    public boolean getAutoplayEnabled() {
        if (mRuntime != null) {
            return mRuntime.getSettings().getAutoplayDefault() == GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED ?
                    true :
                    false;
        }

        return false;
    }

    // NavigationDelegate

    @Override
    public void onLocationChange(@NonNull GeckoSession aSession, String aUri) {
        Log.d(LOGTAG, "SessionStore onLocationChange: " + aUri);
        State state = mSessions.get(aSession.hashCode());
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
        Log.d(LOGTAG, "SessionStore onCanGoBack: " + (aCanGoBack ? "true" : "false"));
        State state = mSessions.get(aSession.hashCode());
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
        Log.d(LOGTAG, "SessionStore onCanGoForward: " + (aCanGoForward ? "true" : "false"));
        State state = mSessions.get(aSession.hashCode());
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
        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();

        String uri = aRequest.uri;

        Log.d(LOGTAG, "onLoadRequest: " + uri);

        String uriOverride = checkYoutubeOverride(uri);
        if (uriOverride != null) {
            aSession.loadUri(uriOverride);
            result.complete(AllowOrDeny.DENY);
            return result;
        }

        if (aSession == mCurrentSession) {
            Log.d(LOGTAG, "Testing for UA override");
            aSession.getSettings().setUserAgentOverride(mUserAgentOverride.lookupOverride(uri));
        }

        if (PRIVATE_BROWSING_URI.equalsIgnoreCase(uri)) {
            switchPrivateMode();
            result.complete(AllowOrDeny.ALLOW);
            return result;
        }

        AtomicInteger count = new AtomicInteger(0);
        AtomicBoolean allowed = new AtomicBoolean(false);
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            GeckoResult<AllowOrDeny> listenerResult = listener.onLoadRequest(aSession, aRequest);
            listenerResult.then(value -> {
                if (AllowOrDeny.ALLOW.equals(value)) {
                    allowed.set(true);
                }
                if (count.getAndIncrement() == mNavigationListeners.size() - 1) {
                    result.complete(allowed.get() ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                }

                return null;
            });
        }

        return result;
    }

    /**
     * 1. Disable YouTube's Polymer layout (which makes YouTube very slow in non-Chrome browsers)
     *    via a query-string parameter in the URL.
     * 2. Rewrite YouTube URLs from `m.youtube.com` -> `youtube.com` (to avoid serving YouTube's
     *    video pages intended for mobile phones, as linked from Google search results).
     */
    private String checkYoutubeOverride(String aUri) {
        try {
            Uri uri = Uri.parse(aUri);
            String hostLower = uri.getHost().toLowerCase();
            if (!hostLower.endsWith(".youtube.com") && !hostLower.endsWith(".youtube-nocookie.com")) {
                return null;
            }

            Uri.Builder uriBuilder = uri.buildUpon();
            Boolean updateUri = false;

            if (!uri.getScheme().equalsIgnoreCase("https")) {
                uriBuilder.scheme("https");
                updateUri = true;
            }
            if (hostLower.startsWith("m.")) {
                uriBuilder.authority(hostLower.replaceFirst("m.", "www."));
                updateUri = true;
            }
            String queryDisablePolymer = uri.getQueryParameter("disable_polymer");
            if (queryDisablePolymer == null) {
                uriBuilder.appendQueryParameter("disable_polymer", "1");
                updateUri = true;
            }

            if (!updateUri) {
                return null;
            }

            return uriBuilder.build().toString();
        } catch (Exception ex) {
            Log.e(LOGTAG, "Unable to construct transformed URL: " + ex.toString());
        }

        return null;
    }

    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession aSession, @NonNull String aUri) {
        Log.d(LOGTAG, "SessionStore onNewSession: " + aUri);

        pushSession(getCurrentSessionId());

        int sessionId;
        boolean isPreviousPrivateMode = mCurrentSession.getSettings().getUsePrivateMode();
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

            if (mCurrentSession != aSession) {
                for (SessionChangeListener listener : mSessionChangeListeners) {
                    listener.onCurrentSessionChange(mCurrentSession, sessionId);
                }
            }
        }
        dumpAllState(mCurrentSession);

        return GeckoResult.fromValue(getSession(sessionId));
    }

    @Override
    public GeckoResult<String> onLoadError(@NonNull GeckoSession session, String uri,  @NonNull WebRequestError error) {
        Log.d(LOGTAG, "SessionStore onLoadError: " + uri);

        return GeckoResult.fromValue(InternalPages.createErrorPage(mContext, uri, error.category, error.code));
    }

    // Progress Listener

    @Override
    public void onPageStart(@NonNull GeckoSession aSession, @NonNull String aUri) {
        Log.d(LOGTAG, "SessionStore onPageStart");
        State state = mSessions.get(aSession.hashCode());
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
        Log.d(LOGTAG, "SessionStore onPageStop");
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }

        state.mIsLoading = false;
        if (!isLocalizedContent(state.mUri)) {
            TelemetryWrapper.uploadPageLoadToHistogram(state.mUri);
        }

        if (mCurrentSession == aSession) {
            for (GeckoSession.ProgressDelegate listener : mProgressListeners) {
                listener.onPageStop(aSession, b);
            }
        }
    }

    @Override
    public void onProgressChange(@NonNull GeckoSession session, int progress) {

    }

    @Override
    public void onSecurityChange(@NonNull GeckoSession aSession, @NonNull SecurityInformation aInformation) {
        Log.d(LOGTAG, "SessionStore onPageStop");
        State state = mSessions.get(aSession.hashCode());
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
                                     @NonNull SessionState aSessionState) {
        State state = mSessions.get(aSession.hashCode());
        if (state != null) {
            state.mSessionState = aSessionState;
        }
    }

    // Content Delegate

    @Override
    public void onTitleChange(@NonNull GeckoSession aSession, String aTitle) {
        Log.d(LOGTAG, "SessionStore onTitleChange");
        State state = mSessions.get(aSession.hashCode());
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
    public void onFocusRequest(@NonNull GeckoSession aSession) {
        Log.d(LOGTAG, "SessionStore onFocusRequest");
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
        Log.d(LOGTAG, "SessionStore onFullScreen");
        State state = mSessions.get(aSession.hashCode());
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
    public void onExternalResponse(@NonNull GeckoSession session, @NonNull GeckoSession.WebResponseInfo response) {

    }

    @Override
    public void onCrash(@NonNull GeckoSession session) {
        Log.e(LOGTAG,"Child crashed. Creating new session");
        int crashedSessionId = SessionStore.get().getCurrentSessionId();
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
    public void notifyAutoFill(@NonNull GeckoSession session, int notification, int virtualId) {

    }

    @Override
    public void onContentBlocked(@NonNull final GeckoSession session, @NonNull final ContentBlocking.BlockEvent event) {
        if ((event.categories & ContentBlocking.AT_AD) != 0) {
          Log.i(LOGTAG, "Blocking Ad: " + event.uri);
        }

        if ((event.categories & ContentBlocking.AT_ANALYTIC) != 0) {
            Log.i(LOGTAG, "Blocking Analytic: " + event.uri);
        }

        if ((event.categories & ContentBlocking.AT_CONTENT) != 0) {
            Log.i(LOGTAG, "Blocking Content: " + event.uri);
        }

        if ((event.categories & ContentBlocking.AT_SOCIAL) != 0) {
            Log.i(LOGTAG, "Blocking Social: " + event.uri);
        }
    }

    // PromptDelegate

    @Override
    public void onAlert(@NonNull GeckoSession session, String title, String msg, @NonNull AlertCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onAlert(session, title, msg, callback);
            }
        }
    }

    @Override
    public void onButtonPrompt(@NonNull GeckoSession session, String title, String msg, String[] btnMsg, @NonNull ButtonCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onButtonPrompt(session, title, msg, btnMsg, callback);
            }
        }
    }

    @Override
    public void onTextPrompt(@NonNull GeckoSession session, String title, String msg, String value, @NonNull TextCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onTextPrompt(session, title, msg, value, callback);
            }
        }
    }

    @Override
    public void onAuthPrompt(@NonNull GeckoSession session, String title, String msg, @NonNull AuthOptions options, @NonNull AuthCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onAuthPrompt(session, title, msg, options, callback);
            }
        }
    }

    @Override
    public void onChoicePrompt(@NonNull GeckoSession session, String title, String msg, int type, @NonNull Choice[] choices, @NonNull ChoiceCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onChoicePrompt(session, title, msg, type, choices, callback);
            }
        }
    }

    @Override
    public void onColorPrompt(@NonNull GeckoSession session, String title, String value, @NonNull TextCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onColorPrompt(session, title, value, callback);
            }
        }
    }

    @Override
    public void onDateTimePrompt(@NonNull GeckoSession session, String title, int type, String value, String min, String max, @NonNull TextCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onDateTimePrompt(session, title, type, value, min, max, callback);
            }
        }
    }

    @Override
    public void onFilePrompt(@NonNull GeckoSession session, String title, int type, String[] mimeTypes, @NonNull FileCallback callback) {
        if (session == mCurrentSession) {
            for (GeckoSession.PromptDelegate listener : mPromptListeners) {
                listener.onFilePrompt(session, title, type, mimeTypes, callback);
            }
        }
    }

    @Override
    public GeckoResult<AllowOrDeny> onPopupRequest(@NonNull final GeckoSession session, final String targetUri) {
        return GeckoResult.fromValue(AllowOrDeny.DENY);
    }

    // MediaDelegate

    @Override
    public void onMediaAdd(@NonNull GeckoSession session, @NonNull MediaElement element) {
        SessionStore.State state = mSessions.get(getSessionId(session));
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
        SessionStore.State state = mSessions.get(getSessionId(session));
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
