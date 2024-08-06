package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WWebRequestError;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;

import java.security.cert.X509Certificate;
import java.util.List;

class NavigationDelegateImpl implements GeckoSession.NavigationDelegate {
    WSession.NavigationDelegate mDelegate;
    SessionImpl mSession;

    public NavigationDelegateImpl(WSession.NavigationDelegate delegate, SessionImpl session) {
        mDelegate = delegate;
        mSession = session;
    }

    @Override
    public void onLocationChange(@NonNull GeckoSession session, @Nullable String url, @NonNull List<GeckoSession.PermissionDelegate.ContentPermission> perms, @NonNull Boolean hasUserGesture) {
        mDelegate.onLocationChange(mSession, url);
    }

    @Override
    public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBack) {
        mDelegate.onCanGoBack(mSession, canGoBack);
    }

    @Override
    public void onCanGoForward(@NonNull GeckoSession session, boolean canGoForward) {
        mDelegate.onCanGoForward(mSession, canGoForward);
    }

    @Nullable
    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession session, @NonNull LoadRequest request) {
        return Utils.map(ResultImpl.from(mDelegate.onLoadRequest(mSession, fromGeckoLoadRequest(request))));
    }

    @Nullable
    @Override
    public GeckoResult<AllowOrDeny> onSubframeLoadRequest(@NonNull GeckoSession session, @NonNull LoadRequest request) {
        return Utils.map(ResultImpl.from(mDelegate.onSubframeLoadRequest(mSession, fromGeckoLoadRequest(request))));
    }

    @Nullable
    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
        GeckoResult<WSession> result = ResultImpl.from(mDelegate.onNewSession(mSession, uri, null));
        if (result == null) {
            return null;
        }
        return result.map(value -> {
            assert value != null;
            return ((SessionImpl) value).getGeckoSession();
        });
    }

    @Nullable
    @Override
    public GeckoResult<String> onLoadError(@NonNull GeckoSession session, @Nullable String uri, @NonNull WebRequestError error) {
        return ResultImpl.from(mDelegate.onLoadError(mSession, uri, new WWebRequestError() {
            @Override
            public int code() {
                return error.code;
            }

            @Override
            public int category() {
                return error.category;
            }

            @Nullable
            @Override
            public X509Certificate certificate() {
                return error.certificate;
            }
        }));
    }

    private WSession.NavigationDelegate.LoadRequest fromGeckoLoadRequest(LoadRequest request) {
        return new WSession.NavigationDelegate.LoadRequest(
                request.uri, request.triggerUri, request.target, request.isRedirect, request.hasUserGesture, request.isDirectNavigation
        );
    }
}
