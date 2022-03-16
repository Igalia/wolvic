package com.igalia.wolvic.browser.api;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AnyThread;
import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URISyntaxException;

import kotlin.Lazy;
import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;

public interface WRuntime {

    class ClearFlags {
        /**
         * Cookies.
         */
        public static final long COOKIES = 1 << 0;

        /**
         * Network cache.
         */
        public static final long NETWORK_CACHE = 1 << 1;

        /**
         * Image cache.
         */
        public static final long IMAGE_CACHE = 1 << 2;

        /**
         * DOM storages.
         */
        public static final long DOM_STORAGES = 1 << 4;

        /**
         * Auth tokens and caches.
         */
        public static final long AUTH_SESSIONS = 1 << 5;

        /**
         * Site permissions.
         */
        public static final long PERMISSIONS = 1 << 6;

        /**
         * All caches.
         */
        public static final long ALL_CACHES = NETWORK_CACHE | IMAGE_CACHE;

        /**
         * All site settings (permissions, content preferences, security settings, etc.).
         */
        public static final long SITE_SETTINGS = 1 << 7 | PERMISSIONS;

        /**
         * All site-related data (cookies, storages, caches, permissions, etc.).
         */
        public static final long SITE_DATA =
                1 << 8 | COOKIES | DOM_STORAGES | ALL_CACHES | PERMISSIONS | SITE_SETTINGS;

        /**
         * All data.
         */
        public static final long ALL = 1 << 9;
    }

    @Retention(RetentionPolicy.SOURCE)
    @LongDef(
            flag = true,
            value = {
                    ClearFlags.COOKIES,
                    ClearFlags.NETWORK_CACHE,
                    ClearFlags.IMAGE_CACHE,
                    ClearFlags.DOM_STORAGES,
                    ClearFlags.AUTH_SESSIONS,
                    ClearFlags.PERMISSIONS,
                    ClearFlags.ALL_CACHES,
                    ClearFlags.SITE_SETTINGS,
                    ClearFlags.SITE_DATA,
                    ClearFlags.ALL
            })
            /* package */ @interface StorageControllerClearFlags {}


    WRuntimeSettings getSettings();

    /**
     * Clear data for all hosts.
     *
     * <p>Note: Any open session may re-accumulate previously cleared data. To ensure that no
     * persistent data is left behind, you need to close all sessions prior to clearing data.
     *
     * @param flags Combination of {@link ClearFlags}.
     * @return A {@link WResult} that will complete when clearing has finished.
     */
    @UiThread
    @NonNull
    WResult<Void> clearData(final @StorageControllerClearFlags long flags);


    /**
     * Returns a WebExtensionController for this WRuntime.
     *
     * @return an instance of {@link WWebExtensionController}.
     */
    @UiThread
    @NonNull
    WWebExtensionController getWebExtensionController();

    /**
     *  Set up the persistence and retrieval of LoginEntrys.
     *
     */
    @UiThread
    @NonNull
    void setUpLoginPersistence(Lazy<LoginsStorage> storage);

    /**
     * Creates a Client for fetching resources via HTTP/s.
     */
    @UiThread
    @NonNull
    Client createFetchClient(Context context);

    /**
     * Sets the External VR shared memory data context.
     * User for WebXR synchronization between the Application and the browser engine.
     * Params:
     * externalContext – A pointer to the external VR context.
     */
    void setExternalVRContext(long externalContext);

    /*
     * Sets the fragment manager. Some engines need it to add sessions/tabs into a view to display them.
     */
    void setFragmentManager(@NonNull FragmentManager fragmentManager, @NonNull ViewGroup container);


    /*
     * Returns the view density (devicePixelRatio) that should be used to display browser engine session.
     */
    @UiThread
    float getDensity();

    /**
     * Notify that the device configuration has changed.
     *
     * @param newConfig The new Configuration object, {@link android.content.res.Configuration}.
     */
    @UiThread
    void configurationChanged(final @NonNull Configuration newConfig);

    /**
     * Appends notes to crash report.
     *
     * @param notes The application notes to append to the crash report.
     */
    @AnyThread
    void appendAppNotesToCrashReport(@NonNull final String notes);


    /**
     * Sends a crash report to the associated crash report server. <br>
     * The {@code appName} needs to be whitelisted for the server to accept the crash. <a
     * href="https://bugzilla.mozilla.org/enter_bug.cgi?product=Socorro">File a bug</a> if you would
     * like to get your app added to the whitelist.
     *
     * @param context The current {@link Context}
     * @param minidumpFile A {@link File} referring to the minidump.
     * @param extrasFile A {@link File} referring to the extras file.
     * @param appName A human-readable app name.
     * @throws IOException This can be thrown if there was a networking error while sending the
     *     report.
     * @throws URISyntaxException This can be thrown if the crash server URI from the extra data was
     *     invalid.
     * @return A WResult containing the crash ID as a String.
     */
    @AnyThread
    @NonNull WResult<String> sendCrashReport(
            @NonNull final Context context,
            @NonNull final File minidumpFile,
            @NonNull final File extrasFile,
            @NonNull final String appName)
            throws IOException, URISyntaxException;


    /**
     * Create and register a CrashHandler for all threads and thread groups.
     *
     * @param appContext A Context for retrieving application information.
     */
     Thread.UncaughtExceptionHandler createCrashHandler(final Context appContext, final Class<? extends Service> handlerService);


    @NonNull CrashReportIntent getCrashReportIntent();

    class CrashReportIntent {
        final public String action_crashed;
        final public String extra_minidump_path;
        final public String extra_extras_path;
        final public String extra_crash_fatal;

        public CrashReportIntent(String action_crashed, String extra_minidump_path, String extra_extras_path, String extra_crash_fatal) {
            this.action_crashed = action_crashed;
            this.extra_minidump_path = extra_minidump_path;
            this.extra_extras_path = extra_extras_path;
            this.extra_crash_fatal = extra_crash_fatal;
        }
    }

}
