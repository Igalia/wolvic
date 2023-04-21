package com.igalia.wolvic.browser.api.impl;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.SystemUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.content_public.browser.BrowserStartupController;
import org.chromium.content_public.browser.DeviceUtils;
import org.chromium.ui.base.ResourceBundle;

import java.util.ArrayList;

public class BrowserInitializer {
    static String LOGTAG = SystemUtils.createLogtag(BrowserInitializer.class);

    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "content_shell";

    private boolean mIsReady = false;
    private ArrayList<Callback> mCallbacks;

    interface Callback {
        default void onReady() {}
    }

    public BrowserInitializer(@NonNull Context context) {
        mCallbacks = new ArrayList<>();
        initBrowserProcess(context);
    }

    public void registerCallback(@NonNull Callback callback) {
        if (mIsReady) {
            callback.onReady();
        } else {
            mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(Callback callback) {
        // It is possible that `callback` is already removed on the browser process startup
        // callback.
        mCallbacks.remove(callback);
    }

    private void initBrowserProcess(Context context) {
        assert isBrowserProcess() == true;

        ContextUtils.initApplicationContext(context);
        ResourceBundle.setNoAvailableLocalePaks();
        // Native libraries for child processes are loaded in its implementations in content.
        LibraryLoader.getInstance().setLibraryProcessType(LibraryProcessType.PROCESS_BROWSER);

        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);

        CommandLine.init(new String[] {});
        DeviceUtils.addDeviceSpecificUserAgentSwitch();
        // TODO(voit): This leads to a crash on startup after chromium update but it might not be
        //  needed at all
        // LibraryLoader.getInstance().ensureInitialized();

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
