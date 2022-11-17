/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.components

import android.util.JsonWriter
import com.igalia.wolvic.browser.engine.Session
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.concept.engine.Settings
import org.json.JSONObject

class WolvicEngineSession(
        val session: Session
): EngineSession() {

    override fun loadUrl(url: String, parent: EngineSession?, flags: LoadUrlFlags, additionalHeaders: Map<String, String>?) {
        session.loadUri(url, flags.value)
    }


    override val settings: Settings = object : Settings() {}
    override fun clearFindMatches() = Unit
    override fun exitFullScreenMode() = Unit
    override fun findAll(text: String) = Unit
    override fun findNext(forward: Boolean) = Unit
    override fun goBack() = Unit
    override fun goForward() = Unit
    override fun goToHistoryIndex(index: Int) = Unit
    override fun loadData(data: String, mimeType: String, encoding: String) = Unit
    override fun reload(flags: LoadUrlFlags) = Unit
    override fun restoreState(state: EngineSessionState) = true
    override fun stopLoading() = Unit
    override fun toggleDesktopMode(enable: Boolean, reload: Boolean) = Unit
    override fun updateTrackingProtection(policy: TrackingProtectionPolicy) = Unit
    override fun purgeHistory() = Unit;
}

private class DummyEngineSessionState : EngineSessionState {
    override fun writeTo(writer: JsonWriter) {}
}