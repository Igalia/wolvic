package org.mozilla.vrbrowser.browser.engine.gecko;

import android.content.Context;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.SessionChangeListener;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.browser.extensions.VimeoExtensionFeature;
import org.mozilla.vrbrowser.browser.extensions.YoutubeExtensionFeature;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.Collections;

import mozilla.components.browser.session.LegacySessionManager;
import mozilla.components.browser.session.SessionManager;
import mozilla.components.browser.state.store.BrowserStore;
import mozilla.components.feature.accounts.FxaCapability;
import mozilla.components.feature.accounts.FxaWebChannelFeature;
import mozilla.components.feature.webcompat.WebCompatFeature;
import mozilla.components.support.base.observer.ObserverRegistry;

public class FxRSessionManager implements SessionChangeListener {

    private static final String LOGTAG = SystemUtils.createLogtag(FxRSessionManager.class);

    private Context mContext;
    private ObserverRegistry mRegistry;
    private SessionManager mSessionManager;

    public FxRSessionManager(@NonNull Context context, @NonNull SessionStore store) {
        mContext = context;
        BrowserStore browserStore = new BrowserStore();
        GeckoEngine geckoEngine = new GeckoEngine(context, store);
        mRegistry = new ObserverRegistry();

        LegacySessionManager legacySessionManager = new LegacySessionManager(
                geckoEngine,
                new SessionManager.EngineSessionLinker(browserStore),
                mRegistry);

        mSessionManager = new SessionManager(
                geckoEngine,
                browserStore,
                legacySessionManager);

        VimeoExtensionFeature.install(geckoEngine);
        YoutubeExtensionFeature.install(geckoEngine);
        WebCompatFeature.INSTANCE.install(geckoEngine);

        FxaWebChannelFeature mFxAWebChannelsFeature = new FxaWebChannelFeature(
                context,
                null,
                geckoEngine,
                mSessionManager,
                ((VRBrowserApplication) context.getApplicationContext()).getServices().getAccountManager(),
                Collections.singleton(FxaCapability.CHOOSE_WHAT_TO_SYNC));
        mFxAWebChannelsFeature.start();
    }

    // SessionChangeListener

    @Override
    public void onSessionOpened(Session aSession) {
        mozilla.components.browser.session.Session session = new mozilla.components.browser.session.Session(
                aSession.getCurrentUri(),
                aSession.isPrivateMode(),
                mozilla.components.browser.session.Session.Source.NONE,
                aSession.getId(),
                mRegistry);
        mSessionManager.add(session, aSession.isActive(), new GeckoEngineSession(mContext, aSession), null);
    }

    @Override
    public void onSessionClosed(Session aSession) {
        mozilla.components.browser.session.Session session = mSessionManager.findSessionById(aSession.getId());
        if (session != null) {
            mSessionManager.remove(session, false);
        }
    }

    @Override
    public void onActiveStateChange(Session aSession, boolean aActive) {
        if (aActive) {
            mozilla.components.browser.session.Session session = mSessionManager.findSessionById(aSession.getId());
            if (session != null) {
                mSessionManager.select(session);
            }
        }
    }
}
