package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.browser.api.WSessionSettings;

import org.chromium.content_public.browser.WebContents;
import org.chromium.wolvic.SessionSettings;

public class SettingsImpl implements WSessionSettings {
    private SessionSettings mSessionSettings = new SessionSettings();

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
    public boolean getUsePrivateMode() { return mPrivateMode; }

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
    public void setUserAgentMode(int mode) {
        mSessionSettings.setUserAgentMode(toUserAgentMode(mode));
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

    @Override
    public void setWebContents(@Nullable WebContents webContents) {
        mSessionSettings.setWebContents(webContents);
    }

    public String getDefaultUserAgent(int mode) {
        return mSessionSettings.getDefaultUserAgent(toUserAgentMode(mode)) + " Wolvic/" + BuildConfig.VERSION_NAME;
    }

    private SessionSettings.UserAgentMode toUserAgentMode(int mode) {
        switch (mode) {
            case WSessionSettings.USER_AGENT_MODE_MOBILE:
                return SessionSettings.UserAgentMode.MOBILE;
            case WSessionSettings.USER_AGENT_MODE_VR:
                return SessionSettings.UserAgentMode.MOBILE_VR;
            case WSessionSettings.USER_AGENT_MODE_DESKTOP:
                return SessionSettings.UserAgentMode.DESKTOP;
            default:
                throw new IllegalStateException("Invalid user agent mode: " + mode);
        }
    }
}