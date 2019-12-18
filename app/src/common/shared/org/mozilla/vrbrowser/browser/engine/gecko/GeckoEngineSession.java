package org.mozilla.vrbrowser.browser.engine.gecko;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.vrbrowser.browser.engine.EngineProvider;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.utils.StringUtils;

import java.util.Objects;
import java.util.function.Function;

import mozilla.components.concept.engine.EngineSession;
import mozilla.components.concept.engine.EngineSessionState;
import mozilla.components.concept.engine.HitResult;
import mozilla.components.concept.engine.Settings;
import mozilla.components.concept.engine.manifest.WebAppManifestParser;
import mozilla.components.concept.engine.request.RequestInterceptor.InterceptionResponse;

public class GeckoEngineSession extends EngineSession {

    private static final String MOZ_NULL_PRINCIPAL = "moz-nullprincipal:";
    private static final String ABOUT_BLANK = "about:blank";
    private static final int PROGRESS_START = 25;
    private static final int PROGRESS_STOP = 100;

    private Session mSession;
    private GeckoRuntime mRuntime;
    private boolean mInitialLoad;
    private Settings mSettings;

    GeckoEngineSession(@NonNull Context context, @NonNull Session session) {
        mSession = session;
        mRuntime = EngineProvider.INSTANCE.getOrCreateRuntime(context);
        mSettings = new FxRSessionSettings(mSession);

        createNavigationDelegate.run();
        createProgressDelegate.run();
        createContentDelegate.run();
    }

    @Nullable
    public GeckoSession getGeckoSession() {
        return mSession.getGeckoSession();
    }

    @NotNull
    @Override
    public Settings getSettings() {
        return mSettings;
    }

    @Override
    public void clearFindMatches() {
        if (getGeckoSession() != null) {
            getGeckoSession().getFinder().clear();
        }
    }

    @Override
    public void enableTrackingProtection(@NotNull TrackingProtectionPolicy trackingProtectionPolicy) {
        if (getGeckoSession() == null) {
            return;
        }

        boolean enabled;
        if (mSession.isPrivateMode()) {
            enabled = trackingProtectionPolicy.getUseForPrivateSessions();

        } else {
            enabled = trackingProtectionPolicy.getUseForRegularSessions();
        }
        /**
         * As described on https://bugzilla.mozilla.org/show_bug.cgi?id=1579264,useTrackingProtection
         * is a misleading setting. When is set to true is blocking content (scripts/sub-resources).
         * Instead of just turn on/off tracking protection. Until, this issue is fixed consumers need
         * a way to indicate, if they want to block content or not, this is why we use
         * [TrackingProtectionPolicy.TrackingCategory.SCRIPTS_AND_SUB_RESOURCES].
         */
        boolean shouldBlockContent = trackingProtectionPolicy.contains(TrackingProtectionPolicy.TrackingCategory.SCRIPTS_AND_SUB_RESOURCES);

        getGeckoSession().getSettings().setUseTrackingProtection(shouldBlockContent);
        if (!enabled) {
            disableTrackingProtectionOnGecko();
        }
        notifyObservers(observer -> {
            observer.onTrackerBlockingEnabledChange(enabled);
            return null;
        });
    }

    @Override
    public void disableTrackingProtection() {
        disableTrackingProtectionOnGecko();
        notifyObservers(observer -> {
            observer.onTrackerBlockingEnabledChange(false);
            return null;
        });
    }

    @Override
    public void exitFullScreenMode() {
        mSession.exitFullScreen();
    }

    @Override
    public void findAll(@NotNull String s) {
        if (getGeckoSession() == null) {
            return;
        }

        notifyObservers(observer -> {
            observer.onFind(s);
            return null;
        });

        getGeckoSession().getFinder().find(s, 0).then(finderResult -> {
            int activeMatchOrdinal;
            if (finderResult != null) {
                if (finderResult.current > 0) {
                    activeMatchOrdinal = finderResult.current - 1;

                } else {
                    activeMatchOrdinal = finderResult.current;
                }
                notifyObservers(observer -> {
                    observer.onFindResult(activeMatchOrdinal, finderResult.total, true);
                    return null;
                });
            }
            return new GeckoResult<Void>();
        });
    }

