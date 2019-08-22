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
import org.mozilla.vrbrowser.browser.BookmarksStore;
import org.mozilla.vrbrowser.browser.HistoryStore;
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionStore implements GeckoSession.PermissionDelegate {

    public final int NO_ACTIVE_STORE_ID = -1;

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
    private HashMap<Integer, SessionStack> mSessionStacks;
    private Integer mActiveStoreId;
    private PermissionDelegate mPermissionDelegate;
    private BookmarksStore mBookmarksStore;
    private HistoryStore mHistoryStore;

    private SessionStore() {
        mSessionStacks = new HashMap<>();
        mActiveStoreId = NO_ACTIVE_STORE_ID;
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

            if (SettingsStore.getInstance(context).getTransparentBorderWidth() > 0) {
                runtimeSettingsBuilder.useMaxScreenDepth(true);
            }

            if (BuildConfig.DEBUG) {
                runtimeSettingsBuilder.arguments(new String[] { "-purgecaches" });
                runtimeSettingsBuilder.debugLogging(true);
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

    public void initializeStores(Context context) {
        mBookmarksStore = new BookmarksStore(context);
        mHistoryStore = new HistoryStore(context);
    }

    public SessionStack createSessionStack(int storeId, boolean privateMode) {
        SessionStack store = new SessionStack(mContext, mRuntime, privateMode);
        store.setPermissionDelegate(this);
        mSessionStacks.put(storeId, store);

        return store;
    }

    public void destroySessionStack(int storeId) {
        SessionStack store = mSessionStacks.remove(storeId);
        if (store != null) {
            store.setPermissionDelegate(null);
            store.shutdown();
        }
    }

    public void setActiveStore(int storeId) {
        mActiveStoreId = storeId;
    }

    public SessionStack getSessionStack(int storeId) {
        return mSessionStacks.get(storeId);
    }

    public SessionStack getActiveStore() {
        return mSessionStacks.get(mActiveStoreId);
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

    public void onPause() {
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setActive(false);
        }
    }

    public void onResume() {
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setActive(true);
        }
    }

    public void onDestroy() {
        HashMap<Integer, SessionStack> sessionStacks = new HashMap<>(mSessionStacks);
        for (Map.Entry<Integer, SessionStack> entry : sessionStacks.entrySet()) {
            destroySessionStack(entry.getKey());
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
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setServo(enabled);
        }
    }

    public void setUaMode(final int mode) {
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setUaMode(mode);
        }
    }

    public void setMultiprocess(final boolean aEnabled) {
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setMultiprocess(aEnabled);
        }
    }

    public void setTrackingProtection(final boolean aEnabled) {
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().setTrackingProtection(aEnabled);
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
        for (Map.Entry<Integer, SessionStack> entry : mSessionStacks.entrySet()) {
            entry.getValue().clearCache(clearFlags);
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
