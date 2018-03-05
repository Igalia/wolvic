/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.util.Log;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.R;

public class URLBarWidget extends UIWidget implements GeckoSession.NavigationDelegate {
    private static final String LOGTAG = "VRB";
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageButton mReloadButton;
    private EditText mURLBar;

    public URLBarWidget(Context aContext) {
        super(aContext);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
    }

    @Override
    public void onNewSession(GeckoSession aSession, String aUrl, GeckoSession.Response<GeckoSession> aResponse) {

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackButton = findViewById(R.id.backButton);
        mForwardButton = findViewById(R.id.forwardButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mURLBar = findViewById(R.id.urlBar);
        mURLBar.setRawInputType(InputType.TYPE_NULL);
        mURLBar.setTextIsSelectable(false);
        mURLBar.setCursorVisible(false);

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
                SessionStore.get().reload();
            }
        });
        SessionStore.get().addListener(this);
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
        SessionStore.get().removeListener(this);
    }

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got location change: " + url);
            mURLBar.setText(url);
            mReloadButton.setEnabled(true);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {
        if (mBackButton != null) {
            Log.e(LOGTAG, "Got onCanGoBack: " + (canGoBack ? "TRUE" : "FALSE"));
            mBackButton.setEnabled(canGoBack);
            mBackButton.setClickable(canGoBack);
        }
    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {
        if (mForwardButton != null) {
            Log.e(LOGTAG, "Got onCanGoForward: " + (canGoForward ? "TRUE" : "FALSE"));
            mForwardButton.setEnabled(canGoForward);
            mForwardButton.setClickable(canGoForward);
        }
    }

    @Override
    public boolean onLoadUri(GeckoSession session, String uri, TargetWindow where) {
        if (mURLBar != null) {
            Log.e(LOGTAG, "Got onLoadUri: " + uri);
            mURLBar.setText(uri);
        }
        return false;
    }
}

