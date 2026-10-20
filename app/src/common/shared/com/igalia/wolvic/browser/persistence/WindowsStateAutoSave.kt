package com.igalia.wolvic.browser.persistence

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class WindowsStateAutoSave @JvmOverloads constructor(
    private val store: WindowsStateStore,
    private val currentState: () -> WindowsState,
    private val scope: CoroutineScope,
    private val minimumInterval: Duration = MINIMUM_INTERVAL,
    private val periodicInterval: Duration = PERIODIC_INTERVAL,
    private val shutdownTimeout: Duration = SHUTDOWN_TIMEOUT,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val snapshots = Channel<WindowsState>(Channel.CONFLATED)

    private val writer = scope.launch(ioDispatcher) {
        for (state in snapshots) {
            store.write(state)
        }
    }

    private var lastSnapshot: TimeMark? = null
    private var isShutDown = false

    private var periodicSaves: Job? = null
    private var pendingSave: Job? = null

    @MainThread
    fun start() {
        if (periodicSaves != null) {
            return
        }

        periodicSaves = scope.launch(mainDispatcher) {
            while (true) {
                delay(periodicInterval)
                requestSave()
            }
        }
    }

    @MainThread
    fun stop() {
        periodicSaves?.cancel()
        periodicSaves = null
    }

    @MainThread
    fun requestSave() {
        if (isShutDown || pendingSave?.isActive == true) {
            return
        }

        val remainingInterval = lastSnapshot?.let { minimumInterval - it.elapsedNow() } ?: Duration.ZERO

        pendingSave = scope.launch(mainDispatcher) {
            delay(remainingInterval)
            takeSnapshot()
        }
    }

    @MainThread
    fun saveNow() {
        if (isShutDown) {
            return
        }

        cancelPendingSave()

        takeSnapshot()
    }

    @MainThread
    fun shutdown() {
        isShutDown = true

        stop()
        cancelPendingSave()

        snapshots.close()

        runBlocking {
            withTimeoutOrNull(shutdownTimeout) { writer.join() }
        }
    }

    private fun cancelPendingSave() {
        pendingSave?.cancel()
        pendingSave = null
    }

    private fun takeSnapshot() {
        lastSnapshot = timeSource.markNow()

        snapshots.trySend(currentState())
    }

    private companion object {
        val MINIMUM_INTERVAL = 2.seconds
        val PERIODIC_INTERVAL = 30.seconds
        val SHUTDOWN_TIMEOUT = 1.seconds
    }
}
