package com.igalia.wolvic.browser.api;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;

import com.igalia.wolvic.ui.adapters.WebApp;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

public interface WSession {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {
                    LOAD_FLAGS_NONE,
                    LOAD_FLAGS_BYPASS_CACHE,
                    LOAD_FLAGS_BYPASS_PROXY,
                    LOAD_FLAGS_EXTERNAL,
                    LOAD_FLAGS_ALLOW_POPUPS,
                    LOAD_FLAGS_FORCE_ALLOW_DATA_URI,
                    LOAD_FLAGS_REPLACE_HISTORY
            })
            /* package */ @interface LoadFlags {}

    /** Default load flag, no special considerations. */
    int LOAD_FLAGS_NONE = 0;

    /** Bypass the cache. */
    int LOAD_FLAGS_BYPASS_CACHE = 1;

    /** Bypass the proxy, if one has been configured. */
     int LOAD_FLAGS_BYPASS_PROXY = 1 << 1;

    /** The load is coming from an external app. Perform additional checks. */
    int LOAD_FLAGS_EXTERNAL = 1 << 2;

    /** Popup blocking will be disabled for this load */
    int LOAD_FLAGS_ALLOW_POPUPS = 1 << 3;

    /** Bypass the URI classifier (content blocking and Safe Browsing). */
     int LOAD_FLAGS_BYPASS_CLASSIFIER = 1 << 4;

    /**
     * Allows a top-level data: navigation to occur. E.g. view-image is an explicit user action which
     * should be allowed.
     */
    int LOAD_FLAGS_FORCE_ALLOW_DATA_URI = 1 << 5;

    /** This flag specifies that any existing history entry should be replaced. */
    int LOAD_FLAGS_REPLACE_HISTORY = 1 << 6;

    interface ContentDelegate {
        /**
         * A page title was discovered in the content or updated after the content loaded.
         *
         * @param session The ISession that initiated the callback.
         * @param title   The title sent from the content.
         */
        @UiThread
        default void onTitleChange(@NonNull final WSession session, @Nullable final String title) {
        }

        /**
         * A preview image was discovered in the content after the content loaded.
         *
         * @param session         The ISession that initiated the callback.
         * @param previewImageUrl The preview image URL sent from the content.
         */
        @UiThread
        default void onPreviewImage(
                @NonNull final WSession session, @NonNull final String previewImageUrl) {
        }

        /**
         * A page has requested focus. Note that window.focus() in content will not result in this being
         * called.
         *
         * @param session The ISession that initiated the callback.
         */
        @UiThread
        default void onFocusRequest(@NonNull final WSession session) {
        }

        /**
         * A page has requested to close
         *
         * @param session The ISession that initiated the callback.
         */
        @UiThread
        default void onCloseRequest(@NonNull final WSession session) {
        }

        /**
         * A page has entered or exited full screen mode. Typically, the implementation would set the
         * Activity containing the ISession to full screen when the page is in full screen mode.
         *
         * @param session    The ISession that initiated the callback.
         * @param fullScreen True if the page is in full screen mode.
         */
        @UiThread
        default void onFullScreen(@NonNull final WSession session, final boolean fullScreen) {
        }

        /**
         * A viewport-fit was discovered in the content or updated after the content.
         *
         * @param session     The ISession that initiated the callback.
         * @param viewportFit The value of viewport-fit of meta element in content.
         * @see <a href="https://drafts.csswg.org/css-round-display/#viewport-fit-descriptor">4.1. The
         * viewport-fit descriptor</a>
         */
        @UiThread
        default void onMetaViewportFitChange(@NonNull final WSession session, @NonNull final String viewportFit) {
        }

        /** Element details for onContextMenu callbacks. */
        class ContextElement {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({TYPE_NONE, TYPE_IMAGE, TYPE_VIDEO, TYPE_AUDIO})
                    /* package */ @interface Type {}

            public static final int TYPE_NONE = 0;
            public static final int TYPE_IMAGE = 1;
            public static final int TYPE_VIDEO = 2;
            public static final int TYPE_AUDIO = 3;

            /** The base URI of the element's document. */
            public final @Nullable String baseUri;

            /** The absolute link URI (href) of the element. */
            public final @Nullable String linkUri;

            /** The title text of the element. */
            public final @Nullable String title;

            /** The alternative text (alt) for the element. */
            public final @Nullable String altText;

            /** The type of the element. One of the {@link WSession.ContentDelegate.ContextElement#TYPE_NONE} flags. */
            public final @Type int type;

            /** The source URI (src) of the element. Set for (nested) media elements. */
            public final @Nullable String srcUri;

            public ContextElement(
                    final @Nullable String baseUri,
                    final @Nullable String linkUri,
                    final @Nullable String title,
                    final @Nullable String altText,
                    final int type,
                    final @Nullable String srcUri) {
                this.baseUri = baseUri;
                this.linkUri = linkUri;
                this.title = title;
                this.altText = altText;
                this.type = type;
                this.srcUri = srcUri;
            }
        }

        /**
         * A user has initiated the context menu via long-press. This event is fired on links, (nested)
         * images and (nested) media elements.
         *
         * @param session The ISession that initiated the callback.
         * @param screenX The screen coordinates of the press.
         * @param screenY The screen coordinates of the press.
         * @param element The details for the pressed element.
         */
        @UiThread
        default void onContextMenu(
                @NonNull final WSession session,
                final int screenX,
                final int screenY,
                @NonNull final WSession.ContentDelegate.ContextElement element) {}

        /**
         * This is fired when there is a response that cannot be handled by the browser (e.g., a download).
         *
         * @param session the ISession that received the external response.
         * @param response the external WebResponse.
         */
        @UiThread
        default void onExternalResponse(
                @NonNull final WSession session, @NonNull final WWebResponse response) {}

        /**
         * The content process hosting this ISession has crashed. The ISession is now closed and
         * unusable. You may call {@link #open(WRuntime)} to recover the session, but no state is
         * preserved. Most applications will want to call {@link #load} or {@link
         * #restoreState(WSessionState)} at this point.
         *
         * @param session The ISession for which the content process has crashed.
         */
        @UiThread
        default void onCrash(@NonNull final WSession session) {}

        /**
         * The content process hosting this ISession has been killed. The ISession is now closed
         * and unusable. You may call {@link #open(WRuntime)} to recover the session, but no state
         * is preserved. Most applications will want to call {@link #load} or {@link
         * #restoreState(WSessionState)} at this point.
         *
         * @param session The ISession for which the content process has been killed.
         */
        @UiThread
        default void onKill(@NonNull final WSession session) {}

        /**
         * Notification that the first content composition has occurred. This callback is invoked for
         * the first content composite after either a start or a restart of the compositor.
         *
         * @param session The ISession that had a first paint event.
         */
        @UiThread
        default void onFirstComposite(@NonNull final WSession session) {}

        /**
         * Notification that the first content paint has occurred. This callback is invoked for the
         * first content paint after a page has been loaded, or after a {@link
         * #onPaintStatusReset(WSession)} event. The function {@link
         * #onFirstComposite(WSession)} will be called once the compositor has started rendering.
         * However, it is possible for the compositor to start rendering before there is any content to
         * render. onFirstContentfulPaint() is called once some content has been rendered. It may be
         * nothing more than the page background color. It is not an indication that the whole page has
         * been rendered.
         *
         * @param session The ISession that had a first paint event.
         */
        @UiThread
        default void onFirstContentfulPaint(@NonNull final WSession session) {}

        /**
         * Notification that the paint status has been reset.
         *
         * <p>This callback is invoked whenever the painted content is no longer being displayed. This
         * can occur in response to the session being paused. After this has fired the compositor may
         * continue rendering, but may not render the page content. This callback can therefore be used
         * in conjunction with {@link #onFirstContentfulPaint(WSession)} to determine when there is
         * valid content being rendered.
         *
         * @param session The ISession that had the paint status reset event.
         */
        @UiThread
        default void onPaintStatusReset(@NonNull final WSession session) {}

        /**
         * A page has requested to change pointer icon.
         *
         * <p>If the application wants to control pointer icon, it should override this, then handle it.
         *
         * @param session The ISession that initiated the callback.
         * @param icon The pointer icon sent from the content.
         */
        @TargetApi(Build.VERSION_CODES.N)
        @UiThread
        default void onPointerIconChange(
                @NonNull final WSession session, @NonNull final PointerIcon icon) {
        }

        /**
         * This is fired when the loaded document has a valid Web App Manifest present.
         *
         * <p>The various colors (theme_color, background_color, etc.) present in the manifest have been
         * transformed into #AARRGGBB format.
         *
         * @param session  The ISession that contains the Web App Manifest
         * @param manifest A parsed and validated {@link WebApp} representing the manifest contents.
         * @see <a href="https://www.w3.org/TR/appmanifest/">Web App Manifest specification</a>
         */
        @UiThread
        default void onWebAppManifest(
                @NonNull final WSession session, @NonNull final WebApp manifest) {
        }

        /**
         * A script has exceeded its execution timeout value
         *
         * @param aSession ISession that initiated the callback.
         * @param scriptFileName Filename of the slow script
         * @return A {@link WResult} with a SlowScriptResponse value which indicates whether to
         *     allow the Slow Script to continue processing. Stop will halt the slow script. Continue
         *     will pause notifications for a period of time before resuming.
         */
        @UiThread
        default @Nullable
        WResult<WSlowScriptResponse> onSlowScript(
                @NonNull final WSession aSession, @NonNull final String scriptFileName) {
            return null;
        }

        /**
         * The app should display its dynamic toolbar, fully expanded to the height that was previously.
         *
         * @param aSession ISession that initiated the callback.
         */
        @UiThread
        default void onShowDynamicToolbar(@NonNull final WSession aSession) {}

        public interface OnPaymentHandlerCallback {
            void onDismiss();
        }

        /**
         * The view should display its payment handler, overlayed on the active tab.
         *
         * @param aSession ISession that initiated the callback.
         * @param aDisplay IDisplay that initiated the callback.
         * @param callback Callback interface.
         */
        @UiThread
        default void onShowPaymentHandler(@NonNull final WSession aSession,
                @NonNull final WDisplay aDisplay,
                @NonNull final OnPaymentHandlerCallback callback) {}
        /**
         * The view should hide its payment handler.
         *
         * @param aSession ISession that initiated the callback.
         */
        @UiThread
        default void onHidePaymentHandler(@NonNull final WSession aSession) {}
    }

    interface NavigationDelegate {
        /**
         * A view has started loading content from the network.
         *
         * @param session The ISession that initiated the callback.
         * @param url The resource being loaded.
         */
        @UiThread
        default void onLocationChange(
                @NonNull final WSession session, @Nullable final String url) {}

        /**
         * The view's ability to go back has changed.
         *
         * @param session The ISession that initiated the callback.
         * @param canGoBack The new value for the ability.
         */
        @UiThread
        default void onCanGoBack(@NonNull final WSession session, final boolean canGoBack) {}

        /**
         * The view's ability to go forward has changed.
         *
         * @param session The ISession that initiated the callback.
         * @param canGoForward The new value for the ability.
         */
        @UiThread
        default void onCanGoForward(@NonNull final WSession session, final boolean canGoForward) {}

        int TARGET_WINDOW_NONE = 0;
        int TARGET_WINDOW_CURRENT = 1;
        int TARGET_WINDOW_NEW = 2;

        /** The load request was triggered by an HTTP redirect. */
        int LOAD_REQUEST_IS_REDIRECT = 0x800000;

        /** Load request details. */
        class LoadRequest {
            public LoadRequest(
                    @NonNull final String uri,
                    @Nullable final String triggerUri,
                    final int target,
                    final boolean isRedirect,
                    final boolean hasUserGesture,
                    final boolean isDirectNavigation) {
                this.uri = uri;
                this.triggerUri = triggerUri;
                this.target = target;
                this.isRedirect = isRedirect;
                this.hasUserGesture = hasUserGesture;
                this.isDirectNavigation = isDirectNavigation;
            }

            /** Empty constructor for tests. */
            protected LoadRequest() {
                uri = "";
                triggerUri = null;
                target = TARGET_WINDOW_NONE;
                isRedirect = false;
                hasUserGesture = false;
                isDirectNavigation = false;
            }

            /** The URI to be loaded. */
            public final @NonNull String uri;

            /**
             * The URI of the origin page that triggered the load request. null for initial loads and
             * loads originating from data: URIs.
             */
            public final @Nullable String triggerUri;

            /**
             * The target where the window has requested to open. One of {@link #TARGET_WINDOW_NONE
             * TARGET_WINDOW_*}.
             */
            public final @TargetWindow int target;

            /**
             * True if and only if the request was triggered by an HTTP redirect.
             *
             * <p>If the user loads URI "a", which redirects to URI "b", then <code>onLoadRequest</code>
             * will be called twice, first with uri "a" and <code>isRedirect = false</code>, then with uri
             * "b" and <code>isRedirect = true</code>.
             */
            public final boolean isRedirect;

            /** True if there was an active user gesture when the load was requested. */
            public final boolean hasUserGesture;

            /**
             * This load request was initiated by a direct navigation from the application. E.g. when
             * calling {@link WSession#load}.
             */
            public final boolean isDirectNavigation;

            @Override
            public String toString() {
                final StringBuilder out = new StringBuilder("LoadRequest { ");
                out.append("uri: " + uri)
                        .append(", triggerUri: " + triggerUri)
                        .append(", target: " + target)
                        .append(", isRedirect: " + isRedirect)
                        .append(", hasUserGesture: " + hasUserGesture)
                        .append(", fromLoadUri: " + hasUserGesture)
                        .append(" }");
                return out.toString();
            }
        }

        /**
         * A request to open an URI. This is called before each top-level page load to allow custom
         * behavior. For example, this can be used to override the behavior of TAGET_WINDOW_NEW
         * requests, which defaults to requesting a new ISession via onNewSession.
         *
         * @param session The ISession that initiated the callback.
         * @param request The {@link WSession.NavigationDelegate.LoadRequest} containing the request details.
         * @return A {@link WResult} with a {@link WAllowOrDeny} value which indicates whether or not
         *     the load was handled. If unhandled, the browser will continue the load as normal. If handled (a
         *     {@link WAllowOrDeny#DENY DENY} value), the browser will abandon the load. A null return value is
         *     interpreted as {@link WAllowOrDeny#ALLOW ALLOW} (unhandled).
         */
        @UiThread
        default @Nullable
        WResult<WAllowOrDeny> onLoadRequest(
                @NonNull final WSession session, @NonNull final WSession.NavigationDelegate.LoadRequest request) {
            return null;
        }

        /**
         * A request to load a URI in a non-top-level context.
         *
         * @param session The ISession that initiated the callback.
         * @param request The {@link WSession.NavigationDelegate.LoadRequest} containing the request details.
         * @return A {@link WResult} with a {@link WAllowOrDeny} value which indicates whether or not
         *     the load was handled. If unhandled, the browser will continue the load as normal. If handled (a
         *     {@link WAllowOrDeny#DENY DENY} value), the browser will abandon the load. A null return value is
         *     interpreted as {@link WAllowOrDeny#ALLOW ALLOW} (unhandled).
         */
        @UiThread
        default @Nullable
        WResult<WAllowOrDeny> onSubframeLoadRequest(
                @NonNull final WSession session, @NonNull final WSession.NavigationDelegate.LoadRequest request) {
            return null;
        }

        public interface OnNewSessionCallback {
            void onNewSession(WSession session);
        }
        /**
         * A request has been made to open a new session. The URI is provided only for informational
         * purposes. Do not call ISession.load here. Additionally, the returned ISession must be
         * a newly-created one.
         *
         * @param session The ISession that initiated the callback.
         * @param uri The URI to be loaded.
         * @param callback A callback with extra work that will be performed after creating the
         *                 session but before notifying the observers/clients.
         * @return A {@link WResult} which holds the returned ISession. May be null, in which
         *     case the request for a new window by web content will fail. e.g., <code>window.open()
         *     </code> will return null. The implementation of onNewSession is responsible for
         *     maintaining a reference to the returned object, to prevent it from being garbage
         *     collected.
         */
        @UiThread
        default @Nullable
        WResult<WSession> onNewSession(
                @NonNull final WSession session, @NonNull final String uri, OnNewSessionCallback callback) {
            return null;
        }

        /**
         * @param session The ISession that initiated the callback.
         * @param uri The URI that failed to load.
         * @param error A WebRequestError containing details about the error
         * @return A URI to display as an error. Returning null will halt the load entirely. The
         *     following special methods are made available to the URI: -
         *     document.addCertException(isTemporary), returns Promise -
         *     document.getFailedCertSecurityInfo(), returns FailedCertSecurityInfo -
         *     document.getNetErrorInfo(), returns NetErrorInfo - document.allowDeprecatedTls, a
         *     property indicating whether or not TLS 1.0/1.1 is allowed -
         *     document.reloadWithHttpsOnlyException()
         * @see <a
         *     href="https://searchfox.org/mozilla-central/source/dom/webidl/FailedCertSecurityInfo.webidl">FailedCertSecurityInfo
         *     IDL</a>
         * @see <a
         *     href="https://searchfox.org/mozilla-central/source/dom/webidl/NetErrorInfo.webidl">NetErrorInfo
         *     IDL</a>
         */
        @UiThread
        default @Nullable
        WResult<String> onLoadError(
                @NonNull final WSession session,
                @Nullable final String uri,
                @NonNull final WWebRequestError error) {
            return null;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            WSession.NavigationDelegate.TARGET_WINDOW_NONE,
            WSession.NavigationDelegate.TARGET_WINDOW_CURRENT,
            WSession.NavigationDelegate.TARGET_WINDOW_NEW
    })
            /* package */ @interface TargetWindow {}


    interface ProgressDelegate {
        /** Class representing security information for a site. */
        class SecurityInformation {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({SECURITY_MODE_UNKNOWN, SECURITY_MODE_IDENTIFIED, SECURITY_MODE_VERIFIED})
                    /* package */ @interface SecurityMode {}

            public static final int SECURITY_MODE_UNKNOWN = 0;
            public static final int SECURITY_MODE_IDENTIFIED = 1;
            public static final int SECURITY_MODE_VERIFIED = 2;

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({CONTENT_UNKNOWN, CONTENT_BLOCKED, CONTENT_LOADED})
                    /* package */ @interface ContentType {}

            public static final int CONTENT_UNKNOWN = 0;
            public static final int CONTENT_BLOCKED = 1;
            public static final int CONTENT_LOADED = 2;
            /** Indicates whether or not the site is secure. */
            public final boolean isSecure;
            /** Indicates whether or not the site is a security exception. */
            public final boolean isException;
            /** Contains the origin of the certificate. */
            public final @Nullable String origin;
            /** Contains the host associated with the certificate. */
            public final @NonNull String host;

            /** The server certificate in use, if any. */
            public final @Nullable
            X509Certificate certificate;

            /**
             * Indicates the security level of the site; possible values are SECURITY_MODE_UNKNOWN,
             * SECURITY_MODE_IDENTIFIED, and SECURITY_MODE_VERIFIED. SECURITY_MODE_IDENTIFIED indicates
             * domain validation only, while SECURITY_MODE_VERIFIED indicates extended validation.
             */
            public final @SecurityMode int securityMode;
            /**
             * Indicates the presence of passive mixed content; possible values are CONTENT_UNKNOWN,
             * CONTENT_BLOCKED, and CONTENT_LOADED.
             */
            public final @ContentType int mixedModePassive;
            /**
             * Indicates the presence of active mixed content; possible values are CONTENT_UNKNOWN,
             * CONTENT_BLOCKED, and CONTENT_LOADED.
             */
            public final @ContentType int mixedModeActive;

            public SecurityInformation(boolean isSecure, boolean isException, @Nullable String origin, @NonNull String host, @Nullable X509Certificate certificate, int securityMode, int mixedModePassive, int mixedModeActive) {
                this.isSecure = isSecure;
                this.isException = isException;
                this.origin = origin;
                this.host = host;
                this.certificate = certificate;
                this.securityMode = securityMode;
                this.mixedModePassive = mixedModePassive;
                this.mixedModeActive = mixedModeActive;
            }

            /** Empty constructor for tests */
            protected SecurityInformation() {
                mixedModePassive = CONTENT_UNKNOWN;
                mixedModeActive = CONTENT_UNKNOWN;
                securityMode = SECURITY_MODE_UNKNOWN;
                isSecure = false;
                isException = false;
                origin = "";
                host = "";
                certificate = null;
            }
        }

        /**
         * A View has started loading content from the network.
         *
         * @param session ISession that initiated the callback.
         * @param url The resource being loaded.
         */
        @UiThread
        default void onPageStart(@NonNull final WSession session, @NonNull final String url) {}

        /**
         * A View has finished loading content from the network.
         *
         * @param session ISession that initiated the callback.
         * @param success Whether the page loaded successfully or an error occurred.
         */
        @UiThread
        default void onPageStop(@NonNull final WSession session, final boolean success) {}

        /**
         * Page loading has progressed.
         *
         * @param session ISession that initiated the callback.
         * @param progress Current page load progress value [0, 100].
         */
        @UiThread
        default void onProgressChange(@NonNull final WSession session, final int progress) {}

        /**
         * The security status has been updated.
         *
         * @param session ISession that initiated the callback.
         * @param securityInfo The new security information.
         */
        @UiThread
        default void onSecurityChange(
                @NonNull final WSession session, @NonNull final WSession.ProgressDelegate.SecurityInformation securityInfo) {}

        /**
         * The browser session state has changed. This can happen in response to navigation, scrolling,
         * or form data changes; the session state passed includes the most up to date information on
         * all of these.
         *
         * @param session ISession that initiated the callback.
         * @param sessionState SessionState representing the latest browser state.
         */
        @UiThread
        default void onSessionStateChange(
                @NonNull final WSession session, @NonNull final WSessionState sessionState) {}
    }

    /** ISession applications implement this interface to handle content scroll events. */
    interface ScrollDelegate {
        /**
         * The scroll position of the content has changed.
         *
         * @param session ISession that initiated the callback.
         * @param scrollX The new horizontal scroll position in pixels.
         * @param scrollY The new vertical scroll position in pixels.
         */
        @UiThread
        default void onScrollChanged(@NonNull final WSession session, final int scrollX, final int scrollY) {}
    }

    interface HistoryDelegate {
        /** A representation of an entry in browser history. */
        interface HistoryItem {
            /**
             * Get the URI of this history element.
             *
             * @return A String representing the URI of this history element.
             */
            @AnyThread
            @NonNull String getUri();

            /**
             * Get the title of this history element.
             *
             * @return A String representing the title of this history element.
             */
            @AnyThread
            @NonNull String getTitle();
        }

        /**
         * A representation of browser history, accessible as a `List`. The list itself and its entries
         * are immutable; any attempt to mutate will result in an `UnsupportedOperationException`.
         */
        interface HistoryList {
            /**
             * Get the current index in browser history.
             *
             * @return An int representing the current index in browser history.
             */
            @AnyThread
            int getCurrentIndex();

            /**
             * Get the list of HistoryItem in browser history.
             *
             * @return A List of HistoryItems.
             */
            @AnyThread
            List<WSession.HistoryDelegate.HistoryItem> getItems();
        }

        /** The URL was visited a top-level window. */
        int VISIT_TOP_LEVEL = 1 << 0;
        /** The URL is the target of a temporary redirect. */
        int VISIT_REDIRECT_TEMPORARY = 1 << 1;
        /** The URL is the target of a permanent redirect. */
        int VISIT_REDIRECT_PERMANENT = 1 << 2;
        /** The URL is temporarily redirected to another URL. */
        int VISIT_REDIRECT_SOURCE = 1 << 3;
        /** The URL is permanently redirected to another URL. */
        int VISIT_REDIRECT_SOURCE_PERMANENT = 1 << 4;
        /** The URL failed to load due to a client or server error. */
        int VISIT_UNRECOVERABLE_ERROR = 1 << 5;

        /**
         * Records a visit to a page.
         *
         * @param session The session where the URL was visited.
         * @param url The visited URL.
         * @param lastVisitedURL The last visited URL in this session, to detect redirects and reloads.
         * @param flags Additional flags for this visit, including redirect and error statuses. This is
         *     a bitmask of one or more {@link #VISIT_TOP_LEVEL VISIT_*} flags, OR-ed together.
         * @return A {@link WResult} completed with a boolean indicating whether to highlight links
         *     for the new URL as visited ({@code true}) or unvisited ({@code false}).
         */
        @UiThread
        default @Nullable
        WResult<Boolean> onVisited(
                @NonNull final WSession session,
                @NonNull final String url,
                @Nullable final String lastVisitedURL,
                @VisitFlags final int flags) {
            return null;
        }

        /**
         * Returns the visited statuses for links on a page. This is used to highlight links as visited
         * or unvisited, for example.
         *
         * @param session The session requesting the visited statuses.
         * @param urls A list of URLs to check.
         * @return A {@link WResult} completed with a list of booleans corresponding to the URLs in
         *     {@code urls}, and indicating whether to highlight links for each URL as visited ({@code
         *     true}) or unvisited ({@code false}).
         */
        @UiThread
        default @Nullable
        WResult<boolean[]> getVisited(
                @NonNull final WSession session, @NonNull final String[] urls) {
            return null;
        }

        @UiThread
        @SuppressWarnings("checkstyle:javadocmethod")
        default void onHistoryStateChange(
                @NonNull final WSession session, @NonNull final WSession.HistoryDelegate.HistoryList historyList) {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {
                    WSession.HistoryDelegate.VISIT_TOP_LEVEL,
                    WSession.HistoryDelegate.VISIT_REDIRECT_TEMPORARY,
                    WSession.HistoryDelegate.VISIT_REDIRECT_PERMANENT,
                    WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE,
                    WSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT,
                    WSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
            })
            /* package */ @interface VisitFlags {}


    /**
     * ISession applications implement this interface to handle prompts triggered by content in
     * the ISession, such as alerts, authentication dialogs, and select list pickers.
     */
    interface PromptDelegate {
        /** PromptResponse is an opaque class created upon confirming or dismissing a prompt. */
        interface PromptResponse {
        }

        interface PromptInstanceDelegate {
            /**
             * Called when this prompt has been dismissed by the system.
             *
             * <p>This can happen e.g. when the page navigates away and the content of the prompt is not
             * relevant anymore.
             *
             * <p>When this method is called, you should hide the prompt UI elements.
             *
             * @param prompt the prompt that should be dismissed.
             */
            @UiThread
            default void onPromptDismiss(final @NonNull WSession.PromptDelegate.BasePrompt prompt) {}
        }

        // Prompts

        interface BasePrompt {
            /** The title of this prompt; may be null. */
            @Nullable String title();

            /**
             * This dismisses the prompt without sending any meaningful information back to content.
             *
             * @return A {@link PromptResponse} with which you can complete the {@link WResult} that
             *     corresponds to this prompt.
             */
            @UiThread
            public @NonNull
            PromptResponse dismiss();

            /**
             * Set the delegate for this prompt.
             *
             * @param delegate the {@link PromptInstanceDelegate} instance.
             */
            @UiThread
            void setDelegate(final @Nullable PromptInstanceDelegate delegate);

            /**
             * Get the delegate for this prompt.
             *
             * @return the {@link WSession.PromptDelegate.PromptInstanceDelegate} instance.
             */
            @UiThread
            @Nullable
            WSession.PromptDelegate.PromptInstanceDelegate getDelegate();
            
            /**
             * This returns true if the prompt has already been confirmed or dismissed.
             *
             * @return A boolean which is true if the prompt has been confirmed or dismissed, and false
             *     otherwise.
             */
            @UiThread
            boolean isComplete();
        }


        /**
         * BeforeUnloadPrompt represents the onbeforeunload prompt. See
         * https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onbeforeunload
         */
        interface BeforeUnloadPrompt extends WSession.PromptDelegate.BasePrompt {
            /**
             * Confirms the prompt.
             *
             * @param allowOrDeny whether the navigation should be allowed to continue or not.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(final @Nullable WAllowOrDeny allowOrDeny);
        }

        /**
         * RepostConfirmPrompt represents a prompt shown whenever the browser needs to resubmit POST
         * data (e.g. due to page refresh).
         */
        interface RepostConfirmPrompt extends WSession.PromptDelegate.BasePrompt {
            /**
             * Confirms the prompt.
             *
             * @param allowOrDeny whether the browser should allow resubmitting data.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(final @Nullable WAllowOrDeny allowOrDeny);
        }

        /**
         * AlertPrompt contains the information necessary to represent a JavaScript alert() call from
         * content; it can only be dismissed, not confirmed.
         */
        interface AlertPrompt extends WSession.PromptDelegate.BasePrompt {
            /** The message to be displayed with this alert; may be null. */
            @Nullable String message();
        }

        /**
         * ButtonPrompt contains the information necessary to represent a JavaScript confirm() call from
         * content.
         */
        interface ButtonPrompt extends WSession.PromptDelegate.BasePrompt {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({WSession.PromptDelegate.ButtonPrompt.Type.POSITIVE, WSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE})
                    /* package */ @interface ButtonType {}

            class Type {
                /** Index of positive response button (eg, "Yes", "OK") */
                public static final int POSITIVE = 0;

                /** Index of negative response button (eg, "No", "Cancel") */
                public static final int NEGATIVE = 2;

                protected Type() {}
            }

            /** The message to be displayed with this prompt; may be null. */
            @Nullable String message();

            /**
             * Confirms this prompt, returning the selected button to content.
             *
             * @param selection An int representing the selected button, must be one of {@link WSession.PromptDelegate.ButtonPrompt.Type}.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@ButtonType final int selection);
        }

        /**
         * TextPrompt contains the information necessary to represent a Javascript prompt() call from
         * content.
         */
        interface TextPrompt extends WSession.PromptDelegate.BasePrompt {
            /** The message to be displayed with this prompt; may be null. */
            @Nullable String message();

            /** The default value for the text field; may be null. */
            @Nullable String defaultValue();

            /**
             * Confirms this prompt, returning the input text to content.
             *
             * @param text A String containing the text input given by the user.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String text);
        }

        /**
         * AuthPrompt contains the information necessary to represent an HTML authorization prompt
         * generated by content.
         */
        interface AuthPrompt extends WSession.PromptDelegate.BasePrompt {
            class AuthOptions {
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        flag = true,
                        value = {
                                WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.HOST,
                                WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PROXY,
                                WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD,
                                WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED,
                                WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.CROSS_ORIGIN_SUB_RESOURCE
                        })
                        /* package */ @interface AuthFlag {}

                /** Auth prompt flags. */
                public static class Flags {
                    /** The auth prompt is for a network host. */
                    public static final int HOST = 1;
                    /** The auth prompt is for a proxy. */
                    public static final int PROXY = 2;
                    /** The auth prompt should only request a password. */
                    public static final int ONLY_PASSWORD = 8;
                    /** The auth prompt is the result of a previous failed login. */
                    public static final int PREVIOUS_FAILED = 16;
                    /** The auth prompt is for a cross-origin sub-resource. */
                    public static final int CROSS_ORIGIN_SUB_RESOURCE = 32;

                    protected Flags() {}
                }

                @Retention(RetentionPolicy.SOURCE)
                @IntDef({WSession.PromptDelegate.AuthPrompt.AuthOptions.Level.NONE, WSession.PromptDelegate.AuthPrompt.AuthOptions.Level.PW_ENCRYPTED, WSession.PromptDelegate.AuthPrompt.AuthOptions.Level.SECURE})
                        /* package */ @interface AuthLevel {}

                /** Auth prompt levels. */
                public static class Level {
                    /** The auth request is unencrypted or the encryption status is unknown. */
                    public static final int NONE = 0;
                    /** The auth request only encrypts password but not data. */
                    public static final int PW_ENCRYPTED = 1;
                    /** The auth request encrypts both password and data. */
                    public static final int SECURE = 2;

                    protected Level() {}
                }

                /** An int bit-field of {@link WSession.PromptDelegate.AuthPrompt.AuthOptions.Flags}. */
                public @AuthFlag final int flags;

                /** A string containing the URI for the auth request or null if unknown. */
                public @Nullable final String uri;

                /** An int, one of {@link WSession.PromptDelegate.AuthPrompt.AuthOptions.Level}, indicating level of encryption. */
                public @AuthLevel final int level;

                /** A string containing the initial username or null if password-only. */
                public @Nullable final String username;

                /** A string containing the initial password. */
                public @Nullable final String password;

                public AuthOptions(int flags, @Nullable String uri, int level, @Nullable String username, @Nullable String password) {
                    this.flags = flags;
                    this.uri = uri;
                    this.level = level;
                    this.username = username;
                    this.password = password;
                }

                /** Empty constructor for tests */
                protected AuthOptions() {
                    flags = 0;
                    uri = "";
                    level = Level.NONE;
                    username = "";
                    password = "";
                }
            }

            /** The message to be displayed with this prompt; may be null. */
            @Nullable String message();

            /** The {@link WSession.PromptDelegate.AuthPrompt.AuthOptions} that describe the type of authorization prompt. */
            @NonNull
            WSession.PromptDelegate.AuthPrompt.AuthOptions authOptions();

            /**
             * Confirms this prompt with just a password, returning the password to content.
             *
             * @param password A String containing the password input by the user.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String password);

            /**
             * Confirms this prompt with a username and password, returning both to content.
             *
             * @param username A String containing the username input by the user.
             * @param password A String containing the password input by the user.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String username, @NonNull final String password);
        }

        /**
         * ChoicePrompt contains the information necessary to display a menu or list prompt generated by
         * content.
         */
        interface ChoicePrompt extends WSession.PromptDelegate.BasePrompt {
            interface Choice {
                /**
                 * A boolean indicating if the item is disabled. Item should not be selectable if this is
                 * true.
                 */
                boolean disabled();

                /**
                 * A String giving the URI of the item icon, or null if none exists (only valid for menus)
                 */
                @Nullable String icon();

                /** A String giving the ID of the item or group */
                @NonNull String id();

                /** A Choice array of sub-items in a group, or null if not a group */
                @Nullable WSession.PromptDelegate.ChoicePrompt.Choice[] items();

                /** A string giving the label for displaying the item or group */
                @NonNull String label();

                /** A boolean indicating if the item should be pre-selected (pre-checked for menu items) */
                boolean selected();

                /** A boolean indicating if the item should be a menu separator (only valid for menus) */
                boolean separator();
            }

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({WSession.PromptDelegate.ChoicePrompt.Type.MENU, WSession.PromptDelegate.ChoicePrompt.Type.SINGLE, WSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE})
                    /* package */ @interface ChoiceType {}

            class Type {
                /** Display choices in a menu that dismisses as soon as an item is chosen. */
                public static final int MENU = 1;

                /** Display choices in a list that allows a single selection. */
                public static final int SINGLE = 2;

                /** Display choices in a list that allows multiple selections. */
                public static final int MULTIPLE = 3;

                protected Type() {}
            }

            /** The message to be displayed with this prompt; may be null. */
            @Nullable String message();

            /** One of {@link WSession.PromptDelegate.ChoicePrompt.Type}. */
            @ChoiceType int type();

            /** An array of {@link WSession.PromptDelegate.ChoicePrompt.Choice} representing possible choices. */
             @NonNull WSession.PromptDelegate.ChoicePrompt.Choice[] choices();

            /**
             * Confirms this prompt with the string id of a single choice.
             *
             * @param selectedId The string ID of the selected choice.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String selectedId);

            /**
             * Confirms this prompt with the string ids of multiple choices
             *
             * @param selectedIds The string IDs of the selected choices.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String[] selectedIds);

            /**
             * Confirms this prompt with a single choice.
             *
             * @param selectedChoice The selected choice.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final WSession.PromptDelegate.ChoicePrompt.Choice selectedChoice);

            /**
             * Confirms this prompt with multiple choices.
             *
             * @param selectedChoices The selected choices.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final WSession.PromptDelegate.ChoicePrompt.Choice[] selectedChoices);
        }

        /**
         * ColorPrompt contains the information necessary to represent a prompt for color input
         * generated by content.
         */
        interface ColorPrompt extends WSession.PromptDelegate.BasePrompt {
            /** The default value supplied by content. */
            @Nullable String defaultValue();

            /**
             * Confirms the prompt and passes the color value back to content.
             *
             * @param color A String representing the color to be returned to content.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String color);
        }

        /**
         * DateTimePrompt contains the information necessary to represent a prompt for date and/or time
         * input generated by content.
         */
        interface DateTimePrompt extends WSession.PromptDelegate.BasePrompt {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({WSession.PromptDelegate.DateTimePrompt.Type.DATE, WSession.PromptDelegate.DateTimePrompt.Type.MONTH, WSession.PromptDelegate.DateTimePrompt.Type.WEEK, WSession.PromptDelegate.DateTimePrompt.Type.TIME, WSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL})
                    /* package */ @interface DatetimeType {}

            class Type {
                /** Prompt for year, month, and day. */
                public static final int DATE = 1;

                /** Prompt for year and month. */
                public static final int MONTH = 2;

                /** Prompt for year and week. */
                public static final int WEEK = 3;

                /** Prompt for hour and minute. */
                public static final int TIME = 4;

                /** Prompt for year, month, day, hour, and minute, without timezone. */
                public static final int DATETIME_LOCAL = 5;

                protected Type() {}
            }

            /** One of {@link WSession.PromptDelegate.DateTimePrompt.Type} indicating the type of prompt. */
            @DatetimeType int type();

            /** A String representing the default value supplied by content. */
            @Nullable String defaultValue();

            /** A String representing the minimum value allowed by content. */
            @Nullable String minValue();

            /** A String representing the maximum value allowed by content. */
            @Nullable String maxValue();

            /**
             * Confirms the prompt and passes the date and/or time value back to content.
             *
             * @param datetime A String representing the date and time to be returned to content.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@NonNull final String datetime);
        }

        /**
         * FilePrompt contains the information necessary to represent a prompt for a file or files
         * generated by content.
         */
        interface FilePrompt extends WSession.PromptDelegate.BasePrompt {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({FilePrompt.Type.SINGLE, FilePrompt.Type.MULTIPLE})
                    /* package */ @interface FileType {}

            /** Types of file prompts. */
            class Type {
                /** Prompt for a single file. */
                public static final int SINGLE = 1;

                /** Prompt for multiple files. */
                public static final int MULTIPLE = 2;

                protected Type() {}
            }

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({FilePrompt.Capture.NONE, FilePrompt.Capture.ANY, FilePrompt.Capture.USER, FilePrompt.Capture.ENVIRONMENT})
                    /* package */ @interface CaptureType {}

            /** Possible capture attribute values. */
            class Capture {
                // These values should match the corresponding values in nsIFilePicker.idl
                /** No capture attribute has been supplied by content. */
                public static final int NONE = 0;

                /** The capture attribute was supplied with a missing or invalid value. */
                public static final int ANY = 1;

                /** The "user" capture attribute has been supplied by content. */
                public static final int USER = 2;

                /** The "environment" capture attribute has been supplied by content. */
                public static final int ENVIRONMENT = 3;

                protected Capture() {}
            }

            /** One of {@link FilePrompt.Type} indicating the prompt type. */
            @FileType int type();

            /**
             * An array of Strings giving the MIME types specified by the "accept" attribute, if any are
             * specified.
             */
            @Nullable String[] mimeTypes();

            /** One of {@link FilePrompt.Capture} indicating the capture attribute supplied by content. */
            @CaptureType int captureType();

            /**
             * Confirms the prompt and passes the file URI back to content.
             *
             * @param context An Application context for parsing URIs.
             * @param uri The URI of the file chosen by the user.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            PromptResponse confirm(@NonNull final Context context, @NonNull final Uri uri);

            /**
             * Confirms the prompt and passes the file URIs back to content.
             *
             * @param context An Application context for parsing URIs.
             * @param uris The URIs of the files chosen by the user.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            PromptResponse confirm(@NonNull final Context context, @NonNull final Uri[] uris);
        }

        /** PopupPrompt contains the information necessary to represent a popup blocking request. */
        interface PopupPrompt extends WSession.PromptDelegate.BasePrompt {
            /** The target URI for the popup; may be null. */
            @Nullable String targetUri();

            /**
             * Confirms the prompt and either allows or blocks the popup.
             *
             * @param response An {@link com.igalia.wolvic.browser.api.AllowOrDeny} specifying whether to allow or deny the popup.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            public @NonNull
            PromptResponse confirm(@NonNull final WAllowOrDeny response);
        }

        /** SharePrompt contains the information necessary to represent a (v1) WebShare request. */
        interface SharePrompt extends WSession.PromptDelegate.BasePrompt {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({WSession.PromptDelegate.SharePrompt.Result.SUCCESS, WSession.PromptDelegate.SharePrompt.Result.FAILURE, WSession.PromptDelegate.SharePrompt.Result.ABORT})
                    /* package */ @interface ShareResult {}

            /** Possible results to a {@link WSession.PromptDelegate.SharePrompt}. */
            class Result {
                /** The user shared with another app successfully. */
                public static final int SUCCESS = 0;

                /** The user attempted to share with another app, but it failed. */
                public static final int FAILURE = 1;

                /** The user aborted the share. */
                public static final int ABORT = 2;

                protected Result() {}
            }

            /** The text for the share request. */
            @Nullable String text();

            /** The uri for the share request. */
            @Nullable String uri();

            /**
             * Confirms the prompt and either blocks or allows the share request.
             *
             * @param response One of {@link WSession.PromptDelegate.SharePrompt.Result} specifying the outcome of the share attempt.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(@ShareResult final int response);

            /**
             * Dismisses the prompt and returns {@link WSession.PromptDelegate.SharePrompt.Result#ABORT} to web content.
             *
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse dismiss();
        }

        /** Request containing information required to resolve Autocomplete prompt requests. */
        interface AutocompleteRequest<T extends WAutocomplete.Option<?>> extends WSession.PromptDelegate.BasePrompt {
            /**
             * The Autocomplete options for this request. This can contain a single or multiple entries.
             */
            @NonNull T[] options();

            /**
             * Confirm the request by responding with a selection. See the PromptDelegate callbacks for
             * specifics.
             *
             * @param selection The {@link WAutocomplete.Option} used to confirm the request.
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse confirm(final @NonNull WAutocomplete.Option<?> selection);

            /**
             * Dismiss the request. See the PromptDelegate callbacks for specifics.
             *
             * @return A {@link WSession.PromptDelegate.PromptResponse} which can be used to complete the {@link WResult}
             *     associated with this prompt.
             */
            @UiThread
            @NonNull
            WSession.PromptDelegate.PromptResponse dismiss();
        }

        // Delegate functions.
        /**
         * Display an alert prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.AlertPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onAlertPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.AlertPrompt prompt) {
            return null;
        }

        /**
         * Display a onbeforeunload prompt.
         *
         * <p>See https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onbeforeunload
         * See {@link WSession.PromptDelegate.BeforeUnloadPrompt}
         *
         * @param session ISession that triggered the prompt
         * @param prompt the {@link WSession.PromptDelegate.BeforeUnloadPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to {@link com.igalia.wolvic.browser.api.AllowOrDeny#ALLOW} if the page is allowed
         *     to continue with the navigation or {@link com.igalia.wolvic.browser.api.AllowOrDeny#DENY} otherwise.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onBeforeUnloadPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.BeforeUnloadPrompt prompt) {
            return null;
        }

        /**
         * Display a POST resubmission confirmation prompt.
         *
         * <p>This prompt will trigger whenever refreshing or navigating to a page needs resubmitting
         * POST data that has been submitted already.
         *
         * @param session ISession that triggered the prompt
         * @param prompt the {@link WSession.PromptDelegate.RepostConfirmPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to {@link com.igalia.wolvic.browser.api.AllowOrDeny#ALLOW} if the page is allowed
         *     to continue with the navigation and resubmit the POST data or {@link org.mozilla.Browser.AllowOrDeny#DENY}
         *     otherwise.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onRepostConfirmPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.RepostConfirmPrompt prompt) {
            return null;
        }

        /**
         * Display a button prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.ButtonPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onButtonPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.ButtonPrompt prompt) {
            return null;
        }

        /**
         * Display a text prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.TextPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onTextPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.TextPrompt prompt) {
            return null;
        }

        /**
         * Display an authorization prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.AuthPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onAuthPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.AuthPrompt prompt) {
            return null;
        }

        /**
         * Display a list/menu prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.ChoicePrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onChoicePrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.ChoicePrompt prompt) {
            return null;
        }

        /**
         * Display a color prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.ColorPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onColorPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.ColorPrompt prompt) {
            return null;
        }

        /**
         * Display a date/time prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.DateTimePrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onDateTimePrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.DateTimePrompt prompt) {
            return null;
        }

        /**
         * Display a file prompt.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.FilePrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onFilePrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.FilePrompt prompt) {
            return null;
        }

        /**
         * Display a popup request prompt; this occurs when content attempts to open a new window in a
         * way that doesn't appear to be the result of user input.
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.PopupPrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onPopupPrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.PopupPrompt prompt) {
            return null;
        }

        /**
         * Display a share request prompt; this occurs when content attempts to use the WebShare API.
         * See: https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share
         *
         * @param session ISession that triggered the prompt.
         * @param prompt The {@link WSession.PromptDelegate.SharePrompt} that describes the prompt.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse} which includes all
         *     necessary information to resolve the prompt.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onSharePrompt(
                @NonNull final WSession session, @NonNull final WSession.PromptDelegate.SharePrompt prompt) {
            return null;
        }

        /**
         * Handle a login save prompt request. This is triggered by the user entering new or modified
         * login credentials into a login form.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}.
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to trigger a {@link
         *     WAutocomplete.StorageDelegate#onLoginSave} request to save the given selection. The
         *     confirmed selection may be an entry out of the request's options, a modified option, or a
         *     freshly created login entry.
         *     <p>Dismiss the request to deny the saving request.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onLoginSave(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSaveOption> request) {
            return null;
        }

        /**
         * Handle a address save prompt request. This is triggered by the user entering new or modified
         * address credentials into a address form.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}.
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to trigger a {@link
         *     WAutocomplete.StorageDelegate#onAddressSave} request to save the given selection. The
         *     confirmed selection may be an entry out of the request's options, a modified option, or a
         *     freshly created address entry.
         *     <p>Dismiss the request to deny the saving request.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onAddressSave(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.AddressSaveOption> request) {
            return null;
        }

        /**
         * Handle a credit card save prompt request. This is triggered by the user entering new or
         * modified credit card credentials into a form.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}.
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to trigger a {@link
         *     WAutocomplete.StorageDelegate#onCreditCardSave} request to save the given selection. The
         *     confirmed selection may be an entry out of the request's options, a modified option, or a
         *     freshly created credit card entry.
         *     <p>Dismiss the request to deny the saving request.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onCreditCardSave(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.CreditCardSaveOption> request) {
            return null;
        }

        /**
         * Handle a login selection prompt request. This is triggered by the user focusing on a login
         * username field.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to let Browser fill out the
         *     login forms with the given selection details. The confirmed selection may be an entry out
         *     of the request's options, a modified option, or a freshly created login entry.
         *     <p>Dismiss the request to deny autocompletion for the detected form.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onLoginSelect(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.LoginSelectOption> request) {
            return null;
        }

        /**
         * Handle a credit card selection prompt request. This is triggered by the user focusing on a
         * credit card input field.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to let Browser fill out the
         *     credit card forms with the given selection details. The confirmed selection may be an
         *     entry out of the request's options, a modified option, or a freshly created credit card
         *     entry.
         *     <p>Dismiss the request to deny autocompletion for the detected form.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onCreditCardSelect(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.CreditCardSelectOption> request) {
            return null;
        }

        /**
         * Handle a address selection prompt request. This is triggered by the user focusing on a
         * address field.
         *
         * @param session The {@link WSession} that triggered the request.
         * @param request The {@link WSession.PromptDelegate.AutocompleteRequest} containing the request details.
         * @return A {@link WResult} resolving to a {@link WSession.PromptDelegate.PromptResponse}
         *     <p>Confirm the request with an {@link WAutocomplete.Option} to let Browser fill out the
         *     address forms with the given selection details. The confirmed selection may be an entry
         *     out of the request's options, a modified option, or a freshly created address entry.
         *     <p>Dismiss the request to deny autocompletion for the detected form.
         */
        @UiThread
        default @Nullable
        WResult<PromptResponse> onAddressSelect(
                @NonNull final WSession session,
                @NonNull final WSession.PromptDelegate.AutocompleteRequest<WAutocomplete.AddressSelectOption> request) {
            return null;
        }
    }

    /**
     * Interface that SessionTextInput uses for performing operations such as opening and closing the
     * software keyboard. If the delegate is not set, these operations are forwarded to the system
     * {@link android.view.inputmethod.InputMethodManager} automatically.
     */
    interface TextInputDelegate {
        /** Restarting input due to an input field gaining focus. */
        int RESTART_REASON_FOCUS = 0;
        /** Restarting input due to an input field losing focus. */
        int RESTART_REASON_BLUR = 1;
        /**
         * Restarting input due to the content of the input field changing. For example, the input field
         * type may have changed, or the current composition may have been committed outside of the
         * input method.
         */
        int RESTART_REASON_CONTENT_CHANGE = 2;

        /**
         * Reset the input method, and discard any existing states such as the current composition or
         * current autocompletion. Because the current focused editor may have changed, as part of the
         * reset, a custom input method would normally call {@link
         * SessionTextInput#onCreateInputConnection} to update its knowledge of the focused editor. Note
         * that {@code restartInput} should be used to detect changes in focus, rather than {@link
         * #showSoftInput} or {@link #hideSoftInput}, because focus changes are not always accompanied
         * by requests to show or hide the soft input. This method is always called, even in viewless
         * mode.
         *
         * @param session Session instance.
         * @param reason Reason for the reset.
         */
        @UiThread
        default void restartInput(
                @NonNull final WSession session, @RestartReason final int reason) {}

        /**
         * Display the soft input. May be called consecutively, even if the soft input is already shown.
         * This method is always called, even in viewless mode.
         *
         * @param session Session instance.
         * @see #hideSoftInput
         */
        @UiThread
        default void showSoftInput(@NonNull final WSession session) {}

        /**
         * Hide the soft input. May be called consecutively, even if the soft input is already hidden.
         * This method is always called, even in viewless mode.
         *
         * @param session Session instance.
         * @see #showSoftInput
         */
        @UiThread
        default void hideSoftInput(@NonNull final WSession session) {}

        /**
         * Update the soft input on the current selection. This method is <i>not</i> called in viewless
         * mode.
         *
         * @param session Session instance.
         * @param selStart Start offset of the selection.
         * @param selEnd End offset of the selection.
         * @param compositionStart Composition start offset, or -1 if there is no composition.
         * @param compositionEnd Composition end offset, or -1 if there is no composition.
         */
        @UiThread
        default void updateSelection(
                @NonNull final WSession session,
                final int selStart,
                final int selEnd,
                final int compositionStart,
                final int compositionEnd) {}

        /**
         * Update the soft input on the current extracted text, as requested through {@link
         * android.view.inputmethod.InputConnection#getExtractedText}. Consequently, this method is
         * <i>not</i> called in viewless mode.
         *
         * @param session Session instance.
         * @param request The extract text request.
         * @param text The extracted text.
         */
        @UiThread
        default void updateExtractedText(
                @NonNull final WSession session,
                @NonNull final ExtractedTextRequest request,
                @NonNull final ExtractedText text) {}

        /**
         * Update the cursor-anchor information as requested through {@link
         * android.view.inputmethod.InputConnection#requestCursorUpdates}. Consequently, this method is
         * <i>not</i> called in viewless mode.
         *
         * @param session Session instance.
         * @param info Cursor-anchor information.
         */
        @UiThread
        default void updateCursorAnchorInfo(
                @NonNull final WSession session, @NonNull final CursorAnchorInfo info) {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            WSession.TextInputDelegate.RESTART_REASON_FOCUS,
            WSession.TextInputDelegate.RESTART_REASON_BLUR,
            WSession.TextInputDelegate.RESTART_REASON_CONTENT_CHANGE
    })
            /* package */ @interface RestartReason {}



    /**
     * ISession applications implement this interface to handle requests for permissions from
     * content, such as geolocation and notifications. For each permission, usually two requests are
     * generated: one request for the Android app permission through requestAppPermissions, which is
     * typically handled by a system permission dialog; and another request for the content permission
     * (e.g. through requestContentPermission), which is typically handled by an app-specific
     * permission dialog.
     *
     * <p>When denying an Android app permission, the response is not stored by Browser. It is the
     * responsibility of the consumer to store the response state and therefore prevent further
     * requests from being presented to the user.
     */
    interface PermissionDelegate {
        /**
         * Permission for using the geolocation API. See:
         * https://developer.mozilla.org/en-US/docs/Web/API/Geolocation
         */
        int PERMISSION_GEOLOCATION = 0;

        /**
         * Permission for using the notifications API. See:
         * https://developer.mozilla.org/en-US/docs/Web/API/notification
         */
        int PERMISSION_DESKTOP_NOTIFICATION = 1;

        /**
         * Permission for using the storage API. See:
         * https://developer.mozilla.org/en-US/docs/Web/API/Storage_API
         */
        int PERMISSION_PERSISTENT_STORAGE = 2;

        /** Permission for using the WebXR API. See: https://www.w3.org/TR/webxr */
        int PERMISSION_XR = 3;

        /** Permission for allowing autoplay of inaudible (silent) video. */
        int PERMISSION_AUTOPLAY_INAUDIBLE = 4;

        /** Permission for allowing autoplay of audible video. */
        int PERMISSION_AUTOPLAY_AUDIBLE = 5;

        /** Permission for accessing system media keys used to decode DRM media. */
        int PERMISSION_MEDIA_KEY_SYSTEM_ACCESS = 6;

        /**
         * Permission for trackers to operate on the page -- disables all tracking protection features
         * for a given site.
         */
        int PERMISSION_TRACKING = 7;

        /**
         * Permission for third party frames to access first party cookies and storage. May be granted
         * heuristically in some cases.
         */
        int PERMISSION_STORAGE_ACCESS = 8;

        /**
         * Represents a content permission -- including the type of permission, the present value of the
         * permission, the URL the permission pertains to, and other information.
         */
        class ContentPermission {
            public ContentPermission(@NonNull String uri, @Nullable String thirdPartyOrigin, boolean privateMode, @Permission  int permission, @Value int value, @Nullable String contextId) {
                this.uri = uri;
                this.thirdPartyOrigin = thirdPartyOrigin;
                this.privateMode = privateMode;
                this.permission = permission;
                this.value = value;
                this.contextId = contextId;
            }

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({VALUE_PROMPT, VALUE_DENY, VALUE_ALLOW})
                    public @interface Value {}

            /** The corresponding permission is currently set to default/prompt behavior. */
            public static final int VALUE_PROMPT = 3;

            /** The corresponding permission is currently set to deny. */
            public static final int VALUE_DENY = 2;

            /** The corresponding permission is currently set to allow. */
            public static final int VALUE_ALLOW = 1;

            /** The URI associated with this content permission. */
            public final @NonNull String uri;

            /**
             * The third party origin associated with the request; currently only used for storage access
             * permission.
             */
            public final @Nullable String thirdPartyOrigin;

            /**
             * A boolean indicating whether this content permission is associated with private browsing.
             */
            public final boolean privateMode;

            /** The type of this permission; one of {@link #PERMISSION_GEOLOCATION PERMISSION_*}. */
            public final @Permission int permission;

            /** The value of the permission; one of {@link #VALUE_PROMPT VALUE_}. */
            public final @Value int value;

            /**
             * The context ID associated with the permission if any.
             *
             * @see WSessionSettings.Builder#contextId
             */
            public final @Nullable String contextId;
        }

        /** Callback interface for notifying the result of a permission request. */
        interface Callback {
            /**
             * Called by the implementation after permissions are granted; the implementation must call
             * either grant() or reject() for every request.
             */
            @UiThread
            default void grant() {}

            /**
             * Called by the implementation when permissions are not granted; the implementation must call
             * either grant() or reject() for every request.
             */
            @UiThread
            default void reject() {}
        }

        /**
         * Request Android app permissions.
         *
         * @param session ISession instance requesting the permissions.
         * @param permissions List of permissions to request; possible values are,
         *     android.Manifest.permission.ACCESS_COARSE_LOCATION
         *     android.Manifest.permission.ACCESS_FINE_LOCATION android.Manifest.permission.CAMERA
         *     android.Manifest.permission.RECORD_AUDIO
         * @param callback Callback interface.
         */
        @UiThread
        default void onAndroidPermissionsRequest(
                @NonNull final WSession session,
                @Nullable final String[] permissions,
                @NonNull final WSession.PermissionDelegate.Callback callback) {
            callback.reject();
        }

        /**
         * Request content permission.
         *
         * <p>Note, that in the case of PERMISSION_PERSISTENT_STORAGE, once permission has been granted
         * for a site, it cannot be revoked. If the permission has previously been granted, it is the
         * responsibility of the consuming app to remember the permission and prevent the prompt from
         * being redisplayed to the user.
         *
         * @param session ISession instance requesting the permission.
         * @param perm An {@link WSession.PermissionDelegate.ContentPermission} describing the permission being requested and its
         *     current status.
         * @return A {@link WResult} resolving to one of {@link WSession.PermissionDelegate.ContentPermission#VALUE_PROMPT
         *     VALUE_*}, determining the response to the permission request and updating the permissions
         *     for this site.
         */
        @UiThread
        default @Nullable
        WResult<Integer> onContentPermissionRequest(
                @NonNull final WSession session, @NonNull WSession.PermissionDelegate.ContentPermission perm) {
            return WResult.fromValue(WSession.PermissionDelegate.ContentPermission.VALUE_PROMPT);
        }

        class MediaSource {
            @Retention(RetentionPolicy.SOURCE)
            @IntDef({
                    SOURCE_CAMERA, SOURCE_SCREEN,
                    SOURCE_MICROPHONE, SOURCE_AUDIOCAPTURE,
                    SOURCE_OTHER
            })
                    /* package */ @interface Source {}

            /** Constant to indicate that camera will be recorded. */
            public static final int SOURCE_CAMERA = 0;

            /** Constant to indicate that screen will be recorded. */
            public static final int SOURCE_SCREEN = 1;

            /** Constant to indicate that microphone will be recorded. */
            public static final int SOURCE_MICROPHONE = 2;

            /** Constant to indicate that device audio playback will be recorded. */
            public static final int SOURCE_AUDIOCAPTURE = 3;

            /** Constant to indicate a media source that does not fall under the other categories. */
            public static final int SOURCE_OTHER = 4;

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({TYPE_VIDEO, TYPE_AUDIO})
                    /* package */ @interface Type {}

            /** The media type is video. */
            public static final int TYPE_VIDEO = 0;

            /** The media type is audio. */
            public static final int TYPE_AUDIO = 1;

            /** A string giving the origin-specific source identifier. */
            public final @NonNull String id;

            /**
             * A string giving the name of the video source from the system (for example, "Camera 0,
             * Facing back, Orientation 90"). May be empty.
             */
            public final @Nullable String name;

            /**
             * An int indicating the media source type. Possible values for a video source are:
             * SOURCE_CAMERA, SOURCE_SCREEN, and SOURCE_OTHER. Possible values for an audio source are:
             * SOURCE_MICROPHONE, SOURCE_AUDIOCAPTURE, and SOURCE_OTHER.
             */
            public final @Source int source;

            /** An int giving the type of media, must be either TYPE_VIDEO or TYPE_AUDIO. */
            public final @Type int type;

            public MediaSource(@NonNull String id, @Nullable String name, @Source int source, @Type int type) {
                this.id = id;
                this.name = name;
                this.source = source;
                this.type = type;
            }

            /** Empty constructor for tests. */
            protected MediaSource() {
                id = null;
                name = null;
                source = SOURCE_CAMERA;
                type = TYPE_VIDEO;
            }
        }

        /**
         * Callback interface for notifying the result of a media permission request, including which
         * media source(s) to use.
         */
        interface MediaCallback {
            /**
             * Called by the implementation after permissions are granted; the implementation must call
             * one of grant() or reject() for every request.
             *
             * @param video "id" value from the bundle for the video source to use, or null when video is
             *     not requested.
             * @param audio "id" value from the bundle for the audio source to use, or null when audio is
             *     not requested.
             */
            @UiThread
            default void grant(final @Nullable String video, final @Nullable String audio) {}

            /**
             * Called by the implementation after permissions are granted; the implementation must call
             * one of grant() or reject() for every request.
             *
             * @param video MediaSource for the video source to use (must be an original MediaSource
             *     object that was passed to the implementation); or null when video is not requested.
             * @param audio MediaSource for the audio source to use (must be an original MediaSource
             *     object that was passed to the implementation); or null when audio is not requested.
             */
            @UiThread
            default void grant(final @Nullable WSession.PermissionDelegate.MediaSource video, final @Nullable WSession.PermissionDelegate.MediaSource audio) {}

            /**
             * Called by the implementation when permissions are not granted; the implementation must call
             * one of grant() or reject() for every request.
             */
            @UiThread
            default void reject() {}
        }

        /**
         * Request content media permissions, including request for which video and/or audio source to
         * use.
         *
         * <p>Media permissions will still be requested if the associated device permissions have been
         * denied if there are video or audio sources in that category that can still be accessed. It is
         * the responsibility of consumers to ensure that media permission requests are not displayed in
         * this case.
         *
         * @param session WSession instance requesting the permission.
         * @param uri The URI of the content requesting the permission.
         * @param video List of video sources, or null if not requesting video.
         * @param audio List of audio sources, or null if not requesting audio.
         * @param callback Callback interface.
         */
        @UiThread
        default void onMediaPermissionRequest(
                @NonNull final WSession session,
                @NonNull final String uri,
                @Nullable final WSession.PermissionDelegate.MediaSource[] video,
                @Nullable final WSession.PermissionDelegate.MediaSource[] audio,
                @NonNull final WSession.PermissionDelegate.MediaCallback callback) {
            callback.reject();
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            WSession.PermissionDelegate.PERMISSION_GEOLOCATION,
            WSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION,
            WSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
            WSession.PermissionDelegate.PERMISSION_XR,
            WSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
            WSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
            WSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
            WSession.PermissionDelegate.PERMISSION_TRACKING,
            WSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS
    })
            /* package */ @interface Permission {}


    interface SelectionActionDelegate {
        /** The selection is collapsed at a single position. */
        int FLAG_IS_COLLAPSED = 1;
        /**
         * The selection is inside editable content such as an input element or contentEditable node.
         */
        int FLAG_IS_EDITABLE = 2;
        /** The selection is inside a password field. */
        int FLAG_IS_PASSWORD = 4;

        /** Hide selection actions and cause {@link #onHideAction} to be called. */
        String ACTION_HIDE = "HIDE";
        /** Copy onto the clipboard then delete the selected content. Selection must be editable. */
        String ACTION_CUT = "CUT";
        /** Copy the selected content onto the clipboard. */
        String ACTION_COPY = "COPY";
        /** Delete the selected content. Selection must be editable. */
        String ACTION_DELETE = "DELETE";
        /** Replace the selected content with the clipboard content. Selection must be editable. */
        String ACTION_PASTE = "PASTE";
        /**
         * Replace the selected content with the clipboard content as plain text. Selection must be
         * editable.
         */
        String ACTION_PASTE_AS_PLAIN_TEXT = "PASTE_AS_PLAIN_TEXT";
        /** Select the entire content of the document or editor. */
        String ACTION_SELECT_ALL = "SELECT_ALL";
        /** Clear the current selection. Selection must not be editable. */
        String ACTION_UNSELECT = "UNSELECT";
        /** Collapse the current selection to its start position. Selection must be editable. */
        String ACTION_COLLAPSE_TO_START = "COLLAPSE_TO_START";
        /** Collapse the current selection to its end position. Selection must be editable. */
        String ACTION_COLLAPSE_TO_END = "COLLAPSE_TO_END";

        /** Represents attributes of a selection. */
        interface Selection {
            /**
             * Flags describing the current selection, as a bitwise combination of the {@link
             * #FLAG_IS_COLLAPSED FLAG_*} constants.
             */
            @SelectionActionDelegateFlag int flags();

            /**
             * Text content of the current selection. An empty string indicates the selection is collapsed
             * or the selection cannot be represented as plain text.
             */
            @NonNull String text();

            /**
             * The bounds of the current selection in client coordinates. Use {@link
             * WSession#getClientToScreenMatrix} to perform transformation to screen coordinates.
             */
            @Nullable RectF clientRect();

            /** Set of valid actions available through {@link WSession.SelectionActionDelegate.Selection#execute(String)} */
            @NonNull @SelectionActionDelegateAction Collection<String> availableActions();

            /**
             * Checks if the passed action is available
             *
             * @param action An {@link WSession.SelectionActionDelegate} to perform
             * @return True if the action is available.
             */
            @AnyThread
            boolean isActionAvailable(@NonNull @SelectionActionDelegateAction final String action);


            /**
             * Execute an {@link WSession.SelectionActionDelegate} action.
             *
             * @throws IllegalStateException If the action was not available.
             * @param action A {@link WSession.SelectionActionDelegate} action.
             */
            @AnyThread
            void execute(@NonNull @SelectionActionDelegateAction final String action);

            /**
             * Hide selection actions and cause {@link #onHideAction} to be called.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void hide() {
                execute(ACTION_HIDE);
            }

            /**
             * Copy onto the clipboard then delete the selected content.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void cut() {
                execute(ACTION_CUT);
            }

            /**
             * Copy the selected content onto the clipboard.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void copy() {
                execute(ACTION_COPY);
            }

            /**
             * Delete the selected content.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void delete() {
                execute(ACTION_DELETE);
            }

            /**
             * Replace the selected content with the clipboard content.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void paste() {
                execute(ACTION_PASTE);
            }

            /**
             * Replace the selected content with the clipboard content as plain text.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void pasteAsPlainText() {
                execute(ACTION_PASTE_AS_PLAIN_TEXT);
            }

            /**
             * Select the entire content of the document or editor.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void selectAll() {
                execute(ACTION_SELECT_ALL);
            }

            /**
             * Clear the current selection.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void unselect() {
                execute(ACTION_UNSELECT);
            }

            /**
             * Collapse the current selection to its start position.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void collapseToStart() {
                execute(ACTION_COLLAPSE_TO_START);
            }

            /**
             * Collapse the current selection to its end position.
             *
             * @throws IllegalStateException If the action was not available.
             */
            @AnyThread
            default void collapseToEnd() {
                execute(ACTION_COLLAPSE_TO_END);
            }
        }

        /**
         * Selection actions are available. Selection actions become available when the user selects
         * some content in the document or editor. Inside an editor, selection actions can also become
         * available when the user explicitly requests editor action UI, for example by tapping on the
         * caret handle.
         *
         * <p>In response to this callback, applications typically display a toolbar containing the
         * selection actions. To perform a certain action, check if the action is available with {@link
         * WSession.SelectionActionDelegate.Selection#isActionAvailable} then either use the relevant helper method or {@link
         * WSession.SelectionActionDelegate.Selection#execute}
         *
         * <p>Once an {@link #onHideAction} call (with particular reasons) or another {@link
         * #onShowActionRequest} call is received, the previous Selection object is no longer usable.
         *
         * @param session The ISession that initiated the callback.
         * @param selection Current selection attributes and Callback object for performing built-in
         *     actions. May be used multiple times to perform multiple actions at once.
         */
        @UiThread
        default void onShowActionRequest(
                @NonNull final WSession session, @NonNull final WSession.SelectionActionDelegate.Selection selection) {}

        /** Actions are no longer available due to the user clearing the selection. */
        final int HIDE_REASON_NO_SELECTION = 0;
        /**
         * Actions are no longer available due to the user moving the selection out of view. Previous
         * actions are still available after a callback with this reason.
         */
        final int HIDE_REASON_INVISIBLE_SELECTION = 1;
        /**
         * Actions are no longer available due to the user actively changing the selection. {@link
         * #onShowActionRequest} may be called again once the user has set a selection, if the new
         * selection has available actions.
         */
        final int HIDE_REASON_ACTIVE_SELECTION = 2;
        /**
         * Actions are no longer available due to the user actively scrolling the page. {@link
         * #onShowActionRequest} may be called again once the user has stopped scrolling the page, if
         * the selection is still visible. Until then, previous actions are still available after a
         * callback with this reason.
         */
        final int HIDE_REASON_ACTIVE_SCROLL = 3;

        /**
         * Previous actions are no longer available due to the user interacting with the page.
         * Applications typically hide the action toolbar in response.
         *
         * @param session The ISession that initiated the callback.
         * @param reason The reason that actions are no longer available, as one of the {@link
         *     #HIDE_REASON_NO_SELECTION HIDE_REASON_*} constants.
         */
        @UiThread
        default void onHideAction(
                @NonNull final WSession session, @SelectionActionDelegateHideReason final int reason) {}
    }

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            WSession.SelectionActionDelegate.ACTION_HIDE,
            WSession.SelectionActionDelegate.ACTION_CUT,
            WSession.SelectionActionDelegate.ACTION_COPY,
            WSession.SelectionActionDelegate.ACTION_DELETE,
            WSession.SelectionActionDelegate.ACTION_PASTE,
            WSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT,
            WSession.SelectionActionDelegate.ACTION_SELECT_ALL,
            WSession.SelectionActionDelegate.ACTION_UNSELECT,
            WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_START,
            WSession.SelectionActionDelegate.ACTION_COLLAPSE_TO_END
    })
            /* package */ @interface SelectionActionDelegateAction {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {WSession.SelectionActionDelegate.FLAG_IS_COLLAPSED, WSession.SelectionActionDelegate.FLAG_IS_EDITABLE})
            /* package */ @interface SelectionActionDelegateFlag {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            WSession.SelectionActionDelegate.HIDE_REASON_NO_SELECTION,
            WSession.SelectionActionDelegate.HIDE_REASON_INVISIBLE_SELECTION,
            WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SELECTION,
            WSession.SelectionActionDelegate.HIDE_REASON_ACTIVE_SCROLL
    })
            /* package */ @interface SelectionActionDelegateHideReason {}

    /**
     * Load the given URI.
     * @param uri The URI of the resource to load.
     */
    @AnyThread
    default void loadUri(final @NonNull String uri) {
        loadUri(uri, LOAD_FLAGS_NONE);
    }

    /**
     * Load the given URI.
     * @param uri The URI of the resource to load.
     */
    @AnyThread
    void loadUri(final @NonNull String uri, final @LoadFlags int flags);

    /**
     * Load the given data.
     * @param data The data of the resource to load.
     * @param mymeType The myme type of the resource to load.
     */
    @AnyThread
    void loadData(final @NonNull byte[] data, final String mymeType);

    default void reload() {
        reload(LOAD_FLAGS_NONE);
    }

    /**
     * Reload the current URI.
     *
     * @param flags the load flags to use, an OR-ed value of {@link #LOAD_FLAGS_NONE LOAD_FLAGS_*}
     */
    @AnyThread
    void reload(final @LoadFlags int flags);


    /** Stop loading. */
    @AnyThread
    void stop();


    /**
     * Set this session as active or inactive, which represents if the session is currently
     * visible or not. Setting a ISession to inactive will significantly reduce its memory
     * footprint, but should only be done if the session is not currently visible. Note that a
     * session can be active (i.e. visible) but not focused. When a session is set inactive, it will
     * flush the session state and trigger a `ProgressDelegate.onSessionStateChange` callback.
     *
     * @param active A boolean determining whether the ISession is active.
     * @see #setFocused
     */
    @AnyThread
    void setActive(final boolean active);

    /**
     * Move focus to this session or away from this session. Only one session has focus at a given
     * time. Note that a session can be unfocused but still active (i.e. visible).
     *
     * @param focused True if the session should gain focus or false if the session should lose focus.
     * @see #setActive
     */
    @AnyThread
    void setFocused(final boolean focused);


    /**
     * Opens the session.
     *
     * <p>Call this when you are ready to use a session instance.
     *
     * <p>The session is in a 'closed' state when first created. Opening it creates the underlying
     * objects necessary to load a page, etc. Most session methods only take affect on an
     * open session, and are queued until the session is opened here. Opening a session is an
     * asynchronous operation.
     *
     * @param runtime The runtime to attach this session to.
     * @see #close
     * @see #isOpen
     */
    @UiThread
    void open(final @NonNull WRuntime runtime);

    /**
     * Return whether this session is open.
     *
     * @return True if session is open.
     * @see #open
     * @see #close
     */
    @AnyThread
    boolean isOpen();

    /**
     * Closes the session.
     *
     * <p>This frees the underlying internal objects and unloads the current page. The session may be
     * reopened later, but page state is not restored. Call this when you are finished using a
     * session instance.
     *
     * @see #open
     * @see #isOpen
     */
    @UiThread
    void close();

    /**
     * Go back in history and assumes the call was based on a user interaction.
     *
     * @see #goBack(boolean)
     */
    @AnyThread
    default void goBack() {
        goBack(true);
    }

    /**
     * Go back in history.
     *
     * @param userInteraction Whether the action was invoked by a user interaction.
     */
    @AnyThread
    void goBack(final boolean userInteraction);

    /**
     * Go forward in history and assumes the call was based on a user interaction.
     *
     * @see #goForward(boolean)
     */
    @AnyThread
    default void goForward() {
        goForward(true);
    }

    /**
     * Go forward in history.
     *
     * @param userInteraction Whether the action was invoked by a user interaction.
     */
    @AnyThread
    void goForward(final boolean userInteraction);

    /**
     * Navigate to an index in browser history; the index of the currently viewed page can be
     * retrieved from an up-to-date HistoryList by calling {@link
     * WSession.HistoryDelegate.HistoryList#getCurrentIndex()}.
     *
     * @param index The index of the location in browser history you want to navigate to.
     */
    @AnyThread
    void gotoHistoryIndex(final int index);

    /**
     * Purge history for the session. The session history is used for back and forward history.
     * Purging the session history means {@link WSession.NavigationDelegate#onCanGoBack(WSession, boolean)}
     * and {@link WSession.NavigationDelegate#onCanGoForward(WSession, boolean)} will be false.
     */
    @AnyThread
    void purgeHistory();

    @AnyThread
    @SuppressWarnings("checkstyle:javadocmethod")
    @NonNull
    WSessionSettings getSettings();

    /**
     * Get the default user-agent for the given mode.
     *
     * @param mode {@link WSessionSettings#USER_AGENT_MODE_MOBILE},
     *             {@link WSessionSettings#USER_AGENT_MODE_DESKTOP},
     *             or {@link WSessionSettings#USER_AGENT_MODE_VR}.
     */
    @AnyThread
    @NonNull
    String getDefaultUserAgent(final int mode);

    @AnyThread
    @NonNull
    WSession.SessionFinder getSessionFinder();

    /** Exits fullscreen mode */
    @AnyThread
    void exitFullScreen();

    interface SessionFinder {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                flag = true,
                value = {
                        FINDER_FIND_BACKWARDS,
                        FINDER_FIND_LINKS_ONLY,
                        FINDER_FIND_MATCH_CASE,
                        FINDER_FIND_WHOLE_WORD
                })
        public @interface FinderFindFlags {}

        /** Go backwards when finding the next match. */
        public static final int FINDER_FIND_BACKWARDS = 1;
        /** Perform case-sensitive match; default is to perform a case-insensitive match. */
        public static final int FINDER_FIND_MATCH_CASE = 1 << 1;
        /** Must match entire words; default is to allow matching partial words. */
        public static final int FINDER_FIND_WHOLE_WORD = 1 << 2;
        /** Limit matches to links on the page. */
        public static final int FINDER_FIND_LINKS_ONLY = 1 << 3;

        public class FinderResult {
            /** Whether a match was found. */
            public boolean found;
            /** Whether the search wrapped around the top or bottom of the page. */
            public boolean wrapped;
            /** Ordinal number of the current match starting from 1, or 0 if no match. */
            public int current;
            /** Total number of matches found so far, or -1 if unknown. */
            public int total;
            /** Search string. */
            @NonNull
            public String searchString;
            /**
             * Flags used for the search; either 0 or a combination of FINDER_FIND_* flags.
             */
            public int flags;
            /** URI of the link, if the current match is a link, or null otherwise. */
            @Nullable
            public String linkUri;
            /** Bounds of the current match in client coordinates, or null if unknown. */
            @Nullable public RectF clientRect;
        }

        /**
         * Find and select a string on the current page, starting from the current selection or the
         * start of the page if there is no selection. Optionally return results related to the
         * search. If searchString is null, search is performed using the previous search string.
         *
         * @param searchString String to search, or null to find again using the previous string.
         * @param flags Flags for performing the search;
         *              either 0 or a combination of FINDER_FIND_* constants.
         * @return WResult<?> instance, Result of the search operation.
         */
        @AnyThread
        @NonNull
        WResult<FinderResult> find(@Nullable String searchString, int flags);

        /** Clear any highlighted find-in-page matches. */
        @AnyThread
        void clear();

        /** Return flags for displaying find-in-page matches.
         *
         * @return Display flags as a combination of FINDER_DISPLAY_* constants.
         */
        @AnyThread
        int getDisplayFlags();

        /** Set flags for displaying find-in-page matches.
         *
         * @param flags Display flags as a combination of FINDER_DISPLAY_* constants.
         */
        @AnyThread
        void setDisplayFlags(int flags);
    }

    /**
     * Acquire the WDisplay instance for providing the session with a drawing Surface. Be sure to
     * call {@link WDisplay#surfaceChanged(Surface, int, int)} on the acquired display if there is
     * already a valid Surface.
     *
     * @return WDisplay instance.
     * @see #releaseDisplay(WDisplay)
     */
    @UiThread
    @NonNull
    WDisplay acquireDisplay();

    /**
     * Release an acquired WDisplay instance. Be sure to call {@link
     * WDisplay#surfaceDestroyed()} before releasing the display if it still has a valid Surface.
     *
     * @param display Acquired WDisplay instance.
     * @see #acquireDisplay()
     */
    @UiThread
    void releaseDisplay(final @NonNull WDisplay display);


    /**
     * Restore a saved state to this ISession; only data that is saved (history, scroll position,
     * zoom, and form data) will be restored. These will overwrite the corresponding state of this
     * ISession.
     *
     * @param state A saved session state; this should originate from onSessionStateChange().
     */
    @AnyThread
    void restoreState(final @NonNull WSessionState state);

    /**
     * Get a matrix for transforming from client coordinates to surface coordinates.
     *
     * @param matrix Matrix to be replaced by the transformation matrix.
     * @see #getClientToScreenMatrix(Matrix)
     * @see #getPageToSurfaceMatrix(Matrix)
     */
    @UiThread
    void getClientToSurfaceMatrix(@NonNull final Matrix matrix);


    /**
     * Get a matrix for transforming from client coordinates to screen coordinates. The client
     * coordinates are in CSS pixels and are relative to the viewport origin; their relation to screen
     * coordinates does not depend on the current scroll position.
     *
     * @param matrix Matrix to be replaced by the transformation matrix.
     * @see #getClientToSurfaceMatrix(Matrix)
     * @see #getPageToScreenMatrix(Matrix)
     */
    @UiThread
    void getClientToScreenMatrix(@NonNull final Matrix matrix);

    interface UrlUtilsVisitor {
        @AnyThread
        boolean isSupportedScheme(@NonNull String scheme);
    }

    /**
     * Get the URL utils visitor for this session.
     *
     * @return UrlUtilsVisitor instance.
     */
    @AnyThread
    @NonNull
    UrlUtilsVisitor getUrlUtilsVisitor();

    /**
     * Get a matrix for transforming from page coordinates to screen coordinates. The page coordinates
     * are in CSS pixels and are relative to the page origin; their relation to screen coordinates
     * depends on the current scroll position of the outermost frame.
     *
     * @param matrix Matrix to be replaced by the transformation matrix.
     * @see #getPageToSurfaceMatrix(Matrix)
     * @see #getClientToScreenMatrix(Matrix)
     */
    @UiThread
    void getPageToScreenMatrix(@NonNull final Matrix matrix);

    /**
     * Get a matrix for transforming from page coordinates to surface coordinates.
     *
     * @param matrix Matrix to be replaced by the transformation matrix.
     * @see #getPageToScreenMatrix(Matrix)
     * @see #getClientToSurfaceMatrix(Matrix)
     */
    @UiThread
    void getPageToSurfaceMatrix(@NonNull final Matrix matrix);

    /**
     * Dispatch a custom GPS location to the session
     *
     * This is only used to override the default GPS implementation in the browser for HVR devices.
     */
    @UiThread
    void dispatchLocation(double latitude, double longitude, double altitude, float accuracy,
                          float altitudeAccuracy, float heading, float speed, float time);

    /**
     * Get the SessionTextInput instance for this session. May be called on any thread.
     *
     * @return SessionTextInput instance.
     */
    @AnyThread
    @NonNull
    WTextInput getTextInput();


    @AnyThread
    void pageZoomIn();

    @AnyThread
    void pageZoomOut();

    @AnyThread
    int getCurrentZoomLevel();

    /**
     * Get the PanZoomController instance for this session.
     *
     * @return PanZoomController instance.
     */
    @UiThread
    @NonNull
    WPanZoomController getPanZoomController();


    /**
     * Set the content callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of ContentDelegate.
     */
    @UiThread
    void setContentDelegate(final @Nullable WSession.ContentDelegate delegate);

    /**
     * Get the content callback handler.
     *
     * @return The current content callback handler.
     */
    @UiThread
    @Nullable
    WSession.ContentDelegate getContentDelegate();

    /**
     * Set the content callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of PermissionDelegate.
     */
    @UiThread
    void setPermissionDelegate(final @Nullable WSession.PermissionDelegate delegate);

    /**
     * Get the content callback handler.
     *
     * @return The current content callback handler.
     */
    @UiThread
    @Nullable
    WSession.PermissionDelegate getPermissionDelegate();

    /**
     * Set the progress callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of ProgressDelegate.
     */
    @UiThread
    void setProgressDelegate(final @Nullable WSession.ProgressDelegate delegate);

    /**
     * Get the progress callback handler.
     *
     * @return The current progress callback handler.
     */
    @UiThread
    @Nullable
    WSession.ProgressDelegate getProgressDelegate();

    /**
     * Set the navigation callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of NavigationDelegate.
     */
    @UiThread
    void setNavigationDelegate(final @Nullable WSession.NavigationDelegate delegate);

    /**
     * Get the navigation callback handler.
     *
     * @return The current navigation callback handler.
     */
    @UiThread
    @Nullable
    WSession.NavigationDelegate getNavigationDelegate();

    /**
     * Set the content scroll callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of ScrollDelegate.
     */
    @UiThread
    void setScrollDelegate(final @Nullable WSession.ScrollDelegate delegate);

    @UiThread
    @SuppressWarnings("checkstyle:javadocmethod")
    @Nullable
    WSession.ScrollDelegate getScrollDelegate();

    /**
     * Set the history tracking delegate for this session, replacing the current delegate if one is
     * set.
     *
     * @param delegate The history tracking delegate, or {@code null} to unset.
     */
    @AnyThread
    void setHistoryDelegate(final @Nullable WSession.HistoryDelegate delegate);

    /** @return The history tracking delegate for this session. */
    @AnyThread
    public @Nullable
    WSession.HistoryDelegate getHistoryDelegate();

    /**
     * Set the content blocking callback handler. This will replace the current handler.
     *
     * @param delegate An implementation of {@link WContentBlocking.Delegate}.
     */
    @AnyThread
    void setContentBlockingDelegate(final @Nullable WContentBlocking.Delegate delegate);

    /**
     * Get the content blocking callback handler.
     *
     * @return The current content blocking callback handler.
     */
    @AnyThread
    @Nullable WContentBlocking.Delegate getContentBlockingDelegate();

    /**
     * Set the current prompt delegate for this ISession.
     *
     * @param delegate PromptDelegate instance or null to use the built-in delegate.
     */
    @AnyThread
    void setPromptDelegate(final @Nullable WSession.PromptDelegate delegate);

    /**
     * Get the current prompt delegate for this ISession.
     *
     * @return PromptDelegate instance or null if using built-in delegate.
     */
    @AnyThread
    @Nullable
    WSession.PromptDelegate getPromptDelegate();

    /**
     * Set the current selection action delegate for this ISession.
     *
     * @param delegate SelectionActionDelegate instance or null to unset.
     */
    @UiThread
    void setSelectionActionDelegate(final @Nullable WSession.SelectionActionDelegate delegate);


    /**
     * Set the media session delegate. This will replace the current handler.
     *
     * @param delegate An implementation of {@link WMediaSession.Delegate}.
     */
    @AnyThread
    void setMediaSessionDelegate(final @Nullable WMediaSession.Delegate delegate);

    /**
     * Get the media session delegate.
     *
     * @return The current media session delegate.
     */
    @AnyThread
    @Nullable WMediaSession.Delegate getMediaSessionDelegate();

    /**
     * Get the current selection action delegate for this ISession.
     *
     * @return SelectionActionDelegate instance or null if not set.
     */
    @AnyThread
    @Nullable
    WSession.SelectionActionDelegate getSelectionActionDelegate();
}
