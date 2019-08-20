package org.mozilla.vrbrowser.browser.engine;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.json.JSONException;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.browser.Media;

import java.io.IOException;
import java.util.ArrayList;

@JsonAdapter(SessionState.SessionStateAdapterFactory.class)
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

    public static class GeckoSessionStateAdapter extends TypeAdapter<GeckoSession.SessionState> {
        @Override
        public void write(JsonWriter out, GeckoSession.SessionState session) throws IOException {
            out.jsonValue(session.toString());
        }

        @Override
        public GeckoSession.SessionState read(JsonReader in) {
            try {
                String session = new JsonParser().parse(in).toString();
                return GeckoSession.SessionState.fromString(session);

            } catch (JSONException e) {
                return null;
            }
        }
    }

    public class SessionStateAdapterFactory implements TypeAdapterFactory {
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            final TypeAdapter<GeckoSession.SessionState> gsDelegate = new GeckoSessionStateAdapter();

            return new TypeAdapter<T>() {
                public void write(JsonWriter out, T value) throws IOException {
                    try {
                        if (value instanceof SessionState) {
                            SessionState session = (SessionState) value;
                            out.beginObject();
                            out.name("mCanGoBack").value(session.mCanGoBack);
                            out.name("mCanGoForward").value(session.mCanGoForward);
                            out.name("mIsLoading").value(session.mIsLoading);
                            out.name("mIsInputActive").value(session.mIsInputActive);
                            out.name("mUri").value(session.mUri);
                            out.name("mPreviousUri").value(session.mPreviousUri);
                            out.name("mTitle").value(session.mTitle);
                            out.name("mFullScreen").value(session.mFullScreen);
                            out.name("mSettings").jsonValue(gson.toJson(session.mSettings));
                            if (session.mSession.getSettings().getUsePrivateMode()) {
                                out.name("mSessionState").jsonValue(null);

                            } else {
                                if (session.mSessionState != null) {
                                    out.name("mSessionState").jsonValue(gsDelegate.toJson(session.mSessionState));

                                } else {
                                    out.name("mSessionState").jsonValue(null);
                                }
                            }
                            out.endObject();

                        } else {
                            delegate.write(out, null);
                        }

                    } catch (IOException e) {
                        delegate.write(out, null);
                    }
                }

                public T read(JsonReader in) throws IOException {
                    try {
                        return delegate.read(in);

                    } catch (IOException e) {
                        in.skipValue();
                        return null;

                    } catch (IllegalStateException e) {
                        in.skipValue();
                        return null;

                    } catch (JsonSyntaxException e) {
                        in.skipValue();
                        return null;
                    }
                }
            };
        }
    }
}
