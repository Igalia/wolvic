package com.igalia.wolvic.browser.engine

import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import com.igalia.wolvic.browser.api.WSession
import com.igalia.wolvic.browser.engine.SessionSuspender.Companion.MAX_INACTIVE_SESSIONS
import com.igalia.wolvic.browser.engine.SessionSuspender.Companion.MAX_INACTIVE_SESSIONS_UNDER_MEMORY_PRESSURE
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@Suppress("DEPRECATION")
class SessionSuspenderTest {

    @Test
    fun testSuspendsLeastRecentlyUsedInactiveSessions() {
        val sessions = listOf(5L, 1L, 8L, 3L, 7L, 2L, 6L, 4L).map { session(lastUse = it) }

        SessionSuspender(sessions).onActiveStateChanged()

        assertSuspended(sessions, expected = sessions.filter { it.lastUse <= 3 })
    }

    @Test
    fun testDoesNotSuspendOrCountActiveSessions() {
        val active = (1L..3L).map { session(lastUse = it, active = true) }
        val inactive = (4L..9L).map { session(lastUse = it) }

        SessionSuspender(active + inactive).onActiveStateChanged()

        assertSuspended(active + inactive, expected = inactive.take(1))
    }

    @Test
    fun testDoesNotCountSuspendedSessions() {
        val suspended = (1L..3L).map { session(lastUse = it, suspended = true) }
        val alive = (4L..3L + MAX_INACTIVE_SESSIONS).map { session(lastUse = it) }

        SessionSuspender(suspended + alive).onActiveStateChanged()

        assertSuspended(alive, expected = emptyList())
    }

    @Test
    fun testIgnoresModerateMemoryPressure() {
        val sessions = (1L..8L).map { session(lastUse = it) }
        val suspender = SessionSuspender(sessions)

        suspender.onTrimMemory(TRIM_MEMORY_RUNNING_MODERATE)
        suspender.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)

        assertSuspended(sessions, expected = emptyList())
    }

    @Test
    fun testSuspendsMoreSessionsUnderCriticalMemoryPressure() {
        for (level in listOf(TRIM_MEMORY_RUNNING_CRITICAL, TRIM_MEMORY_COMPLETE)) {
            val sessions = (1L..8L).map { session(lastUse = it) }

            SessionSuspender(sessions).onTrimMemory(level)

            assertSuspended(sessions, expected = sessions.dropLast(MAX_INACTIVE_SESSIONS_UNDER_MEMORY_PRESSURE))
        }
    }

    private fun session(lastUse: Long, active: Boolean = false, suspended: Boolean = false): Session {
        val wSession = if (suspended) null else mock(WSession::class.java)

        return mock(Session::class.java).also {
            given(it.isActive).willReturn(active)
            given(it.wSession).willReturn(wSession)
            given(it.lastUse).willReturn(lastUse)
        }
    }

    private fun assertSuspended(sessions: List<Session>, expected: List<Session>) {
        sessions.forEach { verify(it, if (it in expected) times(1) else never()).suspend() }
    }
}
