package org.mozilla.vrbrowser.browser.engine;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.engine.Session;

import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.EngineSessionState;
import mozilla.components.concept.engine.Settings;

public class GeckoEngineSession extends EngineSession {

    private Session mSession;

    public GeckoEngineSession(@NonNull Session session) {
        mSession = session;
    }

    public GeckoSession getGeckoSession() {
        return mSession.getGeckoSession();
    }

    @NotNull
    @Override
    public Settings getSettings() {
        return null;
    }

    @Override
    public void clearFindMatches() {

    }

    @Override
    public void disableTrackingProtection() {

    }

    @Override
    public void enableTrackingProtection(@NotNull TrackingProtectionPolicy trackingProtectionPolicy) {

    }

    @Override
    public void exitFullScreenMode() {

    }

    @Override
    public void findAll(@NotNull String s) {

    }

    @Override
    public void findNext(boolean b) {

    }

    @Override
    public void goBack() {

    }

    @Override
    public void goForward() {

    }

    @Override
    public void loadData(@NotNull String s, @NotNull String s1, @NotNull String s2) {

    }

    @Override
    public void loadUrl(@NotNull String s, @Nullable EngineSession engineSession, @NotNull LoadUrlFlags loadUrlFlags) {

    }

    @Override
    public boolean recoverFromCrash() {
        return false;
    }

    @Override
    public void reload() {

    }

    @Override
    public void restoreState(@NotNull EngineSessionState engineSessionState) {

    }

    @NotNull
    @Override
    public EngineSessionState saveState() {
        return null;
    }

    @Override
    public void stopLoading() {

    }

    @Override
    public void toggleDesktopMode(boolean b, boolean b1) {

    }
}
