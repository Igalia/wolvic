package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WContentBlocking;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoSession;

class ContentBlockingDelegateImpl implements ContentBlocking.Delegate {
    WContentBlocking.Delegate mDelegate;
    SessionImpl mSession;

    public ContentBlockingDelegateImpl(WContentBlocking.Delegate delegate, SessionImpl session) {
        mDelegate = delegate;
        mSession = session;
    }


    @Override
    public void onContentBlocked(@NonNull GeckoSession session, @NonNull ContentBlocking.BlockEvent event) {
        mDelegate.onContentBlocked(mSession, fromGeckoBlockEvent(event));
    }

    @Override
    public void onContentLoaded(@NonNull GeckoSession session, @NonNull ContentBlocking.BlockEvent event) {
        mDelegate.onContentLoaded(mSession, fromGeckoBlockEvent(event));
    }

    static WContentBlocking.BlockEvent fromGeckoBlockEvent(@NonNull ContentBlocking.BlockEvent event) {
        return new WContentBlocking.BlockEvent(
          event.uri, fromGeckoAntiTracking(event.getAntiTrackingCategory()), fromGeckoSafeBrowsing(event.getSafeBrowsingCategory()),
                fromGeckoCookieBehavior(event.getCookieBehaviorCategory()), event.isBlocking()
        );
    }

    static int fromGeckoAntiTracking(int flags) {
        int res = 0;
        if ((flags & ContentBlocking.AntiTracking.AD) != 0) {
            res |= WContentBlocking.AntiTracking.AD;
        }
        if ((flags & ContentBlocking.AntiTracking.ANALYTIC) != 0) {
            res |= WContentBlocking.AntiTracking.ANALYTIC;
        }
        if ((flags & ContentBlocking.AntiTracking.CONTENT) != 0) {
            res |= WContentBlocking.AntiTracking.CONTENT;
        }
        if ((flags & ContentBlocking.AntiTracking.CRYPTOMINING) != 0) {
            res |= WContentBlocking.AntiTracking.CRYPTOMINING;
        }
        if ((flags & ContentBlocking.AntiTracking.DEFAULT) != 0) {
            res |= WContentBlocking.AntiTracking.DEFAULT;
        }
        if ((flags & ContentBlocking.AntiTracking.FINGERPRINTING) != 0) {
            res |= WContentBlocking.AntiTracking.FINGERPRINTING;
        }
        if ((flags & ContentBlocking.AntiTracking.NONE) != 0) {
            res |= WContentBlocking.AntiTracking.NONE;
        }
        if ((flags & ContentBlocking.AntiTracking.SOCIAL) != 0) {
            res |= WContentBlocking.AntiTracking.SOCIAL;
        }
        if ((flags & ContentBlocking.AntiTracking.STP) != 0) {
            res |= WContentBlocking.AntiTracking.STP;
        }
        if ((flags & ContentBlocking.AntiTracking.STRICT) != 0) {
            res |= WContentBlocking.AntiTracking.STRICT;
        }
        if ((flags & ContentBlocking.AntiTracking.TEST) != 0) {
            res |= WContentBlocking.AntiTracking.TEST;
        }
        return res;
    }

    static int toGeckoAntitracking(@WContentBlocking.CBAntiTracking int flags) {
        int res = 0;
        if ((flags & WContentBlocking.AntiTracking.AD) != 0) {
            res |= ContentBlocking.AntiTracking.AD;
        }
        if ((flags & WContentBlocking.AntiTracking.ANALYTIC) != 0) {
            res |= ContentBlocking.AntiTracking.ANALYTIC;
        }
        if ((flags & WContentBlocking.AntiTracking.CONTENT) != 0) {
            res |= ContentBlocking.AntiTracking.CONTENT;
        }
        if ((flags & WContentBlocking.AntiTracking.CRYPTOMINING) != 0) {
            res |= ContentBlocking.AntiTracking.CRYPTOMINING;
        }
        if ((flags & WContentBlocking.AntiTracking.DEFAULT) != 0) {
            res |= ContentBlocking.AntiTracking.DEFAULT;
        }
        if ((flags & WContentBlocking.AntiTracking.FINGERPRINTING) != 0) {
            res |= ContentBlocking.AntiTracking.FINGERPRINTING;
        }
        if ((flags & WContentBlocking.AntiTracking.NONE) != 0) {
            res |= ContentBlocking.AntiTracking.NONE;
        }
        if ((flags & WContentBlocking.AntiTracking.SOCIAL) != 0) {
            res |= ContentBlocking.AntiTracking.SOCIAL;
        }
        if ((flags & WContentBlocking.AntiTracking.STP) != 0) {
            res |= ContentBlocking.AntiTracking.STP;
        }
        if ((flags & WContentBlocking.AntiTracking.STRICT) != 0) {
            res |= ContentBlocking.AntiTracking.STRICT;
        }
        if ((flags & WContentBlocking.AntiTracking.TEST) != 0) {
            res |= ContentBlocking.AntiTracking.TEST;
        }

        return res;
    }

