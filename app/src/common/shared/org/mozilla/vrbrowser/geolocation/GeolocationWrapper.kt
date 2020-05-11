package org.mozilla.vrbrowser.geolocation

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mozilla.components.service.location.LocationService
import mozilla.components.service.location.MozillaLocationService
import org.mozilla.vrbrowser.browser.engine.EngineProvider
import org.mozilla.vrbrowser.browser.SettingsStore
import java.util.concurrent.CompletableFuture

object GeolocationWrapper {

    fun update(context: Context) {
        val locationService = MozillaLocationService(
                context,
                EngineProvider.getDefaultClient(context),
                org.mozilla.vrbrowser.BuildConfig.MLS_TOKEN
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
                    org.mozilla.vrbrowser.BuildConfig.MLS_TOKEN
            )
            locationService.fetchRegion(false)
        }

}