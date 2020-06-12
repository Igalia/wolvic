package org.mozilla.vrbrowser.browser.adapter

import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.browser.components.GeckoEngineSession
import org.mozilla.vrbrowser.browser.engine.Session

class ComponentsAdapter private constructor(
        val store: BrowserStore = BrowserStore()
) {
    companion object {
        private val instance: ComponentsAdapter = ComponentsAdapter()

        fun get(): ComponentsAdapter = instance
    }

    fun addSession(session: Session) {
        store.dispatch(TabListAction.AddTabAction(
                tab = session.toTabSessionState()
        ))
    }

    fun removeSession(session: Session) {
        store.dispatch(TabListAction.RemoveTabAction(
                tabId = session.id
        ))
    }

    fun selectSession(session: Session) {
        store.dispatch(TabListAction.SelectTabAction(
                tabId = session.id
        ))
    }

    fun link(tabId: String, geckoSession: GeckoSession) {
        store.dispatch(EngineAction.LinkEngineSessionAction(
                tabId,
                GeckoEngineSession(geckoSession)
        ))
    }

    fun unlink(tabId: String) {
        store.dispatch(EngineAction.UnlinkEngineSessionAction(
                tabId
        ))
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
                    GeckoEngineSession(geckoSession),
                    null
            )
    )
}