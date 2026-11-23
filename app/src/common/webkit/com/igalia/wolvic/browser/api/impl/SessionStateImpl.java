package com.igalia.wolvic.browser.api.impl;

import com.igalia.wolvic.browser.api.WSessionState;

public class SessionStateImpl implements WSessionState {

    public SessionStateImpl() {}

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public String toJson() {
        return "{}";
    }

    public static SessionStateImpl fromJson(String json) {
        // TODO
        return new SessionStateImpl();
    }
}