package com.igalia.wolvic.browser.components

import mozilla.components.feature.top.sites.TopSite

interface TopSitesAdapter {
    fun updateTopSites(sites: List<TopSite>)
    fun setClickListener(clickListener: ClickListener)

    interface ClickListener {
        fun onClicked(site: TopSite) {}
        fun onPinned(site: TopSite) {}
        fun onRemoved(site: TopSite) {}
    }
}