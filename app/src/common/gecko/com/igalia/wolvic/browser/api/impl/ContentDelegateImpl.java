package com.igalia.wolvic.browser.api.impl;
import android.view.PointerIcon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.WResult;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.browser.api.WSlowScriptResponse;
import com.igalia.wolvic.browser.api.WWebResponse;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.geckoview.WebResponse;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Objects;

class ContentDelegateImpl implements GeckoSession.ContentDelegate {
    private WSession.ContentDelegate mDelegate;
    private WSession mSession;

    public ContentDelegateImpl(WSession.ContentDelegate aDelegate, WSession aSession) {
        this.mDelegate = aDelegate;
        this.mSession = aSession;
    }


    @Override
    public void onTitleChange(@NonNull GeckoSession session, @Nullable String title) {
        mDelegate.onTitleChange(mSession, title);
    }

    @Override
    public void onPreviewImage(@NonNull GeckoSession session, @NonNull String previewImageUrl) {
        mDelegate.onPreviewImage(mSession, previewImageUrl);
    }

    @Override
    public void onFocusRequest(@NonNull GeckoSession session) {
        mDelegate.onFocusRequest(mSession);
    }

    @Override
    public void onCloseRequest(@NonNull GeckoSession session) {
        mDelegate.onCloseRequest(mSession);
    }

    @Override
    public void onFullScreen(@NonNull GeckoSession session, boolean fullScreen) {
        mDelegate.onFullScreen(mSession, fullScreen);
    }

    @Override
    public void onMetaViewportFitChange(@NonNull GeckoSession session, @NonNull String viewportFit) {
        mDelegate.onMetaViewportFitChange(mSession, viewportFit);
    }

    @Override
    public void onContextMenu(@NonNull GeckoSession session, int screenX, int screenY, @NonNull ContextElement element) {
        int type;
        switch (element.type) {
            case ContextElement.TYPE_AUDIO:
                type = WSession.ContentDelegate.ContextElement.TYPE_AUDIO;
                break;
            case ContextElement.TYPE_IMAGE:
                type = WSession.ContentDelegate.ContextElement.TYPE_IMAGE;
                break;
            case ContextElement.TYPE_VIDEO:
                type = WSession.ContentDelegate.ContextElement.TYPE_VIDEO;
                break;
            case ContextElement.TYPE_NONE:
            default:
                type = WSession.ContentDelegate.ContextElement.TYPE_NONE;
                break;
        }

        WSession.ContentDelegate.ContextElement elem = new WSession.ContentDelegate.ContextElement(
                element.baseUri, element.linkUri, element.title, element.altText, type, element.srcUri
        );

        mDelegate.onContextMenu(mSession, screenX, screenY, elem);
    }

    @Override
    public void onExternalResponse(@NonNull GeckoSession session, @NonNull WebResponse response) {
        mDelegate.onExternalResponse(mSession, new WWebResponse() {
            @NonNull
            @Override
            public String uri() {
                return response.uri;
            }

            @NonNull
            @Override
            public Map<String, String> headers() {
                return response.headers;
            }

            @Override
            public int statusCode() {
                return response.statusCode;
            }

            @Override
            public boolean redirected() {
                return response.redirected;
            }

            @Override
            public boolean isSecure() {
                return response.isSecure;
            }

            @Nullable
            @Override
            public X509Certificate certificate() {
                return response.certificate;
            }

            @Nullable
            @Override
            public InputStream body() {
                return response.body;
            }
        });
    }

    @Override
    public void onCrash(@NonNull GeckoSession session) {
        mDelegate.onCrash(mSession);
    }

    @Override
    public void onKill(@NonNull GeckoSession session) {
        mDelegate.onKill(mSession);
    }

    @Override
    public void onFirstComposite(@NonNull GeckoSession session) {
        mDelegate.onFirstComposite(mSession);
    }

    @Override
    public void onFirstContentfulPaint(@NonNull GeckoSession session) {
        mDelegate.onFirstContentfulPaint(mSession);
    }

    @Override
    public void onPaintStatusReset(@NonNull GeckoSession session) {
        mDelegate.onPaintStatusReset(mSession);
    }

    @Override
    public void onPointerIconChange(@NonNull GeckoSession session, @NonNull PointerIcon icon) {
        mDelegate.onPointerIconChange(mSession, icon);
    }

    @Override
    public void onWebAppManifest(@NonNull GeckoSession session, @NonNull JSONObject manifest) {
        mDelegate.onWebAppManifest(mSession, manifest);
    }

    @Nullable
    @Override
    public GeckoResult<SlowScriptResponse> onSlowScript(@NonNull GeckoSession geckoSession, @NonNull String scriptFileName) {
        WResult<WSlowScriptResponse> result = mDelegate.onSlowScript(mSession, scriptFileName);
        if (result == null)
            return null;
        return ResultImpl.from(result).map(value -> {
            switch (Objects.requireNonNull(value)) {
                case STOP:
                    return SlowScriptResponse.STOP;
                case CONTINUE:
                default:
                    return SlowScriptResponse.CONTINUE;
            }
        });
    }

    @Override
    public void onShowDynamicToolbar(@NonNull GeckoSession geckoSession) {
        mDelegate.onShowDynamicToolbar(mSession);
    }
}
