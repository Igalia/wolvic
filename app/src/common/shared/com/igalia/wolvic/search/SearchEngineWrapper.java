package com.igalia.wolvic.search;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.geolocation.GeolocationData;
import com.igalia.wolvic.search.suggestions.SearchSuggestionsClientKt;
import com.igalia.wolvic.utils.SystemUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import kotlin.coroutines.CoroutineContext;
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

    private static SearchEngineWrapper mSearchEngineWrapperInstance;

    public static synchronized @NonNull
    SearchEngineWrapper get(final @NonNull Context aContext) {
        if (mSearchEngineWrapperInstance == null) {
            mSearchEngineWrapperInstance = new SearchEngineWrapper(aContext);
        } else if (!aContext.equals(mSearchEngineWrapperInstance.getContext())) {
            // Because of the architecture of the app, this is very unlikely (but just in case...).
            Log.w(LOGTAG, "Context has changed");
            mSearchEngineWrapperInstance = new SearchEngineWrapper(aContext);
        }

        return mSearchEngineWrapperInstance;
    }

    private final WeakReference<Context> mContextRef;
    private SearchEngine mSearchEngine;
    private SearchSuggestionClient mSuggestionsClient;
    private final SharedPreferences mPrefs;
    private boolean mAutocompleteEnabled;
    private HashMap<String, SearchEngine> mSearchEnginesMap;

    private SearchEngineWrapper(@NonNull Context aContext) {
        mContextRef = new WeakReference<>(aContext);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mAutocompleteEnabled = SettingsStore.getInstance(getContext()).isAutocompleteEnabled();

        String preferredSearchEngineId = SettingsStore.getInstance(getContext()).getSearchEngineId();

        setupSearchEngine(aContext, preferredSearchEngineId);
    }

    public void registerForUpdates() {
        if (hasContext()) {
            getContext().registerReceiver(
                    mLocaleChangedReceiver,
                    new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
            if (mPrefs != null) {
                mPrefs.registerOnSharedPreferenceChangeListener(this);
            }
        }
    }

    public void unregisterForUpdates() {
        if (hasContext()) {
            try {
                getContext().unregisterReceiver(mLocaleChangedReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            if (mPrefs != null) {
                mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            }
        }
    }

    public String getSearchURL(String aQuery) {
        return mSearchEngine.buildSearchUrl(aQuery);
    }

    public CompletableFuture<List<String>> getSuggestions(String aQuery) {
        return SearchSuggestionsClientKt.getSuggestionsAsync(mSuggestionsClient, aQuery != null ? aQuery : "");
    }

    public String getResourceURL() {
        Uri uri = Uri.parse(mSearchEngine.buildSearchUrl(""));
        return uri.getScheme() + "://" + uri.getHost();
    }

    public SearchEngine getCurrentSearchEngine() {
        return mSearchEngine;
    }

    // Receiver for locale updates
    private BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                String userSearchEngineId = SettingsStore.getInstance(getContext()).getSearchEngineId();
                setupSearchEngine(context, userSearchEngineId);
            }
        }
    };

    public Collection<SearchEngine> getAvailableSearchEngines() {
        return mSearchEnginesMap.values();
    }

    public void setDefaultSearchEngine() {
        if (hasContext())
            setupSearchEngine(getContext(), null);
    }

    public void setCurrentSearchEngineId(Context context, String searchEngineId) {
        if (hasContext())
            SettingsStore.getInstance(getContext()).setSearchEngineId(searchEngineId);
    }

    /**
     * We cannot send system ACTION_LOCALE_CHANGED so the component refreshes the engines
     * with the updated SearchLocalizationProvider information so we have to update the whole manager.
     *
     * @param aContext Activity context
     * @param userPref User preferred engine (among the available ones)
     */
    private void setupSearchEngine(@NonNull Context aContext, String userPref) {
        List<SearchEngineFilter> engineFilterList = new ArrayList<>();

        GeolocationData data = GeolocationData.parse(SettingsStore.getInstance(aContext).getGeolocationData());
        SearchLocalizationProvider localizationProvider;
        if (data == null) {
            Log.d(LOGTAG, "Using Locale based search localization provider");
            // If we don't have geolocation data we default to the Locale search localization provider
            localizationProvider = new LocaleSearchLocalizationProvider();
        } else {
            Log.d(LOGTAG, "Using Geolocation based search localization provider: " + data);
            // If we have geolocation data we initialize the provider with the received data.
            localizationProvider = new GeolocationLocalizationProvider(data);
        }

        // Configure the assets search with the localization provider and the engines that we want
        // to filter.
        AssetsSearchEngineProvider engineProvider = new AssetsSearchEngineProvider(
                localizationProvider,
                Collections.emptyList(), //engineFilterList,
                Collections.emptyList());

        SearchEngineManager searchEngineManager = new SearchEngineManager(Collections.singletonList(engineProvider),
                (CoroutineContext) Dispatchers.getDefault());

        // If we don't get any result we use the default configuration.
        List<SearchEngine> searchEngines = searchEngineManager.getSearchEngines(aContext);
        if (searchEngines.size() == 0) {
            Log.d(LOGTAG, "  Could not find any available search engines, using default.");
            searchEngineManager = new SearchEngineManager();
            searchEngines = searchEngineManager.getSearchEngines(aContext);
        }

        mSearchEnginesMap = new LinkedHashMap<>(searchEngines.size());
        for (int i = 0; i < searchEngines.size(); i++) {
            SearchEngine searchEngine = searchEngines.get(i);
            mSearchEnginesMap.put(searchEngine.getIdentifier(), searchEngine);
        }

        String userPrefName = "";
        if (mSearchEnginesMap.containsKey(userPref)) {
            // The search component API uses the engine's name, not its identifier.
            userPrefName = mSearchEnginesMap.get(userPref).getName();
        }

        // A name can be used if the user gets to choose among the available engines
        mSearchEngine = searchEngineManager.getDefaultSearchEngine(aContext, userPrefName);

        mSuggestionsClient = new SearchSuggestionClient(mSearchEngine,
                (searchUrl, continuation) -> {
                    if (mAutocompleteEnabled && getVRBrowserActivity() != null) {
                        if (!getVRBrowserActivity().getWindows().isInPrivateMode()) {
                            return SearchSuggestionsClientKt.fetchSearchSuggestions(getContext(), searchUrl);
                        }
                    }
                    return null;
                }
        );
        SettingsStore.getInstance(getContext()).setSearchEngineId(mSearchEngine.getIdentifier());
    }

    private boolean hasContext() {
        return mContextRef.get() != null;
    }

    private Context getContext() {
        return mContextRef.get();
    }

    private VRBrowserActivity getVRBrowserActivity() {
        if (hasContext() && getContext() instanceof VRBrowserActivity)
            return (VRBrowserActivity) getContext();
        else
            return null;
    }

    // SharedPreferences.OnSharedPreferenceChangeListener

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mContextRef.get() != null) {
            if (key.equals(mContextRef.get().getString(R.string.settings_key_geolocation_data)) ||
                    key.equals(mContextRef.get().getString(R.string.settings_key_search_engine_id))) {
                String searchEngineId = SettingsStore.getInstance(mContextRef.get()).getSearchEngineId();
                if (mSearchEngine == null || !Objects.equals(mSearchEngine.getIdentifier(), searchEngineId)) {
                    setupSearchEngine(mContextRef.get(), searchEngineId);
                }
            } else if (key.equals(mContextRef.get().getString(R.string.settings_key_autocomplete))) {
                mAutocompleteEnabled = SettingsStore.getInstance(mContextRef.get()).isAutocompleteEnabled();
            }
        }
    }
}
