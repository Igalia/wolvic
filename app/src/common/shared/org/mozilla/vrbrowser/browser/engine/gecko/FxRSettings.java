package org.mozilla.vrbrowser.browser.engine.gecko;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.Settings;
import mozilla.components.concept.engine.history.HistoryTrackingDelegate;
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme;
import mozilla.components.concept.engine.request.RequestInterceptor;

public class FxRSettings extends Settings {

    private GeckoRuntimeSettings mGeckoRuntimeSettings;

    FxRSettings(@NonNull GeckoRuntimeSettings settings) {
        mGeckoRuntimeSettings = settings;
    }

    @Override
    public boolean getJavascriptEnabled() {
        return mGeckoRuntimeSettings.getJavaScriptEnabled();
    }

    @Override
    public void setJavascriptEnabled(boolean b) {
        mGeckoRuntimeSettings.setJavaScriptEnabled(b);
    }

    @Override
    public boolean getWebFontsEnabled() {
        return mGeckoRuntimeSettings.getWebFontsEnabled();
    }

    @Override
    public void setWebFontsEnabled(boolean b) {
        mGeckoRuntimeSettings.setWebFontsEnabled(b);
    }

    @Override
    public boolean getAutomaticFontSizeAdjustment() {
        return mGeckoRuntimeSettings.getAutomaticFontSizeAdjustment();
    }

    @Override
    public void setAutomaticFontSizeAdjustment(boolean b) {
        mGeckoRuntimeSettings.setAutomaticFontSizeAdjustment(b);
    }

    @Override
    public boolean getAutomaticLanguageAdjustment() {
        return false;
    }

    @Override
    public void setAutomaticLanguageAdjustment(boolean b) {
        // Not yet implemented
    }

    @NotNull
    @Override
    public EngineSession.SafeBrowsingPolicy[] getSafeBrowsingPolicy() {
        return Stream.of(EngineSession.SafeBrowsingPolicy.RECOMMENDED).toArray(EngineSession.SafeBrowsingPolicy[]::new);
    }

    @Override
    public void setSafeBrowsingPolicy(@NotNull EngineSession.SafeBrowsingPolicy[] safeBrowsingPolicies) {
        int policy = Stream.of(safeBrowsingPolicies).mapToInt(EngineSession.SafeBrowsingPolicy::ordinal).sum();
        mGeckoRuntimeSettings.getContentBlocking().setSafeBrowsing(policy);
    }

    @Override
    public void setTrackingProtectionPolicy(@Nullable EngineSession.TrackingProtectionPolicy trackingProtectionPolicy) {
        boolean activateStrictSocialTracking = false;
        if (trackingProtectionPolicy != null) {
            if (trackingProtectionPolicy.getStrictSocialTrackingProtection() != null) {
                activateStrictSocialTracking = Stream.of(trackingProtectionPolicy.getTrackingCategories()).anyMatch(item -> item == EngineSession.TrackingProtectionPolicy.TrackingCategory.STRICT);
            }

            int etpLevel = ContentBlocking.EtpLevel.STRICT;
            if (Stream.of(trackingProtectionPolicy.getTrackingCategories()).collect(Collectors.toList()).contains(EngineSession.TrackingProtectionPolicy.TrackingCategory.NONE)) {
                etpLevel = ContentBlocking.EtpLevel.NONE;
            }

            mGeckoRuntimeSettings.getContentBlocking().setEnhancedTrackingProtectionLevel(etpLevel);
            mGeckoRuntimeSettings.getContentBlocking().setStrictSocialTrackingProtection(activateStrictSocialTracking);
            mGeckoRuntimeSettings.getContentBlocking().setCookieBehavior(trackingProtectionPolicy.getCookiePolicy().getId());
        }
    }

    @Override
    public boolean getTestingModeEnabled() {
        return false;
    }

    @Override
    public void setTestingModeEnabled(boolean b) {
        // Not yet implemented
    }

