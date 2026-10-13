@file:JvmName("WindowsStateSnapshot")

package com.igalia.wolvic.browser.persistence

import com.igalia.wolvic.browser.HistoryStore
import com.igalia.wolvic.browser.engine.Session
import com.igalia.wolvic.browser.engine.SessionState
import com.igalia.wolvic.ui.widgets.WindowWidget
import com.igalia.wolvic.ui.widgets.Windows

fun createWindowsState(
    privateMode: Boolean,
    focusedWindow: WindowWidget,
    regularWindows: List<WindowWidget>,
    sessions: List<Session>,
): WindowsState {
    val tabs = sessions.map { it.sessionState }.filterNot { it.isBlocked() }

    return WindowsState().also { state ->
        state.privateMode = privateMode
        state.focusedWindowPlacement = focusedWindow.restorablePlacement

        state.tabs = tabs.mapTo(ArrayList()) { it.copy() }

        state.regularWindowsState = regularWindows.mapNotNullTo(ArrayList()) { window ->
            window.session?.let { window.toWindowState(tabs.indexOf(it.sessionState)) }
        }
    }
}

private fun SessionState.isBlocked(): Boolean {
    val uri = mUri ?: return false

    return HistoryStore.BLOCK_LIST.any { uri.startsWith(it) }
}

private val WindowWidget.restorablePlacement: Windows.WindowPlacement
    get() = if (isFullScreen) windowPlacementBeforeFullscreen else windowPlacement

private fun WindowWidget.toWindowState(tabIndex: Int): WindowState {
    val widgetPlacement = when {
        isFullScreen -> beforeFullscreenPlacement
        isResizing -> beforeResizePlacement
        else -> placement
    }

    return WindowState().also { state ->
        state.placement = restorablePlacement
        state.tabIndex = tabIndex

        state.textureWidth = widgetPlacement.width
        state.textureHeight = widgetPlacement.height
        state.worldWidth = widgetPlacement.worldWidth

        state.contentType = when {
            isNativeContentVisible -> selectedPanel
            currentContentType == Windows.ContentType.NEW_TAB -> Windows.ContentType.NEW_TAB
            else -> Windows.ContentType.WEB_CONTENT
        }
    }
}