    static int fromGeckoSafeBrowsing(int flags) {
        int res = 0;
        if ((flags & ContentBlocking.SafeBrowsing.DEFAULT) != 0) {
            res |= WContentBlocking.SafeBrowsing.DEFAULT;
        }
        if ((flags & ContentBlocking.SafeBrowsing.HARMFUL) != 0) {
            res |= WContentBlocking.SafeBrowsing.HARMFUL;
        }
        if ((flags & ContentBlocking.SafeBrowsing.MALWARE) != 0) {
            res |= WContentBlocking.SafeBrowsing.MALWARE;
        }
        if ((flags & ContentBlocking.SafeBrowsing.NONE) != 0) {
            res |= WContentBlocking.SafeBrowsing.NONE;
        }
        if ((flags & ContentBlocking.SafeBrowsing.PHISHING) != 0) {
            res |= WContentBlocking.SafeBrowsing.PHISHING;
        }
        if ((flags & ContentBlocking.SafeBrowsing.UNWANTED) != 0) {
            res |= WContentBlocking.SafeBrowsing.UNWANTED;
        }

        return res;
    }

    static int toGeckoSafeBrowsing(@WContentBlocking.CBSafeBrowsing int flags) {
        int res = 0;
        if ((flags & WContentBlocking.SafeBrowsing.DEFAULT) != 0) {
            res |= ContentBlocking.SafeBrowsing.DEFAULT;
        }
        if ((flags & WContentBlocking.SafeBrowsing.HARMFUL) != 0) {
            res |= ContentBlocking.SafeBrowsing.HARMFUL;
        }
        if ((flags & WContentBlocking.SafeBrowsing.MALWARE) != 0) {
            res |= ContentBlocking.SafeBrowsing.MALWARE;
        }
        if ((flags & WContentBlocking.SafeBrowsing.NONE) != 0) {
            res |= ContentBlocking.SafeBrowsing.NONE;
        }
        if ((flags & WContentBlocking.SafeBrowsing.PHISHING) != 0) {
            res |= ContentBlocking.SafeBrowsing.PHISHING;
        }
        if ((flags & WContentBlocking.SafeBrowsing.UNWANTED) != 0) {
            res |= ContentBlocking.SafeBrowsing.UNWANTED;
        }

        return res;
    }

    static int fromGeckoCookieBehavior(int flags) {
        switch (flags) {
            case ContentBlocking.CookieBehavior.ACCEPT_ALL:
                return WContentBlocking.CookieBehavior.ACCEPT_ALL;
            case ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY:
                return WContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY;
            case ContentBlocking.CookieBehavior.ACCEPT_NONE:
                return WContentBlocking.CookieBehavior.ACCEPT_NONE;
            case ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS:
                return WContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS;
            case ContentBlocking.CookieBehavior.ACCEPT_VISITED:
                return WContentBlocking.CookieBehavior.ACCEPT_VISITED;
        }

        throw new RuntimeException("Unreachable code");
    }

    static int toGeckoCookieBehavior(@WContentBlocking.CBCookieBehavior int flags) {
        switch (flags) {
            case WContentBlocking.CookieBehavior.ACCEPT_ALL:
                return ContentBlocking.CookieBehavior.ACCEPT_ALL;
            case WContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY:
                return ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY;
            case WContentBlocking.CookieBehavior.ACCEPT_NONE:
                return ContentBlocking.CookieBehavior.ACCEPT_NONE;
            case WContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS:
                return ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS;
            case WContentBlocking.CookieBehavior.ACCEPT_VISITED:
                return ContentBlocking.CookieBehavior.ACCEPT_VISITED;
        }

        throw new RuntimeException("Unreachable code");
    }

    static int toGeckoEtpLevel(@WContentBlocking.CBEtpLevel int flags) {
        switch (flags) {
            case WContentBlocking.EtpLevel.DEFAULT:
                return ContentBlocking.EtpLevel.DEFAULT;
            case WContentBlocking.EtpLevel.NONE:
                return ContentBlocking.EtpLevel.NONE;
            case WContentBlocking.EtpLevel.STRICT:
                return ContentBlocking.EtpLevel.STRICT;
        }

        throw new RuntimeException("Unreachable code");
    }

    static int fromGeckoEtpLevel(int flags) {
        switch (flags) {
            case ContentBlocking.EtpLevel.DEFAULT:
                return WContentBlocking.EtpLevel.DEFAULT;
            case ContentBlocking.EtpLevel.NONE:
                return WContentBlocking.EtpLevel.NONE;
            case ContentBlocking.EtpLevel.STRICT:
                return WContentBlocking.EtpLevel.STRICT;
        }

        throw new RuntimeException("Unreachable code");
    }

    static int toGeckoCookieLifetime(@WContentBlocking.CBCookieLifetime int flags) {
        switch (flags) {
            case WContentBlocking.CookieLifetime.DAYS:
                return ContentBlocking.CookieLifetime.DAYS;
            case WContentBlocking.CookieLifetime.NORMAL:
                return ContentBlocking.CookieLifetime.NORMAL;
            case WContentBlocking.CookieLifetime.RUNTIME:
                return ContentBlocking.CookieLifetime.RUNTIME;
        }

        throw new RuntimeException("Unreachable code");
    }

    static int fromGeckoCookieLifetime( int flags) {
        switch (flags) {
            case ContentBlocking.CookieLifetime.DAYS:
                return WContentBlocking.CookieLifetime.DAYS;
            case ContentBlocking.CookieLifetime.NORMAL:
                return WContentBlocking.CookieLifetime.NORMAL;
            case ContentBlocking.CookieLifetime.RUNTIME:
                return WContentBlocking.CookieLifetime.RUNTIME;
        }

        throw new RuntimeException("Unreachable code");
    }
}
