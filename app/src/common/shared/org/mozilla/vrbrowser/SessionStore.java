/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.content.Context;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.HashMap;
import java.util.LinkedList;

import android.util.Log;

public class SessionStore implements GeckoSession.NavigationDelegate {
    private static SessionStore mInstance;
    private static final String LOGTAG = "VRB";
    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }

    private LinkedList<GeckoSession.NavigationDelegate> mListeners;

    class State {
        boolean mCanGoBack;
        boolean mCanGoForward;
        String mUri;
        GeckoSession mSession;
    }

    private GeckoSession mCurrentSession;
    private HashMap<Integer, State> mSessions;

    private SessionStore() {
        mListeners = new LinkedList<>();
        mSessions = new HashMap<>();
    }

    private void dumpAllState() {
        for (GeckoSession.NavigationDelegate listener: mListeners) {
            dumpState(listener);
        }
    }

    private void dumpState(GeckoSession.NavigationDelegate aListener) {
        boolean canGoForward = false;
        boolean canGoBack = false;
        String uri = "";
        if (mCurrentSession != null) {
            State state = mSessions.get(mCurrentSession.hashCode());
            if (state != null) {
                canGoBack = state.mCanGoBack;
                canGoForward = state.mCanGoForward;
                uri = state.mUri;
            }
        }
        aListener.onCanGoBack(mCurrentSession, canGoBack);
        aListener.onCanGoForward(mCurrentSession, canGoForward);
        aListener.onLocationChange(mCurrentSession, uri);
    }

    public void addListener(GeckoSession.NavigationDelegate aListener) {
        mListeners.add(aListener);
        dumpState(aListener);
    }

    public void removeListener(GeckoSession.NavigationDelegate aListener) {
        mListeners.remove(aListener);
    }

    public int createSession() {
        State state = new State();
        state.mSession = new GeckoSession();
        int result = state.mSession.hashCode();
        mSessions.put(result, state);
        state.mSession.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, false);
        state.mSession.enableTrackingProtection(GeckoSession.TrackingProtectionDelegate.CATEGORY_AD |
                GeckoSession.TrackingProtectionDelegate.CATEGORY_ANALYTIC |
                GeckoSession.TrackingProtectionDelegate.CATEGORY_SOCIAL |
                GeckoSession.TrackingProtectionDelegate.CATEGORY_CONTENT);
        state.mSession.setNavigationDelegate(this);
        return result;
    }

    public GeckoSession getSession(int aId) {
        State result = mSessions.get(aId);
        if (result == null) {
            return null;
        }
        return result.mSession;
    }

    public void setCurrentSession(int aId, Context aContext) {
        mCurrentSession = null;
        State state = mSessions.get(aId);
        if (state != null) {
            mCurrentSession = state.mSession;
            if (!mCurrentSession.isOpen()) {
                mCurrentSession.openWindow(aContext);
            }
        }
        dumpAllState();
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

    @Override
    public void onLocationChange(GeckoSession aSession, String aUri) {
        Log.e(LOGTAG, "SessionStore onLocationChange: " + aUri);
        State state = mSessions.get(aSession.hashCode());
        if (state == null) {
            return;
        }
        state.mUri = aUri;
        for (GeckoSession.NavigationDelegate listener: mListeners) {
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
        for (GeckoSession.NavigationDelegate listener: mListeners) {
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
        for (GeckoSession.NavigationDelegate listener: mListeners) {
            listener.onCanGoForward(aSession, aCanGoForward);
        }
    }

    @Override
    public boolean onLoadUri(GeckoSession aSession, String aUri, TargetWindow aTarget) {
        return false;
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUri, GeckoSession.Response<GeckoSession> aResponse) {

    }
}
