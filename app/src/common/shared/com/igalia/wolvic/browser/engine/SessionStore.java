package com.igalia.wolvic.browser.engine;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.BookmarksStore;
import com.igalia.wolvic.browser.HistoryStore;
import com.igalia.wolvic.browser.PermissionDelegate;
import com.igalia.wolvic.browser.Services;
import com.igalia.wolvic.browser.SessionChangeListener;
import com.igalia.wolvic.browser.WebAppsStore;
import com.igalia.wolvic.browser.adapter.ComponentsAdapter;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.components.BrowserIconsHelper;
import com.igalia.wolvic.browser.components.WolvicWebExtensionRuntime;
import com.igalia.wolvic.browser.content.TrackingProtectionStore;
import com.igalia.wolvic.browser.extensions.BuiltinExtension;
import com.igalia.wolvic.db.SitePermission;
import com.igalia.wolvic.utils.SystemUtils;
import com.igalia.wolvic.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import mozilla.components.browser.state.state.BrowserState;
import mozilla.components.feature.accounts.FxaCapability;
import mozilla.components.feature.accounts.FxaWebChannelFeature;
import mozilla.components.feature.webcompat.WebCompatFeature;
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature;
import mozilla.components.lib.state.Store;

public class SessionStore implements
        WSession.PermissionDelegate,
        SessionChangeListener,
        ComponentsAdapter.StoreUpdatesListener {

    private static final String LOGTAG = SystemUtils.createLogtag(SessionStore.class);
    private static final int MAX_SESSIONS = 5;

    private static final List<Pair<String, String>> BUILTIN_WEB_EXTENSIONS = Arrays.asList(
            new Pair<>("fxr-webcompat_youtube@mozilla.org", "resource://android/assets/extensions/fxr_youtube/"),
            new Pair<>("fxr-webcompat_mediasession@mozilla.org", "resource://android/assets/extensions/fxr_mediasession/"),
            new Pair<>("icons@mozac.org", "resource://android/assets/extensions/browser-icons/")
    );

    private static SessionStore mInstance;

    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }

    private Executor mMainExecutor;
    private Context mContext;
    private WRuntime mRuntime;
    private ArrayList<Session> mSessions;
    private Session mActiveSession;
    private PermissionDelegate mPermissionDelegate;
    private BookmarksStore mBookmarksStore;
    private HistoryStore mHistoryStore;
    private WebAppsStore mWebAppStore;
    private Services mServices;
    private boolean mSuspendPending;
    private TrackingProtectionStore mTrackingProtectionStore;
    private WolvicWebExtensionRuntime mWebExtensionRuntime;
    private FxaWebChannelFeature mWebChannelsFeature;
    private Store.Subscription mStoreSubscription;
    private BrowserIconsHelper mBrowserIconsHelper;
    private final LinkedHashSet<SessionChangeListener> mSessionChangeListeners;

    private SessionStore() {
        mSessions = new ArrayList<>();
        mSessionChangeListeners = new LinkedHashSet<>();
    }

    public void initialize(Context context) {
        mContext = context;
        mMainExecutor = ((VRBrowserApplication)context.getApplicationContext()).getExecutors().mainThread();

        mRuntime = EngineProvider.INSTANCE.getOrCreateRuntime(context);

        mTrackingProtectionStore = new TrackingProtectionStore(context, mRuntime);
        mTrackingProtectionStore.addListener(new TrackingProtectionStore.TrackingProtectionListener() {
            @Override
            public void onExcludedTrackingProtectionChange(@NonNull String url, boolean excluded, boolean isPrivate) {
                mSessions.forEach(existingSession -> {
                    String currentSessionHost = UrlUtils.getHost(existingSession.getCurrentUri());
                    String sessionHost = UrlUtils.getHost(url);
                    if (currentSessionHost.equals(sessionHost) && existingSession.isPrivateMode() == isPrivate) {
                        existingSession.reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
                    }
                });
            }

            @Override
            public void onTrackingProtectionLevelUpdated(int level) {
                mSessions.forEach(session -> {
                    if (session.isActive()) {
                        session.updateTrackingProtection();
                        session.reload(WSession.LOAD_FLAGS_BYPASS_CACHE);
                    } else {
                        session.suspend();
                    }
                });
            }
        });

        mWebExtensionRuntime = new WolvicWebExtensionRuntime(mContext, mRuntime);

        mServices = ((VRBrowserApplication) context.getApplicationContext()).getServices();

        mBookmarksStore = new BookmarksStore(context);
        mHistoryStore = new HistoryStore(context);
        mWebAppStore = new WebAppsStore(context);

        // Web Extensions initialization
        BUILTIN_WEB_EXTENSIONS.forEach(extension -> BuiltinExtension.install(mWebExtensionRuntime, extension.first, extension.second));
        mBrowserIconsHelper = new BrowserIconsHelper(context, mWebExtensionRuntime, ComponentsAdapter.get().getStore());

        WebCompatFeature.INSTANCE.install(mWebExtensionRuntime);
        WebCompatReporterFeature.INSTANCE.install(mWebExtensionRuntime, context.getString(R.string.app_name));
        mWebChannelsFeature = new FxaWebChannelFeature(
                null,
                mWebExtensionRuntime,
                ComponentsAdapter.get().getStore(),
                mServices.getAccountManager(),
                mServices.getServerConfig(),
                Collections.singleton(FxaCapability.CHOOSE_WHAT_TO_SYNC));

        mWebChannelsFeature.start();

        ComponentsAdapter.get().addStoreUpdatesListener(this);

        if (BuildConfig.DEBUG) {
            mStoreSubscription = ComponentsAdapter.get().getStore().observeManually(browserState -> {
                ((Activity)mContext).runOnUiThread(() -> {
                    if (mSessions == null || browserState == null) {
                        return;
                    }
                    Log.d(LOGTAG, "Session status BEGIN");
                    Log.d(LOGTAG, "[Total] BrowserStore: " + browserState.getTabs().size() + ", SessionStore: " + mSessions.size());
                    for (int i=0; i<browserState.getTabs().size(); i++) {
                        boolean isPrivate = browserState.getTabs().get(i).getContent().getPrivate();
                        Log.d(LOGTAG, "BrowserStore Session: " + browserState.getTabs().get(i).getId() + (isPrivate ? " (PB)" : ""));
                    }
                    int suspendedCount = 0;
                    for (int i=0; i<mSessions.size(); i++) {
                        boolean suspended = mSessions.get(i).getSessionState().mSession == null && !mSessions.get(i).isActive();
                        boolean isPrivate = mSessions.get(i).isPrivateMode();
                        Log.d(LOGTAG, "SessionStore Session: " + mSessions.get(i).getId() + (isPrivate ? " (PB)" : "") + (suspended ? " (suspended)" : ""));
                        if (suspended) {
                            suspendedCount++;
                        }
                    }
                    Log.d(LOGTAG, "[Alive] BrowserStore: " + browserState.getTabs().size() + ", SessionStore: " + (mSessions.size() - suspendedCount));
                    Log.d(LOGTAG, "Session status END");
                    return;
                });
                return null;
            });
            mStoreSubscription.resume();
        }
    }

    @NonNull
    private Session addSession(@NonNull Session aSession) {
        aSession.setPermissionDelegate(this);
        aSession.addNavigationListener(mServices);
        mSessions.add(aSession);
        sessionActiveStateChanged();

        if (BuildConfig.DEBUG) {
            mStoreSubscription.resume();
        }

        return aSession;
    }

    @NonNull
    public Session createWebExtensionSession(boolean aPrivateMode) {
        SessionSettings settings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        Session session = Session.createWebExtensionSession(
                mContext,
                mRuntime,
                settings,
                Session.SESSION_OPEN,
                this);
        return addSession(session);
    }

    @NonNull
    public Session createWebExtensionSession(boolean openSession, boolean aPrivateMode) {
        SessionSettings settings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        Session session = Session.createWebExtensionSession(
                mContext,
                mRuntime,
                settings, openSession ? Session.SESSION_OPEN : Session.SESSION_DO_NOT_OPEN,
                this);
        return addSession(session);
    }

    @NonNull
    public Session createSession(boolean aPrivateMode) {
        SessionSettings settings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        return createSession(settings, Session.SESSION_OPEN);
    }

    @NonNull
    public Session createSession(boolean openSession, boolean aPrivateMode) {
        SessionSettings settings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        return createSession(settings, openSession ? Session.SESSION_OPEN : Session.SESSION_DO_NOT_OPEN);
    }

    @NonNull
    Session createSession(@NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode) {
        Session session = Session.createSession(mContext, mRuntime, aSettings, aOpenMode, this);
        return addSession(session);
    }

    @NonNull
    public Session createSuspendedSession(SessionState aRestoreState) {
        Session session = Session.createSuspendedSession(mContext, mRuntime, aRestoreState, this);
        return addSession(session);
    }

    @NonNull
    public Session createSuspendedSession(final String aUri, final boolean aPrivateMode) {
        SessionState state = new SessionState();
        state.mUri = aUri;
        state.mSettings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        Session session = Session.createSuspendedSession(mContext, mRuntime, state, this);
        return addSession(session);
    }

    private void shutdownSession(@NonNull Session aSession) {
        aSession.setPermissionDelegate(null);
        aSession.shutdown();
        if (BuildConfig.DEBUG) {
            mStoreSubscription.resume();
        }
    }

    public void destroySession(Session aSession) {
        mSessions.remove(aSession);
        if (aSession != null) {
            shutdownSession(aSession);
        }
    }

    public void destroySession(@NonNull String sessionId) {
        mSessions.stream().filter(session -> session.getId().equals(sessionId)).findFirst().ifPresent(this::destroySession);
    }

    public void destroyPrivateSessions() {
        mSessions.removeIf(session -> {
            if (!session.isPrivateMode()) {
                return false;
            }
            shutdownSession(session);
            return true;
        });
    }

    public void suspendAllInactiveSessions() {
        for (Session session: mSessions) {
            if (!session.isActive()) {
                session.suspend();
            }
        }
        if (BuildConfig.DEBUG) {
            mStoreSubscription.resume();
        }
    }

    public @Nullable Session getSession(String aId) {
        return mSessions.stream().filter(session -> session.getId().equals(aId)).findFirst().orElse(null);
    }

    public @Nullable Session getSessionByUri(String uri) {
        return mSessions.stream().filter(session -> session.getCurrentUri().equals(uri)).findFirst().orElse(null);
    }

    public @Nullable Session getSession(WSession aSession) {
        return mSessions.stream().filter(session -> session.getWSession() == aSession).findFirst().orElse(null);
    }

    public @NonNull List<Session> getSessionsByHost(@NonNull String aHost, boolean aIsPrivate) {
        return mSessions.stream()
                .filter(session -> session.isPrivateMode() == aIsPrivate)
                .filter(session -> UrlUtils.getHost(session.getCurrentUri()).equals(aHost))
                .collect(Collectors.toList());
    }

    public void setActiveSession(Session aSession) {
        if (aSession != null) {
            aSession.setActive(true);
        }
        mActiveSession = aSession;
    }

    public void setActiveSession(@NonNull String sessionId) {
        Session session = getSession(sessionId);
        if (session != null) {
            setActiveSession(session);
        }
    }

    private void limitInactiveSessions() {
        Log.d(LOGTAG, "Limiting Inactive Sessions");
        suspendAllInactiveSessions();
        mSuspendPending = false;
    }

    void sessionActiveStateChanged() {
        if (mSuspendPending) {
            return;
        }
        int count = 0;
        int activeCount = 0;
        int inactiveCount = 0;
        int suspendedCount = 0;
        for(Session session: mSessions) {
            if (session.getWSession() != null) {
                count++;
                if (session.isActive()) {
                    activeCount++;
                } else {
                    inactiveCount++;
                }
            } else {
                suspendedCount++;
            }
        }
        if (count > MAX_SESSIONS) {
            Log.d(LOGTAG, "Too many sessions. Active: " + activeCount + " Inactive: " + inactiveCount + " Suspended: " + suspendedCount);
            mSuspendPending = true;
            mMainExecutor.execute(this::limitInactiveSessions);
        }
    }

    public Session getActiveSession() {
        return mActiveSession;
    }

    public List<Session> getSessions(boolean aPrivateMode) {
        return mSessions.stream().filter(session -> session.isPrivateMode() == aPrivateMode).collect(Collectors.toList());
    }

    public ArrayList<Session> getSortedSessions(boolean aPrivateMode) {
        ArrayList<Session> result = new ArrayList<>(mSessions);
        result.removeIf(session -> session.isPrivateMode() != aPrivateMode);
        result.sort((o1, o2) -> {
            if (o2.getLastUse() < o1.getLastUse()) {
                return -1;
            }
            return o2.getLastUse() == o1.getLastUse() ? 0 : 1;
        });
        return result;
    }

    public void setPermissionDelegate(PermissionDelegate delegate) {
        mPermissionDelegate = delegate;
    }

    public void addSessionChangeListener(SessionChangeListener listener) {
        mSessionChangeListeners.add(listener);
    }

    public void removeSessionChangeListener(SessionChangeListener listener) {
        mSessionChangeListeners.remove(listener);
    }

    public BookmarksStore getBookmarkStore() {
        return mBookmarksStore;
    }

    public HistoryStore getHistoryStore() {
        return mHistoryStore;
    }

    public WebAppsStore getWebAppsStore() {
        return mWebAppStore;
    }

    public TrackingProtectionStore getTrackingProtectionStore() {
        return mTrackingProtectionStore;
    }

    public WolvicWebExtensionRuntime getWebExtensionRuntime() {
        return mWebExtensionRuntime;
    }

    @NonNull
    public BrowserIconsHelper getBrowserIcons() {
        return mBrowserIconsHelper;
    }

    public void purgeSessionHistory() {
        for (Session session : mSessions) {
            session.purgeHistory();
        }
    }

    public void onDestroy() {
        for (int i = mSessions.size() - 1; i >= 0; --i) {
            destroySession(mSessions.get(i));
        }

        if (mBookmarksStore != null) {
            mBookmarksStore.removeAllListeners();
        }

        if (mHistoryStore != null) {
            mHistoryStore.removeAllListeners();
        }

        if (mWebChannelsFeature != null) {
            mWebChannelsFeature.stop();
        }

        if (BuildConfig.DEBUG && mStoreSubscription != null) {
            mStoreSubscription.unsubscribe();
        }

        ComponentsAdapter.get().removeStoreUpdatesListener(this);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mRuntime != null) {
            mRuntime.configurationChanged(newConfig);
        }

        mBookmarksStore.onConfigurationChanged();
    }

    // Runtime Settings

    public void setConsoleOutputEnabled(boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setConsoleOutputEnabled(enabled);
        }
    }

    public void setRemoteDebugging(final boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setRemoteDebuggingEnabled(enabled);
        }
    }

    public void setLocales(List<String> locales) {
        if (mRuntime != null) {
            mRuntime.getSettings().setLocales(locales.toArray(new String[0]));
        }
    }

    public void clearCache(long clearFlags) {
        LinkedList<Session> activeSession = new LinkedList<>();
        for (Session session: mSessions) {
            if (session.getWSession() != null) {
                session.suspend();
                activeSession.add(session);
            }
        }
        mRuntime.clearData(clearFlags).then(aVoid -> {
            for (Session session: activeSession) {
                session.recreateSession();
            }
            return null;
        });
    }

    // Permission Delegate

    @Override
    public void onAndroidPermissionsRequest(@NonNull WSession session, @Nullable String[] permissions, @NonNull Callback callback) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onAndroidPermissionsRequest(session, permissions, callback);
        }
    }

    @Override
    public WResult<Integer> onContentPermissionRequest(@NonNull WSession session, @NonNull ContentPermission perm) {
        if (mPermissionDelegate != null) {
            return mPermissionDelegate.onContentPermissionRequest(session, perm);
        }
        return WResult.fromValue(ContentPermission.VALUE_DENY);
    }

    @Override
    public void onMediaPermissionRequest(@NonNull WSession session, @NonNull String uri, @Nullable MediaSource[] video, @Nullable MediaSource[] audio, @NonNull MediaCallback callback) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onMediaPermissionRequest(session, uri, video, audio, callback);
        }
    }

    public void addPermissionException(@NonNull String uri, @SitePermission.Category int category) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.addPermissionException(uri, category, false);
        }
    }

    public void removePermissionException(@NonNull String uri, @SitePermission.Category int category) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.removePermissionException(uri, category);
        }
    }

    // SessionChangeListener

    @Override
    public void onSessionAdded(Session aSession) {
        ComponentsAdapter.get().addSession(aSession);
        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onSessionAdded(aSession);
        }
    }

    @Override
    public void onSessionOpened(Session aSession) {
        ComponentsAdapter.get().link(aSession);
        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onSessionOpened(aSession);
        }
    }

    @Override
    public void onSessionClosed(Session aSession) {
        ComponentsAdapter.get().unlink(aSession);
        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onSessionClosed(aSession);
        }
    }

    @Override
    public void onSessionRemoved(String aId) {
        ComponentsAdapter.get().removeSession(aId);
        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onSessionRemoved(aId);
        }
    }

    @Override
    public void onSessionStateChanged(Session aSession, boolean aActive) {
        if (aActive) {
            ComponentsAdapter.get().selectSession(aSession);
        }
        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onSessionStateChanged(aSession, aActive);
        }
    }

    @Override
    public void onCurrentSessionChange(WSession aOldSession, WSession aSession) {
        Session oldSession = getSession(aOldSession);
        Session newSession = getSession(aSession);
        if (oldSession != null) {
            ComponentsAdapter.get().unlink(oldSession);
        }
        if (newSession != null) {
            ComponentsAdapter.get().link(newSession);
        }

        for (SessionChangeListener listener : mSessionChangeListeners) {
            listener.onCurrentSessionChange(aOldSession, aSession);
        }
    }

    @Override
    public void onStackSession(Session aSession) {
        ComponentsAdapter.get().link(aSession);
    }

    @Override
    public void onUnstackSession(Session aSession, Session aParent) {
        // unlink/remove are called by destroySession
        destroySession(aSession);
    }

    // StoreUpdatesListener

    @Override
    public void onTabSelected(@NonNull BrowserState state, @Nullable mozilla.components.browser.state.state.SessionState tab) { }

}
