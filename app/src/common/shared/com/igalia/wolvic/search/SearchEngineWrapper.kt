package com.igalia.wolvic.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.igalia.wolvic.R
import com.igalia.wolvic.VRBrowserActivity
import com.igalia.wolvic.browser.SettingsStore
import com.igalia.wolvic.geolocation.GeolocationData
import com.igalia.wolvic.search.suggestions.fetchSearchSuggestions
import com.igalia.wolvic.search.suggestions.getSuggestionsAsync
import com.igalia.wolvic.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.RegionState
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.search.ext.buildSearchUrl
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.suggestions.SearchSuggestionClient
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

class SearchEngineWrapper private constructor(aContext: Context) :
    OnSharedPreferenceChangeListener {
    private val mContextRef: WeakReference<Context?>
    private var currentSearchEngine: SearchEngine? = null
    private var mSuggestionsClient: SearchSuggestionClient? = null
    private val mPrefs: SharedPreferences?
    private var mAutocompleteEnabled: Boolean
    private var mSearchEnginesMap: LinkedHashMap<String?, SearchEngine>? = null
    private val mIoDispatcher: CoroutineContext = Dispatchers.IO
    private var mRegionState: RegionState? = null
    private val mBrowserStore = BrowserStore(
        middleware = listOf(
            SearchMiddleware(aContext,
                ioDispatcher = mIoDispatcher)
        ))
    fun registerForUpdates() {
        if (hasContext()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context!!.registerReceiver(
                    mLocaleChangedReceiver,
                    IntentFilter(Intent.ACTION_LOCALE_CHANGED), Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context!!.registerReceiver(
                        mLocaleChangedReceiver,
                        IntentFilter(Intent.ACTION_LOCALE_CHANGED)
                )
            }
            mPrefs?.registerOnSharedPreferenceChangeListener(this)
        }
    }

    fun setCurrentSearchEngine(searchEngine: SearchEngine?) {
        currentSearchEngine = searchEngine
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
        if (currentSearchEngine == null) setupPreferredSearchEngine()
        return currentSearchEngine!!.buildSearchUrl(aQuery!!)
    }

    fun getSuggestions(aQuery: String?): CompletableFuture<List<String>?> {
        return getSuggestionsAsync(mSuggestionsClient!!, aQuery ?: "")
    }

    val resourceURL: String
        get() {
            if (currentSearchEngine == null) setupPreferredSearchEngine()
            val uri = Uri.parse(currentSearchEngine!!.buildSearchUrl(""))
            return uri.scheme + "://" + uri.host
        }

    // Receiver for locale updates
    private val mLocaleChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
                setupPreferredSearchEngine()
            }
        }
    }

    init {
        mContextRef = WeakReference(aContext)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context!!)
        mAutocompleteEnabled = SettingsStore.getInstance(context!!).isAutocompleteEnabled
        setupPreferredSearchEngine()
    }

    val availableSearchEngines: Collection<SearchEngine>
        get() {
            updateSearchEngine()
            return mSearchEnginesMap!!.values
        }

    fun setDefaultSearchEngine() {
        if (hasContext()) setupSearchEngine(context!!, null)
    }

    fun setCurrentSearchEngineId(context: Context?, searchEngineId: String?) {
        if (hasContext()) SettingsStore.getInstance(context!!).searchEngineId = searchEngineId
    }

    fun setupPreferredSearchEngine() {
        val preferredSearchEngineId = SettingsStore.getInstance(context!!).searchEngineId
        setupSearchEngine(context!!, preferredSearchEngineId)
    }

    /**
     * We cannot directly getCurrentSearchEngine() if it's not fully initialized, so use this method instead.
     *
     * @return SearchEngine currentSearchEngine
     */
    fun resolveCurrentSearchEngine(): SearchEngine {
        if (currentSearchEngine == null) setupPreferredSearchEngine()
        return currentSearchEngine!!
    }

    /**
     * We cannot send system ACTION_LOCALE_CHANGED when the component refreshes the engines
     * with the updated information so we have to update the whole manager.
     *
     * @param aContext Activity context
     * @param userPref User preferred engine (among the available ones)
     */
    private fun setupSearchEngine(aContext: Context?, userPref: String?) {
        val thisContext: Context = aContext ?: context!!

        val data = GeolocationData.parse(SettingsStore.getInstance(thisContext).geolocationData)
        val regionState = if (data != null) {
            Log.d(LOGTAG, "Using Geolocation based search localization: $data")
            // If we have geolocation data we initialize with the received data.
            val country = data.countryCode
            RegionState(country, country)
        } else {
            val country = thisContext.resources.configuration.locales.get(0).country
            RegionState(country, country)
        }

        if (mRegionState == null || mRegionState != regionState) {
            mBrowserStore.dispatch(
                SearchAction.SetRegionAction(
                    regionState,
                ),
            )
            mRegionState = regionState
        }

        val searchEngines: List<SearchEngine> = mBrowserStore.state.search.searchEngines
        mSearchEnginesMap = LinkedHashMap(searchEngines.size)
        for (i in searchEngines.indices) {
            val searchEngine = searchEngines[i]
            mSearchEnginesMap!![searchEngine.id] = searchEngine
        }

        val newSearchEngine = if (mSearchEnginesMap!!.containsKey(userPref)) {
            mSearchEnginesMap!![userPref]
        } else {
            mSearchEnginesMap!![mBrowserStore.state.search.regionDefaultSearchEngineId]
        }

        if (newSearchEngine == null || currentSearchEngine == newSearchEngine) return

        mSuggestionsClient = SearchSuggestionClient(
            newSearchEngine
        ) label@
        { searchUrl: String? ->
            if (mAutocompleteEnabled && vRBrowserActivity != null) {
                if (!vRBrowserActivity!!.windows.isInPrivateMode) {
                    return@label fetchSearchSuggestions(thisContext, searchUrl!!)
                }
            }
            null
        }
        currentSearchEngine = newSearchEngine
    }

    private fun hasContext(): Boolean {
        return mContextRef.get() != null
    }

    private fun updateSearchEngine() {
        val newSearchEngineName = if (currentSearchEngine != null) {
            currentSearchEngine!!.id
        } else {
            null
        }

        setupSearchEngine(null, newSearchEngineName)
    }

    private val context: Context?
        private get() = mContextRef.get()
    private val vRBrowserActivity: VRBrowserActivity?
        private get() = if (hasContext() && context is VRBrowserActivity) context as VRBrowserActivity? else null

    // SharedPreferences.OnSharedPreferenceChangeListener
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (mContextRef.get() != null) {
            if (key == mContextRef.get()!!
                    .getString(R.string.settings_key_geolocation_data) || key == mContextRef.get()!!
                    .getString(R.string.settings_key_search_engine_id)
            ) {
                val searchEngineId = SettingsStore.getInstance(mContextRef.get()!!).searchEngineId
                if (currentSearchEngine == null || currentSearchEngine!!.id != searchEngineId) {
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
