package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WRuntimeSettings;
import com.igalia.wolvic.browser.api.WWebExtensionController;
import com.igalia.wolvic.utils.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

import kotlin.Lazy;

import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.components.signin.AccountManagerFacadeImpl;
import org.chromium.components.signin.AccountManagerFacadeProvider;
import org.chromium.components.signin.SystemAccountManagerDelegate;
import org.chromium.content_public.browser.BrowserStartupController;
import org.chromium.content_public.browser.DeviceUtils;
import org.chromium.ui.base.ResourceBundle;
import org.chromium.wolvic.VRManager;

public class RuntimeImpl implements WRuntime {
    static String LOGTAG = SystemUtils.createLogtag(RuntimeImpl.class);
    private Context mContext;
    private WRuntimeSettings mRuntimeSettings;
    private WebExtensionControllerImpl mWebExtensionController;

    private final AutocompleteStorageProxy mAutocompleteStorageProxy;

    private ViewGroup mContainerView;
    private boolean mIsReady = false;
    private ArrayList<RuntimeImpl.Callback> mCallbacks;
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "content_shell";

    interface Callback {
        default void onReady() {}
    }

    public RuntimeImpl(@NonNull Context ctx, @NonNull WRuntimeSettings settings) {
        mContext = ctx;
        mRuntimeSettings = settings;
        mWebExtensionController = new WebExtensionControllerImpl();
        mAutocompleteStorageProxy = new AutocompleteStorageProxy();
        mCallbacks = new ArrayList<>();
        initBrowserProcess(mContext);
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    @Override
    public WRuntimeSettings getSettings() {
        return mRuntimeSettings;
    }

    @NonNull
    @Override
    public WResult<Void> clearData(long flags) {
        // TODO: Implement
        return WResult.fromValue(null);
    }

    @NonNull
    @Override
    public WWebExtensionController getWebExtensionController() {
        return mWebExtensionController;
    }

    @NonNull
    @Override
    public void setUpLoginPersistence(Lazy<LoginsStorage> storage) {
        mAutocompleteStorageProxy.setDelegate(AutocompleteDelegateWrapper.create(storage));
    }

    @NonNull
    public AutocompleteStorageProxy getUpLoginPersistence() {
        return mAutocompleteStorageProxy;
    }

    @NonNull
    @Override
    public Client createFetchClient(Context context) {
        return new HttpURLConnectionClient();
    }

    @Override
    public void setExternalVRContext(long externalContext) {
        VRManager.setExternalContext(externalContext);
    }

    @Override
    public void setContainerView(@NonNull ViewGroup containerView) {
        mContainerView = containerView;
    }

    @Override
    public float getDensity() {
        return mContext.getResources().getDisplayMetrics().density;
    }

    @Override
    public void configurationChanged(@NonNull Configuration newConfig) {
        // TODO: Implement
    }

    @Override
    public void appendAppNotesToCrashReport(@NonNull String notes) {
        // TODO: Implement
    }

    @NonNull
    @Override
    public WResult<String> sendCrashReport(@NonNull Context context, @NonNull File minidumpFile, @NonNull File extrasFile, @NonNull String appName) throws IOException, URISyntaxException {
        // TODO: Implement correctly
        return WResult.fromValue("");
    }

    @Override
    public Thread.UncaughtExceptionHandler createCrashHandler(Context appContext, Class<? extends Service> handlerService) {
        // TODO: Implement correctly
        return (t, e) -> e.printStackTrace();
    }

    @NonNull
    @Override
    public CrashReportIntent getCrashReportIntent() {
        // TODO: Implement correctly
        return new CrashReportIntent("", "", "", "");
    }

    @NonNull
    public ViewGroup getContainerView() {
        return mContainerView;
    }

    public void registerCallback(@NonNull RuntimeImpl.Callback callback) {
        if (mIsReady) {
            callback.onReady();
        } else {
            mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(@NonNull RuntimeImpl.Callback callback) {
        // It is possible that `callback` is already removed on the browser process startup
        // callback.
        mCallbacks.remove(callback);
    }

    private void setupWebGLMSAA() {
        String MSAALevelAsString = Integer.toString(mRuntimeSettings.getGlMsaaLevel());
        if (mRuntimeSettings.getGlMsaaLevel() == 0)
            CommandLine.getInstance().appendSwitchWithValue("webgl-antialiasing-mode", "none");
        CommandLine.getInstance().appendSwitchWithValue("webgl-msaa-sample-count", MSAALevelAsString);
    }

    private void initBrowserProcess(Context context) {
        assert isBrowserProcess() == true;

        ContextUtils.initApplicationContext(context);
        ResourceBundle.setNoAvailableLocalePaks();
        // Native libraries for child processes are loaded in its implementations in content.
        LibraryLoader.getInstance().setLibraryProcessType(LibraryProcessType.PROCESS_BROWSER);

        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);

        CommandLine.init(new String[] {});
        if (BuildConfig.DEBUG)
            CommandLine.getInstance().appendSwitchWithValue("enable-logging", "stderr");
        if (BuildConfig.FLAVOR_abi == "x64")
            CommandLine.getInstance().appendSwitchWithValue("disable-features", "Vulkan");

        // Enable WebXR Hand Input, which is disabled by default in blink (experimental)
        CommandLine.getInstance().appendSwitchWithValue("enable-features", "WebXRHandInput");

        setupWebGLMSAA();
        DeviceUtils.addDeviceSpecificUserAgentSwitch();
        LibraryLoader.getInstance().ensureInitialized();

        // Initialize the AccountManagerFacade with the correct AccountManagerDelegate. Must be done
        // only once and before AccountManagerFacadeProvider.getInstance() is invoked.
        AccountManagerFacadeProvider.setInstance(
                new AccountManagerFacadeImpl(new SystemAccountManagerDelegate()));

        BrowserStartupController.getInstance().startBrowserProcessesAsync(
                LibraryProcessType.PROCESS_BROWSER, true /* startGpuProcess */, false /* startMinimalBrowser */,
                new BrowserStartupController.StartupCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(LOGTAG, "The browser process started!");
                        mIsReady = true;
                        mCallbacks.forEach(callback -> {
                            callback.onReady();
                        });
                        mCallbacks.clear();
                    }

                    @Override
                    public void onFailure() {
                        Log.e(LOGTAG, "Failed to start the browser process");
                        // Clear callbacks on failure. This is needed as long as we don't retry to
                        // start the browser process.
                        mCallbacks.clear();
                    }
                });
    }

    private static boolean isBrowserProcess() {
        return !ContextUtils.getProcessName().contains(":");
    }

}
