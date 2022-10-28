package com.igalia.wolvic.browser.components

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import com.igalia.wolvic.browser.engine.EngineProvider
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest

// Small helper class to simplify getting favicons.
object BrowserIconsHelper {

    @SuppressLint("StaticFieldLeak")
    private lateinit var browserIcons: BrowserIcons;

    @JvmStatic
    fun get(context: Context): BrowserIcons {
        if (!::browserIcons.isInitialized) {
            browserIcons =
                BrowserIcons(context.applicationContext, EngineProvider.createClient(context))
        }
        return browserIcons
    }

    @JvmStatic
    fun loadIntoView(
        context: Context,
        view: ImageView,
        url: String,
        size: IconRequest.Size = IconRequest.Size.DEFAULT
    ) {
        if (!::browserIcons.isInitialized) {
            browserIcons =
                BrowserIcons(context.applicationContext, EngineProvider.createClient(context))
        }
        val request = IconRequest(url, size, emptyList(), null, false)
        browserIcons.loadIntoView(view, request, null, null)
    }
}
