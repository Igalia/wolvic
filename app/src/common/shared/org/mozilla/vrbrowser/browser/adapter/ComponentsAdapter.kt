/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.adapter

import kotlinx.coroutines.flow.collect
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifChanged
import org.mozilla.vrbrowser.browser.components.GeckoEngineSession
import org.mozilla.vrbrowser.browser.engine.Session

class ComponentsAdapter private constructor(
        val store: BrowserStore = BrowserStore()
) {
    interface StoreUpdatesListener {
        fun onTabSelected(state: BrowserState, tab: SessionState?) {}
    }

    private val storeUpdatesListeners = arrayListOf<StoreUpdatesListener>()

    fun addStoreUpdatesListener(listener: StoreUpdatesListener) {
        storeUpdatesListeners.add(listener)
    }

    fun removeStoreUpdatesListener(listener: StoreUpdatesListener) {
        storeUpdatesListeners.remove(listener)
    }

    companion object {
        private val instance: ComponentsAdapter = ComponentsAdapter()

        @JvmStatic
        fun get(): ComponentsAdapter = instance
    }

    fun addSession(session: Session) {
        store.dispatch(TabListAction.AddTabAction(
                tab = session.toTabSessionState()
        ))
    }

    fun removeSession(id: String) {
        store.dispatch(TabListAction.RemoveTabAction(
                tabId = id
        ))
    }

    fun selectSession(session: Session) {
        store.dispatch(TabListAction.SelectTabAction(
                tabId = session.id
        ))
    }

    fun link(session: Session) {
        store.dispatch(EngineAction.LinkEngineSessionAction(
                session.id,
                GeckoEngineSession(session)
        ))
    }

    fun unlink(session: Session) {
        // Whenever a extension popup is closed we have to notify to unset the popupSession
        store.state.extensions.map { it.value }.forEach {
            it.popupSession?.let {
                engineSession ->
                if ((engineSession as GeckoEngineSession).session == session) {
                    store.dispatch(
                            WebExtensionAction.UpdatePopupSessionAction(it.id, popupSession = null)
                    )
                }
            }
        }
        store.dispatch(EngineAction.UnlinkEngineSessionAction(
                session.id
        ))
    }

    init {
        // This flow calls listeners when an Add-On request a Session selection
        store.flowScoped { flow ->
            flow.ifChanged { it.selectedTab }
                    .collect { state ->
                        storeUpdatesListeners.forEach { listener ->
                            listener.onTabSelected(state, state.selectedTab)
                        }
                    }
        }
    }

    fun getSessionStateForSession(session: Session?): SessionState? {
        return store.state.tabs.firstOrNull() {
            it.id == session?.id
        }
    }

    fun getSortedEnabledExtensions(): List<WebExtensionState> {
        return store.state.extensions.values.filter { it.enabled }.sortedBy { it.name }
    }
}

private fun Session.toTabSessionState(): TabSessionState {
    return TabSessionState(
            id = id,
            content = ContentState(
                    url = currentUri,
                    private = isPrivateMode,
                    title = currentTitle
            ),
            parentId = null,
            extensionState = emptyMap(),
            readerState = ReaderState(),
            engineState = EngineState(
                    GeckoEngineSession(this),
                    null
            )
    )
}