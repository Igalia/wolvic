package com.igalia.wolvic.browser.api.impl;

import android.app.Service;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WRuntimeSettings;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntime;

class RuntimeSettingsImpl extends WRuntimeSettings {
    GeckoRuntime mRuntime;

    public RuntimeSettingsImpl(GeckoRuntime runtime, WRuntimeSettings settings) {
        mRuntime = runtime;
        mContentBlocking = new RuntimeSettingsImpl.ContentBlockingSettingsImpl();
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
        if (val == null) {
            return 0;
        }
        return val;
    }

    @Override
    public int getDisplayDpiOverride() {
        Integer val = mRuntime.getSettings().getDisplayDpiOverride();
        if (val == null) {
            return 0;
        }
        return val;
    }

    @Override
    public int getScreenWidthOverride() {
        Rect rect = mRuntime.getSettings().getScreenSizeOverride();
        if (rect == null) {
            return 0;
        }
        return rect.width();
    }

    @Override
    public int getScreenHeightOverride() {
        Rect rect = mRuntime.getSettings().getScreenSizeOverride();
        if (rect != null) {
            return 0;
        }
        return rect.height();
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
        return mContentBlocking;
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

    class ContentBlockingSettingsImpl extends WContentBlocking.Settings {
        @Override
        public int getAntiTracking() {
            return ContentBlockingDelegateImpl.fromGeckoAntiTracking(mRuntime.getSettings().getContentBlocking().getAntiTrackingCategories());
        }

        @Override
        public void setAntiTracking(int antiTracking) {
            mRuntime.getSettings().getContentBlocking().setAntiTracking(ContentBlockingDelegateImpl.toGeckoAntitracking(antiTracking));
        }

        @Override
        public int getSafeBrowsing() {
            return ContentBlockingDelegateImpl.fromGeckoSafeBrowsing(mRuntime.getSettings().getContentBlocking().getSafeBrowsingCategories());
        }

        @Override
        public void setSafeBrowsing(int safeBrowsing) {
            mRuntime.getSettings().getContentBlocking().setSafeBrowsing(ContentBlockingDelegateImpl.toGeckoSafeBrowsing(safeBrowsing));
        }

        @Override
        public int getCookieBehavior() {
            return ContentBlockingDelegateImpl.fromGeckoCookieBehavior(mRuntime.getSettings().getContentBlocking().getCookieBehavior());
        }

        @Override
        public void setCookieBehavior(int cookieBehavior) {
            mRuntime.getSettings().getContentBlocking().setCookieBehavior(ContentBlockingDelegateImpl.toGeckoCookieBehavior(cookieBehavior));
        }

        @Override
        public int getCookieBehaviorPrivate() {
            return ContentBlockingDelegateImpl.fromGeckoCookieBehavior(mRuntime.getSettings().getContentBlocking().getCookieBehaviorPrivateMode());
        }

        @Override
        public void setCookieBehaviorPrivate(int cookieBehaviorPrivate) {
            mRuntime.getSettings().getContentBlocking().setCookieBehaviorPrivateMode(ContentBlockingDelegateImpl.toGeckoCookieBehavior(cookieBehaviorPrivate));
        }

        @Override
        public int getCookieLifetime() {
            return ContentBlockingDelegateImpl.fromGeckoCookieLifetime(mRuntime.getSettings().getContentBlocking().getCookieLifetime());
        }

        @Override
        public void setCookieLifetime(int cookieLifetime) {
            mRuntime.getSettings().getContentBlocking().setCookieLifetime(ContentBlockingDelegateImpl.toGeckoCookieLifetime(cookieLifetime));
        }

        @Override
        public int getEnhancedTrackingProtectionLevel() {
            return ContentBlockingDelegateImpl.fromGeckoEtpLevel(mRuntime.getSettings().getContentBlocking().getEnhancedTrackingProtectionLevel());
        }

        @Override
        public void setEnhancedTrackingProtectionLevel(int etpLevel) {
            mRuntime.getSettings().getContentBlocking().setEnhancedTrackingProtectionLevel(ContentBlockingDelegateImpl.toGeckoEtpLevel(etpLevel));
        }

        @Override
        public boolean isStrictSocialTrackingProtection() {
            return mRuntime.getSettings().getContentBlocking().getStrictSocialTrackingProtection();
        }

        @Override
        public void setStrictSocialTrackingProtection(boolean strictSocialTrackingProtection) {
            mRuntime.getSettings().getContentBlocking().setStrictSocialTrackingProtection(strictSocialTrackingProtection);
        }

        @Override
        public boolean isCookiePurging() {
            return mRuntime.getSettings().getContentBlocking().getCookiePurging();
        }

        @Override
        public void setCookiePurging(boolean cookiePurging) {
            mRuntime.getSettings().getContentBlocking().setCookiePurging(cookiePurging);
        }
    }
}
