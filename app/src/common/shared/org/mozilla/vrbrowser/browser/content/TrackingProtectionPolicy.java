package org.mozilla.vrbrowser.browser.content;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.ContentBlocking.AntiTracking;

public class TrackingProtectionPolicy {

    private static final int RECOMMENDED =
            AntiTracking.AD |
            AntiTracking.ANALYTIC |
            AntiTracking.SOCIAL |
            AntiTracking.TEST |
            AntiTracking.STP |
            AntiTracking.CRYPTOMINING;
    private static final int STRICT =
            RECOMMENDED | AntiTracking.FINGERPRINTING;

    private int trackingPolicy;
    private int cookiePolicy;

    private TrackingProtectionPolicy() {
        trackingPolicy = AntiTracking.NONE;
    }

    /**
     * Strict policy.
     * Combining the [TrackingCategory.STRICT] plus a cookiePolicy of [ACCEPT_NON_TRACKERS]
     * This is the strictest setting and may cause issues on some web sites.
     */
    static TrackingProtectionPolicy strict() {
        TrackingProtectionPolicy policy = new TrackingProtectionPolicy();
        policy.trackingPolicy = STRICT;
        policy.cookiePolicy = ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS;
        return policy;
    }

    /**
     * Recommended policy.
     * Combining the [TrackingCategory.RECOMMENDED] plus a [CookiePolicy] of [ACCEPT_NON_TRACKERS].
     * This is the recommended setting.
     */
    static TrackingProtectionPolicy recommended() {
        TrackingProtectionPolicy policy = new TrackingProtectionPolicy();
        policy.trackingPolicy = RECOMMENDED;
        policy.cookiePolicy = ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS;
        return policy;
    }

    static TrackingProtectionPolicy none() {
        TrackingProtectionPolicy policy = new TrackingProtectionPolicy();
        policy.trackingPolicy = AntiTracking.NONE;
        policy.cookiePolicy = ContentBlocking.CookieBehavior.ACCEPT_ALL;
        return policy;
    }

    public boolean shouldBlockContent() {
        return trackingPolicy == STRICT;
    }

    public int getCookiePolicy() {
        return cookiePolicy;
    }

    public int getAntiTrackingPolicy() {
        return trackingPolicy;
    }

}
