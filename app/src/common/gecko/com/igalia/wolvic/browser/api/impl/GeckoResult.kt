package com.igalia.wolvic.browser.api

import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Wait for a GeckoResult to be complete in a co-routine.
 */
suspend fun <T> GeckoResult<T>.await() = suspendCoroutine<T?> { continuation ->
    then({
        continuation.resume(it)
        GeckoResult<Void>()
    }, {
        continuation.resumeWithException(it)
        GeckoResult<Void>()
    })
}