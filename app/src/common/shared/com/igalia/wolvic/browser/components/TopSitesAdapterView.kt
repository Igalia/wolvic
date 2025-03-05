package com.igalia.wolvic.browser.components

import mozilla.components.feature.top.sites.TopSite
import mozilla.components.feature.top.sites.view.TopSitesView

class TopSitesAdapterView(
    private val adapter: TopSitesAdapter
) : TopSitesView {
    override fun displayTopSites(topSites: List<TopSite>) {
        // We could sort and filter the results at this point.
        adapter.updateTopSites(topSites)
    }
}