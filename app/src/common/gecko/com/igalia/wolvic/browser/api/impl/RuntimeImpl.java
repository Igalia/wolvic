package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WRuntimeSettings;
import com.igalia.wolvic.browser.api.WWebExtensionController;

import org.mozilla.geckoview.CrashHandler;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.CrashReporter;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoVRManager;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.StorageController;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import kotlin.Lazy;
import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;

public class RuntimeImpl implements WRuntime {
    private Context mContext;
    private GeckoRuntime mRuntime;
    private RuntimeSettingsImpl mSettings;
    private WebExtensionControllerImpl mWebExtensionController;
    private GeckoWebExecutor mExecutor;

    public RuntimeImpl(Context ctx, WRuntimeSettings settings) {
        mContext = ctx;
        GeckoRuntimeSettings.Builder builder = new GeckoRuntimeSettings.Builder();
        builder.crashHandler(settings.getCrashHandler())
                .aboutConfigEnabled(settings.isAboutConfigEnabled())
                .allowInsecureConnections((int)settings.getAllowInsecureConenctions())
                .contentBlocking(new ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlockingDelegateImpl.toGeckoAntitracking(settings.getContentBlocking().getAntiTracking()))
                        .enhancedTrackingProtectionLevel(ContentBlockingDelegateImpl.toGeckoEtpLevel(settings.getContentBlocking().getEnhancedTrackingProtectionLevel()))
                        .cookieBehavior(ContentBlockingDelegateImpl.toGeckoCookieBehavior(settings.getContentBlocking().getCookieBehavior()))
                        .cookieBehaviorPrivateMode(ContentBlockingDelegateImpl.toGeckoCookieBehavior(settings.getContentBlocking().getCookieBehaviorPrivate()))
                        .safeBrowsing(ContentBlockingDelegateImpl.toGeckoSafeBrowsing(settings.getContentBlocking().getSafeBrowsing()))
                        .build())
                .displayDensityOverride(settings.getDisplayDensityOverride())
                .remoteDebuggingEnabled(settings.isRemoteDebugging())
                .displayDpiOverride(settings.getDisplayDpiOverride())
                .enterpriseRootsEnabled(settings.isEnterpriseRootsEnabled())
                .screenSizeOverride(settings.getScreenWidthOverride(), settings.getScreenHeightOverride())
                .inputAutoZoomEnabled(settings.isInputAutoZoomEnabled())
                .doubleTapZoomingEnabled(settings.isDoubleTapZoomingEnabled())
                .debugLogging(settings.isConsoleServiceToLogcat())
                .consoleOutput(settings.isConsoleOutputEnabled())
                .loginAutofillEnabled(settings.isAutofillLoginsEnabled())
                .configFilePath(settings.getConfigFilePath())
                .javaScriptEnabled(settings.isJavaScriptEnabled())
                .glMsaaLevel(settings.getGlMsaaLevel())
                .webManifest(settings.isWebManifestEnabled())
                .pauseForDebugger(settings.isPauseForDebuggerEnabled())
                .preferredColorScheme(toGeckoColorScheme(settings.getPreferredColorScheme()));

