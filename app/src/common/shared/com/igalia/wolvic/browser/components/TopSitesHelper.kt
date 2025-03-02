package com.igalia.wolvic.browser.components

import android.content.Context
import com.igalia.wolvic.VRBrowserApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.feature.top.sites.TopSitesConfig
import mozilla.components.feature.top.sites.TopSitesFeature
import mozilla.components.feature.top.sites.TopSitesStorage
import mozilla.components.feature.top.sites.TopSitesUseCases
import mozilla.components.feature.top.sites.view.TopSitesView
import mozilla.components.support.base.feature.LifecycleAwareFeature

// Small helper class to simplify working with top sites (frequent, recent, pinned).
class TopSitesHelper(
    context: Context
) {
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
     * The returned feature should be connected to a lifecycle.
     *
     * @param view The view that will display the top sites
     * @return A LifecycleAwareFeature that should be connected to the UI lifecycle
     */
    fun createFeature(view: TopSitesView): LifecycleAwareFeature {
        return TopSitesFeature(
            view = view,
            storage = storage,
            config = { config }
        )
    }

    /**
     * Adds a new pinned site.
     */
    fun addPinnedSite(title: String, url: String) {
        CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
            useCases.addPinnedSites(title, url)
        }
    }

    /**
     * Removes the specified top site.
     */
    fun removeSite(site: TopSite) {
        CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
            useCases.removeTopSites(site)
        }
    }

    /**
     * Updates an existing top site with new information.
     */
    fun updateSite(site: TopSite, newTitle: String, newUrl: String) {
        CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
            useCases.updateTopSites(site, newTitle, newUrl)
        }
    }
}