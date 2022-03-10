package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WSessionSettings;

import org.mozilla.geckoview.GeckoSessionSettings;

public class SettingsImpl implements WSessionSettings {
    private GeckoSessionSettings mSettings;

    public SettingsImpl(boolean usePrivateMode) {
        GeckoSessionSettings.Builder builder = new GeckoSessionSettings.Builder();
        builder.usePrivateMode(usePrivateMode);
        mSettings = builder.build();
    }

    public SettingsImpl(GeckoSessionSettings settings) {
        mSettings = settings;
    }

    /* package */ GeckoSessionSettings getGeckoSettings() {
        return mSettings;
    }

    @Override
    public void setUseTrackingProtection(boolean value) {
        mSettings.setUseTrackingProtection(value);
    }


    @Override
    public void setSuspendMediaWhenInactive(boolean value) {
        mSettings.setSuspendMediaWhenInactive(value);
    }

    @Override
    public void setAllowJavascript(boolean value) {
        mSettings.setAllowJavascript(value);
    }

    @Override
    public void setFullAccessibilityTree(boolean value) {
        mSettings.setFullAccessibilityTree(value);
    }

    @Override
    public boolean getUseTrackingProtection() {
        return mSettings.getUseTrackingProtection();
    }

    @Override
    public boolean getUsePrivateMode() {
        return mSettings.getUsePrivateMode();
    }

    @Nullable
    @Override
    public String getContextId() {
        return mSettings.getContextId();
    }

    @Override
    public boolean getSuspendMediaWhenInactive() {
        return mSettings.getSuspendMediaWhenInactive();
    }

    @Override
    public boolean getAllowJavascript() {
        return mSettings.getAllowJavascript();
    }

    @Override
    public boolean getFullAccessibilityTree() {
        return mSettings.getFullAccessibilityTree();
    }

    @Override
    public void setUserAgentMode(int value) {
        mSettings.setUserAgentMode(value);
    }

    @Override
    public void setDisplayMode(int value) {
        mSettings.setDisplayMode(value);
    }

    @Override
    public void setViewportMode(int value) {
        mSettings.setViewportMode(value);
    }

    @Override
    public int getUserAgentMode() {
        return mSettings.getUserAgentMode();
    }

    @Override
    public int getDisplayMode() {
        return mSettings.getDisplayMode();
    }

    @Override
    public int getViewportMode() {
        return mSettings.getViewportMode();
    }

    @Override
    public void setUserAgentOverride(@Nullable String value) {
        mSettings.setUserAgentOverride(value);
    }

    @Nullable
    @Override
    public String getUserAgentOverride() {
        return mSettings.getUserAgentOverride();
    }
}
