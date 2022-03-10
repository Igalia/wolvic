package com.igalia.wolvic.browser.api;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Content Blocking API to hold and control anti-tracking, cookie and Safe Browsing settings. */
@AnyThread
public class WContentBlocking {

    @AnyThread
    public static class Settings {
        @CBAntiTracking int mAntiTracking = AntiTracking.DEFAULT;
        @CBSafeBrowsing int mSafeBrowsing = SafeBrowsing.DEFAULT;
        @CBCookieBehavior int mCookieBehavior = CookieBehavior.ACCEPT_NON_TRACKERS;
        @CBCookieBehavior int mCookieBehaviorPrivate = CookieBehavior.ACCEPT_NON_TRACKERS;
        @CBCookieLifetime int mCookieLifetime = CookieLifetime.NORMAL;
        @CBEtpLevel int mEtpLevel = EtpLevel.DEFAULT;
        boolean mStrictSocialTrackingProtection = false;
        boolean mCookiePurging = false;

        public int getAntiTracking() {
            return mAntiTracking;
        }

        public void setAntiTracking(int antiTracking) {
            mAntiTracking = antiTracking;
        }

        public int getSafeBrowsing() {
            return mSafeBrowsing;
        }

        public void setSafeBrowsing(int safeBrowsing) {
            mSafeBrowsing = safeBrowsing;
        }

        public int getCookieBehavior() {
            return mCookieBehavior;
        }

        public void setCookieBehavior(int cookieBehavior) {
            mCookieBehavior = cookieBehavior;
        }

        public int getCookieBehaviorPrivate() {
            return mCookieBehaviorPrivate;
        }

        public void setCookieBehaviorPrivate(int cookieBehaviorPrivate) {
            mCookieBehaviorPrivate = cookieBehaviorPrivate;
        }

        public int getCookieLifetime() {
            return mCookieLifetime;
        }

        public void setCookieLifetime(int cookieLifetime) {
            mCookieLifetime = cookieLifetime;
        }

        public int getEnhancedTrackingProtectionLevel() {
            return mEtpLevel;
        }

        public void setEnhancedTrackingProtectionLevel(int etpLevel) {
            mEtpLevel = etpLevel;
        }

        public boolean isStrictSocialTrackingProtection() {
            return mStrictSocialTrackingProtection;
        }

        public void setStrictSocialTrackingProtection(boolean strictSocialTrackingProtection) {
            mStrictSocialTrackingProtection = strictSocialTrackingProtection;
        }

        public boolean isCookiePurging() {
            return mCookiePurging;
        }

        public void setCookiePurging(boolean cookiePurging) {
            mCookiePurging = cookiePurging;
        }

        @AnyThread
        public static class Builder {
            Settings mSettings = new Settings();

            public Settings build() {
                return mSettings;
            }

            /**
             * Set anti-tracking categories.
             *
             * @param cat The categories of resources that should be blocked. Use one or more of the
             *            {@link WContentBlocking.AntiTracking} flags.
             * @return This Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder antiTracking(final @CBAntiTracking int cat) {
                mSettings.mAntiTracking = cat;
                return this;
            }

            /**
             * Set safe browsing categories.
             *
             * @param cat The categories of resources that should be blocked. Use one or more of the
             *            {@link WContentBlocking.SafeBrowsing} flags.
             * @return This Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder safeBrowsing(final @CBSafeBrowsing int cat) {
                mSettings.mSafeBrowsing = cat;
                return this;
            }

            /**
             * Set cookie storage behavior.
             *
             * @param behavior The storage behavior that should be applied. Use one of the {@link
             *                 WContentBlocking.CookieBehavior} flags.
             * @return The Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder cookieBehavior(final @CBCookieBehavior int behavior) {
                mSettings.mCookieBehavior = behavior;
                return this;
            }

            /**
             * Set cookie storage behavior in private browsing mode.
             *
             * @param behavior The storage behavior that should be applied. Use one of the {@link
             *                 WContentBlocking.CookieBehavior} flags.
             * @return The Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder cookieBehaviorPrivateMode(final @CBCookieBehavior int behavior) {
                mSettings.mCookieBehaviorPrivate = behavior;
                return this;
            }

            /**
             * Set the cookie lifetime.
             *
             * @param lifetime The enforced cookie lifetime. Use one of the {@link WContentBlocking.CookieLifetime} flags.
             * @return The Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder cookieLifetime(final @CBCookieLifetime int lifetime) {
                mSettings.mCookieLifetime = lifetime;
                return this;
            }

            /**
             * Set the ETP behavior level.
             *
             * @param level The level of ETP blocking to use. Only takes effect if cookie behavior is set
             *              to {@link WContentBlocking.CookieBehavior#ACCEPT_NON_TRACKERS} or {@link
             *              WContentBlocking.CookieBehavior#ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS}.
             * @return The Builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder enhancedTrackingProtectionLevel(final @CBEtpLevel int level) {
                mSettings.mEtpLevel = level;
                return this;
            }

            /**
             * Set whether or not strict social tracking protection is enabled. This will block resources
             * from loading if they are on the social tracking protection list, rather than just blocking
             * cookies as with normal social tracking protection.
             *
             * @param enabled A boolean indicating whether or not strict social tracking protection should
             *                be enabled.
             * @return The builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder strictSocialTrackingProtection(final boolean enabled) {
                mSettings.mStrictSocialTrackingProtection = enabled;
                return this;
            }

            /**
             * Set whether or not to automatically purge tracking cookies. This will purge cookies from
             * tracking sites that do not have recent user interaction provided that the cookie behavior
             * is set to either {@link WContentBlocking.CookieBehavior#ACCEPT_NON_TRACKERS} or {@link
             * WContentBlocking.CookieBehavior#ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS}.
             *
             * @param enabled A boolean indicating whether or not cookie purging should be enabled.
             * @return The builder instance.
             */
            public @NonNull
            WContentBlocking.Settings.Builder cookiePurging(final boolean enabled) {
                mSettings.mCookiePurging = enabled;
                return this;
            }
        }
    }

