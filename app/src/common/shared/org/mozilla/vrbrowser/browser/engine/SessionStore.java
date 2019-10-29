package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.vrbrowser.BuildConfig;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SessionStore implements GeckoSession.PermissionDelegate {

    private static final String[] WEB_EXTENSIONS = new String[] {
            "webcompat_vimeo",
            "webcompat_youtube"
    };

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

    private SessionStore() {
        mSessions = new ArrayList<>();
    }

    public void setContext(Context context, Bundle aExtras) {
        mContext = context;

        if (mRuntime == null) {
            // FIXME: Once GeckoView has a prefs API
            SessionUtils.vrPrefsWorkAround(context, aExtras);

            GeckoRuntimeSettings.Builder runtimeSettingsBuilder = new GeckoRuntimeSettings.Builder();
            runtimeSettingsBuilder.crashHandler(CrashReporterService.class);
            runtimeSettingsBuilder.contentBlocking((new ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.AD | ContentBlocking.AntiTracking.SOCIAL| ContentBlocking.AntiTracking.ANALYTIC))
                    .build());
            runtimeSettingsBuilder.consoleOutput(SettingsStore.getInstance(context).isConsoleLogsEnabled());
            runtimeSettingsBuilder.displayDensityOverride(SettingsStore.getInstance(context).getDisplayDensity());
            runtimeSettingsBuilder.remoteDebuggingEnabled(SettingsStore.getInstance(context).isRemoteDebuggingEnabled());
            runtimeSettingsBuilder.displayDpiOverride(SettingsStore.getInstance(context).getDisplayDpi());
            runtimeSettingsBuilder.screenSizeOverride(SettingsStore.getInstance(context).getMaxWindowWidth(),
                    SettingsStore.getInstance(context).getMaxWindowHeight());
            runtimeSettingsBuilder.autoplayDefault(SettingsStore.getInstance(mContext).isAutoplayEnabled() ? GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED : GeckoRuntimeSettings.AUTOPLAY_DEFAULT_BLOCKED);

            if (SettingsStore.getInstance(context).getTransparentBorderWidth() > 0) {
                runtimeSettingsBuilder.useMaxScreenDepth(true);
            }

            if (BuildConfig.DEBUG) {
                runtimeSettingsBuilder.arguments(new String[] { "-purgecaches" });
                runtimeSettingsBuilder.debugLogging(true);
                runtimeSettingsBuilder.aboutConfigEnabled(true);
            } else {
                runtimeSettingsBuilder.debugLogging(SettingsStore.getInstance(context).isDebugLogginEnabled());
            }

            mRuntime = GeckoRuntime.create(context, runtimeSettingsBuilder.build());
            for (String extension: WEB_EXTENSIONS) {
                String path = "resource://android/assets/web_extensions/" + extension + "/";
                mRuntime.registerWebExtension(new WebExtension(path));
            }

        } else {
            mRuntime.attachTo(context);
        }
    }

    public GeckoRuntime getRuntime() {
        return mRuntime;
    }

    public void initializeServices() {
        mServices = ((VRBrowserApplication)mContext.getApplicationContext()).getServices();
    }

    public void initializeStores(Context context) {
        mBookmarksStore = new BookmarksStore(context);
        mHistoryStore = new HistoryStore(context);
    }

    public Session createSession(boolean aPrivateMode) {
        return createSession(aPrivateMode, null, Session.SESSION_OPEN);
    }

    public Session createSession(boolean aPrivateMode, @Nullable SessionSettings aSettings, @Session.SessionOpenModeFlags int aOpenMode) {
        Session session = new Session(mContext, mRuntime, aPrivateMode, aSettings, aOpenMode);
        session.setPermissionDelegate(this);
        session.addNavigationListener(mServices);
        mSessions.add(session);

        return session;
    }

    public Session createSession(SessionState aRestoreState) {
        Session session = new Session(mContext, mRuntime, aRestoreState);
        session.setPermissionDelegate(this);
        session.addNavigationListener(mServices);
        mSessions.add(session);

        return session;
    }

    public void destroySession(Session aSession) {
        mSessions.remove(aSession);
        if (aSession != null) {
            aSession.setPermissionDelegate(null);
            aSession.removeNavigationListener(mServices);
            aSession.shutdown();
        }
    }

    public @Nullable Session getSession(String aId) {
        return mSessions.stream().filter(session -> session.getId().equals(aId)).findFirst().orElse(null);
    }

    public void setActiveSession(Session aSession) {
        mActiveSession = aSession;
    }

    public Session getActiveSession() {
        return mActiveSession;
    }

    public ArrayList<Session> getSortedSessions(boolean aPrivateMode) {
        ArrayList<Session> result = new ArrayList<>(mSessions);
        result.removeIf(session -> session.isPrivateMode() != aPrivateMode);
        result.sort((o1, o2) -> (int)(o2.getLastUse() - o1.getLastUse()));
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

    public void setMultiprocess(final boolean aEnabled) {
        for (Session session: mSessions) {
            session.setMultiprocess(aEnabled);
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

    public void setAutoplayEnabled(final boolean enabled) {
        if (mRuntime != null) {
            mRuntime.getSettings().setAutoplayDefault(enabled ?
                    GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED :
                    GeckoRuntimeSettings.AUTOPLAY_DEFAULT_BLOCKED);
        }
    }

    public boolean getAutoplayEnabled() {
        if (mRuntime != null) {
            return mRuntime.getSettings().getAutoplayDefault() == GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED;
        }

        return false;
    }

    public void setLocales(List<String> locales) {
        if (mRuntime != null) {
            mRuntime.getSettings().setLocales(locales.stream().toArray(String[]::new));
        }
    }

    public void clearCache(long clearFlags) {
        for (Session session: mSessions) {
            session.clearCache(clearFlags);
        }
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
