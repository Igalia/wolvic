package org.mozilla.vrbrowser.browser.engine.gecko;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoSession;

import mozilla.components.concept.engine.EngineSessionState;

public class GeckoEngineSessionState implements EngineSessionState {

    private static final String GECKO_STATE_KEY = "GECKO_STATE";

    protected GeckoSession.SessionState mActualState;

    public GeckoEngineSessionState(GeckoSession.SessionState state) {
        mActualState = state;
    }

    @NotNull
    @Override
    public JSONObject toJSON() {
        JSONObject state = new JSONObject();
        if (mActualState != null) {
            try {
                // GeckoView provides a String representing the entire session state. We
                // store this String using a single Map entry with key GECKO_STATE_KEY.
                state.put(GECKO_STATE_KEY, mActualState.toString());

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return state;
    }

    public static GeckoEngineSessionState fromJSON(JSONObject json) {
        try {
            String state = json.getString(GECKO_STATE_KEY);
            new GeckoEngineSessionState(GeckoSession.SessionState.fromString(state));

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new GeckoEngineSessionState(null);
    }
}
