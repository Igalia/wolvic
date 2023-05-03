package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WRuntimeSettings;
import com.igalia.wolvic.browser.api.WWebExtensionController;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import kotlin.Lazy;

import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;

import org.chromium.wolvic.VRManager;

public class RuntimeImpl implements WRuntime {
    private BrowserInitializer mBrowserInitializer;
    private Context mContext;
    private WRuntimeSettings mRuntimeSettings;
    private WebExtensionControllerImpl mWebExtensionController;
    private ViewGroup mContainerView;

    public RuntimeImpl(@NonNull Context ctx, @NonNull WRuntimeSettings settings) {
        mContext = ctx;
        mRuntimeSettings = settings;
        mWebExtensionController = new WebExtensionControllerImpl();
        mBrowserInitializer = new BrowserInitializer(ctx);
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    public void addViewToBrowserContainer(View view) {
        SettingsStore settings = SettingsStore.getInstance(mContext);
        // using the default window size here, it will be updated later in onSurfaceChanged()
        mContainerView.addView(view,
                new ViewGroup.LayoutParams(settings.getWindowWidth(), settings.getWindowHeight()));
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

    @NonNull
    public BrowserInitializer getBrowserInitializer() {
        return mBrowserInitializer;
    }
}
