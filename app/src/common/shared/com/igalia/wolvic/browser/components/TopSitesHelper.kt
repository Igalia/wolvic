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

// Helper class to simplify working with top sites (frequent, recent, pinned).
class TopSitesHelper(context: Context, private val scope: CoroutineScope) {

    private val storage: TopSitesStorage
    private val useCases: TopSitesUseCases
    private val config: TopSitesConfig

    private val TOTAL_SITES = 12

    init {
        val app = context.applicationContext as VRBrowserApplication
        storage = DefaultTopSitesStorage(
            pinnedSitesStorage = app.places.pinned,
            historyStorage = app.places.history,
            defaultTopSites = listOf()
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

    fun createFeature(adapter: TopSitesAdapter): TopSitesFeature {
        return TopSitesFeature(
            view = TopSitesAdapterView(adapter),
            storage = storage,
            config = { config })
    }

    fun addPinnedSite(title: String, url: String) {
        scope.launch(Dispatchers.IO) {
            useCases.addPinnedSites(title, url)
        }
    }

    fun removeSite(site: TopSite) {
        scope.launch(Dispatchers.IO) {
            useCases.removeTopSites(site)
        }
    }

    fun updateSite(site: TopSite, newTitle: String, newUrl: String) {
        scope.launch(Dispatchers.IO) {
            useCases.updateTopSites(site, newTitle, newUrl)
        }
    }
}