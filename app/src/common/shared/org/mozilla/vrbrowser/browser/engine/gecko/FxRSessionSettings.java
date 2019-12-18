package org.mozilla.vrbrowser.browser.engine.gecko;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.browser.engine.Session;

import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.Settings;
import mozilla.components.concept.engine.history.HistoryTrackingDelegate;
import mozilla.components.concept.engine.request.RequestInterceptor;

public class FxRSessionSettings extends Settings {

    private Session mSession;

    FxRSessionSettings(@NonNull Session session) {
        mSession = session;
    }

    @Override
    public boolean getJavascriptEnabled() {
        if (mSession.getGeckoSession() != null) {
            return mSession.getGeckoSession().getSettings().getAllowJavascript();

        } else{
            return false;
        }
    }

    @Override
    public void setJavascriptEnabled(boolean b) {
        if (mSession.getGeckoSession() != null) {
            mSession.getGeckoSession().getSettings().setAllowJavascript(b);
        }
    }

    @Nullable
    @Override
    public String getUserAgentString() {
        if (mSession.getGeckoSession() != null) {
            return mSession.getGeckoSession().getSettings().getUserAgentOverride();

        } else {
            return null;
        }
    }

    @Override
    public void setUserAgentString(@Nullable String s) {
        if (mSession.getGeckoSession() != null) {
            mSession.getGeckoSession().getSettings().setUserAgentOverride(s);
        }
    }

    @Override
    public boolean getSuspendMediaWhenInactive() {
        if (mSession.getGeckoSession() != null) {
            return mSession.getGeckoSession().getSettings().getSuspendMediaWhenInactive();

        } else {
            return false;
        }
    }

    @Override
    public void setSuspendMediaWhenInactive(boolean b) {
        if (mSession.getGeckoSession() != null) {
            mSession.getGeckoSession().getSettings().setSuspendMediaWhenInactive(b);
        }
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
}