    @Override
    public void findNext(boolean forward) {
        if (getGeckoSession() == null) {
            return;
        }

        int  findFlags;
        if (forward) {
            findFlags = 0;
        } else {
            findFlags = GeckoSession.FINDER_FIND_BACKWARDS;
        }
        getGeckoSession().getFinder().find(null, findFlags).then(finderResult -> {
            int activeMatchOrdinal;
            if (finderResult != null) {
                if (finderResult.current > 0) {
                    activeMatchOrdinal = finderResult.current - 1;

                } else {
                    activeMatchOrdinal = finderResult.current;
                }
                notifyObservers(observer -> {
                    observer.onFindResult(activeMatchOrdinal, finderResult.total, true);
                    return null;
                });
            }
            return new GeckoResult<Void>();
        });
    }

    @Override
    public void goBack() {
        mSession.goBack();
    }

    @Override
    public void goForward() {
        mSession.goForward();
    }

    @Override
    public void loadData(@NotNull String data, @NotNull String mimeType, @NotNull String encoding) {
        if (getGeckoSession() == null) {
            return;
        }

        if ("base64".equals(encoding)) {
            getGeckoSession().loadData(data.getBytes(), mimeType);
        } else {
            getGeckoSession().loadString(data, mimeType);
        }
    }

    @Override
    public void loadUrl(@NotNull String url, @Nullable EngineSession engineSession, @NotNull LoadUrlFlags loadUrlFlags) {
        if (getGeckoSession() == null || url.isEmpty()) {
            return;
        }

        GeckoSession parentSession = engineSession != null ? ((GeckoEngineSession)engineSession).getGeckoSession() : null;
        getGeckoSession().loadUri(url, parentSession, loadUrlFlags.getValue());
    }

    @Override
    public boolean recoverFromCrash() {
        return false;
    }

    @Override
    public void reload() {
        mSession.reload();
    }

    @Override
    public void restoreState(@NotNull EngineSessionState engineSessionState) {
        if (!(engineSessionState instanceof GeckoEngineSessionState)) {
            throw new IllegalStateException("Can only restore from GeckoEngineSessionState");
        }

        if (((GeckoEngineSessionState) engineSessionState).mActualState == null) {
            return;
        }

        if (getGeckoSession() != null) {
            getGeckoSession().restoreState(((GeckoEngineSessionState) engineSessionState).mActualState);
        }
    }

    @NotNull
    @Override
    public EngineSessionState saveState() {
        return new GeckoEngineSessionState(mSession.getSessionState().mSessionState);
    }

    @Override
    public void stopLoading() {
        mSession.stop();
    }

    @Override
    public void toggleDesktopMode(boolean enable, boolean reload) {
        mSession.setUaMode(enable ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP : GeckoSessionSettings.USER_AGENT_MODE_VR);

        if (reload) {
            reload();
        }
    }

    @Override
    public void close() {
        super.close();
    }

