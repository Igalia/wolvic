package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;

import org.jetbrains.annotations.NotNull;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.browser.SettingsStore;

class SessionSettings {

    private boolean isMultiprocessEnabled;
    private boolean isTrackingProtectionEnabled;
    private boolean isSuspendMediaWhenInactiveEnabled;
    private int userAgentMode;
    private boolean isServoEnabled;

    private SessionSettings(@NotNull Builder builder) {
        this.isMultiprocessEnabled = builder.isMultiprocessEnabled;
        this.isTrackingProtectionEnabled = builder.isTrackingProtectionEnabled;
        this.isSuspendMediaWhenInactiveEnabled = builder.isSuspendMediaWhenInactiveEnabled;
        this.userAgentMode = builder.userAgentMode;
        this.isServoEnabled = builder.isServoEnabled;
    }

    public boolean isMultiprocessEnabled() {
        return isMultiprocessEnabled;
    }

    public void setMultiprocessEnabled(boolean enabled) {
        isMultiprocessEnabled = enabled;
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

    public boolean isServoEnabled() {
        return isServoEnabled;
    }

    public void setServoEnabled(boolean enabled) {
        isServoEnabled = enabled;
    }

    public static class Builder {

        private boolean isMultiprocessEnabled;
        private boolean isTrackingProtectionEnabled;
        private boolean isSuspendMediaWhenInactiveEnabled;
        private int userAgentMode;
        private boolean isServoEnabled;

        public Builder() {
        }

        public Builder withMultiprocess(boolean isMultiprocessEnabled){
            this.isMultiprocessEnabled = isMultiprocessEnabled;
            return this;
        }

        public Builder withTrackingProteccion(boolean isTrackingProtectionEnabled){
            this.isTrackingProtectionEnabled = isTrackingProtectionEnabled;
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

        public Builder withServo(boolean isServoEnabled){
            this.isServoEnabled= isServoEnabled;
            return this;
        }

        public Builder withDefaultSettings(Context context) {
            return new SessionSettings.Builder()
                    .withMultiprocess(SettingsStore.getInstance(context).isMultiprocessEnabled())
                    .withTrackingProteccion(SettingsStore.getInstance(context).isTrackingProtectionEnabled())
                    .withSuspendMediaWhenInactive(true)
                    .withUserAgent(GeckoSessionSettings.USER_AGENT_MODE_VR)
                    .withServo(false);
        }

        public SessionSettings build(){
            SessionSettings settings = new SessionSettings(this);

            return settings;

        }
    }

}
