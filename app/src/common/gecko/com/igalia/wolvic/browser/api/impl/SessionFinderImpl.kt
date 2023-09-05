package com.igalia.wolvic.browser.api.impl

import com.igalia.wolvic.browser.api.WResult
import com.igalia.wolvic.browser.api.WSession
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.SessionFinder

class SessionFinderImpl(private val mFinder: SessionFinder) : WSession.SessionFinder {
    override fun find(searchString: String?, flags: Int): WResult<WSession.SessionFinder.FinderResult> {
        return ResultImpl(mFinder.find(searchString, flags)).then { result: GeckoSession.FinderResult? ->
            val f = WSession.SessionFinder.FinderResult()
            result?.let {
                f.found = it.found
                f.wrapped = it.wrapped
                f.current = it.current
                f.total = it.total
                f.searchString = it.searchString
                f.flags = it.flags
                f.linkUri = it.linkUri
                f.clientRect = it.clientRect
            }
            ResultImpl(GeckoResult.fromValue(f))
        }
    }

    override fun clear() {
        mFinder.clear()
    }

    override fun getDisplayFlags(): Int {
        return mFinder.displayFlags
    }

    override fun setDisplayFlags(flags: Int) {
        mFinder.displayFlags = flags
    }
}