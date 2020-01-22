package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SessionStore implements GeckoSession.PermissionDelegate {
    private static final String LOGTAG = SystemUtils.createLogtag(SessionStore.class);
    private static final int MAX_GECKO_SESSIONS = 5;

    private static SessionStore mInstance;

    public static SessionStore get() {
        if (mInstance == null) {
            mInstance = new SessionStore();
        }
        return mInstance;
    }

    private Context mContext;
    private GeckoRuntime mRuntime;
    private ArrayList<Session> mSessions;
    private Session mActiveSession;
    private PermissionDelegate mPermissionDelegate;
    private BookmarksStore mBookmarksStore;
    private HistoryStore mHistoryStore;
    private Services mServices;
    private boolean mSuspendPending;

    private SessionStore() {
        mSessions = new ArrayList<>();
    }

    public void setContext(Context context, Bundle aExtras) {
        mContext = context;

        // FIXME: Once GeckoView has a prefs API
        SessionUtils.vrPrefsWorkAround(context, aExtras);

        mRuntime = EngineProvider.INSTANCE.getOrCreateRuntime(context);
    }

    public void initializeServices() {
        mServices = ((VRBrowserApplication)mContext.getApplicationContext()).getServices();
    }

    public void initializeStores(Context context) {
        mBookmarksStore = new BookmarksStore(context);
        mHistoryStore = new HistoryStore(context);
    }

    @NonNull
    private Session addSession(@NonNull Session aSession) {
        aSession.setPermissionDelegate(this);
        aSession.addNavigationListener(mServices);
        mSessions.add(aSession);
        sessionActiveStateChanged();
        return aSession;
    }

    @NonNull
    public Session createSession(boolean aPrivateMode) {
        SessionSettings settings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        return createSession(settings, Session.SESSION_OPEN);
    }

    @NonNull
    /* package */ Session createSession(@NonNull SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode) {
        return addSession(new Session(mContext, mRuntime, aSettings, aOpenMode));
    }

    @NonNull
    public Session createSuspendedSession(SessionState aRestoreState) {
        return addSession(new Session(mContext, mRuntime, aRestoreState));
    }

    @NonNull
    public Session createSuspendedSession(final String aUri, final boolean aPrivateMode) {
        SessionState state = new SessionState();
        state.mUri = aUri;
        state.mSettings = new SessionSettings(new SessionSettings.Builder().withDefaultSettings(mContext).withPrivateBrowsing(aPrivateMode));
        Session session = new Session(mContext, mRuntime, state);
        return addSession(session);
    }

    private void shutdownSession(@NonNull Session aSession) {
        aSession.setPermissionDelegate(null);
        aSession.removeNavigationListener(mServices);
        aSession.shutdown();
    }

    public void destroySession(Session aSession) {
        mSessions.remove(aSession);
        if (aSession != null) {
            shutdownSession(aSession);
        }
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
    }

    public @Nullable Session getSession(String aId) {
        return mSessions.stream().filter(session -> session.getId().equals(aId)).findFirst().orElse(null);
    }

    public void setActiveSession(Session aSession) {
        if (aSession != null) {
            aSession.setActive(true);
        }
        mActiveSession = aSession;
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
            if (session.getGeckoSession() != null) {
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
        if (count > MAX_GECKO_SESSIONS) {
            Log.d(LOGTAG, "Too many GeckoSessions. Active: " + activeCount + " Inactive: " + inactiveCount + " Suspended: " + suspendedCount);
            mSuspendPending = true;
            ThreadUtils.postToUiThread(this::limitInactiveSessions);
        }
    }

    public Session getActiveSession() {
        return mActiveSession;
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

    public BookmarksStore getBookmarkStore() {
        return mBookmarksStore;
    }

    public HistoryStore getHistoryStore() {
        return mHistoryStore;
    }

    public void purgeSessionHistory() {
        for (Session session: mSessions) {
            session.purgeHistory();
        }
    }

    public void onPause() {
        for (Session session: mSessions) {
            session.setActive(false);
        }
    }

    public void onResume() {
        for (Session session: mSessions) {
            session.setActive(true);
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
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mRuntime != null) {
            mRuntime.configurationChanged(newConfig);
        }
    }

    // Session Settings

    public void setServo(final boolean enabled) {
        for (Session session: mSessions) {
            session.setServo(enabled);
        }
    }

    public void setUaMode(final int mode) {
        for (Session session: mSessions) {
            session.setUaMode(mode);
        }
    }

    public void resetMultiprocess() {
        for (Session session: mSessions) {
            session.resetMultiprocess();
        }
    }

    public void setTrackingProtection(final boolean aEnabled) {
        for (Session session: mSessions) {
            session.setTrackingProtection(aEnabled);
        }
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
            mRuntime.getSettings().setLocales(locales.stream().toArray(String[]::new));
        }
    }

    public void clearCache(long clearFlags) {
        LinkedList<Session> activeSession = new LinkedList<>();
        for (Session session: mSessions) {
            if (session.getGeckoSession() != null) {
                session.suspend();
                activeSession.add(session);
            }
        }
        mRuntime.getStorageController().clearData(clearFlags).then(aVoid -> {
            for (Session session: activeSession) {
                session.recreateSession();
            }
            return null;
        });
    }

    // Permission Delegate

    @Override
    public void onAndroidPermissionsRequest(@NonNull GeckoSession session, @Nullable String[] permissions, @NonNull Callback callback) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onAndroidPermissionsRequest(session, permissions, callback);
        }
    }

    @Override
    public void onContentPermissionRequest(@NonNull GeckoSession session, @Nullable String uri, int type, @NonNull Callback callback) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onContentPermissionRequest(session, uri, type, callback);
        }
    }

    @Override
    public void onMediaPermissionRequest(@NonNull GeckoSession session, @NonNull String uri, @Nullable MediaSource[] video, @Nullable MediaSource[] audio, @NonNull MediaCallback callback) {
        if (mPermissionDelegate != null) {
            mPermissionDelegate.onMediaPermissionRequest(session, uri, video, audio, callback);
        }
    }
}
