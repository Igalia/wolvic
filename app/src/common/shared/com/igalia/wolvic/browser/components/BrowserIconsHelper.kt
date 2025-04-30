package com.igalia.wolvic.browser.components

import android.content.Context
import android.widget.ImageView
import com.igalia.wolvic.R
import com.igalia.wolvic.browser.engine.EngineProvider
import com.igalia.wolvic.utils.UrlUtils
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine

// Small helper class to simplify getting favicons.
class BrowserIconsHelper(context: Context, engine: Engine, store: BrowserStore) {

    private val browserIcons: BrowserIcons
    private val aboutPageFavicon = R.mipmap.ic_launcher

    init {
        browserIcons =
            BrowserIcons(context.applicationContext, EngineProvider.createClient(context))
        browserIcons.install(engine, store)
    }

    fun loadIntoView(
        view: ImageView,
        url: String,
        size: IconRequest.Size = IconRequest.Size.DEFAULT
    ) {
        if (UrlUtils.isAboutPage(url)) {
            view.setImageResource(aboutPageFavicon)
        } else {
            val request = IconRequest(url, size, emptyList(), null, false)
            browserIcons.loadIntoView(view, request, null, null)
        }
    }

    fun loadIntoView(
        view: ImageView,
        url: String,
        resources: List<IconRequest.Resource> = emptyList(),
        size: IconRequest.Size = IconRequest.Size.DEFAULT
    ) {
        val request = IconRequest(url, size, resources, null, false)
        browserIcons.loadIntoView(view, request, null, null)
    }
}
