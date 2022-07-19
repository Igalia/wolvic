package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WRuntimeSettings;
import com.igalia.wolvic.browser.api.WWebExtensionController;

import org.chromium.weblayer.Browser;
import org.chromium.weblayer.Tab;
import org.chromium.weblayer.UnsupportedVersionException;
import org.chromium.weblayer.WebLayer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import kotlin.Lazy;
import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;

public class RuntimeImpl implements WRuntime {
    private Context mContext;
    private WebLayer mWebLayer;
    private WRuntimeSettings mRuntimeSettings;
    private WebExtensionControllerImpl mWebExtensionController;
    private ViewGroup mViewContainer;
    private FragmentManager mFragmentManager;
    private CopyOnWriteArrayList<BrowserDisplay> mPrivateDisplays = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<BrowserDisplay> mDisplays = new CopyOnWriteArrayList<>();

    public RuntimeImpl(@NonNull Context ctx, @NonNull WRuntimeSettings settings) {
        mContext = ctx;
        mRuntimeSettings = settings;
        mWebExtensionController = new WebExtensionControllerImpl();

        try {
            mWebLayer = WebLayer.loadSync(mContext.getApplicationContext());
        } catch (UnsupportedVersionException e) {
            throw new RuntimeException("Failed to initialize WebLayer", e);
        }
    }

    Context getContext() {
        return mContext;
    }

    private BrowserDisplay createBrowserDisplay(boolean incognito) {
        assert mViewContainer != null;
        assert mFragmentManager != null;

        String profileName = incognito ? null : "DefaultProfile";
        Fragment fragment = WebLayer.createBrowserFragment(profileName);
        BrowserDisplay display = new BrowserDisplay(mContext);
        display.attach(mFragmentManager, mViewContainer, fragment);
        return display;
    }

    public Tab createTab(boolean incognito) {
        CopyOnWriteArrayList<BrowserDisplay> displays = incognito ? mPrivateDisplays : mDisplays;
        if (displays.isEmpty()) {
            displays.add(createBrowserDisplay(incognito));
        }
        return displays.get(0).getBrowser().createTab();
    }

    public BrowserDisplay acquireDisplay(boolean incognito) {
        CopyOnWriteArrayList<BrowserDisplay> displays = incognito ? mPrivateDisplays : mDisplays;
        Optional<BrowserDisplay> display = displays.stream().filter(BrowserDisplay::isAcquired).findFirst();
        if (display.isPresent()) {
            return display.get();
        }

        BrowserDisplay newDisplay = createBrowserDisplay(incognito);
        displays.add(newDisplay);
        return newDisplay;
    }

    public void releaseDisplay(@NonNull BrowserDisplay display) {
        display.setAcquired(false);
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
        // TODO: Implement
    }

    @NonNull
    @Override
    public Client createFetchClient(Context context) {
        return new HttpURLConnectionClient();
    }

    @Override
    public void setExternalVRContext(long externalContext) {
        // TODO: Implement
    }

    @Override
    public void setFragmentManager(@NonNull FragmentManager fragmentManager, @NonNull ViewGroup container) {
        mFragmentManager = fragmentManager;
        mViewContainer = container;
    }

    @Override
    public float getDensity() {
        return mContext.getResources().getDisplayMetrics().density;
    }

    @Override
    public void configurationChanged(@NonNull Configuration newConfig) {
        // no op as WebLayer uses FragmentManager lifecycle.
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
}

