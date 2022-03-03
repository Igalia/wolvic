package com.igalia.wolvic.browser.api;

import com.igalia.wolvic.browser.api.impl.SessionStateImpl;

/*
 * Interface representing a saved session state.
 */
public interface WSessionState {
    boolean isEmpty();
    String toJson();

    static WSessionState fromJson(String json) {
        return SessionStateImpl.fromJson(json);
    }
}
