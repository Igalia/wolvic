package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.PageZoomLevels;
import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-site page zoom for GeckoView, which has no page zoom API of its own.
 *
 * Zoom levels are remembered per host, like desktop browsers do, and applied through the built-in
 * page_zoom extension, which sets BrowsingContext.fullZoom from the parent process. Its content
 * script connects to us on every top-level page load and we answer with the level for that host.
 * Levels set in private sessions are kept in memory until the last private session is closed.
 */
class PageZoomController {
    private static final String LOGTAG = SystemUtils.createLogtag(PageZoomController.class);

    private static final String EXTENSION_ID = "page-zoom@igalia.com";
    private static final String EXTENSION_URL = "resource://android/assets/extensions/page_zoom/";
    private static final String NATIVE_APP = "pageZoom";
    private static final String PREFS_NAME = "page_zoom";

    private static class SessionState {
        @Nullable WebExtension.Port port;
        @Nullable String host;
    }

    private final SharedPreferences mPrefs;
    private final Map<String, Integer> mPrivateLevels = new HashMap<>();
    private final Map<SessionImpl, SessionState> mSessions = new HashMap<>();
    private @Nullable WebExtension mExtension;

    PageZoomController(@NonNull Context context, @NonNull GeckoRuntime runtime) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_URL, EXTENSION_ID).accept(extension -> {
            mExtension = extension;
            for (SessionImpl session : mSessions.keySet()) {
                setMessageDelegate(session);
            }
        }, throwable -> Log.e(LOGTAG, "Unable to install the page zoom extension: " + throwable));
    }

    void attach(@NonNull SessionImpl session) {
        mSessions.put(session, new SessionState());
        if (mExtension != null) {
            setMessageDelegate(session);
        }
    }

    void detach(@NonNull SessionImpl session) {
        if (mSessions.remove(session) == null) {
            return;
        }
        if (mExtension != null) {
            session.getGeckoSession().getWebExtensionController().setMessageDelegate(mExtension, null, NATIVE_APP);
        }
        if (isPrivate(session) && mSessions.keySet().stream().noneMatch(PageZoomController::isPrivate)) {
            mPrivateLevels.clear();
        }
    }

    int getZoomLevel(@NonNull SessionImpl session) {
        SessionState state = mSessions.get(session);
        if (state == null || state.host == null) {
            return PageZoomLevels.DEFAULT_LEVEL;
        }
        return getZoomLevel(state.host, isPrivate(session));
    }

    void zoomIn(@NonNull SessionImpl session) {
        setZoomLevel(session, PageZoomLevels.zoomIn(getZoomLevel(session)));
    }

    void zoomOut(@NonNull SessionImpl session) {
        setZoomLevel(session, PageZoomLevels.zoomOut(getZoomLevel(session)));
    }

    private void setZoomLevel(@NonNull SessionImpl session, int level) {
        SessionState state = mSessions.get(session);
        if (state == null || state.host == null) {
            return;
        }
        String host = state.host;
        if (isPrivate(session)) {
            mPrivateLevels.put(host, level);
        } else if (level == PageZoomLevels.DEFAULT_LEVEL) {
            mPrefs.edit().remove(host).apply();
        } else {
            mPrefs.edit().putInt(host, level).apply();
        }

        // Like desktop browsers, update every page showing the same host.
        for (Map.Entry<SessionImpl, SessionState> entry : mSessions.entrySet()) {
            if (host.equals(entry.getValue().host)) {
                applyZoomLevel(entry.getValue(), getZoomLevel(host, isPrivate(entry.getKey())));
            }
        }
    }

    private int getZoomLevel(@NonNull String host, boolean isPrivate) {
        Integer level = isPrivate ? mPrivateLevels.get(host) : null;
        return level != null ? level : mPrefs.getInt(host, PageZoomLevels.DEFAULT_LEVEL);
    }

    private void setMessageDelegate(@NonNull SessionImpl session) {
        session.getGeckoSession().getWebExtensionController().setMessageDelegate(mExtension, new WebExtension.MessageDelegate() {
            @Override
            public void onConnect(@NonNull WebExtension.Port port) {
                SessionState state = mSessions.get(session);
                if (state == null || !port.sender.isTopLevel()) {
                    port.disconnect();
                    return;
                }
                state.port = port;
                state.host = getHost(port.sender.url);
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onDisconnect(@NonNull WebExtension.Port disconnected) {
                        // A new page may have connected before the previous one went away.
                        if (state.port == disconnected) {
                            state.port = null;
                            state.host = null;
                        }
                    }
                });
                if (state.host != null) {
                    // Always apply, even the default, since the BrowsingContext may keep the previous page's zoom.
                    applyZoomLevel(state, getZoomLevel(state.host, isPrivate(session)));
                }
            }
        }, NATIVE_APP);
    }

    private static void applyZoomLevel(@NonNull SessionState state, int level) {
        if (state.port == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("type", "apply");
            message.put("zoom", level / 100.0);
            state.port.postMessage(message);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Unable to send the page zoom level: " + e);
        }
    }

    private static boolean isPrivate(@NonNull SessionImpl session) {
        return session.getGeckoSession().getSettings().getUsePrivateMode();
    }

    private static @Nullable String getHost(@Nullable String url) {
        if (url == null) {
            return null;
        }
        String host = Uri.parse(url).getHost();
        return TextUtils.isEmpty(host) ? null : host;
    }
}
