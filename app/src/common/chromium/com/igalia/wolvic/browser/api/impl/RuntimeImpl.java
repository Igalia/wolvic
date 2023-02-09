package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WRuntime;
import com.igalia.wolvic.browser.api.WRuntimeSettings;
import com.igalia.wolvic.browser.api.WWebExtensionController;

import org.chromium.components.embedder_support.view.WolvicContentRenderView;
import org.chromium.content_public.browser.WebContents;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import kotlin.Lazy;
import mozilla.components.concept.fetch.Client;
import mozilla.components.concept.storage.LoginsStorage;
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient;

public class RuntimeImpl implements WRuntime {
    private Context mContext;
    private WRuntimeSettings mSettings;
    private WWebExtensionController mWebExtensionController;
    private FragmentManager mFragmentManager;
    private ViewGroup mViewContainer;
    private ContentShellController mContentShellController;
    private BrowserDisplay mBrowserDisplay;
    private WolvicContentRenderView mContentViewRenderView;

    public RuntimeImpl(@NonNull Context context, @NonNull WRuntimeSettings settings) {
        Log.e("WolvicLifecycle", "RuntimeImpl()");
        mContentShellController = new ContentShellController();
        mContext = context;
        mSettings = settings;
        mWebExtensionController = new WebExtensionControllerImpl();
    }

    public ContentShellController getContentShellController() {
        return mContentShellController;
    }

    public BrowserDisplay createBrowserDisplay(WebContents webContents) {
        mContentViewRenderView = new WolvicContentRenderView(mContext);
        mContentViewRenderView.onNativeLibraryLoaded(mContentShellController.getWindowAndroid());
        mContentViewRenderView.setCurrentWebContents(webContents);

        mBrowserDisplay = new BrowserDisplay(mContext);

        ContentShellFragment fragment = new ContentShellFragment();
        fragment.setContentViewRenderView(mContentViewRenderView);
        mBrowserDisplay.attach(mFragmentManager, mViewContainer, fragment);
        return mBrowserDisplay;
    }

    public BrowserDisplay getBrowserDisplay() {
        return mBrowserDisplay;
    }

    public WolvicContentRenderView getRenderView() {
        return mContentViewRenderView;
    }

    @Override
    public WRuntimeSettings getSettings() {
        return mSettings;
    }

    @NonNull
    @Override
    public WResult<Void> clearData(long flags) {
        return null;
    }

    @NonNull
    @Override
    public WWebExtensionController getWebExtensionController() {
        return mWebExtensionController;
    }

    @NonNull
    @Override
    public void setUpLoginPersistence(Lazy<LoginsStorage> storage) {

    }

    @NonNull
    @Override
    public Client createFetchClient(Context context) {
        return new HttpURLConnectionClient();
    }

    @Override
    public void setExternalVRContext(long externalContext) {

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

    }

    @Override
    public void appendAppNotesToCrashReport(@NonNull String notes) {

    }

    @NonNull
    @Override
    public WResult<String> sendCrashReport(@NonNull Context context, @NonNull File minidumpFile, @NonNull File extrasFile, @NonNull String appName) throws IOException, URISyntaxException {
        return null;
    }

    @Override
    public Thread.UncaughtExceptionHandler createCrashHandler(Context appContext, Class<? extends Service> handlerService) {
        return null;
    }

    @NonNull
    @Override
    public CrashReportIntent getCrashReportIntent() {
        return new CrashReportIntent("", "", "", "");
    }
}
