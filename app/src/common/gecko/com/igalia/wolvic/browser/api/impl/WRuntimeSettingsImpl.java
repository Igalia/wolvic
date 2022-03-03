package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WRuntimeSettings;

import org.mozilla.geckoview.GeckoRuntime;

class WRuntimeSettingsImpl extends WRuntimeSettings {
    GeckoRuntime mRuntime;

    public WRuntimeSettingsImpl(GeckoRuntime runtime, WRuntimeSettings settings) {
        mRuntime = runtime;
        mContentBlocking = settings.getContentBlocking();
    }


    @Override
    public String[] getArgs() {
        return mRuntime.getSettings().getArguments();
    }

    @Override
    public Bundle getExtras() {
        return mRuntime.getSettings().getExtras();
    }

    @Override
    public String getConfigFilePath() {
        return mRuntime.getSettings().getConfigFilePath();
    }

    @Override
    public boolean isWebManifestEnabled() {
        return mRuntime.getSettings().getWebManifestEnabled();
    }

    @Override
    public boolean isJavaScriptEnabled() {
        return mRuntime.getSettings().getJavaScriptEnabled();
    }

    @Override
    public boolean isRemoteDebugging() {
        return mRuntime.getSettings().getRemoteDebuggingEnabled();
    }

    @Override
    public boolean isWebFonts() {
        return mRuntime.getSettings().getWebFontsEnabled();
    }

    @Override
    public boolean isConsoleOutputEnabled() {
        return mRuntime.getSettings().getConsoleOutputEnabled();
    }

    @Override
    public float getFontSizeFactor() {
        return mRuntime.getSettings().getFontSizeFactor();
    }

    @Override
    public boolean isEnterpriseRootsEnabled() {
        return mRuntime.getSettings().getEnterpriseRootsEnabled();
    }

    @Override
    public boolean isFontInflationEnabled() {
        return mRuntime.getSettings().getFontInflationEnabled();
    }

    @Override
    public boolean isInputAutoZoomEnabled() {
        return mRuntime.getSettings().getInputAutoZoomEnabled();
    }

    @Override
    public boolean isDoubleTapZoomingEnabled() {
        return mRuntime.getSettings().getDoubleTapZoomingEnabled();
    }

    @Override
    public int getGlMsaaLevel() {
        return mRuntime.getSettings().getGlMsaaLevel();
    }

    @Override
    public boolean isConsoleServiceToLogcat() {
        return mRuntime.getSettings().getConsoleOutputEnabled();
    }

    @Override
    public boolean isAboutConfigEnabled() {
        return mRuntime.getSettings().getAboutConfigEnabled();
    }

    @Override
    public boolean isForceUserScalable() {
        return mRuntime.getSettings().getForceUserScalableEnabled();
    }

    @Override
    public boolean isAutofillLoginsEnabled() {
        return mRuntime.getSettings().getLoginAutofillEnabled();
    }

    @Override
    public int getAllowInsecureConenctions() {
        return mRuntime.getSettings().getAllowInsecureConnections();
    }

    @Override
    public int getPreferredColorScheme() {
        return mRuntime.getSettings().getPreferredColorScheme();
    }

    @Override
    public boolean isPauseForDebuggerEnabled() {
        return mRuntime.getSettings().getPauseForDebuggerEnabled();
    }

    @Override
    public boolean isUseMaxScreenDepth() {
        return mRuntime.getSettings().getUseMaxScreenDepth();
    }

    @Override
    public float getDisplayDensityOverride() {
        Float val = mRuntime.getSettings().getDisplayDensityOverride();
        if (val != null) {
            return val;
        }
        return 0;
    }

    @Override
    public int getDisplayDpiOverride() {
        Integer val = mRuntime.getSettings().getDisplayDpiOverride();
        if (val != null) {
            return val;
        }
        return 0;
    }

    @Override
    public int getScreenWidthOverride() {
        Rect rect = mRuntime.getSettings().getScreenSizeOverride();
        if (rect != null) {
            return rect.width();
        }
        return  0;
    }

    @Override
    public int getScreenHeightOverride() {
        Rect rect = mRuntime.getSettings().getScreenSizeOverride();
        if (rect != null) {
            return rect.height();
        }
        return  0;
    }

    @Override
    public Class<? extends Service> getCrashHandler() {
        return mRuntime.getSettings().getCrashHandler();
    }

    @Override
    public String[] getRequestedLocales() {
        return mRuntime.getSettings().getLocales();
    }

    @Override
    public WContentBlocking.Settings getContentBlocking() {
        return super.getContentBlocking();
    }

    @Override
    public void setConsoleOutputEnabled(boolean enabled) {
        mRuntime.getSettings().setConsoleOutputEnabled(enabled);
    }

    @Override
    public void setRemoteDebuggingEnabled(boolean enabled) {
        mRuntime.getSettings().setRemoteDebuggingEnabled(enabled);
    }

    @Override
    public void setLocales(@Nullable String[] requestedLocales) {
        mRuntime.getSettings().setLocales(requestedLocales);
    }

    @Override
    public void setLoginAutofillEnabled(boolean enabled) {
        mRuntime.getSettings().setLoginAutofillEnabled(enabled);
    }
}
