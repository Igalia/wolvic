package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WWebRequestError;

import org.chromium.content_public.browser.LifecycleState;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.url.GURL;

import java.security.cert.X509Certificate;

public class TabWebContentsObserver extends WebContentsObserver {
    private @NonNull SessionImpl mSession;

    public TabWebContentsObserver(WebContents webContents, @NonNull SessionImpl session) {
        super(webContents);
        mSession = session;
    }

    @Override
    public void didStartLoading(GURL url) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStart(mSession, url.getSpec());
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void didRedirectNavigation(NavigationHandle navigationHandle) {
        dispatchCanGoBackOrForward();
    }

    /**
     *  Called when the browser process starts a navigation in the primary main frame. Called before
     * initiating the network request. See also
     * https://chromium.googlesource.com/chromium/src/+/main/docs/navigation.md#Navigation
     * @param navigationHandle
     */
    @Override
    public void didStartNavigationInPrimaryMainFrame(NavigationHandle navigationHandle) {
        super.didStartNavigationInPrimaryMainFrame(navigationHandle);

        WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate == null)
            return;

        // FIXME: how to select different target windows? Does it make sense here?.
        delegate.onLoadRequest(mSession, new WSession.NavigationDelegate.LoadRequest(
                navigationHandle.getUrl().getSpec(), navigationHandle.getReferrerUrl().getSpec(),
                WSession.NavigationDelegate.TARGET_WINDOW_CURRENT, navigationHandle.isRedirect(),
                navigationHandle.hasUserGesture(), navigationHandle.isRendererInitiated()));
    }

    private int toWSessionOnVisitedFlags(NavigationHandle navigationHandle) {
        int flags = 0;
        // TODO: get different types of redirection.
        if (navigationHandle.isInPrimaryMainFrame())
            flags |= WSession.HistoryDelegate.VISIT_TOP_LEVEL;
        if (navigationHandle.isRedirect()) {
            int statusCode = navigationHandle.httpStatusCode();
            flags |= (statusCode == 301 || statusCode == 308) ?
                    WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT :
                    WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE;
        }
        if (navigationHandle.isErrorPage())
            flags |= WSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR;
        return flags;
    }

    /**
     * Called when the navigation is committed. The commit can be an error page if the server
     * responded with an error code or a successful document. See also:
     * https://chromium.googlesource.com/chromium/src/+/main/docs/navigation.md#Navigation
     * @param navigationHandle
     */
    @Override
    public void didFinishNavigationInPrimaryMainFrame(NavigationHandle navigationHandle) {
        WSession.NavigationDelegate navigationDelegate = mSession.getNavigationDelegate();
        if (navigationDelegate == null)
            return;

        if (navigationHandle.isErrorPage()) {
            didFailLoad(true, navigationHandle.errorCode(), navigationHandle.getUrl(), 0);
            return;
        }

        navigationDelegate.onLocationChange(mSession, navigationHandle.getUrl().getSpec());
        WSession.HistoryDelegate historyDelegate = mSession.getHistoryDelegate();
        if (historyDelegate != null) {
            historyDelegate.onVisited(mSession, navigationHandle.getUrl().getSpec(), navigationHandle.getReferrerUrl().getSpec(), toWSessionOnVisitedFlags(navigationHandle));
        }
    }

    @Override
    public void didStopLoading(GURL url, boolean isKnownValid) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onPageStop(mSession, true);
        }
        dispatchCanGoBackOrForward();
    }

    @Override
    public void didFailLoad(boolean isInPrimaryMainFrame, int errorCode, GURL failingUrl, @LifecycleState int rfhLifecycleState) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null && isInPrimaryMainFrame) {
            delegate.onPageStop(mSession, false);
        }
        dispatchCanGoBackOrForward();

        WSession.NavigationDelegate navigationDelegate = mSession.getNavigationDelegate();
        if (navigationDelegate != null) {
            byte[] errorData = navigationDelegate.onLoadErrorData(mSession, failingUrl.getSpec(), new WWebRequestError() {
                @Override
                public int code() {
                    return errorCode;
                }

                @Override
                public int category() {
                    // FIXME: can we improve this?.
                    return ERROR_CATEGORY_UNKNOWN;
                }

                @Nullable
                @Override
                public X509Certificate certificate() {
                    // FIXME: can we improve this?.
                    return null;
                }
            });
            mSession.loadData(errorData, "text/html");
        }
    }

    private void dispatchCanGoBackOrForward() {
        @Nullable WSession.NavigationDelegate delegate = mSession.getNavigationDelegate();
        if (delegate != null) {
            WebContents webContents = mWebContents.get();
            if (webContents == null)
                return;

            delegate.onCanGoBack(mSession, webContents.getNavigationController().canGoBack());
            delegate.onCanGoForward(mSession, webContents.getNavigationController().canGoForward());
        }
    }

    @Override
    public void loadProgressChanged(float progress) {
        @Nullable WSession.ProgressDelegate delegate = mSession.getProgressDelegate();
        if (delegate != null) {
            delegate.onProgressChange(mSession, (int)(progress * 100));
        }
    }

    @Override
    public void didFirstVisuallyNonEmptyPaint() {
        @Nullable WSession.ContentDelegate delegate = mSession.getContentDelegate();
        if (delegate != null) {
            delegate.onFirstContentfulPaint(mSession);
        }
    }

    @Override
    public void hasEffectivelyFullscreenVideoChange(boolean isFullscreen) {
        mSession.getTab().onMediaFullscreen(isFullscreen);
    }
}
