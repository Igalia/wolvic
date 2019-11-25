package org.mozilla.vrbrowser.search;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserApplication;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.geolocation.GeolocationData;
import org.mozilla.vrbrowser.search.suggestions.SuggestionsClient;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import kotlinx.coroutines.Dispatchers;
import mozilla.components.browser.search.SearchEngine;
import mozilla.components.browser.search.SearchEngineManager;
import mozilla.components.browser.search.provider.AssetsSearchEngineProvider;
import mozilla.components.browser.search.provider.filter.SearchEngineFilter;
import mozilla.components.browser.search.provider.localization.LocaleSearchLocalizationProvider;
import mozilla.components.browser.search.provider.localization.SearchLocalizationProvider;
import mozilla.components.browser.search.suggestions.SearchSuggestionClient;

public class SearchEngineWrapper implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOGTAG = SystemUtils.createLogtag(SearchEngineWrapper.class);

    // Specific FxR engine overrides. US is already overridden by browser-search component
    // https://github.com/MozillaReality/FirefoxReality/issues/248#issuecomment-412278211
    private static final Map<String , String> REGION_ENGINE_OVERRIDE = new HashMap<String, String>() {{
        put("CN", "baidu");
        put("RU", "yandex-ru");
        put("BY", "yandex.by");
        put("TR", "yandex-tr");
        put("KZ", "yandex-kz");
    }};

    private static String EMPTY = "";

    private static SearchEngineWrapper mSearchEngineWrapperInstance;

    public static synchronized @NonNull
    SearchEngineWrapper get(final @NonNull Context aContext) {
        if (mSearchEngineWrapperInstance == null) {
            mSearchEngineWrapperInstance = new SearchEngineWrapper(aContext);
        }

        return mSearchEngineWrapperInstance;
    }

    public interface SuggestionsDelegate {
        void OnSuggestions(List<String> aSuggestionsList);
    }

    private Context mContext;
    private SearchEngine mSearchEngine;
    private SearchLocalizationProvider mLocalizationProvider;
    private SearchEngineManager mSearchEngineManager;
    private SearchSuggestionClient mSuggestionsClient;
    private SharedPreferences mPrefs;
    private Executor mUIThreadExecutor;

    private SearchEngineWrapper(@NonNull Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mUIThreadExecutor = ((VRBrowserApplication)aContext.getApplicationContext()).getExecutors().mainThread();

        setupSearchEngine(aContext, EMPTY);
    }

    public void registerForUpdates() {
        if (mContext != null) {
            mContext.registerReceiver(
                    mLocaleChangedReceiver,
                    new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
            if (mPrefs != null) {
                mPrefs.registerOnSharedPreferenceChangeListener(this);
            }
        }
    }

    public void unregisterForUpdates() {
        if (mContext != null) {
            mContext.unregisterReceiver(mLocaleChangedReceiver);
            if (mPrefs != null) {
                mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            }
        }
    }

    public String getSearchURL(String aQuery) {
        return mSearchEngine.buildSearchUrl(aQuery);
    }

    private String getSuggestionURL(String aQuery) {
        return mSearchEngine.buildSuggestionsURL(aQuery);
    }

    public CompletableFuture<List<String>> getSuggestions(String aQuery) {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        // TODO: Use mSuggestionsClient.getSuggestions when fixed in browser-search.
        String query = getSuggestionURL(aQuery);
        mUIThreadExecutor.execute(() ->
                SuggestionsClient.getSuggestions(mSearchEngine, query).thenAcceptAsync(future::complete));

        return future;
    }

    public String getResourceURL() {
        Uri uri = Uri.parse(mSearchEngine.buildSearchUrl("")) ;
        return uri.getScheme() + "://" + uri.getHost();
    }

    public String getIdentifier() {
        return mSearchEngine.getIdentifier();
    }

    // Receiver for locale updates
    private BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == Intent.ACTION_LOCALE_CHANGED) {
                setupSearchEngine(context, EMPTY);
            }
        }
    };

    /**
     * We cannot send system ACTION_LOCALE_CHANGED so the component refreshes the engines
     * with the updated SearchLocalizationProvider information so we have to update the whole manager.
     * @param aContext Activity context
     * @param userPref User preferred engine (among the available ones)
     */
    private void setupSearchEngine(@NonNull Context aContext, String userPref) {
        List<SearchEngineFilter> engineFilterList = new ArrayList<>();

        GeolocationData data = GeolocationData.parse(SettingsStore.getInstance(aContext).getGeolocationData());
        if (data == null) {
            // If we don't have geolocation data we default to the Locale search localization provider
            mLocalizationProvider = new LocaleSearchLocalizationProvider();

        } else {
            // If we have geolocation data we initialize the provider with the received data
            // and setup a filter to filter the engines that we need to override for FxR.
            mLocalizationProvider = new GeolocationLocalizationProvider(data);
            if (getEngine(data.getCountryCode()) != null) {
                SearchEngineFilter engineFilter = (ctx, searchEngine) ->
                        searchEngine.getIdentifier().equalsIgnoreCase(getEngine(data.getCountryCode()));
                engineFilterList.add(engineFilter);
            }
        }

        // Configure the assets search with the localization provider and the engines that we want
        // to filter.
        AssetsSearchEngineProvider engineProvider = new AssetsSearchEngineProvider(
                mLocalizationProvider,
                engineFilterList,
                Collections.emptyList());

        mSearchEngineManager = new SearchEngineManager(Arrays.asList(engineProvider), Dispatchers.getDefault());

        // If we don't get any result we use the default configuration.
        if (mSearchEngineManager.getSearchEngines(aContext).size() == 0) {
            mSearchEngineManager = new SearchEngineManager();
        }

        // A name can be used if the user get's to choose among the available engines
        mSearchEngine = mSearchEngineManager.getDefaultSearchEngine(aContext, userPref);
        mSuggestionsClient = new SearchSuggestionClient(mSearchEngine, (s, continuation) -> null);
    }

    private String getEngine(String aCountryCode) {
        return REGION_ENGINE_OVERRIDE.get(aCountryCode);
    }

    // SharedPreferences.OnSharedPreferenceChangeListener

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mContext != null) {
            if (key == mContext.getString(R.string.settings_key_geolocation_data)) {
                setupSearchEngine(mContext, EMPTY);
            }
        }
    }
}
