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

import org.mozilla.gecko.GeckoSession;
import org.mozilla.vrbrowser.BrowserSession;
import org.mozilla.vrbrowser.R;

public class URLBarWidget extends UIWidget implements GeckoSession.NavigationListener {
    private BrowserSession mSession;
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
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackButton = findViewById(R.id.backButton);
        mForwardButton = findViewById(R.id.forwardButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mURLBar = findViewById(R.id.urlBar);
        mURLBar.setRawInputType(InputType.TYPE_NULL);
        mURLBar.setTextIsSelectable(false);
        mURLBar.setCursorVisible(false);

        updateViews();

        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSession != null) {
                    mSession.getGeckoSession().goBack();
                }
            }
        });

        mForwardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSession != null) {
                    mSession.getGeckoSession().goForward();
                }
            }
        });

        mReloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSession != null) {
                    mSession.getGeckoSession().reload();
                }
            }
        });
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
        if (mSession != null) {
            mSession.removeNavigationListener(this);
        }
        mSession = null;
    }

    public void setSession(BrowserSession aSession) {
        if (mSession != null) {
            mSession.removeNavigationListener(this);
        }
        mSession = aSession;
        mSession.addNavigationListener(this);
        updateViews();
    }

    private void updateViews() {
        if (mSession != null) {
            mBackButton.setEnabled(mSession.canGoBack());
            mForwardButton.setEnabled(mSession.canGoForward());
            mReloadButton.setEnabled(mSession.getUrl() != null);
            mURLBar.setText(mSession.getUrl());
        } else {
            mBackButton.setEnabled(false);
            mForwardButton.setEnabled(false);
            mReloadButton.setEnabled(false);
            mURLBar.setText("");
        }
    }

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (mURLBar != null) {
            mURLBar.setText(url);
        }
    }

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {
        mBackButton.setEnabled(canGoBack);
    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {
        mForwardButton.setEnabled(canGoForward);
    }

    @Override
    public boolean onLoadUri(GeckoSession session, String uri, TargetWindow where) {
        if (mURLBar != null) {
            mURLBar.setText(uri);
        }
        return false;
    }
}

