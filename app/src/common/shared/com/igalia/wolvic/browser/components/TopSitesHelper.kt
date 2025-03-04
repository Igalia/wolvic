package com.igalia.wolvic.browser.components

import android.content.Context
import com.igalia.wolvic.VRBrowserApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.FrecencyThresholdOption
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.feature.top.sites.TopSitesConfig
import mozilla.components.feature.top.sites.TopSitesFeature
import mozilla.components.feature.top.sites.TopSitesFrecencyConfig
import mozilla.components.feature.top.sites.TopSitesProviderConfig
import mozilla.components.feature.top.sites.TopSitesStorage
import mozilla.components.feature.top.sites.TopSitesUseCases

/**
 * Helper class to simplify working with top sites (frequent, recent, pinned).
 */
class TopSitesHelper(context: Context, private val scope: CoroutineScope) {

    private val storage: TopSitesStorage
    private val useCases: TopSitesUseCases
    private val config: TopSitesConfig

    private val TOTAL_SITES = 16

    init {
        val app = context.applicationContext as VRBrowserApplication
        storage = DefaultTopSitesStorage(
            pinnedSitesStorage = app.places.pinned,
            historyStorage = app.places.history,
            defaultTopSites = listOf(
                Pair("Wolvic", "https://www.wolvic.com"), Pair("Igalia", "http://www.igalia.com")
            )
        )
        useCases = TopSitesUseCases(storage)
        config = TopSitesConfig(
            totalSites = TOTAL_SITES,
            frecencyConfig = TopSitesFrecencyConfig(
                frecencyTresholdOption = FrecencyThresholdOption.SKIP_ONE_TIME_PAGES,
                frecencyFilter = { topSite ->
                    !topSite.url.lowercase().startsWith("about:")
                }
            ),
            providerConfig = TopSitesProviderConfig(
                showProviderTopSites = true
            )
        )
    }

    /**
     * Creates and returns a TopSitesFeature connected to the given view.
     *
     * @param adapter The view that will display the top sites
     * @return A `TopSitesFeature` that should be started/stopped with the lifecycle.
     */
    fun createFeature(adapter: TopSitesAdapter): TopSitesFeature {
        return TopSitesFeature(
            view = TopSitesAdapterView(adapter),
            storage = storage,
            config = { config })
    }

    /**
     * Adds a new pinned site asynchronously.
     */
    fun addPinnedSite(title: String, url: String) {
        scope.launch(Dispatchers.IO) {
            useCases.addPinnedSites(title, url)
        }
    }

    /**
     * Removes the specified top site asynchronously.
     */
    fun removeSite(site: TopSite) {
        scope.launch(Dispatchers.IO) {
            useCases.removeTopSites(site)
        }
    }

    /**
     * Updates an existing top site with new title and URL asynchronously.
     */
    fun updateSite(site: TopSite, newTitle: String, newUrl: String) {
        scope.launch(Dispatchers.IO) {
            useCases.updateTopSites(site, newTitle, newUrl)
        }
    }
}