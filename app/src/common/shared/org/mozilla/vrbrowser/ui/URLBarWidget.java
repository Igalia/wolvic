/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.R;

public class URLBarWidget extends UIWidget implements GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate {
    private static final String LOGTAG = "VRB";
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageButton mReloadButton;
    private ImageButton mHomeButton;
    private NavigationURLBar mURLBar;
    private boolean mIsLoading;

    public URLBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.navigation_bar, this);
        mBackButton = findViewById(R.id.backButton);
        mForwardButton = findViewById(R.id.forwardButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mHomeButton = findViewById(R.id.homeButton);
        mURLBar = findViewById(R.id.urlBar);

        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().goBack();
            }
        });

        mForwardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().goForward();
            }
        });

        mReloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsLoading) {
                    SessionStore.get().stop();
                } else {
                    SessionStore.get().reload();
                }
            }
        });

        mHomeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
            }
        });

        SessionStore.get().addNavigationListener(this);
        SessionStore.get().addProgressListener(this);
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUrl, GeckoSession.Response<GeckoSession> aResponse) {

    }


    @Override
    public void releaseWidget() {
        super.releaseWidget();
        SessionStore.get().removeNavigationListener(this);
        SessionStore.get().removeProgressListener(this);
    }

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got location change: " + url);
            mURLBar.setURL(url);
            mReloadButton.setEnabled(true);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession aSession, boolean canGoBack) {
        if (mBackButton != null) {
            Log.e(LOGTAG, "Got onCanGoBack: " + (canGoBack ? "TRUE" : "FALSE"));
            mBackButton.setEnabled(canGoBack);
            mBackButton.setClickable(canGoBack);
        }
    }

    @Override
    public void onCanGoForward(GeckoSession aSession, boolean canGoForward) {
        if (mForwardButton != null) {
            Log.e(LOGTAG, "Got onCanGoForward: " + (canGoForward ? "TRUE" : "FALSE"));
            mForwardButton.setEnabled(canGoForward);
            mForwardButton.setClickable(canGoForward);
        }
    }

    @Override
    public boolean onLoadRequest(GeckoSession session, String uri, int target) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onLoadUri: " + uri);
            mURLBar.setURL(uri);
        }
        return false;
    }

    // Progress Listener

    @Override
    public void onPageStart(GeckoSession aSession, String aUri) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onPageStart: " + aUri);
            mURLBar.setURL(aUri);
            mURLBar.setIsLoading(true);
        }
        mIsLoading = true;
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.icon_exit_normal);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onPageStop");
            mURLBar.setIsLoading(false);
        }
        mIsLoading = false;
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_reload);
        }
    }

    @Override
    public void onSecurityChange(GeckoSession geckoSession, SecurityInformation securityInformation) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onSecurityChange: " + securityInformation.isSecure);
            mURLBar.setIsInsecure(!securityInformation.isSecure);
        }
    }
}

