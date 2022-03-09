package com.igalia.wolvic.geolocation

import android.content.Context
import com.igalia.wolvic.browser.SettingsStore
import com.igalia.wolvic.browser.engine.EngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mozilla.components.service.location.LocationService
import mozilla.components.service.location.MozillaLocationService
import java.util.concurrent.CompletableFuture

object GeolocationWrapper {

    fun update(context: Context) {
        val locationService = MozillaLocationService(
                context,
                EngineProvider.getDefaultClient(context),
                com.igalia.wolvic.BuildConfig.MLS_TOKEN
        )
        CoroutineScope(Dispatchers.IO).launch {
            locationService.fetchRegion(true)?.run {
                val data: GeolocationData = GeolocationData.create(countryCode, countryName)
                SettingsStore.getInstance(context).geolocationData = data.toString()
            }
        }
    }

    fun get(context: Context): CompletableFuture<LocationService.Region?> =
        GlobalScope.future {
            val locationService = MozillaLocationService(
                    context,
                    EngineProvider.getDefaultClient(context),
                    com.igalia.wolvic.BuildConfig.MLS_TOKEN
            )
            locationService.fetchRegion(false)
        }

}