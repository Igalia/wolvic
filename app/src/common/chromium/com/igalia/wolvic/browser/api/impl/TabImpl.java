package com.igalia.wolvic.browser.api.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import org.chromium.components.embedder_support.view.ContentView;
import org.chromium.content_public.browser.MediaSessionObserver;
import org.chromium.content_public.browser.SelectionPopupController;
import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.Tab;
import org.chromium.wolvic.TabCompositorView;

/**
 * Controls a single tab content in a browser for chromium backend.
 */
public class TabImpl extends Tab {
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
        mTabMediaSessionObserver = new TabMediaSessionObserver(mWebContents, session);
        mTabWebContentsDelegate = new TabWebContentsDelegate(session, mWebContents);
        setWebContentsDelegate(mWebContents, mTabWebContentsDelegate);

        mWebContentsObserver = new TabWebContentsObserver(this, session);

        SelectionPopupController controller =
                SelectionPopupController.fromWebContents(mWebContents);
        controller.setDelegate(
                new SelectionPopupControllerDelegate(mWebContents,
                        controller.getDelegateEventHandler(), session));
    }

    public void exitFullScreen() {
        mWebContents.exitFullscreen();
    }

    public void onMediaFullscreen(boolean isFullscreen) {
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
