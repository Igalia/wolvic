package com.igalia.wolvic.ui.widgets;

import com.igalia.wolvic.AppExecutors;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.Addons;
import com.igalia.wolvic.browser.LoginStorage;
import com.igalia.wolvic.browser.Places;
import com.igalia.wolvic.browser.Services;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.db.AppDatabase;
import com.igalia.wolvic.db.DataRepository;
import com.igalia.wolvic.downloads.DownloadsManager;
import com.igalia.wolvic.speech.SpeechRecognizer;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.EnvironmentsManager;
import com.igalia.wolvic.utils.DictionariesManager;

public interface AppServicesProvider {

    SessionStore getSessionStore();
    Services getServices();
    Places getPlaces();
    AppDatabase getDatabase();
    AppExecutors getExecutors();
    DataRepository getRepository();
    BitmapCache getBitmapCache();
    Accounts getAccounts();
    DownloadsManager getDownloadsManager();
    SpeechRecognizer getSpeechRecognizer();
    EnvironmentsManager getEnvironmentsManager();
    DictionariesManager getDictionariesManager();
    LoginStorage getLoginStorage();
    Addons getAddons();
    ConnectivityReceiver getConnectivityReceiver();
}
