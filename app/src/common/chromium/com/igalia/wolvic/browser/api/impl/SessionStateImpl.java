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

    @Override
    public String getCurrentUri() {
        // TODO: this is required to implement session persistence.
        return null;
    }

    public static SessionStateImpl fromJson(String json) {
        // TODO: returning null drops the saved state instead of keeping a placeholder that would
        // be written back as "{}" and break the Gecko backend when restoring.
        return null;
    }
}