package com.igalia.wolvic.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.igalia.wolvic.R
import com.igalia.wolvic.VRBrowserActivity
import com.igalia.wolvic.browser.SettingsStore
import com.igalia.wolvic.geolocation.GeolocationData
import com.igalia.wolvic.search.suggestions.fetchSearchSuggestions
import com.igalia.wolvic.search.suggestions.getSuggestionsAsync
import com.igalia.wolvic.utils.SystemUtils
import kotlinx.coroutines.Dispatchers.Default
import mozilla.components.browser.search.SearchEngine
import mozilla.components.browser.search.SearchEngineManager
import mozilla.components.browser.search.provider.AssetsSearchEngineProvider
import mozilla.components.browser.search.provider.filter.SearchEngineFilter
import mozilla.components.browser.search.provider.localization.LocaleSearchLocalizationProvider
import mozilla.components.browser.search.provider.localization.SearchLocalizationProvider
import mozilla.components.browser.search.suggestions.SearchSuggestionClient
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

class SearchEngineWrapper private constructor(aContext: Context) :
    OnSharedPreferenceChangeListener {
    private val mContextRef: WeakReference<Context?>
    var currentSearchEngine: SearchEngine? = null
        private set
    private var mSuggestionsClient: SearchSuggestionClient? = null
    private val mPrefs: SharedPreferences?
    private var mAutocompleteEnabled: Boolean
    private var mSearchEnginesMap: LinkedHashMap<String?, SearchEngine>? = null
    fun registerForUpdates() {
        if (hasContext()) {
            context!!.registerReceiver(
                mLocaleChangedReceiver,
                IntentFilter(Intent.ACTION_LOCALE_CHANGED)
            )
            mPrefs?.registerOnSharedPreferenceChangeListener(this)
        }
    }

    fun unregisterForUpdates() {
        if (hasContext()) {
            try {
                context!!.unregisterReceiver(mLocaleChangedReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
            mPrefs?.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    fun getSearchURL(aQuery: String?): String {
        return currentSearchEngine!!.buildSearchUrl(aQuery!!)
    }

    fun getSuggestions(aQuery: String?): CompletableFuture<List<String>?> {
        return getSuggestionsAsync(mSuggestionsClient!!, aQuery ?: "")
    }

    val resourceURL: String
        get() {
            val uri = Uri.parse(currentSearchEngine!!.buildSearchUrl(""))
            return uri.scheme + "://" + uri.host
        }

    // Receiver for locale updates
    private val mLocaleChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
                val userSearchEngineId = SettingsStore.getInstance(context).searchEngineId
                setupSearchEngine(context, userSearchEngineId)
            }
        }
    }

    init {
        mContextRef = WeakReference(aContext)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context!!)
        mAutocompleteEnabled = SettingsStore.getInstance(context!!).isAutocompleteEnabled
        val preferredSearchEngineId = SettingsStore.getInstance(context!!).searchEngineId
        setupSearchEngine(aContext, preferredSearchEngineId)
    }

    val availableSearchEngines: Collection<SearchEngine>
        get() = mSearchEnginesMap!!.values

    fun setDefaultSearchEngine() {
        if (hasContext()) setupSearchEngine(context!!, null)
    }

    fun setCurrentSearchEngineId(context: Context?, searchEngineId: String?) {
        if (hasContext()) SettingsStore.getInstance(this.context!!).searchEngineId = searchEngineId
    }

    /**
     * We cannot send system ACTION_LOCALE_CHANGED so the component refreshes the engines
     * with the updated SearchLocalizationProvider information so we have to update the whole manager.
     *
     * @param aContext Activity context
     * @param userPref User preferred engine (among the available ones)
     */
    private fun setupSearchEngine(aContext: Context, userPref: String?) {
        val engineFilterList: List<SearchEngineFilter> = ArrayList()
        val data = GeolocationData.parse(SettingsStore.getInstance(aContext).geolocationData)
        val localizationProvider: SearchLocalizationProvider = if (data == null) {
            Log.d(LOGTAG, "Using Locale based search localization provider")
            // If we don't have geolocation data we default to the Locale search localization provider
            LocaleSearchLocalizationProvider()
        } else {
            Log.d(LOGTAG, "Using Geolocation based search localization provider: $data")
            // If we have geolocation data we initialize the provider with the received data.
            GeolocationLocalizationProvider(data)
        }

        // Configure the assets search with the localization provider and the engines that we want
        // to filter.
        val engineProvider = AssetsSearchEngineProvider(
            localizationProvider, emptyList(), emptyList()
        )
        var searchEngineManager = SearchEngineManager(
            listOf(engineProvider),
            (Default as CoroutineContext)
        )

        // If we don't get any result we use the default configuration.
        var searchEngines = searchEngineManager.getSearchEngines(aContext)
        if (searchEngines.isEmpty()) {
            Log.d(LOGTAG, "  Could not find any available search engines, using default.")
            searchEngineManager = SearchEngineManager()
            searchEngines = searchEngineManager.getSearchEngines(aContext)
        }
        mSearchEnginesMap = LinkedHashMap(searchEngines.size)
        for (i in searchEngines.indices) {
            val searchEngine = searchEngines[i]
            mSearchEnginesMap!![searchEngine.identifier] = searchEngine
        }
        var userPrefName = ""
        if (mSearchEnginesMap!!.containsKey(userPref)) {
            // The search component API uses the engine's name, not its identifier.
            userPrefName = mSearchEnginesMap!![userPref]!!.name
        }

        // A name can be used if the user gets to choose among the available engines
        currentSearchEngine = searchEngineManager.getDefaultSearchEngine(aContext, userPrefName)
        mSuggestionsClient = SearchSuggestionClient(
            currentSearchEngine!!
        ) label@
        { searchUrl: String? ->
            if (mAutocompleteEnabled && vRBrowserActivity != null) {
                if (!vRBrowserActivity!!.windows.isInPrivateMode) {
                    return@label fetchSearchSuggestions(context!!, searchUrl!!)
                }
            }
            null
        }
        SettingsStore.getInstance(context!!).searchEngineId = currentSearchEngine!!.identifier
    }

    private fun hasContext(): Boolean {
        return mContextRef.get() != null
    }

    private val context: Context?
        private get() = mContextRef.get()
    private val vRBrowserActivity: VRBrowserActivity?
        private get() = if (hasContext() && context is VRBrowserActivity) context as VRBrowserActivity? else null

    // SharedPreferences.OnSharedPreferenceChangeListener
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (mContextRef.get() != null) {
            if (key == mContextRef.get()!!
                    .getString(R.string.settings_key_geolocation_data) || key == mContextRef.get()!!
                    .getString(R.string.settings_key_search_engine_id)
            ) {
                val searchEngineId = SettingsStore.getInstance(mContextRef.get()!!).searchEngineId
                if (currentSearchEngine == null || currentSearchEngine!!.identifier != searchEngineId) {
                    setupSearchEngine(mContextRef.get()!!, searchEngineId)
                }
            } else if (key == mContextRef.get()!!.getString(R.string.settings_key_autocomplete)) {
                mAutocompleteEnabled =
                    SettingsStore.getInstance(mContextRef.get()!!).isAutocompleteEnabled
            }
        }
    }

    companion object {
        private val LOGTAG = SystemUtils.createLogtag(SearchEngineWrapper::class.java)
        private var mSearchEngineWrapperInstance: SearchEngineWrapper? = null

        @JvmStatic
        @Synchronized
        operator fun get(aContext: Context): SearchEngineWrapper {
            if (mSearchEngineWrapperInstance == null) {
                mSearchEngineWrapperInstance = SearchEngineWrapper(aContext)
            } else if (aContext != mSearchEngineWrapperInstance!!.context) {
                // Because of the architecture of the app, this is very unlikely (but just in case...).
                Log.w(LOGTAG, "Context has changed")
                mSearchEngineWrapperInstance = SearchEngineWrapper(aContext)
            }
            return mSearchEngineWrapperInstance!!
        }
    }
}