    public static class AntiTracking {
        public static final int NONE = 0;

        /** Block advertisement trackers. */
        public static final int AD = 1 << 1;

        /** Block analytics trackers. */
        public static final int ANALYTIC = 1 << 2;

        /**
         * Block social trackers. Note: This is not the same as "Social Tracking Protection", which is
         * controlled by {@link #STP}.
         */
        public static final int SOCIAL = 1 << 3;

        /** Block content trackers. May cause issues with some web sites. */
        public static final int CONTENT = 1 << 4;

        /** Block test trackers (used for tests). */
        public static final int TEST = 1 << 5;

        /** Block cryptocurrency miners. */
        public static final int CRYPTOMINING = 1 << 6;

        /** Block fingerprinting trackers. */
        public static final int FINGERPRINTING = 1 << 7;

        /** Block trackers on the Social Tracking Protection list. */
        public static final int STP = 1 << 8;

        /** Block ad, analytic, social and test trackers. */
        public static final int DEFAULT = AD | ANALYTIC | SOCIAL | TEST;

        /** Block all known trackers. May cause issues with some web sites. */
        public static final int STRICT = DEFAULT | CONTENT | CRYPTOMINING | FINGERPRINTING;

        protected AntiTracking() {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {
                    WContentBlocking.AntiTracking.AD,
                    WContentBlocking.AntiTracking.ANALYTIC,
                    WContentBlocking.AntiTracking.SOCIAL,
                    WContentBlocking.AntiTracking.CONTENT,
                    WContentBlocking.AntiTracking.TEST,
                    WContentBlocking.AntiTracking.CRYPTOMINING,
                    WContentBlocking.AntiTracking.FINGERPRINTING,
                    WContentBlocking.AntiTracking.DEFAULT,
                    WContentBlocking.AntiTracking.STRICT,
                    WContentBlocking.AntiTracking.STP,
                    WContentBlocking.AntiTracking.NONE
            })
            public @interface CBAntiTracking {}

    public static class SafeBrowsing {
        public static final int NONE = 0;

        /** Block malware sites. */
        public static final int MALWARE = 1 << 10;

        /** Block unwanted sites. */
        public static final int UNWANTED = 1 << 11;

        /** Block harmful sites. */
        public static final int HARMFUL = 1 << 12;

        /** Block phishing sites. */
        public static final int PHISHING = 1 << 13;

        /** Block all unsafe sites. */
        public static final int DEFAULT = MALWARE | UNWANTED | HARMFUL | PHISHING;

        protected SafeBrowsing() {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {
                    WContentBlocking.SafeBrowsing.MALWARE, WContentBlocking.SafeBrowsing.UNWANTED,
                    WContentBlocking.SafeBrowsing.HARMFUL, WContentBlocking.SafeBrowsing.PHISHING,
                    WContentBlocking.SafeBrowsing.DEFAULT, WContentBlocking.SafeBrowsing.NONE
            })
            public @interface CBSafeBrowsing {}

    // Sync values with nsICookieService.idl.
    public static class CookieBehavior {
        /** Accept first-party and third-party cookies and site data. */
        public static final int ACCEPT_ALL = 0;

        /**
         * Accept only first-party cookies and site data to block cookies which are not associated with
         * the domain of the visited site.
         */
        public static final int ACCEPT_FIRST_PARTY = 1;

        /** Do not store any cookies and site data. */
        public static final int ACCEPT_NONE = 2;

        /**
         * Accept first-party and third-party cookies and site data only from sites previously visited
         * in a first-party context.
         */
        public static final int ACCEPT_VISITED = 3;

