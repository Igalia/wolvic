package com.igalia.wolvic.findinpage

import com.igalia.wolvic.browser.api.WResult
import com.igalia.wolvic.browser.api.WSession
import mozilla.components.browser.state.state.content.FindResultState
import mozilla.components.feature.findinpage.view.FindInPageView
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.android.view.hideKeyboard

/**
 * Interactor that implements [FindInPageView.Listener] and notifies about actions the user
 * performed (e.g. "find next result").
 */
class FindInPageInteractor(
    private val view: FindInPageView,
    private val onClose: (() -> Unit)? = null,
) : FindInPageView.Listener, LifecycleAwareFeature, UserInteractionHandler {
    private var sessionFinder: WSession.SessionFinder? = null

    private fun findSession(searchString: String?, flags: Int) {
        sessionFinder?.find(searchString, flags)?.then { result: WSession.SessionFinder.FinderResult? ->
            result?.let {
                if (searchString != null || it.total > 0) {
                    val activeMatchOrdinal = if (it.current > 0) it.current - 1 else it.current
                    view.displayResult(FindResultState(activeMatchOrdinal, it.total, true))
                }
            }
            WResult.fromValue(null)
        }
    }

    override fun start() {
        view.listener = this
    }

    override fun stop() {
        view.listener = null
    }

    fun bind(finder: WSession.SessionFinder) {
        sessionFinder = finder
    }

    override fun onPreviousResult() {
        findSession(null, WSession.SessionFinder.FINDER_FIND_BACKWARDS)

        view.asView().hideKeyboard()
    }

    override fun onNextResult() {
        findSession(null, 0)
        view.asView().hideKeyboard()
    }

    override fun onClose() {
        onClose?.invoke()
    }

    override fun onBackPressed(): Boolean {
        onClose()
        return true
    }

    fun unbind() {
        sessionFinder?.clear()
        sessionFinder = null
        onClose()
    }

    override fun onFindAll(query: String) {
        findSession(query, 0)
    }

    override fun onClearMatches() {
        sessionFinder?.clear()
    }
}
