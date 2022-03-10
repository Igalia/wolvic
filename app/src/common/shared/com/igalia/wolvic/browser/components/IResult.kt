/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.components

import com.igalia.wolvic.browser.api.WResult
import kotlinx.coroutines.*
import mozilla.components.concept.engine.CancellableOperation
import kotlin.coroutines.*

/**
 * Wait for a WResult to be complete in a co-routine.
 */
suspend fun <T> WResult<T>.await() = suspendCoroutine<T?> { continuation ->
    then({
        continuation.resume(it)
        WResult.create<Void>()
    }, {
        continuation.resumeWithException(it)
        WResult.create<Void>()
    })
}

/**
 * Converts a [WResult] to a [CancellableOperation].
 */
fun <T> WResult<T>.asCancellableOperation(): CancellableOperation {
    val res = this
    return object : CancellableOperation {
        override fun cancel(): Deferred<Boolean> {
            val result = CompletableDeferred<Boolean>()
            res.cancel().then({
                result.complete(it ?: false)
                WResult.create<Void>()
            }, { throwable ->
                result.completeExceptionally(throwable)
                WResult.create<Void>()
            })
            return result
        }
    }
}

/**
 * Create a WResult from a co-routine.
 */
@Suppress("TooGenericExceptionCaught")
fun <T> CoroutineScope.launchWResult(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
) = WResult.create<T>().apply {
    launch(context, start) {
        try {
            val value = block()
            complete(value)
        } catch (exception: Throwable) {
            completeExceptionally(exception)
        }
    }
}
