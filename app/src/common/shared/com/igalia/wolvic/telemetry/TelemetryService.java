package com.igalia.wolvic.telemetry;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.utils.SystemUtils;

import mozilla.components.concept.sync.FxAEntryPoint;

public class TelemetryService {

    private final static String APP_NAME = "wolvic";
    private final static String LOGTAG = SystemUtils.createLogtag(TelemetryService.class);
    private static boolean initialized = false;
    private static Context context = null;
    private static ITelemetry service;
    private static boolean started;

    // We should call this at the application initial stage.
    public static void init(@NonNull Context aContext) {
        if (initialized)
            return;

        context = aContext;
        initialized = true;

        final boolean telemetryEnabled = SettingsStore.getInstance(aContext).isTelemetryEnabled();
        if (telemetryEnabled) {
            start();
        }
    }

    public static void setService(ITelemetry impl) {
        service = impl;
        if (started && service != null) {
            service.start();
        }
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void start() {
        started = true;
        if (service != null) {
            service.start();
        }
    }

    // It would be called when users turn on/off the setting of telemetry.
    // e.g., SettingsStore.getInstance(context).setTelemetryEnabled();
    public static void stop() {
        started = false;
        if (service != null) {
            service.stop();
        }
    }

    // Signal model: durations -> timed spans; discrete counts -> counter metrics;
    // funnel/journey-shaped actions -> events (which retain session context).

    // Records how long a page took to load. The caller (Session) owns the start timestamp so that
    // concurrent loads across windows are measured independently. URLs are **not** recorded for privacy.
    public static void pageLoadTime(long durationMillis) {
        if (service == null) {
            return;
        }
        service.timedEvent("pageLoad", durationMillis, null);
    }

    public static void windowsResizeEvent() {
        if (service == null) {
            return;
        }
        service.count("windows_resize", null);
    }

    public static void windowsMoveEvent() {
        if (service == null) {
            return;
        }
        service.count("windows_move", null);
    }

    public static void activePlacementEvent(int from, boolean active) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("from", from);
        bundle.putBoolean("active", active);
        service.count("active_placement", bundle);
    }

    public static void openWindowsEvent(int from, int to, boolean isPrivate) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("from", from);
        bundle.putInt("to", to);
        bundle.putBoolean("isPrivate", isPrivate);
        service.count("windows_open", bundle);
    }

    public static void resetOpenedWindowsCount(int number, boolean isPrivate) {
        if (service == null) {
            return;
        }
        // A snapshot of the current window count -> event (a counter can't carry a value).
        Bundle bundle = new Bundle();
        bundle.putInt("number", number);
        bundle.putBoolean("isPrivate", isPrivate);
        service.event("windows_reset", bundle);
    }

    public static void sessionStop() {
        if (service == null) {
            return;
        }
        service.count("session_stop", null);
    }

    @UiThread
    public static void urlBarEvent(boolean aIsUrl) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean("isUrl", aIsUrl);
        service.count("urlbar", bundle);
    }

    @UiThread
    public static void voiceInputEvent() {
        if (service == null) {
            return;
        }
        service.count("voice_input", null);
    }

    // Records how long an immersive session lasted. The caller (VRBrowserActivity) owns the start timestamp.
    public static void immersiveTime(long durationMillis) {
        if (service == null) {
            return;
        }
        service.timedEvent("immersiveSession", durationMillis, null);
    }

    // windowId is intentionally not recorded: it is high-cardinality and would explode a metric's time-series count. We keep only the aggregate count.
    public static void openWindowEvent(int windowId) {
        if (service == null) {
            return;
        }
        service.count("window_open", null);
    }

    public static void closeWindowEvent(int windowId) {
        if (service == null) {
            return;
        }
        service.count("window_close", null);
    }

    public static void newWindowOpenEvent() {
        if (service == null) {
            return;
        }
        service.count("new_window_open", null);
    }

    public static class FxA implements FxAEntryPoint {

        public static void signIn() {
            if (service == null) {
                return;
            }
            service.event("fxa_sign_in", null);
        }

        public static void signInResult(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.event("fxa_sign_in_result", bundle);
        }

        public static void signOut() {
            if (service == null) {
                return;
            }
            service.event("fxa_sign_out", null);
        }

        public static void bookmarksSyncStatus(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.count("fxa_bookmarks_sync", bundle);
        }

        public static void historySyncStatus(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.count("fxa_history_sync", bundle);
        }

        public static void sentTab() {
            if (service == null) {
                return;
            }
            service.count("fxa_sent_tab", null);
        }

        public static void receivedTab(@NonNull mozilla.components.concept.sync.DeviceType source) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putInt("source", source.ordinal());
            service.count("fxa_received_tab", bundle);
        }

        @NonNull
        @Override
        public String getEntryName() {
            return APP_NAME;
        }
    }

    public static class Tabs {

        public enum TabSource {
            CONTEXT_MENU,       // Tab opened from the browsers long click context menu
            TABS_DIALOG,        // Tab opened from the tabs dialog
            BOOKMARKS,          // Tab opened from the bookmarks panel
            HISTORY,            // Tab opened from the history panel
            DOWNLOADS,          // Tab opened from the downloads panel
            FXA_LOGIN,          // Tab opened by the FxA login flow
            RECEIVED,           // Tab opened by FxA when a tab is received
            PRE_EXISTING,       // Tab opened as a result of restoring the last session
            BROWSER,            // Tab opened by the browser as a result of a new window open
        }

        public static void openedCounter(@NonNull TabSource source) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putInt("source", source.ordinal());
            service.count("tab_opened", bundle);
        }

        public static void activatedEvent() {
            if (service == null) {
                return;
            }
            service.count("tab_activated", null);
        }
    }
}