    // To fully disable tracking protection we need to change the different tracking protection
    // variables to none.
    private void disableTrackingProtectionOnGecko() {
        if (getGeckoSession() != null) {
            getGeckoSession().getSettings().setUseTrackingProtection(false);
        }

        mRuntime.getSettings().getContentBlocking().setAntiTracking(ContentBlocking.AntiTracking.NONE);
        mRuntime.getSettings().getContentBlocking().setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL);
        mRuntime.getSettings().getContentBlocking().setStrictSocialTrackingProtection(false);
        mRuntime.getSettings().getContentBlocking().setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE);
    }

    /**
     * Indicates if this [EngineSession] should be ignored the tracking protection policies.
     * @param onResult A callback to inform if this [EngineSession] is in
     * the exception list, true if it is in, otherwise false.
     */
    private void isIgnoredForTrackingProtection(@NonNull Function<Boolean, Void> onResult) {
        if (getGeckoSession() == null) {
            onResult.apply(false);
            return;
        }

        mRuntime.getContentBlockingController().checkException(getGeckoSession()).accept(aBoolean -> {
            if (aBoolean != null) {
                onResult.apply(aBoolean);

            } else {
                onResult.apply(false);
            }
        });
    }

    private Runnable createProgressDelegate = new Runnable() {
        @Override
        public void run() {
            mSession.addProgressListener(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStart(@NonNull GeckoSession geckoSession, @NonNull String url) {
                    notifyObservers(observer -> {
                        observer.onProgress(PROGRESS_START);
                        observer.onLoadingStateChange(true);
                        return null;
                    });
                }

                @Override
                public void onPageStop(@NonNull GeckoSession geckoSession, boolean success) {
                    // by the time we reach here, any new request will come from web content.
                    // If it comes from the chrome, loadUrl(url) or loadData(string) will set it to
                    // false.
                    notifyObservers(observer -> {
                        observer.onProgress(PROGRESS_STOP);
                        observer.onLoadingStateChange(false);
                        return null;
                    });
                }

                @Override
                public void onProgressChange(@NonNull GeckoSession geckoSession, int progress) {
                    notifyObservers(observer -> {
                        observer.onProgress(progress);
                        return null;
                    });
                }

                @Override
                public void onSecurityChange(@NonNull GeckoSession geckoSession, @NonNull SecurityInformation securityInfo) {
                    // Ignore initial load of about:blank (see https://github.com/mozilla-mobile/android-components/issues/403)
                    if (mInitialLoad && securityInfo.origin != null && securityInfo.origin.startsWith(MOZ_NULL_PRINCIPAL)) {
                        return;
                    }

                    notifyObservers(observer -> {
                        observer.onSecurityChange(securityInfo.isSecure, securityInfo.host, securityInfo.issuerOrganization);
                        return null;
                    });
                }

            });
        }
    };

    private Runnable createNavigationDelegate = new Runnable() {
        @Override
        public void run() {
            mSession.addNavigationListener(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(@NonNull GeckoSession geckoSession, @Nullable String url) {
                    if (url == null) {
                        return; // ¯\_(ツ)_/¯
                    }

                    // Ignore initial load of about:blank (see https://github.com/mozilla-mobile/android-components/issues/403)
                    if (mInitialLoad && Objects.equals(url, ABOUT_BLANK)) {
                        return;
                    }

                    mInitialLoad = false;

                    isIgnoredForTrackingProtection(aBoolean -> {
                        notifyObservers(observer -> {
                            observer.onExcludedOnTrackingProtectionChange(aBoolean);
                            return null;
                        });
                        return null;
                    });

                    notifyObservers(observer -> {
                        observer.onLocationChange(url);
                        return null;
                    });
                }

                @Override
                public void onCanGoBack(@NonNull GeckoSession geckoSession, boolean canGoBack) {
                    notifyObservers(observer -> {
                        observer.onNavigationStateChange(canGoBack, null);
                        return null;
                    });
                }

                @Override
                public void onCanGoForward(@NonNull GeckoSession geckoSession, boolean canGoForward) {
                    notifyObservers(observer -> {
                        observer.onNavigationStateChange(null, canGoForward);
                        return null;
                    });
                }

                @Nullable
                @Override
                public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession geckoSession, @NonNull LoadRequest loadRequest) {
                    if (loadRequest.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                    }

                    InterceptionResponse response = null;
                    if (mSettings.getRequestInterceptor() != null) {
                        response = mSettings.getRequestInterceptor().onLoadRequest(GeckoEngineSession.this, loadRequest.uri);
                        if (response instanceof InterceptionResponse.Content) {
                            InterceptionResponse.Content content = (InterceptionResponse.Content) response;
                            loadData(content.getData(), content.getMimeType(), content.getEncoding());

                        } else {
                            loadUrl(loadRequest.uri, GeckoEngineSession.this, LoadUrlFlags.Companion.none());
                        }
                    }

                    if (response != null) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY);

                    } else if (!isObserved()) {
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW);

                    } else {
                        notifyObservers(observer -> {
                            // Unlike the name LoadRequest.isRedirect may imply this flag is not about http redirects. The flag
                            // is "True if and only if the request was triggered by an HTTP redirect."
                            // See: https://bugzilla.mozilla.org/show_bug.cgi?id=1545170
                            observer.onLoadRequest(
                                    loadRequest.uri,
                                    loadRequest.isRedirect,
                                    loadRequest.hasUserGesture
                            );
                            return null;
                        });

                        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                    }
                }

                @Nullable
                @Override
                public GeckoResult<String> onLoadError(@NonNull GeckoSession geckoSession, @Nullable String s, @NonNull WebRequestError webRequestError) {
                    return null;
                }
            });
        }
    };

    private Runnable createContentDelegate = new Runnable() {
        @Override
        public void run() {
            mSession.addContentListener(new GeckoSession.ContentDelegate() {
                @Override
                public void onTitleChange(@NonNull GeckoSession geckoSession, @Nullable String title) {
                    if (!mSession.isPrivateMode()) {
                        if (mSession.getCurrentUri() != null) {
                            if (mSettings.getHistoryTrackingDelegate() != null) {
                                mSettings.getHistoryTrackingDelegate().onTitleChanged(mSession.getCurrentUri(), title, null);
                            }
                        }
                    }
                    notifyObservers(observer -> {
                       observer.onTitleChange(title != null ? title : "");
                       return null;
                    });
                }

                @Override
                public void onFullScreen(@NonNull GeckoSession geckoSession, boolean fullScreen) {
                    notifyObservers(observer -> {
                        observer.onFullScreenChange(fullScreen);
                        return null;
                    });
                }

                @Override
                public void onContextMenu(@NonNull GeckoSession geckoSession, int screenX, int screenY, @NonNull ContextElement element) {
                    HitResult hitResult = handleLongClick(element.srcUri, element.type, element.linkUri, element.title);
                    notifyObservers(observer -> {
                       observer.onLongPress(hitResult);
                       return null;
                    });
                }

                @Override
                public void onExternalResponse(@NonNull GeckoSession geckoSession, @NonNull GeckoSession.WebResponseInfo webResponseInfo) {
                    notifyObservers(observer -> {
                        observer.onExternalResource(
                                webResponseInfo.uri,
                                webResponseInfo.filename,
                                webResponseInfo.contentLength,
                                webResponseInfo.contentType,
                                null,
                                null);
                        return null;
                    });
                }

                @Override
                public void onCrash(@NonNull GeckoSession geckoSession) {
                    notifyObservers(observer -> {
                       observer.onCrash();
                       return null;
                    });
                }

                @Override
                public void onKill(@NonNull GeckoSession geckoSession) {
                    notifyObservers(observer -> {
                        observer.onProcessKilled();
                        return null;
                    });
                }

                @Override
                public void onWebAppManifest(@NonNull GeckoSession geckoSession, @NonNull JSONObject jsonObject) {
                    WebAppManifestParser.Result parsed = new WebAppManifestParser().parse(jsonObject);
                    if (parsed instanceof WebAppManifestParser.Result.Success) {
                        notifyObservers(observer -> {
                           observer.onWebAppManifestLoaded(((WebAppManifestParser.Result.Success) parsed).getManifest());
                           return null;
                        });
                    }
                }
            });
        }
    };

    private HitResult handleLongClick(@Nullable String elementSrc, Integer elementType, @Nullable String uri, @Nullable String title) {
        switch (elementType) {
            case GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO: {
                if (elementSrc != null) {
                    return new HitResult.AUDIO(elementSrc);
                }
            }
            break;
            case GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO: {
                if (elementSrc != null) {
                    return new HitResult.VIDEO(elementSrc);
                }
            }
            break;
            case GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE: {
                if (elementSrc != null && uri != null) {
                    return new HitResult.IMAGE_SRC(elementSrc, uri);

                } else if (elementSrc != null) {
                    return new HitResult.IMAGE(elementSrc, title);

                }
            }
            break;
            case GeckoSession.ContentDelegate.ContextElement.TYPE_NONE: {
                if (elementSrc != null) {
                    if (StringUtils.isPhone(elementSrc)) {
                        return new HitResult.PHONE(elementSrc);

                    } else if (StringUtils.isEmail(elementSrc)) {
                        return new HitResult.EMAIL(elementSrc);

                    } else if (StringUtils.isGeolocation(elementSrc)) {
                        return new HitResult.GEO(elementSrc);

                    } else {
                        return new HitResult.UNKNOWN("");
                    }

                } else if (uri != null) {
                    return new HitResult.UNKNOWN(uri);
                }
            }
            break;
            default: {
                return new HitResult.UNKNOWN("");
            }
        }

        return new HitResult.UNKNOWN("");
    }


}
