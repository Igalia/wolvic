package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSessionSettings;

public class SettingsImpl implements WSessionSettings {
    boolean mPrivateMode;
    boolean mUseTrackingProtection = true;
    boolean mSuspendMediaWhenInactive = false;
    boolean mAllowJavaScript = true;
    boolean mFullAccesibilityTree = false;
    String mUserAgentOverride;
    int mDisplayMode = WSessionSettings.DISPLAY_MODE_BROWSER;
    int mViewportMode = WSessionSettings.VIEWPORT_MODE_MOBILE;
    int mUserAgentMode = WSessionSettings.USER_AGENT_MODE_VR;

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
        // TODO: implement
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
        // TODO: implement
        return WSessionSettings.USER_AGENT_MODE_MOBILE;
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
        // TODO: override in browser engine
        mUserAgentOverride = value;
    }

    @Nullable
    @Override
    public String getUserAgentOverride() {
        return mUserAgentOverride;
    }
}