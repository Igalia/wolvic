package org.mozilla.vrbrowser.browser.engine;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtensionController;
import org.mozilla.vrbrowser.browser.extensions.GeckoWebExtension;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import mozilla.components.concept.engine.Engine;
import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.EngineSessionState;
import mozilla.components.concept.engine.EngineView;
import mozilla.components.concept.engine.Settings;
import mozilla.components.concept.engine.content.blocking.TrackerLog;
import mozilla.components.concept.engine.content.blocking.TrackingProtectionExceptionStorage;
import mozilla.components.concept.engine.utils.EngineVersion;
import mozilla.components.concept.engine.webextension.WebExtension;
import mozilla.components.concept.engine.webextension.WebExtensionDelegate;
import mozilla.components.concept.engine.webnotifications.WebNotificationDelegate;
import mozilla.components.concept.engine.webpush.WebPushDelegate;
import mozilla.components.concept.engine.webpush.WebPushHandler;

import static org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION;

public class GeckoEngine implements Engine {

    private SessionStore mSessionStore;
    private WebExtensionDelegate mWebExtensionDelegate;

    public GeckoEngine(@NonNull SessionStore sessionstore) {
        mSessionStore = sessionstore;
    }

    @NotNull
    @Override
    public Settings getSettings() {
        return null;
    }

    @NotNull
    @Override
    public TrackingProtectionExceptionStorage getTrackingProtectionExceptionStore() {
        return null;
    }

    @NotNull
    @Override
    public EngineVersion getVersion() {
        EngineVersion version = EngineVersion.Companion.parse(MOZILLA_VERSION);
        if (version != null) {
            return version;
        }

        throw new IllegalStateException("Could not determine engine version");
    }

    @Override
    public void clearData(@NotNull BrowsingData browsingData, @Nullable String host, @NotNull Function0<Unit> onSuccess, @NotNull Function1<? super Throwable, Unit> onError) {
        Long types = (long) browsingData.getTypes();
        if (host != null) {
            mSessionStore.getRuntime().getStorageController().clearDataFromHost(host, types).then(aVoid -> {
                onSuccess.invoke();
                return null;
            }, throwable -> {
                onError.invoke(throwable);
                return null;
            });

        } else {
            mSessionStore.getRuntime().getStorageController().clearData(types).then(aVoid -> {
                onSuccess.invoke();
                return null;
            }, throwable -> {
                onError.invoke(throwable);
                return null;
            });
        }

    }

    @NotNull
    @Override
    public EngineSession createSession(boolean b) {
        return null;
    }

    @NotNull
    @Override
    public EngineSessionState createSessionState(@NotNull JSONObject jsonObject) {
        return null;
    }

    @NotNull
    @Override
    public EngineView createView(@NotNull Context context, @Nullable AttributeSet attributeSet) {
        return null;
    }

    @Override
    public void getTrackersLog(@NotNull EngineSession engineSession, @NotNull Function1<? super List<TrackerLog>, Unit> function1, @NotNull Function1<? super Throwable, Unit> function11) {

    }

    @Override
    public void installWebExtension(@NotNull String id, @NotNull String url, boolean allowContentMessaging, @NotNull Function1<? super WebExtension, Unit> onSuccess, @NotNull Function2<? super String, ? super Throwable, Unit> onError) {
        final GeckoWebExtension extension = new GeckoWebExtension(id, url, allowContentMessaging);
        mSessionStore.getRuntime().registerWebExtension(extension.getNativeExtension()).then(aVoid -> {
            if (mWebExtensionDelegate != null) {
                mWebExtensionDelegate.onInstalled(extension);
            }

            onSuccess.invoke(extension);

            return new GeckoResult<>();

        }, throwable -> {
            onError.invoke(id, throwable);

            return new GeckoResult<>();
        });
    }

    @NotNull
    @Override
    public String name() {
        return "Gecko";
    }

    @Override
    public void registerWebExtensionDelegate(@NotNull WebExtensionDelegate webExtensionDelegate) {
        mWebExtensionDelegate = webExtensionDelegate;

        mSessionStore.getRuntime().getWebExtensionController().setTabDelegate(new WebExtensionController.TabDelegate() {
            @NotNull
            @Override
            public GeckoResult<GeckoSession> onNewTab(@androidx.annotation.Nullable org.mozilla.geckoview.WebExtension webExtension, @androidx.annotation.Nullable String url) {
                Session session = mSessionStore.createSession(mSessionStore.getActiveSession().isPrivateMode());
                if (webExtension != null) {
                    GeckoWebExtension extension = new GeckoWebExtension(webExtension.id, webExtension.location, true);
                    mWebExtensionDelegate.onNewTab(extension, url != null ? url : "", new GeckoEngineSession(session));
                }

                return GeckoResult.fromValue(session.getGeckoSession());
            }
        });
    }

    @Override
    public void registerWebNotificationDelegate(@NotNull WebNotificationDelegate webNotificationDelegate) {

    }

    @NotNull
    @Override
    public WebPushHandler registerWebPushDelegate(@NotNull WebPushDelegate webPushDelegate) {
        return null;
    }

    @Override
    public void speculativeConnect(@NotNull String s) {

    }

    @Override
    public void warmUp() {

    }
}
