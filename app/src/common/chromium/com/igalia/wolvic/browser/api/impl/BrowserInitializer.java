package com.igalia.wolvic.browser.api.impl;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.SystemUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.content_public.browser.BrowserStartupController;
import org.chromium.content_public.browser.DeviceUtils;
import org.chromium.ui.base.ResourceBundle;

public class BrowserInitializer {
    static String LOGTAG = SystemUtils.createLogtag(BrowserInitializer.class);

    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "content_shell";

    public BrowserInitializer(@NonNull Context context) {
        initBrowserProcess(context);
    }

    private void initBrowserProcess(Context context) {
        assert isBrowserProcess() == true;

        ContextUtils.initApplicationContext(context);
        ResourceBundle.setNoAvailableLocalePaks();
        // Native libraries for child processes are loaded in its implementations in content.
        LibraryLoader.getInstance().setLibraryProcessType(LibraryProcessType.PROCESS_BROWSER);

        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);
        // Initialize with the application context to monitor the activity status.
        ApplicationStatus.initialize((Application) context.getApplicationContext());

        CommandLine.init(new String[] {});
        DeviceUtils.addDeviceSpecificUserAgentSwitch();
        LibraryLoader.getInstance().ensureInitialized();

        BrowserStartupController.getInstance().startBrowserProcessesAsync(
                LibraryProcessType.PROCESS_BROWSER, true /* startGpuProcess */, false /* startMinimalBrowser */,
                new BrowserStartupController.StartupCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(LOGTAG, "The browser process started!");
                    }

                    @Override
                    public void onFailure() {
                        Log.e(LOGTAG, "Failed to start the browser process");
                    }
                });
    }

    private static boolean isBrowserProcess() {
        return !ContextUtils.getProcessName().contains(":");
    }
}
