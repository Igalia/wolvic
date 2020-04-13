/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.db.AppDatabase;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.telemetry.GleanMetricsService;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;
import org.mozilla.vrbrowser.ui.adapters.Language;
import org.mozilla.vrbrowser.ui.widgets.AppServicesProvider;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.LocaleUtils;

public class VRBrowserApplication extends Application implements AppServicesProvider {

    private AppExecutors mAppExecutors;
    private BitmapCache mBitmapCache;
    private Services mServices;
    private Places mPlaces;
    private Accounts mAccounts;
    private DownloadsManager mDownloadsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppExecutors = new AppExecutors();
        mBitmapCache = new BitmapCache(this, mAppExecutors.diskIO(), mAppExecutors.mainThread());

        TelemetryWrapper.init(this);
        GleanMetricsService.init(this);
    }

    protected void onActivityCreate() {
        mPlaces = new Places(this);
        mServices = new Services(this, mPlaces);
        mAccounts = new Accounts(this);
        mDownloadsManager = new DownloadsManager(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        Context context = LocaleUtils.init(base);
        super.attachBaseContext(context);
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
}
