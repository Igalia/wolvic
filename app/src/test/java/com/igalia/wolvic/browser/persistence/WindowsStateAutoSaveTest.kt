package com.igalia.wolvic.browser.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.igalia.wolvic.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = TestApplication::class)
class WindowsStateAutoSaveTest {

    private lateinit var store: RecordingStore
    private var state = WindowsState()

    @Before
    fun setUp() {
        store = RecordingStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun savesPeriodicallyOnlyWhileStarted() = runTest {
        val autoSave = createAutoSave()
        autoSave.start()

        advanceTimeBy(PERIODIC_INTERVAL)
        runCurrent()

        assertEquals(1, store.writes.size)

        autoSave.stop()

        advanceTimeBy(PERIODIC_INTERVAL * 2)
        runCurrent()

        assertEquals(1, store.writes.size)
    }

    @Test
    fun ignoresRequestsAfterShutdown() = runTest {
        val autoSave = createAutoSave()
        autoSave.start()

        autoSave.shutdown()

        autoSave.saveNow()

        advanceTimeBy(PERIODIC_INTERVAL * 2)
        runCurrent()

        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun shutdownWaitsForThePendingWrite() {
        val slowStore = SlowStore(ApplicationProvider.getApplicationContext())
        val scope = CoroutineScope(Dispatchers.IO)
        val autoSave = WindowsStateAutoSave(
            store = slowStore,
            currentState = { state },
            scope = scope,
            shutdownTimeout = SHUTDOWN_TIMEOUT,
            ioDispatcher = Dispatchers.IO,
        )

        autoSave.saveNow()
        autoSave.shutdown()

        assertEquals(listOf(state), slowStore.writes)

        scope.cancel()
    }

    private fun TestScope.createAutoSave(): WindowsStateAutoSave {
        return WindowsStateAutoSave(
            store = store,
            currentState = { state },
            scope = backgroundScope,
            periodicInterval = PERIODIC_INTERVAL,
            mainDispatcher = StandardTestDispatcher(testScheduler),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private open class RecordingStore(context: Context) : WindowsStateStore(context) {

        val writes: MutableList<WindowsState> = CopyOnWriteArrayList()

        override fun write(aState: WindowsState) {
            writes += aState
        }
    }

    private class SlowStore(context: Context) : RecordingStore(context) {

        override fun write(aState: WindowsState) {
            Thread.sleep(WRITE_DURATION.inWholeMilliseconds)

            super.write(aState)
        }
    }

    private companion object {
        val PERIODIC_INTERVAL = 30.seconds
        val WRITE_DURATION = 200.milliseconds
        val SHUTDOWN_TIMEOUT = 10.seconds
    }
}
