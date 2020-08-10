/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.browser.components

import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.concept.engine.Settings
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.vrbrowser.browser.engine.Session
import org.mozilla.vrbrowser.browser.engine.SessionStore

class GeckoEngineSession(
        val session: Session
): EngineSession() {

    val geckoSession: GeckoSession = session.geckoSession

    override fun loadUrl(url: String, parent: EngineSession?, flags: LoadUrlFlags, additionalHeaders: Map<String, String>?) {
        session.loadUri(url, flags.value)
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
    override fun goToHistoryIndex(index: Int) = Unit
    override fun loadData(data: String, mimeType: String, encoding: String) = Unit
    override fun recoverFromCrash(): Boolean = true
    override fun reload(flags: LoadUrlFlags) = Unit
    override fun restoreState(state: EngineSessionState) = true
    override fun saveState(): EngineSessionState = DummyEngineSessionState()
    override fun stopLoading() = Unit
    override fun toggleDesktopMode(enable: Boolean, reload: Boolean) = Unit
}

private class DummyEngineSessionState : EngineSessionState {
    override fun toJSON(): JSONObject = JSONObject()
}