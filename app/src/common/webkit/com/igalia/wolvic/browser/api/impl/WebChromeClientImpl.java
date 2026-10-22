package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.wpe.wpeview.WPEView;
import com.wpe.wpeview.WebChromeClient;

class WebChromeClientImpl implements WebChromeClient {
    @NonNull SessionImpl mSession;

    public WebChromeClientImpl(@NonNull SessionImpl session) {
        mSession = session;
    }

    @Override
    public void onProgressChanged(WPEView view, int progress) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onProgressChange(mSession, progress);
        }
    }

    @Override
    public void onReceivedTitle(WPEView view, String title) {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onTitleChange(mSession, title);
        }
    }
}
