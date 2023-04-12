package com.igalia.wolvic.browser.api.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import org.chromium.wolvic.Tab;

/**
 * Controlls a single tab content in a browser for chromium backend.
 */
public class TabImpl extends Tab {
    private TabWebContentsObserver mWebContentsObserver;

    public TabImpl(@NonNull Context context, @NonNull SessionImpl session) {
        super(context);
        registerCallbacks(session);
    }

    private void registerCallbacks(@NonNull SessionImpl session) {
        mWebContentsObserver = new TabWebContentsObserver(mWebContents, session);
    }
}
