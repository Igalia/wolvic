package com.igalia.wolvic.browser.persistence

import com.igalia.wolvic.browser.SessionChangeListener
import com.igalia.wolvic.browser.engine.Session

class SessionsChangeAutoSaveTrigger(private val autoSave: WindowsStateAutoSave) : SessionChangeListener {

    override fun onSessionAdded(aSession: Session) = requestSaveFor(aSession)

    override fun onSessionRemoved(aId: String) = autoSave.requestSave()

    override fun onSessionStateChanged(aSession: Session, aActive: Boolean) = requestSaveFor(aSession)

    override fun onSessionLoadFinished(aSession: Session) = requestSaveFor(aSession)

    override fun onEngineSessionStateUpdated(aSession: Session) = requestSaveFor(aSession)

    private fun requestSaveFor(session: Session) {
        if (!session.isPrivateMode) {
            autoSave.requestSave()
        }
    }
}
