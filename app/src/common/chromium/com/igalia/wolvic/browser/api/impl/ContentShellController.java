package com.igalia.wolvic.browser.api.impl;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.task.PostTask;
import org.chromium.content_public.browser.BrowserStartupController;
import org.chromium.content_public.browser.DeviceUtils;
import org.chromium.content_public.browser.UiThreadTaskTraits;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.IntentRequestTracker;
import org.chromium.ui.base.ResourceBundle;
import org.chromium.ui.base.WindowAndroid;
import com.igalia.wolvic.VRBrowserActivity;

public class ContentShellController {
    private static final String TAG = "ContentShellController";
    private static final String COMMAND_LINE_FILE = "/data/local/tmp/content-shell-command-line";
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "content_shell";
    private static final String ACTIVE_SHELL_URL_KEY = "activeUrl";
    private static final String COMMAND_LINE_ARGS_KEY = "commandLineArgs";

    private Activity mActivity;
    private boolean isActivityInitialized = false;
    private IntentRequestTracker mIntentRequestTracker;
    private ActivityWindowAndroid mWindowAndroid;

    public void initApplication(Application application) {
        boolean isBrowserProcess = !ContextUtils.getProcessName().contains(":");
        ContextUtils.initApplicationContext(application);
        ResourceBundle.setNoAvailableLocalePaks();
        LibraryLoader.getInstance().setLibraryProcessType(isBrowserProcess
                ? LibraryProcessType.PROCESS_BROWSER
                : LibraryProcessType.PROCESS_CHILD);
        if (isBrowserProcess) {
            PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);
            ApplicationStatus.initialize(application);
        }
    }

    public WebContents createWebContents() {
        return ((VRBrowserActivity) mActivity).createWebContents();
    }

    public void initActivity(Activity activity) {
        mActivity = activity;
        CommandLine.init(new String[]{});
        DeviceUtils.addDeviceSpecificUserAgentSwitch();
        LibraryLoader.getInstance().ensureInitialized();
        mIntentRequestTracker = IntentRequestTracker.createFromActivity(activity);
        mWindowAndroid = new ActivityWindowAndroid(activity, false, mIntentRequestTracker);
        BrowserStartupController.getInstance().startBrowserProcessesAsync(
                LibraryProcessType.PROCESS_BROWSER, true, false,
                new BrowserStartupController.StartupCallback() {
                    @Override
                    public void onSuccess() {
                        onBrowserStarted();
                    }

                    @Override
                    public void onFailure() {
                        onBrowserStartFailed();
                    }
                });
    }

    private void onBrowserStarted() {
        Log.e(TAG, "WolvicLifecycle browser started");
    }

    private void onBrowserStartFailed() {
        Log.e(TAG, "WolvicLifecycle browser start failed");
    }

    public WindowAndroid getWindowAndroid() {
        return mWindowAndroid;
    }
}
