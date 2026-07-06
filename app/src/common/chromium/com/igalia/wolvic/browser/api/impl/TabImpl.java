package com.igalia.wolvic.browser.api.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import org.chromium.components.embedder_support.view.ContentView;
import org.chromium.content_public.browser.MediaSession;
import org.chromium.content_public.browser.SelectionPopupController;
import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.Tab;
import org.chromium.wolvic.TabCompositorView;

/**
 * Controls a single tab content in a browser for chromium backend.
 */
public class TabImpl extends Tab {
    private SessionImpl mSession;
    private TabMediaSessionObserver mTabMediaSessionObserver;
    private TabWebContentsDelegate mTabWebContentsDelegate;
    private TabWebContentsObserver mWebContentsObserver;
    private WebContents mPaymentHandlerWebContents;
    // TODO: Need to Payment's mediator
    private ContentView mPaymentHandlerContentView;
    private TabCompositorView mPaymentHandlerCompositorView;


    public TabImpl(@NonNull Context context, @NonNull SessionImpl session, WebContents webContents) {
        super(context, session.getSettings().getUsePrivateMode(), webContents);
        registerCallbacks(session);
    }

    private void registerCallbacks(@NonNull SessionImpl session) {
        mSession = session;

        mTabWebContentsDelegate = new TabWebContentsDelegate(session, mWebContents);
        setWebContentsDelegate(mWebContents, mTabWebContentsDelegate);

        mWebContentsObserver = new TabWebContentsObserver(this, session);

        // The native MediaSession is created lazily (on first media use), so it usually does not
        // exist yet and MediaSession.fromWebContents() returns null. If it already exists, observe
        // it now; otherwise TabWebContentsObserver.mediaSessionCreated() does it when the session
        // appears. The session is created at most once per WebContents.
        MediaSession mediaSession = MediaSession.fromWebContents(mWebContents);
        if (mediaSession != null)
            createMediaSessionObserver(mediaSession);

        SelectionPopupController controller =
                SelectionPopupController.fromWebContents(mWebContents);
        controller.setDelegate(
                new SelectionPopupControllerDelegate(mWebContents,
                        controller.getDelegateEventHandler(), session));
    }

    /* package */ void createMediaSessionObserver(@NonNull MediaSession mediaSession) {
        mTabMediaSessionObserver = new TabMediaSessionObserver(mediaSession, mWebContents, mSession);
    }

    public void exitFullScreen() {
        mWebContents.exitFullscreen();
    }

    public void onMediaFullscreen(boolean isFullscreen) {
        if (mTabMediaSessionObserver != null)
            mTabMediaSessionObserver.onMediaFullscreen(isFullscreen);
    }

    public void purgeHistory() {
        mWebContents.getNavigationController().clearHistory();
    }

    public void setPaymentWebContents(WebContents webContents, ContentView contentView, TabCompositorView compositorView) {
        mPaymentHandlerWebContents = webContents;
        mPaymentHandlerContentView = contentView;
        mPaymentHandlerCompositorView = compositorView;
    }

    public WebContents getActiveWebContents() {
        return mPaymentHandlerWebContents != null ? mPaymentHandlerWebContents : mWebContents;
    }

    public ContentView getActiveContentView() {
       return mPaymentHandlerContentView != null ? mPaymentHandlerContentView : getContentView();
    }

    public TabCompositorView getActiveCompositorView() {
       return mPaymentHandlerCompositorView != null ? mPaymentHandlerCompositorView : getCompositorView();
    }
}
