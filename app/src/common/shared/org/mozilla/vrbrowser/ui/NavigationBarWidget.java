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

import org.mozilla.geckoview.GeckoResponse;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class NavigationBarWidget extends UIWidget implements GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate {
    private static final String LOGTAG = "VRB";
    private AudioEngine mAudio;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageButton mReloadButton;
    private ImageButton mHomeButton;
    private NavigationURLBar mURLBar;
    private boolean mIsLoading;

    public NavigationBarWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public NavigationBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public NavigationBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.navigation_bar, this);
        mAudio = AudioEngine.fromContext(aContext);
        mBackButton = findViewById(R.id.backButton);
        mForwardButton = findViewById(R.id.forwardButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mHomeButton = findViewById(R.id.homeButton);
        mURLBar = findViewById(R.id.urlBar);

        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().goBack();
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.BACK);
                }
            }
        });

        mForwardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().goForward();
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
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
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });

        mHomeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionStore.get().loadUri(SessionStore.DEFAULT_URL);
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }
            }
        });


        SessionStore.get().addNavigationListener(this);
        SessionStore.get().addProgressListener(this);
    }

    public void setURLText(String aText) {
        mURLBar.setURLText(aText);
    }

    @Override
    void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.width = 720;
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.browser_world_width);
        aPlacement.height = 45;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 1.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.translationY = -20;
        aPlacement.opaque = false;
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUrl, GeckoResponse<GeckoSession> aResponse) {
        aResponse.respond(null);
    }

    public void release() {
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
    public void onLoadRequest(GeckoSession aSession, String aUri, int target, int flags, GeckoResponse<Boolean> aResponse) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onLoadUri: " + aUri);
            mURLBar.setURL(aUri);
        }
        aResponse.respond(null);
    }

    // Progress Listener

    @Override
    public void onPageStart(GeckoSession aSession, String aUri) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onPageStart: " + aUri);
            mURLBar.setURL(aUri);
        }
        mIsLoading = true;
        mURLBar.setIsLoading(true);
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_exit);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
        mIsLoading = false;
        mURLBar.setIsLoading(false);
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

