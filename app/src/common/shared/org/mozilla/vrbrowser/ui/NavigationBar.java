/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.util.Log;
import android.widget.LinearLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;

public class NavigationBar extends FrameLayout implements GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate {
    private static final String LOGTAG = "VRB";
    private LinearLayout mContainer;
    private AudioEngine mAudio;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageButton mReloadButton;
    private ImageButton mHomeButton;
    private NavigationURLBar mURLBar;
    private boolean mIsLoading;
    private boolean mIsPrivate;
    private NavigationBar.Delegate mDelegate;
    private Drawable mDefaultBackground;

    public interface Delegate {
        void onCloseClick();
    }

    public NavigationBar(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public NavigationBar(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public NavigationBar(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.navigation_bar, this);
        mAudio = AudioEngine.fromContext(aContext);
        mContainer = findViewById(R.id.navigationBarContainer);
        mDefaultBackground = mContainer.getBackground();
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

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setIsPrivate(boolean aPrivate) {
        if (mIsPrivate != aPrivate) {
            mIsPrivate = aPrivate;
            if (aPrivate) {
                mContainer.setBackgroundResource(R.color.eggplant);
            } else {
                mContainer.setBackground(mDefaultBackground);
            }
        }
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUrl, GeckoSession.Response<GeckoSession> aResponse) {
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
    public void onLoadRequest(GeckoSession session, String uri, int target, GeckoSession.Response<Boolean> aResponse) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onLoadUri: " + uri);
            mURLBar.setURL(uri);
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
        if (mReloadButton != null) {
            mReloadButton.setImageResource(R.drawable.ic_icon_exit);
        }
    }

    @Override
    public void onPageStop(GeckoSession aSession, boolean b) {
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

