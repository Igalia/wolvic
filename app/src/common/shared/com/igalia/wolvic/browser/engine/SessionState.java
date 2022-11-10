package com.igalia.wolvic.browser.engine;

import androidx.annotation.IntDef;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.igalia.wolvic.browser.Media;
import com.igalia.wolvic.browser.api.WDisplay;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSessionState;
import com.igalia.wolvic.ui.adapters.WebApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@JsonAdapter(SessionState.SessionStateAdapterFactory.class)
public class SessionState {
    @IntDef(value = { WEBXR_UNUSED, WEBXR_ALLOWED, WEBXR_BLOCKED})
    public @interface WebXRState {}
    public static final int WEBXR_UNUSED = 0;
    public static final int WEBXR_ALLOWED = 1;
    public static final int WEBXR_BLOCKED = 2;

    @IntDef(value = { POPUP_UNUSED, POPUP_ALLOWED, POPUP_BLOCKED})
    public @interface PopupState {}
    public static final int POPUP_UNUSED = 0;
    public static final int POPUP_ALLOWED = 1;
    public static final int POPUP_BLOCKED = 2;

    @IntDef(value = { DRM_UNUSED, DRM_ALLOWED, DRM_BLOCKED})
    public @interface DrmState {}
    public static final int DRM_UNUSED = 0;
    public static final int DRM_ALLOWED = 1;
    public static final int DRM_BLOCKED = 2;

    private transient boolean mIsActive;
    public boolean mCanGoBack;
    public boolean mCanGoForward;
    public boolean mIsLoading;
    public boolean mIsInputActive;
    public transient WSession.ProgressDelegate.SecurityInformation mSecurityInformation;
    public String mUri = "";
    public String mPreviousUri;
    public String mTitle = "";
    public transient WebApp mWebAppManifest;
    public transient boolean mFullScreen;
    public transient boolean mInKioskMode;
    public transient WSession mSession;
    public transient WDisplay mDisplay;
    public SessionSettings mSettings;
    public transient ArrayList<Media> mMediaElements = new ArrayList<>();
    public transient @WebXRState int mWebXRState = WEBXR_UNUSED;
    public transient @PopupState int mPopUpState = POPUP_UNUSED;
    public transient @DrmState int mDrmState = DRM_UNUSED;
    @JsonAdapter(SessionState.ISessionStateAdapter.class)
    public WSessionState mSessionState;
    public long mLastUse;
    public String mRegion;
    public String mId = UUID.randomUUID().toString();
    public String mParentId; // Parent session stack Id.
    public transient boolean mIsWebExtensionSession = false;

    public SessionState recreate() {
        SessionState result = new SessionState();
        result.mUri = mUri;
        result.mPreviousUri = mPreviousUri;
        result.mTitle = mTitle;
        result.mSettings = mSettings;
        result.mSessionState = mSessionState;
        result.mLastUse = mLastUse;
        result.mRegion = mRegion;
        result.mId = mId;
        result.mParentId = mParentId;
        result.mIsWebExtensionSession = mIsWebExtensionSession;

        return result;
    }

    public static class ISessionStateAdapter extends TypeAdapter<WSessionState> {
        @Override
        public void write(JsonWriter out, WSessionState state) throws IOException {
            out.jsonValue(state.toJson());
        }

        @Override
        public WSessionState read(JsonReader in) {
            try {
                String session = new JsonParser().parse(in).toString();
                return WSessionState.fromJson(session);

            } catch (Exception e) {
                return null;
            }
        }
    }

    boolean isActive() {
        return mIsActive;
    }

    void setActive(boolean active) {
        if (active == mIsActive) {
            return;
        }
        mIsActive = active;
        SessionStore.get().sessionActiveStateChanged();
    }

    public class SessionStateAdapterFactory implements TypeAdapterFactory {
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            final TypeAdapter<WSessionState> gsDelegate = new ISessionStateAdapter();

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
                            out.name("mSettings").jsonValue(gson.toJson(session.mSettings));
                            out.name("mLastUse").value(session.mLastUse);
                            out.name("mRegion").value(session.mRegion);
                            out.name("mId").value(session.mId);
                            out.name("mParentId").value(session.mParentId);
                            if (session.mSettings != null) {
                                if (session.mSettings.isPrivateBrowsingEnabled()) {
                                    out.name("mSessionState").jsonValue(null);

                                } else {
                                    if (session.mSessionState != null) {
                                        out.name("mSessionState").jsonValue(gsDelegate.toJson(session.mSessionState));

                                    } else {
                                        out.name("mSessionState").jsonValue(null);
                                    }
                                }
                            }
                            if (session.mSettings != null) {
                                out.name("mSettings").jsonValue(gson.toJson(session.mSettings));
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
                    } catch (Exception e) {
                        in.skipValue();
                        return null;
                    }
                }
            };
        }
    }
}