    @Nullable
    @Override
    public String getUserAgentString() {
        return GeckoSession.getDefaultUserAgent();
    }

    @Override
    public void setUserAgentString(@org.jetbrains.annotations.Nullable String s) {
        // Not yet implemented
    }

    @NotNull
    @Override
    public PreferredColorScheme getPreferredColorScheme() {
        return fromGeckoValue(mGeckoRuntimeSettings.getPreferredColorScheme());
    }

    @Override
    public void setPreferredColorScheme(@NotNull PreferredColorScheme preferredColorScheme) {
        mGeckoRuntimeSettings.setPreferredColorScheme(toGeckoValue(preferredColorScheme));
    }

    @Override
    public boolean getAllowAutoplayMedia() {
        return mGeckoRuntimeSettings.getAutoplayDefault() == GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED;
    }

    @Override
    public void setAllowAutoplayMedia(boolean b) {
        mGeckoRuntimeSettings.setAutoplayDefault(b ? GeckoRuntimeSettings.AUTOPLAY_DEFAULT_ALLOWED : GeckoRuntimeSettings.AUTOPLAY_DEFAULT_BLOCKED);
    }

    @Override
    public boolean getSuspendMediaWhenInactive() {
        return false;
    }

    @Override
    public void setSuspendMediaWhenInactive(boolean b) {
        // Not yet implemented
    }

    @Override
    public boolean getRemoteDebuggingEnabled() {
        return mGeckoRuntimeSettings.getRemoteDebuggingEnabled();
    }

    @Override
    public void setRemoteDebuggingEnabled(boolean b) {
        mGeckoRuntimeSettings.setRemoteDebuggingEnabled(b);
    }

    @Nullable
    @Override
    public Boolean getFontInflationEnabled() {
        return mGeckoRuntimeSettings.getFontInflationEnabled();
    }

    @Override
    public void setFontInflationEnabled(@Nullable Boolean aBoolean) {
        mGeckoRuntimeSettings.setFontInflationEnabled(aBoolean);
    }

    @Nullable
    @Override
    public Float getFontSizeFactor() {
        return mGeckoRuntimeSettings.getFontSizeFactor();
    }

    @Override
    public void setFontSizeFactor(@Nullable Float aFloat) {
        mGeckoRuntimeSettings.setFontSizeFactor(aFloat);
    }

    @Override
    public boolean getForceUserScalableContent() {
        return mGeckoRuntimeSettings.getForceUserScalableEnabled();
    }

    @Override
    public void setForceUserScalableContent(boolean b) {
        mGeckoRuntimeSettings.setForceUserScalableEnabled(b);
    }

    @Nullable
    @Override
    public EngineSession.TrackingProtectionPolicy getTrackingProtectionPolicy() {
        return null;
    }

    @Nullable
    @Override
    public HistoryTrackingDelegate getHistoryTrackingDelegate() {
        return null;
    }

    @Nullable
    @Override
    public RequestInterceptor getRequestInterceptor() {
        return null;
    }

    private static PreferredColorScheme fromGeckoValue(int geckoValue) {
        switch (geckoValue) {
            case GeckoRuntimeSettings.COLOR_SCHEME_DARK: return PreferredColorScheme.Dark.INSTANCE;
            case GeckoRuntimeSettings.COLOR_SCHEME_LIGHT: return PreferredColorScheme.Light.INSTANCE;
            case GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM:
            default: return PreferredColorScheme.System.INSTANCE;
        }
    }

    private static int toGeckoValue(PreferredColorScheme scheme) {
        if (scheme == PreferredColorScheme.Dark.INSTANCE) {
            return GeckoRuntimeSettings.COLOR_SCHEME_DARK;

        } else if (scheme == PreferredColorScheme.Light.INSTANCE) {
            return GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;

        } else if (scheme == PreferredColorScheme.System.INSTANCE) {
            return GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM;
        }

        return GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM;
    }
}
