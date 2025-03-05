package com.igalia.wolvic.browser.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import com.igalia.wolvic.utils.BitmapCache
import com.igalia.wolvic.utils.SystemUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import mozilla.components.support.images.CancelOnDetach
import mozilla.components.support.images.DesiredSize
import mozilla.components.support.images.decoder.AndroidImageDecoder
import java.io.IOException
import java.util.concurrent.TimeUnit

class RemoteImageHelper(
    private val context: Context,
    private val client: Client
) {
    private val LOGTAG = SystemUtils.createLogtag(RemoteImageHelper::class.java)
    private val decoder = AndroidImageDecoder()

    fun loadIntoView(view: ImageView, url: String, private: Boolean = false) {
        val bitmapCache = BitmapCache.getInstance(context)
        
        // Tag the view with the URL to identify the image that it should display.
        view.tag = url

        // Target size is either the view dimensions or the default if the view is not measured yet.
        val targetSize = if (view.width > 0 && view.height > 0) {
            Math.max(view.width, view.height)
        } else {
            DEFAULT_TARGET_SIZE
        }

        // Size guidance for the image decoder.
        val desiredSize = DesiredSize(
            targetSize = targetSize,
            minSize = targetSize / DEFAULT_MIN_MAX_MULTIPLIER,
            maxSize = targetSize * DEFAULT_MIN_MAX_MULTIPLIER,
            maxScaleFactor = DEFAULT_MAXIMUM_SCALE_FACTOR
        )

        // ...and apply it on the main UI thread.
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedBitmap = bitmapCache.getBitmap(url).get()

                if (cachedBitmap != null) {
                    // If the image is in cache, we can simply apply it (in the Main thread).
                    withContext(Dispatchers.Main) {
                        if (url == view.tag) {
                            view.setImageBitmap(cachedBitmap)
                        }
                    }
                } else {
                    // Otherwise, we fetch it and decode the image.
                    val bitmap = fetchAndDecode(url, desiredSize, private)

                    if (bitmap != null) {
                        bitmapCache.addBitmap(url, bitmap)

                        // Apply the image (in the Main thread).
                        withContext(Dispatchers.Main) {
                            if (url == view.tag) {
                                view.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(LOGTAG, "Error loading image: ${e.message}")
            }
        }

        // NOTE: To support RecyclerView scrolling, image downloads will continue when views are detached.
        // If needed, this can be changed with view.addOnAttachStateChangeListener(CancelOnDetach(job))).
    }

    private fun fetchAndDecode(
        url: String,
        desiredSize: DesiredSize,
        private: Boolean
    ): Bitmap? {
        val request = Request(
            url = url.trim(),
            method = Request.Method.GET,
            cookiePolicy = if (private) Request.CookiePolicy.OMIT else Request.CookiePolicy.INCLUDE,
            connectTimeout = Pair(DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS),
            readTimeout = Pair(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS),
            redirect = Request.Redirect.FOLLOW,
            useCaches = true,
            private = private
        )

        return try {
            val response = client.fetch(request)
            if (response.isSuccess) {
                val data = response.body.useStream { it.readBytes() }
                decoder.decode(data, desiredSize)
            } else {
                response.close()
                null
            }
        } catch (e: IOException) {
            Log.w(LOGTAG, "Error loading image: ${e.message}")
            null
        }
    }

    companion object {
        private const val DEFAULT_TARGET_SIZE = 256
        private const val DEFAULT_MAXIMUM_SCALE_FACTOR = 2.0f
        private const val DEFAULT_MIN_MAX_MULTIPLIER = 4
        private const val DEFAULT_CONNECT_TIMEOUT = 5L
        private const val DEFAULT_READ_TIMEOUT = 20L
    }
}