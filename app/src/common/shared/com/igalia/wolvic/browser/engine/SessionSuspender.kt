package com.igalia.wolvic.browser.engine

import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE

class SessionSuspender(private val sessions: Iterable<Session>) {

    fun onActiveStateChanged() {
        suspendLeastRecentlyUsed(keep = MAX_INACTIVE_SESSIONS)
    }

    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> suspendLeastRecentlyUsed(keep = MAX_INACTIVE_SESSIONS_UNDER_MEMORY_PRESSURE)
        }
    }

    private fun suspendLeastRecentlyUsed(keep: Int) {
        sessions
            .filter { !it.isActive && it.wSession != null }
            .sortedByDescending { it.lastUse }
            .drop(keep)
            .forEach(Session::suspend)
    }

    companion object {
        internal const val MAX_INACTIVE_SESSIONS = 5
        internal const val MAX_INACTIVE_SESSIONS_UNDER_MEMORY_PRESSURE = 3
    }
}
