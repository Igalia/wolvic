/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.media.MediaCas;
import android.util.Log;

public class SessionStore implements GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate {
    private static SessionStore mInstance;
    private static final String LOGTAG = "VRB";
    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }
    public static final String DEFAULT_URL = "resource://android/assets/html/index.html";

    private LinkedList<GeckoSession.NavigationDelegate> mNavigationListeners;
    private LinkedList<GeckoSession.ProgressDelegate> mProgressListeners;
    private LinkedList<GeckoSession.ContentDelegate> mContentListeners;
    private LinkedList<SessionChangeListener> mSessionChangeListeners;


    public interface SessionChangeListener {
        void onNewSession(GeckoSession aSession, int aId);
        void onRemoveSession(GeckoSession aSession, int aId);
        void onCurrentSessionChange(GeckoSession aSession, int aId);
    }

    class State {
        boolean mCanGoBack;
        boolean mCanGoForward;
        boolean mIsLoading;
        GeckoSession.ProgressDelegate.SecurityInformation mSecurityInformation;
        String mUri;
        String mTitle;
        GeckoSession mSession;
    }

    private GeckoRuntime mRuntime;
    private GeckoSession mCurrentSession;
    private HashMap<Integer, State> mSessions;
    private boolean mShowSoftInputOnFocus = true;

    private SessionStore() {
        mNavigationListeners = new LinkedList<>();
        mProgressListeners = new LinkedList<>();
        mContentListeners = new LinkedList<>();
        mSessionChangeListeners = new LinkedList<>();

        mSessions = new LinkedHashMap<>();
    }

    public void setContext(Context aContext) {
        if (mRuntime == null) {
            mRuntime = GeckoRuntime.create(aContext);
        } else {
            mRuntime.attachTo(aContext);
        }
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

    public static class SessionSettings {
        public boolean multiprocess = false;
        public boolean privateMode = false;
        public boolean trackingProtection = true;
    }

    public int createSession() {
        return createSession(new SessionSettings());
    }
    public int createSession(SessionSettings aSettings) {
        State state = new State();
        state.mSession = new GeckoSession();
        state.mSession.getTextInput().setShowSoftInputOnFocus(mShowSoftInputOnFocus);

        int result = state.mSession.hashCode();
        mSessions.put(result, state);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, aSettings.multiprocess);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_PRIVATE_MODE, aSettings.privateMode);
        if (aSettings.trackingProtection) {
            state.mSession.enableTrackingProtection(GeckoSession.TrackingProtectionDelegate.CATEGORY_AD |
                    GeckoSession.TrackingProtectionDelegate.CATEGORY_ANALYTIC |
                    GeckoSession.TrackingProtectionDelegate.CATEGORY_SOCIAL |
                    GeckoSession.TrackingProtectionDelegate.CATEGORY_CONTENT);
        }
        state.mSession.setNavigationDelegate(this);
        state.mSession.setProgressDelegate(this);
        state.mSession.setContentDelegate(this);
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
            mSessions.remove(aSessionId);
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onRemoveSession(session, aSessionId);
            }
            session.setActive(false);
            session.stop();
        }
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
        mCurrentSession.goBack();
    }

    public void goForward() {
        if (mCurrentSession == null) {
            return;
        }
        mCurrentSession.goForward();
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
        mCurrentSession.loadUri(aUri);
    }

    public GeckoSession getCurrentSession() {
        return mCurrentSession;
    }

    public int getCurrentSessionId() {
        if (mCurrentSession == null) {
            return -1;
        }
        return mCurrentSession.hashCode();
    }

    public void setShowSoftInputOnFocus(final boolean aShowSoftInputOnFocus) {
        mShowSoftInputOnFocus = aShowSoftInputOnFocus;
        for (HashMap.Entry<Integer, State> entry : mSessions.entrySet()) {
            entry.getValue().mSession.getTextInput().setShowSoftInputOnFocus(mShowSoftInputOnFocus);
        }
    }

    @Override
    public void onLocationChange(GeckoSession aSession, String aUri) {
        Log.e(LOGTAG, "SessionStore onLocationChange: " + aUri);
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            Log.e(LOGTAG, "Unknown session!");
            return;
        }
        state.mUri = aUri;
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            listener.onLocationChange(aSession, aUri);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession aSession, boolean aCanGoBack) {
        Log.e(LOGTAG, "SessionStore onCanGoBack: " + (aCanGoBack ? "TRUE" : "FALSE"));
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
        Log.e(LOGTAG, "SessionStore onCanGoForward: " + (aCanGoForward ? "TRUE" : "FALSE"));
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mCanGoForward = aCanGoForward;
        for (GeckoSession.NavigationDelegate listener: mNavigationListeners) {
            listener.onCanGoForward(aSession, aCanGoForward);
        }
    }

    @Override
    public void onLoadRequest(GeckoSession aSession, String aUri, int aTarget, GeckoSession.Response<Boolean> aResponse) {
        aResponse.respond(null);
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUri, GeckoSession.Response<GeckoSession> aResponse) {
        Log.e(LOGTAG,"Got onNewSession: " + aUri);
        int sessionId = createSession();
        mCurrentSession = null;
        State state = mSessions.get(sessionId);
        if (state != null) {
            mCurrentSession = state.mSession;
            for (SessionChangeListener listener: mSessionChangeListeners) {
                listener.onCurrentSessionChange(mCurrentSession, sessionId);
            }
        }
        dumpAllState(mCurrentSession);
        aResponse.respond(getSession(sessionId));
    }

    // Progress Listener
    @Override
    public void onPageStart(GeckoSession aSession, String aUri) {
        Log.e(LOGTAG, "SessionStore onPageStart: " + aUri);
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
        Log.e(LOGTAG, "SessionStore onPageStop");
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
    public void onSecurityChange(GeckoSession aSession, SecurityInformation aInformation) {
        Log.e(LOGTAG, "SessionStore onPageStop");
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
        Log.e(LOGTAG, "SessionStore onTitleChange");
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

    }

    @Override
    public void onCloseRequest(GeckoSession aSession) {

    }

    @Override
    public void onFullScreen(GeckoSession aSession, boolean b) {

    }

    @Override
    public void onContextMenu(GeckoSession aSession, int i, int i1, String s, int i2, String s1) {

    }

    @Override
    public void onExternalResponse(GeckoSession session, GeckoSession.WebResponseInfo response) {

    }
}
