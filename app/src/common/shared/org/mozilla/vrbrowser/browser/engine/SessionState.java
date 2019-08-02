package org.mozilla.vrbrowser.browser.engine;

import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.json.JSONException;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.Media;

import java.io.IOException;
import java.util.ArrayList;

public class SessionState {

    public boolean mCanGoBack;
    public boolean mCanGoForward;
    public boolean mIsLoading;
    public boolean mIsInputActive;
    public transient GeckoSession.ProgressDelegate.SecurityInformation mSecurityInformation;
    public String mUri;
    public String mPreviousUri;
    public String mTitle;
    public boolean mFullScreen;
    public transient GeckoSession mSession;
    public SessionSettings mSettings;
    public transient ArrayList<Media> mMediaElements = new ArrayList<>();
    @JsonAdapter(SessionState.GeckoSessionStateAdapter.class)
    public GeckoSession.SessionState mSessionState;

    public class GeckoSessionStateAdapter extends TypeAdapter<GeckoSession.SessionState> {
        @Override public void write(JsonWriter out, GeckoSession.SessionState session) throws IOException {
            out.jsonValue(session.toString());
        }
        @Override public GeckoSession.SessionState read(JsonReader in) {
            try {
                String session = new JsonParser().parse(in).toString();
                return GeckoSession.SessionState.fromString(session);

            } catch (JSONException e) {
                return null;
            }
        }
    }
}
