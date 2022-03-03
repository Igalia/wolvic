package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WSession;

import org.mozilla.geckoview.GeckoSession;

class ProgressDelegateImpl implements GeckoSession.ProgressDelegate {
    WSession.ProgressDelegate mDelegate;
    SessionImpl mSession;

    public ProgressDelegateImpl(WSession.ProgressDelegate delegate, SessionImpl session) {
        mDelegate = delegate;
        mSession = session;
    }


    @Override
    public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
        mDelegate.onPageStart(mSession, url);
    }

    @Override
    public void onPageStop(@NonNull GeckoSession session, boolean success) {
        mDelegate.onPageStop(mSession, success);
    }

    @Override
    public void onProgressChange(@NonNull GeckoSession session, int progress) {
        mDelegate.onProgressChange(mSession, progress);
    }

    @Override
    public void onSecurityChange(@NonNull GeckoSession session, @NonNull SecurityInformation info) {
        mDelegate.onSecurityChange(mSession, new WSession.ProgressDelegate.SecurityInformation(
            info.isSecure, info.isException, info.origin, info.host, info.certificate,
                fromGeckoSecurityMode(info.securityMode), fromGeckoContentType(info.mixedModePassive), fromGeckoContentType(info.mixedModeActive)
        ));
    }

    @Override
    public void onSessionStateChange(@NonNull GeckoSession session, @NonNull GeckoSession.SessionState sessionState) {
        mDelegate.onSessionStateChange(mSession, new SessionStateImpl(sessionState));
    }

    private int fromGeckoSecurityMode(int securityMode) {
        switch (securityMode) {
            case SecurityInformation.SECURITY_MODE_IDENTIFIED:
                return WSession.ProgressDelegate.SecurityInformation.SECURITY_MODE_IDENTIFIED;
            case SecurityInformation.SECURITY_MODE_UNKNOWN:
                return WSession.ProgressDelegate.SecurityInformation.SECURITY_MODE_UNKNOWN;
            case SecurityInformation.SECURITY_MODE_VERIFIED:
                return WSession.ProgressDelegate.SecurityInformation.SECURITY_MODE_VERIFIED;
        }

        throw new RuntimeException("Unhandled ProgressDelegate securityMode");
    }

    private int fromGeckoContentType(int contentType) {
        switch (contentType) {
            case SecurityInformation.CONTENT_BLOCKED:
                return WSession.ProgressDelegate.SecurityInformation.CONTENT_BLOCKED;
            case SecurityInformation.CONTENT_LOADED:
                return WSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED;
            case SecurityInformation.CONTENT_UNKNOWN:
                return WSession.ProgressDelegate.SecurityInformation.CONTENT_UNKNOWN;
        }

        throw new RuntimeException("Unhandled ProgressDelegate contentType");
    }
}
