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
        service.customEvent("windowsResizeEvent");
    }

    public static void windowsMoveEvent() {
        if (service == null) {
            return;
        }
        service.customEvent("windowsMoveEvent");
    }

    public static void activePlacementEvent(int from, boolean active) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("from", from);
        bundle.putBoolean("active", active);
        service.customEvent("activePlacementEvent", bundle);
    }

    public static void openWindowsEvent(int from, int to, boolean isPrivate) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("from", from);
        bundle.putInt("to", to);
        bundle.putBoolean("isPrivate", isPrivate);
        service.customEvent("openWindowsEvent", bundle);
    }

    public static void resetOpenedWindowsCount(int number, boolean isPrivate) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("number", number);
        bundle.putBoolean("isPrivate", isPrivate);
        service.customEvent("resetOpenedWindowsCount", bundle);
    }

    public static void sessionStop() {
        if (service == null) {
            return;
        }
        service.customEvent("sessionStop");
    }

    @UiThread
    public static void urlBarEvent(boolean aIsUrl) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean("isUrl", aIsUrl);
        service.customEvent("urlBarEvent", bundle);
    }

    @UiThread
    public static void voiceInputEvent() {
        if (service == null) {
            return;
        }
        service.customEvent("voiceInputEvent");
    }

    // Records how long an immersive session lasted. The caller (VRBrowserActivity) owns the start timestamp.
    public static void immersiveTime(long durationMillis) {
        if (service == null) {
            return;
        }
        service.timedEvent("immersiveSession", durationMillis, null);
    }

    public static void openWindowEvent(int windowId) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("windowId", windowId);
        service.customEvent("openWindowEvent", bundle);
    }

    public static void closeWindowEvent(int windowId) {
        if (service == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("windowId", windowId);
        service.customEvent("closeWindowEvent", bundle);
    }

    public static void newWindowOpenEvent() {
        if (service == null) {
            return;
        }
        service.customEvent("newWindowOpenEvent");
    }

    public static class FxA implements FxAEntryPoint {

        public static void signIn() {
            if (service == null) {
                return;
            }
            service.customEvent("FxA_signIn");
        }

        public static void signInResult(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.customEvent("FxA_signInResult", bundle);
        }

        public static void signOut() {
            if (service == null) {
                return;
            }
            service.customEvent("FxA_signOut");
        }

        public static void bookmarksSyncStatus(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.customEvent("FxA_bookmarksSyncStatus");
        }

        public static void historySyncStatus(boolean status) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putBoolean("status", status);
            service.customEvent("FxA_historySyncStatus", bundle);
        }

        public static void sentTab() {
            if (service == null) {
                return;
            }
            service.customEvent("FxA_sentTab");
        }

        public static void receivedTab(@NonNull mozilla.components.concept.sync.DeviceType source) {
            if (service == null) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putInt("source", source.ordinal());
            service.customEvent("FxA_receivedTab", bundle);
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
            service.customEvent("tab_opened", bundle);
        }

        public static void activatedEvent() {
            if (service == null) {
                return;
            }
            service.customEvent("tab_activated");
        }
    }
}
