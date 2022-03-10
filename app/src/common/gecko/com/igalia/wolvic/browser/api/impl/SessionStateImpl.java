package com.igalia.wolvic.browser.api.impl;

import androidx.annotation.NonNull;

import com.igalia.wolvic.browser.api.WSessionState;

import org.mozilla.geckoview.GeckoSession;

import java.util.Objects;

public class SessionStateImpl implements WSessionState {
    private GeckoSession.SessionState mState;

    public SessionStateImpl(@NonNull GeckoSession.SessionState aState) {
        this.mState = aState;
    }

    @Override
    public boolean isEmpty() {
        return mState.isEmpty();
    }

    @Override
    public String toJson() {
        return mState.toString();
    }

    public static SessionStateImpl fromJson(String json) {
        return new SessionStateImpl(Objects.requireNonNull(GeckoSession.SessionState.fromString(json)));
    }

    public GeckoSession.SessionState getGeckoState() {
        return mState;
    }
}
