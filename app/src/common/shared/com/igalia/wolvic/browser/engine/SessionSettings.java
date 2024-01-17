package com.igalia.wolvic.browser.engine;

import android.content.Context;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.content.TrackingProtectionPolicy;
import com.igalia.wolvic.browser.content.TrackingProtectionStore;

class SessionSettings {

    private boolean isPrivateBrowsingEnabled;
    private boolean isTrackingProtectionEnabled;
    private boolean isSuspendMediaWhenInactiveEnabled;
    private int userAgentMode;
    private int viewportMode;
    private String userAgentOverride;

    /* package */ SessionSettings(@NonNull Builder builder) {
        this.isPrivateBrowsingEnabled = builder.isPrivateBrowsingEnabled;
        this.isTrackingProtectionEnabled = builder.isTrackingProtectionEnabled;
        this.isSuspendMediaWhenInactiveEnabled = builder.isSuspendMediaWhenInactiveEnabled;
        this.userAgentMode = builder.userAgentMode;
        this.viewportMode = builder.viewportMode;
        this.userAgentOverride = builder.userAgentOverride;
    }

    public boolean isPrivateBrowsingEnabled() { return isPrivateBrowsingEnabled; }
    public void setPrivateBrowsingEnabled(boolean enabled) {
        isPrivateBrowsingEnabled = enabled;
    }

    public boolean isTrackingProtectionEnabled() {
        return isTrackingProtectionEnabled;
    }

    public void setTrackingProtectionEnabled(boolean enabled) {
        isTrackingProtectionEnabled = enabled;
    }

    public boolean isSuspendMediaWhenInactiveEnabled() {
        return isSuspendMediaWhenInactiveEnabled;
    }

    public int getUserAgentMode() {
        return userAgentMode;
    }

    public void setUserAgentMode(int mode) {
        userAgentMode = mode;
    }

    public void setUserAgentOverride(String userAgentOverride) {
        this.userAgentOverride = userAgentOverride;
    }

    public String getUserAgentOverride() {
        return userAgentOverride;
    }

    public int getViewportMode() { return viewportMode; }

    public void setViewportMode(final int mode) { viewportMode = mode; }

    public static class Builder {

        private boolean isPrivateBrowsingEnabled;
        private boolean isTrackingProtectionEnabled;
        private boolean isSuspendMediaWhenInactiveEnabled;
        private int userAgentMode;
        private int viewportMode;
        private String userAgentOverride;

        public Builder() {
        }

        public Builder withPrivateBrowsing(boolean enabled) {
            isPrivateBrowsingEnabled = enabled;
            isTrackingProtectionEnabled = isPrivateBrowsingEnabled || isTrackingProtectionEnabled;

            return this;
        }

        public Builder withTrackingProtection(boolean isTrackingProtectionEnabled){
            this.isTrackingProtectionEnabled = isPrivateBrowsingEnabled || isTrackingProtectionEnabled;
            return this;
        }

        public Builder withSuspendMediaWhenInactive(boolean isSuspendMediaWhenInactiveEnabled){
            this.isSuspendMediaWhenInactiveEnabled = isSuspendMediaWhenInactiveEnabled;
            return this;
        }

        public Builder withUserAgent(int userAgent){
            this.userAgentMode = userAgent;
            return this;
        }

        public Builder withViewport(int viewport) {
            this.viewportMode = viewport;
            return this;
        }

        public Builder withUserAgentOverride(String userAgentOverride) {
            this.userAgentOverride = userAgentOverride;
            return this;
        }

        public Builder withDefaultSettings(Context context) {
            int ua = SettingsStore.getInstance(context).getUaMode();
            int viewport = WSessionSettings.VIEWPORT_MODE_MOBILE;

            TrackingProtectionPolicy policy = TrackingProtectionStore.getTrackingProtectionPolicy(context);
            return new SessionSettings.Builder()
                    .withPrivateBrowsing(false)
                    .withTrackingProtection(policy.shouldBlockContent())
                    .withSuspendMediaWhenInactive(true)
                    .withUserAgent(ua)
                    .withViewport(viewport);
        }

        public SessionSettings build(){
            return new SessionSettings(this);
        }
    }
}