        mRuntime = GeckoRuntime.create(ctx, builder.build());
        mSettings = new RuntimeSettingsImpl(mRuntime, settings);
        mWebExtensionController = new WebExtensionControllerImpl(mRuntime);
        mExecutor = new GeckoWebExecutor(mRuntime);
    }

    GeckoRuntime getGeckoRuntime() {
        return  mRuntime;
    }


    @Override
    public WRuntimeSettings getSettings() {
        return mSettings;
    }

    @NonNull
    @Override
    public WResult<Void> clearData(long flags) {
        return new ResultImpl<>(mRuntime.getStorageController().clearData(toGeckoStorageFlags(flags)));
    }

    @NonNull
    @Override
    public WWebExtensionController getWebExtensionController() {
        return mWebExtensionController;
    }

    @NonNull
    @Override
    public void setUpLoginPersistence(Lazy<LoginsStorage> storage) {
        mRuntime.setAutocompleteStorageDelegate(GeckoAutocompleteDelegateWrapper.create(storage, mSettings.isAutofillLoginsEnabled()));
    }

    @NonNull
    @Override
    public Client createFetchClient(Context context) {
        return GeckoViewFetchClient.create(mExecutor);
    }

    @Override
    public void setExternalVRContext(long aContext) {
        GeckoVRManager.setExternalContext(aContext);
    }

    @Override
    public void setContainerView(@NonNull ViewGroup containerView) {
        // No op. Gecko doesn't require views to render GeckoSessions. See GeckoDisplay.
    }

    @Override
    public float getDensity() {
        // GeckoDisplay uses 1.0 density and internally scales the devicePixelRatio to provided Surface.
        return 1.0f;
    }

    @Override
    public void configurationChanged(@NonNull Configuration newConfig) {
        mRuntime.configurationChanged(newConfig);
    }

    @Override
    public void appendAppNotesToCrashReport(@NonNull String notes) {
        mRuntime.appendAppNotesToCrashReport(notes);
    }

    @NonNull
    @Override
    public WResult<String> sendCrashReport(@NonNull Context context, @NonNull File minidumpFile, @NonNull File extrasFile, @NonNull String appName) throws IOException, URISyntaxException {
        return new ResultImpl<>(CrashReporter.sendCrashReport(context, minidumpFile, extrasFile, appName));
    }

    @Override
    public Thread.UncaughtExceptionHandler createCrashHandler(Context appContext, Class<? extends Service> handlerService) {
        return new CrashHandler(appContext, handlerService) {
            @Override
            public Bundle getCrashExtras(final Thread thread, final Throwable exc) {
                final Bundle extras = super.getCrashExtras(thread, exc);
                if (extras == null) {
                    return null;
                }
                extras.putString("Version", org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION);
                extras.putString("BuildID", org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID);
                extras.putString("Vendor", org.mozilla.geckoview.BuildConfig.MOZ_APP_VENDOR);
                extras.putString("ReleaseChannel", org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL);
                return extras;
            }
        };
    }

    @NonNull
    @Override
    public CrashReportIntent getCrashReportIntent() {
        return new CrashReportIntent(GeckoRuntime.ACTION_CRASHED, GeckoRuntime.EXTRA_MINIDUMP_PATH, GeckoRuntime.EXTRA_EXTRAS_PATH, GeckoRuntime.EXTRA_CRASH_PROCESS_TYPE);
    }

    static int toGeckoColorScheme(@WRuntimeSettings.ColorScheme int flags) {
        switch (flags) {
            case WRuntimeSettings.COLOR_SCHEME_DARK:
                return GeckoRuntimeSettings.COLOR_SCHEME_DARK;
            case WRuntimeSettings.COLOR_SCHEME_LIGHT:
                return GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;
            case WRuntimeSettings.COLOR_SCHEME_SYSTEM:
                return GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM;
        }

        throw new RuntimeException("Unreachable code");
    }

    private long toGeckoStorageFlags(@WRuntime.StorageControllerClearFlags long flags) {
        long res = 0;
        if ((flags & ClearFlags.COOKIES) != 0) {
            res |= StorageController.ClearFlags.COOKIES;
        }
        if ((flags & ClearFlags.NETWORK_CACHE) != 0) {
            res |= StorageController.ClearFlags.NETWORK_CACHE;
        }
        if ((flags & ClearFlags.IMAGE_CACHE) != 0) {
            res |= StorageController.ClearFlags.IMAGE_CACHE;
        }
        if ((flags & ClearFlags.DOM_STORAGES) != 0) {
            res |= StorageController.ClearFlags.DOM_STORAGES;
        }
        if ((flags & ClearFlags.AUTH_SESSIONS) != 0) {
            res |= StorageController.ClearFlags.AUTH_SESSIONS;
        }
        if ((flags & ClearFlags.PERMISSIONS) != 0) {
            res |= StorageController.ClearFlags.PERMISSIONS;
        }
        if ((flags & ClearFlags.SITE_DATA) != 0) {
            res |= StorageController.ClearFlags.SITE_DATA;
        }
        if ((flags & ClearFlags.ALL_CACHES) != 0) {
            res |= StorageController.ClearFlags.ALL_CACHES;
        }
        if ((flags & ClearFlags.ALL) != 0) {
            res |= StorageController.ClearFlags.ALL;
        }

        return res;
    }
}
