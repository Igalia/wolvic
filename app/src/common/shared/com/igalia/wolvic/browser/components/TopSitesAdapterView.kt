package com.igalia.wolvic.browser.components

import android.util.Log
import com.igalia.wolvic.utils.UrlUtils
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.feature.top.sites.view.TopSitesView

class TopSitesAdapterView(
    private val adapter: TopSitesAdapter
) : TopSitesView {
    override fun displayTopSites(topSites: List<TopSite>) {

        val LOGTAG = "TopSitesAdapterView"
        Log.e(LOGTAG, "Total sites received: ${topSites.size}")
        Log.e(LOGTAG, "Sites full dump:")
        topSites.forEachIndexed { index, site ->
            Log.e(
                LOGTAG,
                "$index: Title='${site.title}' URL='${site.url}' Type='${site.type}' Id='${site.id}'"
            )
        }


        // Remove duplicated domains from the results.
        val seenDomains = mutableSetOf<String>()
        val deduplicatedSites = topSites.filterNot { site ->
            val domain = UrlUtils.stripCommonSubdomains(UrlUtils.getHost(site.url))
            if (seenDomains.contains(domain)) {
                true
            } else {
                seenDomains.add(domain)
                false
            }
        }
        // adapter.updateTopSites(deduplicatedSites)
        adapter.updateTopSites(topSites)
    }
}