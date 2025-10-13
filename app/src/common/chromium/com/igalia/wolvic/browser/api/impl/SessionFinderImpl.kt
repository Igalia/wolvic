package com.igalia.wolvic.browser.api.impl

import com.igalia.wolvic.browser.api.WResult
import com.igalia.wolvic.browser.api.WSession
import org.chromium.components.find_in_page.FindInPageBridge
import org.chromium.components.find_in_page.FindNotificationDetails
import org.chromium.content_public.browser.WebContents

class SessionFinderImpl(private val webContents: WebContents) : WSession.SessionFinder, TabWebContentsDelegate.FindInPageDelegate {
    private val mFinder: FindInPageBridge = FindInPageBridge(webContents);
    private var findWResult: WResult<WSession.SessionFinder.FinderResult> = WResult.create()
    private var previousSearchString: String? = null

    override fun find(searchString: String?, flags: Int): WResult<WSession.SessionFinder.FinderResult> {
        // To navigate among the search results we get a null searchString. However Chromium expects
        // the same searchString as the previous one, so we store it and use it if the new one is null.
        val newSearchString = searchString?.let {
            previousSearchString = it
            it
        } ?: previousSearchString
        findWResult = WResult.create();
        mFinder.activateFindInPageResultForAccessibility();
        var forwardDirection = (flags and WSession.SessionFinder.FINDER_FIND_BACKWARDS) == 0;
        val caseSensitive = (flags and WSession.SessionFinder.FINDER_FIND_MATCH_CASE) != 0;
        mFinder.startFinding(newSearchString, forwardDirection, caseSensitive);
        return findWResult;
    }

    override fun clear() {
        previousSearchString?.let { mFinder.stopFinding(true) }
        previousSearchString = null
    }

    override fun getDisplayFlags(): Int {
        return 0;
    }

    override fun setDisplayFlags(flags: Int) {

    }

    override fun onFindResultAvailable(details: FindNotificationDetails) {
        val finderResult = WSession.SessionFinder.FinderResult().apply {
            found = details.numberOfMatches > 0
            current = details.activeMatchOrdinal
            total = details.numberOfMatches
        }
        findWResult.complete(finderResult)
    }
}