        /**
         * Accept only first-party and non-tracking third-party cookies and site data to block cookies
         * which are not associated with the domain of the visited site set by known trackers.
         */
        public static final int ACCEPT_NON_TRACKERS = 4;

        /**
         * Enable dynamic first party isolation (dFPI); this will block third-party tracking cookies in
         * accordance with the ETP level and isolate non-tracking third-party cookies.
         */
        public static final int ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS = 5;

        protected CookieBehavior() {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            WContentBlocking.CookieBehavior.ACCEPT_ALL, WContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
            WContentBlocking.CookieBehavior.ACCEPT_NONE, WContentBlocking.CookieBehavior.ACCEPT_VISITED,
            WContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
    })
            public @interface CBCookieBehavior {}

    // Sync values with nsICookieService.idl.
    public static class CookieLifetime {
        /** Accept default cookie lifetime. */
        public static final int NORMAL = 0;

        /** Downgrade cookie lifetime to this runtime's lifetime. */
        public static final int RUNTIME = 2;

        /** Limit cookie lifetime to N days. Defaults to 90 days. */
        public static final int DAYS = 3;

        protected CookieLifetime() {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WContentBlocking.CookieLifetime.NORMAL, WContentBlocking.CookieLifetime.RUNTIME, WContentBlocking.CookieLifetime.DAYS})
            public @interface CBCookieLifetime {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WContentBlocking.EtpLevel.NONE, WContentBlocking.EtpLevel.DEFAULT, WContentBlocking.EtpLevel.STRICT})
            public @interface CBEtpLevel {}

    /** Possible settings for ETP. */
    public static class EtpLevel {
        /** Do not enable ETP at all. */
        public static final int NONE = 0;

        /** Enable ETP for ads, analytic, and social tracking lists. */
        public static final int DEFAULT = 1;

        /**
         * Enable ETP for all of the default lists as well as the content list. May break many sites!
         */
        public static final int STRICT = 2;
    }

    /** Holds content block event details. */
    public static class BlockEvent {
        /** The URI of the blocked resource. */
        public final @NonNull String uri;

        private final @CBAntiTracking int mAntiTrackingCat;
        private final @CBSafeBrowsing int mSafeBrowsingCat;
        private final @CBCookieBehavior int mCookieBehaviorCat;
        private final boolean mIsBlocking;

        @SuppressWarnings("checkstyle:javadocmethod")
        public BlockEvent(
                @NonNull final String uri,
                final @CBAntiTracking int atCat,
                final @CBSafeBrowsing int sbCat,
                final @CBCookieBehavior int cbCat,
                final boolean isBlocking) {
            this.uri = uri;
            this.mAntiTrackingCat = atCat;
            this.mSafeBrowsingCat = sbCat;
            this.mCookieBehaviorCat = cbCat;
            this.mIsBlocking = isBlocking;
        }

        /**
         * The anti-tracking category types of the blocked resource.
         *
         * @return One or more of the {@link WContentBlocking.AntiTracking} flags.
         */
        @UiThread
        public @CBAntiTracking int getAntiTrackingCategory() {
            return mAntiTrackingCat;
        }

        /**
         * The safe browsing category types of the blocked resource.
         *
         * @return One or more of the {@link WContentBlocking.SafeBrowsing} flags.
         */
        @UiThread
        public @CBSafeBrowsing int getSafeBrowsingCategory() {
            return mSafeBrowsingCat;
        }

        /**
         * The cookie types of the blocked resource.
         *
         * @return One or more of the {@link WContentBlocking.CookieBehavior} flags.
         */
        @UiThread
        public @CBCookieBehavior int getCookieBehaviorCategory() {
            return mCookieBehaviorCat;
        }


        @UiThread
        @SuppressWarnings("checkstyle:javadocmethod")
        public boolean isBlocking() {
            return mIsBlocking;
        }
    }

    /** WSession applications implement this interface to handle content blocking events. */
    public interface Delegate {
        /**
         * A content element has been blocked from loading. Set blocked element categories via {@link
         * WRuntimeSettings} and enable content blocking via {@link WSessionSettings}.
         *
         * @param session The WSession that initiated the callback.
         * @param event The {@link WContentBlocking.BlockEvent} details.
         */
        @UiThread
        default void onContentBlocked(
                @NonNull final WSession session, @NonNull final WContentBlocking.BlockEvent event) {}

        /**
         * A content element that could be blocked has been loaded.
         *
         * @param session The WSession that initiated the callback.
         * @param event The {@link WContentBlocking.BlockEvent} details.
         */
        @UiThread
        default void onContentLoaded(
                @NonNull final WSession session, @NonNull final WContentBlocking.BlockEvent event) {}
    }
}
