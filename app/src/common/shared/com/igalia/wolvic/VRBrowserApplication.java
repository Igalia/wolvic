/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.StrictMode;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.Addons;
import com.igalia.wolvic.browser.LoginStorage;
import com.igalia.wolvic.browser.Places;
import com.igalia.wolvic.browser.Services;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.db.AppDatabase;
import com.igalia.wolvic.db.DataRepository;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.adapters.Language;
import com.igalia.wolvic.ui.widgets.AppServicesProvider;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.EnvironmentsManager;
import com.igalia.wolvic.utils.DictionariesManager;
import com.igalia.wolvic.utils.LocaleUtils;

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
    private DictionariesManager mDictionariesManager;
    private Addons mAddons;
    private ConnectivityReceiver mConnectivityManager;
    private Activity mCurrentActivity;

    protected void onActivityCreate(@NonNull Context activityContext) {
        onConfigurationChanged(activityContext.getResources().getConfiguration());
        TelemetryService.init(activityContext);
        mAppExecutors = new AppExecutors();
        mConnectivityManager = new ConnectivityReceiver(activityContext);
        mConnectivityManager.init();
        mPlaces = new Places(activityContext);
        mServices = new Services(activityContext, mPlaces);
        mLoginStorage = new LoginStorage(activityContext);
        mAccounts = new Accounts(activityContext);
        mSessionStore = SessionStore.get();
        mSessionStore.initialize(activityContext);
        mSessionStore.setLocales(LocaleUtils.getPreferredLanguageTags(activityContext));
        mDownloadsManager = new DownloadsManager(activityContext);
        mDownloadsManager.init();
        mBitmapCache = new BitmapCache(activityContext, mAppExecutors.diskIO(), mAppExecutors.mainThread());
        mEnvironmentsManager = new EnvironmentsManager(activityContext);
        mEnvironmentsManager.init();
        mDictionariesManager = new DictionariesManager(activityContext);
        mDictionariesManager.init();
        mAddons = new Addons(activityContext, mSessionStore);
    }

    protected void onActivityDestroy() {
        mConnectivityManager.end();
        mDownloadsManager.end();
        mEnvironmentsManager.end();
        mDictionariesManager.end();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Context context = LocaleUtils.init(this);
        Language language = LocaleUtils.getDisplayLanguage(context);
        newConfig.setLocale(language.getLocale());
        // TODO: Deprecated updateConfiguration(Configuration,DisplayMetrics),
        //  see https://github.com/Igalia/wolvic/issues/797
        getApplicationContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: eventually add .penaltyDeath() to the policies once we have fixed all the issues.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
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
    public DictionariesManager getDictionariesManager() {
        return mDictionariesManager;
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

    public Activity getCurrentActivity() { return mCurrentActivity; }

    public void setCurrentActivity(Activity activity) { mCurrentActivity = activity; }
}
