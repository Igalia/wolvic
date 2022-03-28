package com.igalia.wolvic.browser.api;

import android.app.Service;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class WRuntimeSettings {
    /** A light theme for web content is preferred. */
    public static final int COLOR_SCHEME_LIGHT = 0;
    /** A dark theme for web content is preferred. */
    public static final int COLOR_SCHEME_DARK = 1;
    /** The preferred color scheme will be based on system settings. */
    public static final int COLOR_SCHEME_SYSTEM = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({COLOR_SCHEME_LIGHT, COLOR_SCHEME_DARK, COLOR_SCHEME_SYSTEM})
            public @interface ColorScheme {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ALLOW_ALL, HTTPS_ONLY_PRIVATE, HTTPS_ONLY})
            public @interface HttpsOnlyMode {}

    /** Allow all insecure connections */
    public static final int ALLOW_ALL = 0;
    /** Allow insecure connections in normal browsing, but only HTTPS in private browsing. */
    public static final int HTTPS_ONLY_PRIVATE = 1;
    /** Only allow HTTPS connections. */
    public static final int HTTPS_ONLY = 2;


    /** Settings builder used to construct the settings object. */
    @AnyThread
    public static final class Builder {

        private WRuntimeSettings mSettings = new WRuntimeSettings();

        private WRuntimeSettings getSettings() {
            return mSettings;
        }

        public WRuntimeSettings build() {
            return mSettings;
        }
        /**
         * Set the custom Browser process arguments.
         *
         * @param args The Browser process arguments.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder arguments(final @NonNull String[] args) {
            if (args == null) {
                throw new IllegalArgumentException("Arguments must not  be null");
            }
            getSettings().mArgs = args;
            return this;
        }

        /**
         * Set the custom Browser intent extras.
         *
         * @param extras The Browser intent extras.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder extras(final @NonNull Bundle extras) {
            if (extras == null) {
                throw new IllegalArgumentException("Extras must not  be null");
            }
            getSettings().mExtras = extras;
            return this;
        }

        /**
         * Path to configuration file from which Browser will read configuration options such as Browser
         * process arguments, environment variables, and preferences.
         *
         * <p>Note: this feature is only available for <code>{@link Build.VERSION#SDK_INT} &gt; 21</code>, on
         * older devices this will be silently ignored.
         *
         * @param configFilePath Configuration file path to read from, or <code>null</code> to use
         *     default location <code>/data/local/tmp/$PACKAGE-Browser-config.yaml</code>.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder configFilePath(final @Nullable String configFilePath) {
            getSettings().mConfigFilePath = configFilePath;
            return this;
        }

        /**
         * Set whether JavaScript support should be enabled.
         *
         * @param flag A flag determining whether JavaScript should be enabled. Default is true.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder javaScriptEnabled(final boolean flag) {
            getSettings().mJavaScript = flag;
            return this;
        }

        /**
         * Set whether remote debugging support should be enabled.
         *
         * @param enabled True if remote debugging should be enabled.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder remoteDebuggingEnabled(final boolean enabled) {
            getSettings().mRemoteDebugging = enabled;
            return this;
        }

        /**
         * Set whether support for web fonts should be enabled.
         *
         * @param flag A flag determining whether web fonts should be enabled. Default is true.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder webFontsEnabled(final boolean flag) {
            getSettings().mWebFonts = flag;
            return this;
        }

        /**
         * Set whether there should be a pause during startup. This is useful if you need to wait for a
         * debugger to attach.
         *
         * @param enabled A flag determining whether there will be a pause early in startup. Defaults to
         *     false.
         * @return This Builder.
         */
        public @NonNull
        WRuntimeSettings.Builder pauseForDebugger(final boolean enabled) {
            getSettings().mDebugPause = enabled;
            return this;
        }
        /**
         * Set whether the to report the full bit depth of the device.
         *
         * <p>By default, 24 bits are reported for high memory devices and 16 bits for low memory
         * devices. If set to true, the device's maximum bit depth is reported. On most modern devices
         * this will be 32 bit screen depth.
         *
         * @param enable A flag determining whether maximum screen depth should be used.
         * @return This Builder.
         */
        public @NonNull
        WRuntimeSettings.Builder useMaxScreenDepth(final boolean enable) {
            getSettings().mUseMaxScreenDepth = enable;
            return this;
        }

        /**
         * Set whether web manifest support is enabled.
         *
         * <p>This controls if Browser actually downloads, or "obtains", web manifests and processes them.
         * Without setting this pref, trying to obtain a manifest throws.
         *
         * @param enabled A flag determining whether Web Manifest processing support is enabled.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder webManifest(final boolean enabled) {
            getSettings().mWebManifest = enabled;
            return this;
        }

        /**
         * Set whether or not web console messages should go to logcat.
         *
         * <p>Note: If enabled, Browser performance may be negatively impacted if content makes heavy use
         * of the console API.
         *
         * @param enabled A flag determining whether or not web console messages should be printed to
         *     logcat.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder consoleOutput(final boolean enabled) {
            getSettings().mConsoleOutput = enabled;
            return this;
        }

        /**
         * Set a font size factor that will operate as a global text zoom. All font sizes will be
         * multiplied by this factor.
         *
         * <p>The default factor is 1.0.
         *
         * <p>This setting cannot be modified if {@link WRuntimeSettings.Builder#automaticFontSizeAdjustment automatic
         * font size adjustment} has already been enabled.
         *
         * @param fontSizeFactor The factor to be used for scaling all text. Setting a value of 0
         *     disables both this feature and {@link WRuntimeSettings.Builder#fontInflation font inflation}.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder fontSizeFactor(final float fontSizeFactor) {
            getSettings().mFontSizeFactor = fontSizeFactor;
            return this;
        }

        /**
         * Enable the Enterprise Roots feature.
         *
         * <p>When Enabled, Browser will fetch the third-party root certificates added to the Android
         * OS CA store and will use them internally.
         *
         * @param enabled whether to enable this feature or not
         * @return The builder instance
         */
        public @NonNull
        WRuntimeSettings.Builder enterpriseRootsEnabled(final boolean enabled) {
            getSettings().mEnterpriseRootsEnabled = enabled;
            return this;
        }

        /**
         * Set whether or not font inflation for non mobile-friendly pages should be enabled. The
         * default value of this setting is <code>false</code>.
         *
         * <p>When enabled, font sizes will be increased on all pages that are lacking a &lt;meta&gt;
         * viewport tag and have been loaded in a session using {@link
         * WSessionSettings#VIEWPORT_MODE_MOBILE}. To improve readability, the font inflation logic
         * will attempt to increase font sizes for the main text content of the page only.
         *
         * <p>The magnitude of font inflation applied depends on the {@link WRuntimeSettings.Builder#fontSizeFactor font
         * size factor} currently in use.
         *
         * <p>This setting cannot be modified if {@link WRuntimeSettings.Builder#automaticFontSizeAdjustment automatic
         * font size adjustment} has already been enabled.
         *
         * @param enabled A flag determining whether or not font inflation should be enabled.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder fontInflation(final boolean enabled) {
            getSettings().mFontInflationEnabled = enabled;
            return this;
        }

        /**
         * Set the display density override.
         *
         * @param density The display density value to use for overriding the system default.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder displayDensityOverride(final float density) {
            getSettings().mDisplayDensityOverride = density;
            return this;
        }

        /**
         * Set the display DPI override.
         *
         * @param dpi The display DPI value to use for overriding the system default.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder displayDpiOverride(final int dpi) {
            getSettings().mDisplayDpiOverride = dpi;
            return this;
        }

        /**
         * Set the screen size override.
         *
         * @param width The screen width value to use for overriding the system default.
         * @param height The screen height value to use for overriding the system default.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder screenSizeOverride(final int width, final int height) {
            getSettings().mScreenWidthOverride = width;
            getSettings().mScreenHeightOverride = height;
            return this;
        }

        /**
         * Set whether login forms should be filled automatically if only one viable candidate is
         * provided via {@link WAutocomplete.StorageDelegate#onLoginFetch onLoginFetch}.
         *
         * @param enabled A flag determining whether login autofill should be enabled.
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder loginAutofillEnabled(final boolean enabled) {
            getSettings().mAutofillLogins = enabled;
            return this;
        }

        /**
         * When set, the specified {@link android.app.Service} will be started by an {@link
         * android.content.Intent} with action {@link WRuntime#ACTION_CRASHED} when a crash is
         * encountered. Crash details can be found in the Intent extras, such as {@link
         * WRuntime#EXTRA_MINIDUMP_PATH}. <br>
         * <br>
         * The crash handler Service must be declared to run in a different process from the {@link
         * WRuntime}. Additionally, the handler will be run as a foreground service, so the normal
         * rules about activating a foreground service apply. <br>
         * <br>
         * In practice, you have one of three options once the crash handler is started:
         *
         * <ul>
         *   <li>Call {@link android.app.Service#startForeground(int, android.app.Notification)}. You
         *       can then take as much time as necessary to report the crash.
         *   <li>Start an activity. Unless you also call {@link android.app.Service#startForeground(int,
         *       android.app.Notification)} this should be in a different process from the crash
         *       handler, since Android will kill the crash handler process as part of the background
         *       execution limitations.
         *   <li>Schedule work via {@link android.app.job.JobScheduler}. This will allow you to do
         *       substantial work in the background without execution limits.
         * </ul>
         *
         * <br>
         * You can use {@link CrashReporter} to send the report to Mozilla, which provides Mozilla with
         * data needed to fix the crash. Be aware that the minidump may contain personally identifiable
         * information (PII). Consult Mozilla's <a href="https://www.mozilla.org/en-US/privacy/">privacy
         * policy</a> for information on how this data will be handled.
         *
         * @param handler The class for the crash handler Service.
         * @return This builder instance.
         * @see <a href="https://developer.android.com/about/versions/oreo/background">Android
         *     Background Execution Limits</a>
         * @see WRuntime#ACTION_CRASHED
         */
        public @NonNull
        WRuntimeSettings.Builder crashHandler(final @Nullable Class<? extends Service> handler) {
            getSettings().mCrashHandler = handler;
            return this;
        }

        /**
         * Set the locale.
         *
         * @param requestedLocales List of locale codes in Browser format ("en" or "en-US").
         * @return The builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder locales(final @Nullable String[] requestedLocales) {
            getSettings().mRequestedLocales = requestedLocales;
            return this;
        }

        @SuppressWarnings("checkstyle:javadocmethod")
        public @NonNull
        WRuntimeSettings.Builder contentBlocking(final @NonNull WContentBlocking.Settings cb) {
            getSettings().mContentBlocking = cb;
            return this;
        }

        /**
         * Sets the preferred color scheme override for web content.
         *
         * @param scheme The preferred color scheme. Must be one of the {@link
         *     WRuntimeSettings#COLOR_SCHEME_LIGHT COLOR_SCHEME_*} constants.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder preferredColorScheme(final @ColorScheme int scheme) {
            getSettings().mPreferredColorScheme = scheme;
            return this;
        }

        /**
         * Set whether auto-zoom to editable fields should be enabled.
         *
         * @param flag True if auto-zoom should be enabled, false otherwise.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder inputAutoZoomEnabled(final boolean flag) {
            getSettings().mInputAutoZoom = flag;
            return this;
        }

        /**
         * Set whether double tap zooming should be enabled.
         *
         * @param flag True if double tap zooming should be enabled, false otherwise.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder doubleTapZoomingEnabled(final boolean flag) {
            getSettings().mDoubleTapZooming = flag;
            return this;
        }

        /**
         * Sets the WebGL MSAA level.
         *
         * @param level number of MSAA samples, 0 if MSAA should be disabled.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder glMsaaLevel(final int level) {
            getSettings().mGlMsaaLevel = level;
            return this;
        }


        /**
         * Enables Browser and Browser Logging. Logging is on by default. Does not control all logging
         * in Browser. Logging done in Java code must be stripped out at build time.
         *
         * @param enable True if logging is enabled.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder debugLogging(final boolean enable) {
            getSettings().mDevToolsConsoleToLogcat = enable;
            getSettings().mConsoleServiceToLogcat = enable;
            getSettings().mLogLevel = enable ? "Debug" : "Fatal";
            return this;
        }

        /**
         * Sets whether or not about:config should be enabled. This is a page that allows users to
         * directly modify Browser preferences. Modification of some preferences may cause the app to
         * break in unpredictable ways -- crashes, performance issues, security vulnerabilities, etc.
         *
         * @param flag True if about:config should be enabled, false otherwise.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder aboutConfigEnabled(final boolean flag) {
            getSettings().mAboutConfig = flag;
            return this;
        }

        /**
         * Sets whether or not pinch-zooming should be enabled when <code>user-scalable=no</code> is set
         * on the viewport.
         *
         * @param flag True if force user scalable zooming should be enabled, false otherwise.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder forceUserScalableEnabled(final boolean flag) {
            getSettings().mForceUserScalable = flag;
            return this;
        }

        /**
         * Sets whether and where insecure (non-HTTPS) connections are allowed.
         *
         * @param level One of the {@link WRuntimeSettings#ALLOW_ALL HttpsOnlyMode} constants.
         * @return This Builder instance.
         */
        public @NonNull
        WRuntimeSettings.Builder allowInsecureConnections(final @HttpsOnlyMode int level) {
            getSettings().mAllowInsecureConenctions = level;
            return this;
        }
    }

    public String[] getArgs() {
        return mArgs;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public String getConfigFilePath() {
        return mConfigFilePath;
    }

    public boolean isWebManifestEnabled() {
        return mWebManifest;
    }

    public boolean isJavaScriptEnabled() {
        return mJavaScript;
    }

    public boolean isRemoteDebugging() {
        return mRemoteDebugging;
    }

    public boolean isWebFonts() {
        return mWebFonts;
    }

    public boolean isConsoleOutputEnabled() {
        return mConsoleOutput;
    }

    public float getFontSizeFactor() {
        return mFontSizeFactor;
    }

    public boolean isEnterpriseRootsEnabled() {
        return mEnterpriseRootsEnabled;
    }

    public boolean isFontInflationEnabled() {
        return mFontInflationEnabled;
    }

    public boolean isInputAutoZoomEnabled() {
        return mInputAutoZoom;
    }

    public boolean isDoubleTapZoomingEnabled() {
        return mDoubleTapZooming;
    }

    public int getGlMsaaLevel() {
        return mGlMsaaLevel;
    }

    public boolean isConsoleServiceToLogcat() {
        return mConsoleServiceToLogcat;
    }

    public boolean isAboutConfigEnabled() {
        return mAboutConfig;
    }

    public boolean isForceUserScalable() {
        return mForceUserScalable;
    }

    public boolean isAutofillLoginsEnabled() {
        return mAutofillLogins;
    }

    public int getAllowInsecureConenctions() {
        return mAllowInsecureConenctions;
    }

    public int getPreferredColorScheme() {
        return mPreferredColorScheme;
    }

    public boolean isPauseForDebuggerEnabled() {
        return mDebugPause;
    }

    public boolean isUseMaxScreenDepth() {
        return mUseMaxScreenDepth;
    }

    public float getDisplayDensityOverride() {
        return mDisplayDensityOverride;
    }

    public int getDisplayDpiOverride() {
        return mDisplayDpiOverride;
    }

    public int getScreenWidthOverride() {
        return mScreenWidthOverride;
    }

    public int getScreenHeightOverride() {
        return mScreenHeightOverride;
    }

    public Class<? extends Service> getCrashHandler() {
        return mCrashHandler;
    }

    public String[] getRequestedLocales() {
        return mRequestedLocales;
    }

    public WContentBlocking.Settings getContentBlocking() {
        return mContentBlocking;
    }

    /**
     * Set whether or not web console messages should go to logcat.
     *
     * <p>Note: If enabled, performance may be negatively impacted if content makes heavy use of
     * the console API.
     *
     * @param enabled A flag determining whether or not web console messages should be printed to
     *     logcat.
     * @return This BrowserRuntimeSettings instance.
     */
    public void setConsoleOutputEnabled(final boolean enabled) {
        mConsoleOutput = enabled;
    };


    /**
     * Set whether remote debugging support should be enabled.
     *
     * @param enabled True if remote debugging should be enabled.
     * @return This BrowserRuntimeSettings instance.
     */
    public void setRemoteDebuggingEnabled(final boolean enabled) {
        mRemoteDebugging = enabled;
    };


    /**
     * Set the locale.
     *
     * @param requestedLocales An ordered list of locales in format ("en-US").
     */
    public void setLocales(final @Nullable String[] requestedLocales) {
        mRequestedLocales = requestedLocales;
    };


    /**
     * Set whether login forms should be filled automatically if only one viable candidate is provided
     * via {@link com.igalia.wolvic.browser.api.Autocomplete.StorageDelegate#onLoginFetch onLoginFetch}.
     *
     * @param enabled A flag determining whether login autofill should be enabled.
     * @return The builder instance.
     */
    public void setLoginAutofillEnabled(final boolean enabled) {
        mAutofillLogins = enabled;
    }


    String[] mArgs;
    Bundle mExtras;
    String mConfigFilePath;
    boolean mWebManifest = true;
    boolean mJavaScript = true;
    boolean mRemoteDebugging = false;
    boolean mWebFonts = true;
    boolean mConsoleOutput = false;
    float mFontSizeFactor = 100;
    boolean mEnterpriseRootsEnabled = false;
    boolean mFontInflationEnabled = false;
    boolean mInputAutoZoom = true;
    boolean mDoubleTapZooming = true;
    int mGlMsaaLevel = 0;
    String mLogLevel = "Debug";
    boolean mConsoleServiceToLogcat = true;
    boolean mDevToolsConsoleToLogcat = true;
    boolean mAboutConfig = true;
    boolean mForceUserScalable = false;
    boolean mAutofillLogins = true;
    @HttpsOnlyMode int mAllowInsecureConenctions = ALLOW_ALL;

    int mPreferredColorScheme = COLOR_SCHEME_SYSTEM;
    boolean mDebugPause;
    boolean mUseMaxScreenDepth;
    float mDisplayDensityOverride = -1.0f;
    int mDisplayDpiOverride;
    int mScreenWidthOverride;
    int mScreenHeightOverride;
    Class<? extends Service> mCrashHandler;
    String[] mRequestedLocales;
    protected WContentBlocking.Settings mContentBlocking;

}
