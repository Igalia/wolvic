package org.mozilla.vrbrowser.ui.widgets;

import com.mozilla.speechlibrary.SpeechService;

import org.mozilla.vrbrowser.AppExecutors;
import org.mozilla.vrbrowser.browser.Accounts;
import org.mozilla.vrbrowser.browser.LoginStorage;
import org.mozilla.vrbrowser.browser.Places;
import org.mozilla.vrbrowser.browser.Services;
import org.mozilla.vrbrowser.db.AppDatabase;
import org.mozilla.vrbrowser.db.DataRepository;
import org.mozilla.vrbrowser.downloads.DownloadsManager;
import org.mozilla.vrbrowser.utils.BitmapCache;
import org.mozilla.vrbrowser.utils.EnvironmentsManager;

public interface AppServicesProvider {

    Services getServices();
    Places getPlaces();
    AppDatabase getDatabase();
    AppExecutors getExecutors();
    DataRepository getRepository();
    BitmapCache getBitmapCache();
    Accounts getAccounts();
    DownloadsManager getDownloadsManager();
    SpeechService getSpeechService();
    EnvironmentsManager getEnvironmentsManager();
    LoginStorage getLoginStorage();
}
