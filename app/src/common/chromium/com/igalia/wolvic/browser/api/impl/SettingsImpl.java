package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.Nullable;

import org.chromium.wolvic.SessionSettings;

import com.igalia.wolvic.browser.api.WSessionSettings;

public class SettingsImpl implements WSessionSettings {
    SessionSettings mSessionSettings = new SessionSettings();

    // TODO: move these fields to the Chromium backend once they are supported.
    boolean mPrivateMode;
    boolean mUseTrackingProtection = true;
    boolean mSuspendMediaWhenInactive = false;
    boolean mAllowJavaScript = true;
    boolean mFullAccesibilityTree = false;
    int mDisplayMode = WSessionSettings.DISPLAY_MODE_BROWSER;
    int mViewportMode = WSessionSettings.VIEWPORT_MODE_MOBILE;

    public SettingsImpl(boolean aPrivateMode) {
        mPrivateMode = aPrivateMode;
    }

    @Override
    public void setUseTrackingProtection(boolean value) {
        mUseTrackingProtection = value;
    }

    @Override
    public void setSuspendMediaWhenInactive(boolean value) {
        mSuspendMediaWhenInactive = value;
    }

    @Override
    public void setAllowJavascript(boolean value) {
        mAllowJavaScript = value;
    }

    @Override
    public void setFullAccessibilityTree(boolean value) {
        mFullAccesibilityTree = value;
    }

    @Override
    public boolean getUseTrackingProtection() {
        return mUseTrackingProtection;
    }

    @Override
    public boolean getUsePrivateMode() {
        // TODO: implement
        return false;
    }

    @Nullable
    @Override
    public String getContextId() {
        return null;
    }

    @Override
    public boolean getSuspendMediaWhenInactive() {
        return mSuspendMediaWhenInactive;
    }

    @Override
    public boolean getAllowJavascript() {
        return mAllowJavaScript;
    }

    @Override
    public boolean getFullAccessibilityTree() {
        return mFullAccesibilityTree;
    }

    @Override
    public void setUserAgentMode(int value) {
        switch (value) {
            case WSessionSettings.USER_AGENT_MODE_MOBILE:
                mSessionSettings.setUserAgentMode(SessionSettings.UserAgentMode.MOBILE);
                break;
            case WSessionSettings.USER_AGENT_MODE_DESKTOP:
                mSessionSettings.setUserAgentMode(SessionSettings.UserAgentMode.DESKTOP);
                break;
            case WSessionSettings.USER_AGENT_MODE_VR:
                mSessionSettings.setUserAgentMode(SessionSettings.UserAgentMode.MOBILE_VR);
                break;
            default:
                throw new IllegalArgumentException("Invalid user agent mode: " + value);
        }
    }

    @Override
    public void setDisplayMode(int value) {
        mDisplayMode = value;
    }

    @Override
    public void setViewportMode(int value) {
        // TODO: implement
    }

    @Override
    public int getUserAgentMode() {
        SessionSettings.UserAgentMode mode = mSessionSettings.getUserAgentMode();
        switch (mode) {
            case MOBILE:
                return WSessionSettings.USER_AGENT_MODE_MOBILE;
            case DESKTOP:
                return WSessionSettings.USER_AGENT_MODE_DESKTOP;
            case MOBILE_VR:
                return WSessionSettings.USER_AGENT_MODE_VR;
            default:
                throw new IllegalStateException("Invalid user agent mode: " + mode);
        }
    }

    @Override
    public int getDisplayMode() {
        return mDisplayMode;
    }

    @Override
    public int getViewportMode() {
        return mViewportMode;
    }

    @Override
    public void setUserAgentOverride(@Nullable String value) {
        mSessionSettings.setUserAgentOverride(value);
    }

    @Nullable
    @Override
    public String getUserAgentOverride() {
        return mSessionSettings.getUserAgentOverride();
    }
}