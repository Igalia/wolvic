package com.igalia.wolvic.browser.components

import android.content.Context
import com.igalia.wolvic.VRBrowserApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.top.sites.*

/**
 * Helper class to simplify working with top sites (frequent, recent, pinned).
 */
class TopSitesHelper(context: Context, private val scope: CoroutineScope) {

    private val storage: TopSitesStorage
    private val useCases: TopSitesUseCases
    private val config: TopSitesConfig

    init {
        val app = context.applicationContext as VRBrowserApplication
        storage = DefaultTopSitesStorage(
            pinnedSitesStorage = app.places.pinned,
            historyStorage = app.places.history
        )
        useCases = TopSitesUseCases(storage)
        config = TopSitesConfig(totalSites = 8)
    }

    /**
     * Creates and returns a TopSitesFeature connected to the given view.
     *
     * @param view The view that will display the top sites
     * @return A `TopSitesFeature` that should be started/stopped with the lifecycle.
     */
    fun createFeature(view: TopSitesView): TopSitesFeature {
        return TopSitesFeature(
            view = view,
            storage = storage,
            config = { config }
        )
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