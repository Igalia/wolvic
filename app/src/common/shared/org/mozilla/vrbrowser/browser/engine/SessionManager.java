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
import org.mozilla.vrbrowser.browser.PermissionDelegate;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.crashreporting.CrashReporterService;

import java.util.HashMap;
import java.util.Map;

public class SessionManager implements GeckoSession.PermissionDelegate {

    public final int NO_ACTIVE_STORE_ID = -1;

    private static final String[] WEB_EXTENSIONS = new String[] {
            "webcompat_vimeo",
            "webcompat_youtube"
    };

    private static SessionManager mInstance;

    public static SessionManager get() {
        if (mInstance == null) {
            mInstance = new SessionManager();
        }
        return mInstance;
    }

    private Context mContext;
    private GeckoRuntime mRuntime;
    private HashMap<Integer, SessionStore> mSessionStores;
    private Integer mActiveStoreId;
    private PermissionDelegate mPermissionDelegate;
    private BookmarksStore mBookmarksStore;

    private SessionManager() {
        mSessionStores = new HashMap<>();
        mActiveStoreId = NO_ACTIVE_STORE_ID;
    }

    public void setContext(Context context, Bundle aExtras) {
        mContext = context;

        if (mRuntime == null) {
            // FIXME: Once GeckoView has a prefs API
            SessionUtils.vrPrefsWorkAround(context, aExtras);

            GeckoRuntimeSettings.Builder runtimeSettingsBuilder = new GeckoRuntimeSettings.Builder();
            runtimeSettingsBuilder.crashHandler(CrashReporterService.class);
            runtimeSettingsBuilder.contentBlocking((new ContentBlocking.Settings.Builder())
                    .categories(ContentBlocking.AT_AD | ContentBlocking.AT_SOCIAL | ContentBlocking.AT_ANALYTIC)
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

    public void initializeBookmarkStore(Context context) {
        mBookmarksStore = new BookmarksStore(context);
    }

    public SessionStore createSessionStore(int storeId) {
        SessionStore store = new SessionStore(mContext, mRuntime);
        store.setPermissionDelegate(this);
        store.registerListeners();
        mSessionStores.put(storeId, store);

        return store;
    }

    public void destroySessionStore(int storeId) {
        SessionStore store = mSessionStores.remove(storeId);
        if (store != null) {
            store.setPermissionDelegate(null);
            store.unregisterListeners();
            store.shutdown();
        }

        if (mRuntime != null)
            mRuntime.shutdown();
    }

    public void setActiveStore(int storeId) {
        mActiveStoreId = storeId;
    }

    public SessionStore getSessionStore(int storeId) {
        return mSessionStores.get(storeId);
    }

    public SessionStore getActiveStore() {
        return mSessionStores.get(mActiveStoreId);
    }

    public void setPermissionDelegate(PermissionDelegate delegate) {
        mPermissionDelegate = delegate;
    }

    public BookmarksStore getBookmarkStore() {
        return mBookmarksStore;
    }

    public void onPause() {
        for (Map.Entry<Integer, SessionStore> entry : mSessionStores.entrySet()) {
            entry.getValue().setActive(false);
        }
    }

    public void onResume() {
        for (Map.Entry<Integer, SessionStore> entry : mSessionStores.entrySet()) {
            entry.getValue().setActive(true);
        }
    }

    public void onDestroy() {
        for (Map.Entry<Integer, SessionStore> entry : mSessionStores.entrySet()) {
            destroySessionStore(entry.getKey());
        }

        if (mBookmarksStore != null) {
            mBookmarksStore.removeAllListeners();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mRuntime != null) {
            mRuntime.configurationChanged(newConfig);
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
            return mRuntime.getSettings().getAutoplayDefault() == GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED ?
                    true :
                    false;
        }

        return false;
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
