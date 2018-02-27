/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.concurrent.CopyOnWriteArrayList;

public class BrowserSession implements GeckoSession.NavigationListener {
    private GeckoSession mSession;
    private String mUri;
    private boolean mCanGoBack;
    private boolean mCanGoForward;
    private CopyOnWriteArrayList<GeckoSession.NavigationListener> mNavigationListeners;

    public BrowserSession(GeckoSession aSession) {
        mSession = aSession;
        mNavigationListeners = new CopyOnWriteArrayList<>();

        // Remove once e10s issues have been resolved.
        mSession.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, false);
        mSession.setNavigationListener(this);
    }

    public String getUrl() {
        return mUri;
    }

    public boolean canGoBack() {
        return mCanGoBack;
    }

    public boolean canGoForward() {
        return mCanGoForward;
    }

    public GeckoSession getGeckoSession() {
        return mSession;
    }

    public void loadUri(String aUri) {
        mSession.loadUri(aUri);
        mUri = aUri;
    }

    public void addNavigationListener(GeckoSession.NavigationListener aListener) {
        if (!mNavigationListeners.contains(aListener)) {
            mNavigationListeners.add(aListener);
        }
    }

    public void removeNavigationListener(GeckoSession.NavigationListener aListener) {
        mNavigationListeners.remove(aListener);
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUrl, GeckoSession.Response<GeckoSession> aResponse) {

    }

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        mUri = url;
        for (GeckoSession.NavigationListener listener: mNavigationListeners) {
            listener.onLocationChange(session, url);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {
        mCanGoBack = canGoBack;
        for (GeckoSession.NavigationListener listener: mNavigationListeners) {
            listener.onCanGoBack(session, canGoBack);
        }
    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {
        mCanGoForward = canGoForward;
        for (GeckoSession.NavigationListener listener: mNavigationListeners) {
            listener.onCanGoForward(session, canGoForward);
        }
    }

    @Override
    public boolean onLoadUri(GeckoSession session, String uri, TargetWindow where) {
        mUri = uri;
        boolean result = false;
        for (GeckoSession.NavigationListener listener: mNavigationListeners) {
            result |= listener.onLoadUri(session, uri, where);
        }
        return result;
    }

}
