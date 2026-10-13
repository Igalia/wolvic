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

class WindowsStateAutoSave @JvmOverloads constructor(
    private val store: WindowsStateStore,
    private val currentState: () -> WindowsState,
    private val scope: CoroutineScope,
    private val periodicInterval: Duration = PERIODIC_INTERVAL,
    private val shutdownTimeout: Duration = SHUTDOWN_TIMEOUT,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val snapshots = Channel<WindowsState>(Channel.CONFLATED)

    private val writer = scope.launch(ioDispatcher) {
        for (state in snapshots) {
            store.write(state)
        }
    }

    private var isShutDown = false

    private var periodicSaves: Job? = null

    @MainThread
    fun start() {
        if (periodicSaves != null) {
            return
        }

        periodicSaves = scope.launch(mainDispatcher) {
            while (true) {
                delay(periodicInterval)
                saveNow()
            }
        }
    }

    @MainThread
    fun stop() {
        periodicSaves?.cancel()
        periodicSaves = null
    }

    @MainThread
    fun saveNow() {
        if (isShutDown) {
            return
        }

        snapshots.trySend(currentState())
    }

    @MainThread
    fun shutdown() {
        isShutDown = true

        stop()

        snapshots.close()

        runBlocking {
            withTimeoutOrNull(shutdownTimeout) { writer.join() }
        }
    }

    private companion object {
        val PERIODIC_INTERVAL = 30.seconds
        val SHUTDOWN_TIMEOUT = 1.seconds
    }
}
