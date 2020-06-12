package org.mozilla.vrbrowser.browser.components

import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.concept.engine.Settings
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.browser.engine.SessionStore

class GeckoEngineSession(
        val geckoSession: GeckoSession
): EngineSession() {
    constructor() :
        this(SessionStore.get().createSession(false, false).geckoSession)

    override fun loadUrl(url: String, parent: EngineSession?, flags: LoadUrlFlags, additionalHeaders: Map<String, String>?) {
        geckoSession.loadUri(url)
    }

    override val settings: Settings = object : Settings() {}
    override fun clearFindMatches() = Unit
    override fun disableTrackingProtection() = Unit
    override fun enableTrackingProtection(policy: TrackingProtectionPolicy) = Unit
    override fun exitFullScreenMode() = Unit
    override fun findAll(text: String) = Unit
    override fun findNext(forward: Boolean) = Unit
    override fun goBack() = Unit
    override fun goForward() = Unit
    override fun loadData(data: String, mimeType: String, encoding: String) = Unit
    override fun recoverFromCrash(): Boolean = true
    override fun reload() = Unit
    override fun restoreState(state: EngineSessionState) = true
    override fun saveState(): EngineSessionState = DummyEngineSessionState()
    override fun stopLoading() = Unit
    override fun toggleDesktopMode(enable: Boolean, reload: Boolean) = Unit
}

private class DummyEngineSessionState : EngineSessionState {
    override fun toJSON(): JSONObject = JSONObject()
}