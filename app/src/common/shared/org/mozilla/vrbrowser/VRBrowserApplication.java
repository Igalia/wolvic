/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.Addons;
import org.mozilla.vrbrowser.browser.LoginStorage;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.db.AppDatabase;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.speech.MozillaSpeechRecognizer;
import org.mozilla.vrbrowser.speech.SpeechRecognizer;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.ui.widgets.AppServicesProvider;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;
import org.mozilla.vrbrowser.utils.EnvironmentsManager;
import org.mozilla.vrbrowser.utils.LocaleUtils;
import org.mozilla.vrbrowser.utils.SystemUtils;

public class VRBrowserApplication extends Application implements AppServicesProvider {

    private SessionStore mSessionStore;
    private AppExecutors mAppExecutors;
    private BitmapCache mBitmapCache;
    private Services mServices;
    private LoginStorage mLoginStorage;
    private Places mPlaces;
    private Accounts mAccounts;
    private DownloadsManager mDownloadsManager;
    private SpeechRecognizer mSpeechRecognizer;
    private EnvironmentsManager mEnvironmentsManager;
    private Addons mAddons;
    private ConnectivityReceiver mConnectivityManager;

    @Override
    public void onCreate() {
        super.onCreate();

        if (!SystemUtils.isMainProcess(this)) {
            // If this is not the main process then do not continue with the initialization here. Everything that
            // follows only needs to be done in our app's main process and should not be done in other processes like
            // a GeckoView child process or the crash handling process. Most importantly we never want to end up in a
            // situation where we create a GeckoRuntime from the Gecko child process.
            return;
        }

        // Fix potential Gecko static initialization order.
        // GeckoResult.allow() and GeckoResult.deny() static initializer might get a null mDispatcher
        // depending on how JVM classloader does the initialization job.
        // See https://github.com/MozillaReality/FirefoxReality/issues/3651
        Looper.getMainLooper().getThread();
        GleanMetricsService.init(this, EngineProvider.INSTANCE.getDefaultClient(this));
    }

    protected void onActivityCreate(@NonNull Context activityContext) {
        onConfigurationChanged(activityContext.getResources().getConfiguration());
        EngineProvider.INSTANCE.getDefaultGeckoWebExecutor(activityContext);
        mAppExecutors = new AppExecutors();
        mConnectivityManager = new ConnectivityReceiver(activityContext);
        mConnectivityManager.init();
        mPlaces = new Places(activityContext);
        mServices = new Services(activityContext, mPlaces);
        mLoginStorage = new LoginStorage(this);
        mAccounts = new Accounts(activityContext);
        mSessionStore = SessionStore.get();
        mSessionStore.initialize(activityContext);
        mSessionStore.setLocales(LocaleUtils.getPreferredLanguageTags(activityContext));
        mDownloadsManager = new DownloadsManager(activityContext);
        mDownloadsManager.init();
        mSpeechRecognizer = new MozillaSpeechRecognizer(activityContext);
        mBitmapCache = new BitmapCache(activityContext, mAppExecutors.diskIO(), mAppExecutors.mainThread());
        mEnvironmentsManager = new EnvironmentsManager(activityContext);
        mEnvironmentsManager.init();
        mAddons = new Addons(activityContext, mSessionStore);
    }

    protected void onActivityDestroy() {
        mConnectivityManager.end();
        mDownloadsManager.end();
        mEnvironmentsManager.end();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Context context = LocaleUtils.init(this);
        Language language = LocaleUtils.getDisplayLanguage(context);
        newConfig.setLocale(language.getLocale());
        getApplicationContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());
        super.onConfigurationChanged(newConfig);
    }

    public Services getServices() {
        return mServices;
    }

    public LoginStorage getLoginStorage() {
        return mLoginStorage;
    }

    public Places getPlaces() {
        return mPlaces;
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getAppDatabase(this, mAppExecutors);
    }

    public AppExecutors getExecutors() {
        return mAppExecutors;
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase(), mAppExecutors);
    }

    public BitmapCache getBitmapCache() {
        return mBitmapCache;
    }

    public Accounts getAccounts() {
        return mAccounts;
    }

    public DownloadsManager getDownloadsManager() {
        return mDownloadsManager;
    }

    @Override
    public SpeechRecognizer getSpeechRecognizer() {
        return mSpeechRecognizer;
    }

    public void setSpeechRecognizer(SpeechRecognizer customRecognizer) {
        mSpeechRecognizer = customRecognizer;
    }

    @Override
    public EnvironmentsManager getEnvironmentsManager() {
        return mEnvironmentsManager;
    }

    @Override
    public Addons getAddons() {
        return mAddons;
    }

    @Override
    public SessionStore getSessionStore() {
        return mSessionStore;
    }

    @Override
    public ConnectivityReceiver getConnectivityReceiver() {
        return mConnectivityManager;
    }
}
