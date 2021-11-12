package org.mozilla.vrbrowser.ui.widgets;

import com.mozilla.speechlibrary.SpeechService;

import org.mozilla.vrbrowser.AppExecutors;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.LoginStorage;
import org.mozilla.vrbrowser.browser.Addons;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.db.AppDatabase;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.speech.SpeechRecognizer;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.ConnectivityReceiver;
import org.mozilla.vrbrowser.utils.EnvironmentsManager;

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
    LoginStorage getLoginStorage();
    Addons getAddons();
    ConnectivityReceiver getConnectivityReceiver();
}
