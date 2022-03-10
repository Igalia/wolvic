package com.igalia.wolvic.browser.api;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.X509Certificate;

/**
 * WebRequestError is simply a container for error codes and categories used by {@link
 * WSession.NavigationDelegate#onLoadError(WSession, String, WWebRequestError)}.
 */
@AnyThread
public interface WWebRequestError {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_CATEGORY_UNKNOWN,
            ERROR_CATEGORY_SECURITY,
            ERROR_CATEGORY_NETWORK,
            ERROR_CATEGORY_CONTENT,
            ERROR_CATEGORY_URI,
            ERROR_CATEGORY_PROXY,
            ERROR_CATEGORY_SAFEBROWSING
    })
            /* package */ @interface ErrorCategory {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_UNKNOWN,
            ERROR_SECURITY_SSL,
            ERROR_SECURITY_BAD_CERT,
            ERROR_NET_RESET,
            ERROR_NET_INTERRUPT,
            ERROR_NET_TIMEOUT,
            ERROR_CONNECTION_REFUSED,
            ERROR_UNKNOWN_PROTOCOL,
            ERROR_UNKNOWN_HOST,
            ERROR_UNKNOWN_SOCKET_TYPE,
            ERROR_UNKNOWN_PROXY_HOST,
            ERROR_MALFORMED_URI,
            ERROR_REDIRECT_LOOP,
            ERROR_SAFEBROWSING_PHISHING_URI,
            ERROR_SAFEBROWSING_MALWARE_URI,
            ERROR_SAFEBROWSING_UNWANTED_URI,
            ERROR_SAFEBROWSING_HARMFUL_URI,
            ERROR_CONTENT_CRASHED,
            ERROR_OFFLINE,
            ERROR_PORT_BLOCKED,
            ERROR_PROXY_CONNECTION_REFUSED,
            ERROR_FILE_NOT_FOUND,
            ERROR_FILE_ACCESS_DENIED,
            ERROR_INVALID_CONTENT_ENCODING,
            ERROR_UNSAFE_CONTENT_TYPE,
            ERROR_CORRUPTED_CONTENT,
            ERROR_DATA_URI_TOO_LONG,
            ERROR_HTTPS_ONLY
    })
            /* package */ @interface Error {}


    /**
     * This is normally used for error codes that don't currently fit into any of the other
     * categories.
     */
    int ERROR_CATEGORY_UNKNOWN = 0x1;

    /** This is used for error codes that relate to SSL certificate validation. */
    int ERROR_CATEGORY_SECURITY = 0x2;

    /** This is used for error codes relating to network problems. */
    int ERROR_CATEGORY_NETWORK = 0x3;

    /** This is used for error codes relating to invalid or corrupt web pages. */
    int ERROR_CATEGORY_CONTENT = 0x4;

    int ERROR_CATEGORY_URI = 0x5;
    int ERROR_CATEGORY_PROXY = 0x6;
    int ERROR_CATEGORY_SAFEBROWSING = 0x7;

    /** An unknown error occurred */
    int ERROR_UNKNOWN = 0x11;

    // Security
    /** This is used for a variety of SSL negotiation problems. */
    int ERROR_SECURITY_SSL = 0x22;

    /** This is used to indicate an untrusted or otherwise invalid SSL certificate. */
    int ERROR_SECURITY_BAD_CERT = 0x32;

    // Network
    /** The network connection was interrupted. */
    int ERROR_NET_INTERRUPT = 0x23;

    /** The network request timed out. */
    int ERROR_NET_TIMEOUT = 0x33;

    /** The network request was refused by the server. */
    int ERROR_CONNECTION_REFUSED = 0x43;

    /** The network request tried to use an unknown socket type. */
    int ERROR_UNKNOWN_SOCKET_TYPE = 0x53;

    /** A redirect loop was detected. */
    int ERROR_REDIRECT_LOOP = 0x63;

    /** This device does not have a network connection. */
    int ERROR_OFFLINE = 0x73;

    /** The request tried to use a port that is blocked by either the OS or the browsr engine. */
    int ERROR_PORT_BLOCKED = 0x83;

    /** The connection was reset. */
    int ERROR_NET_RESET = 0x93;

    /**
     * Browser could not connect to this website in HTTPS-only mode. Call
     * document.reloadWithHttpsOnlyException() in the error page to temporarily disable HTTPS only
     * mode for this request.
     *
     * <p>See also {@link WSession.NavigationDelegate#onLoadError}
     */
    int ERROR_HTTPS_ONLY = 0xA3;

    // Content
    /** A content type was returned which was deemed unsafe. */
    int ERROR_UNSAFE_CONTENT_TYPE = 0x24;

    /** The content returned was corrupted. */
    int ERROR_CORRUPTED_CONTENT = 0x34;

    /** The content process crashed. */
    int ERROR_CONTENT_CRASHED = 0x44;

    /** The content has an invalid encoding. */
    int ERROR_INVALID_CONTENT_ENCODING = 0x54;

    // URI
    /** The host could not be resolved. */
    int ERROR_UNKNOWN_HOST = 0x25;

    /** An invalid URL was specified. */
    int ERROR_MALFORMED_URI = 0x35;

    /** An unknown protocol was specified. */
    int ERROR_UNKNOWN_PROTOCOL = 0x45;

    /** A file was not found (usually used for file:// URIs). */
    int ERROR_FILE_NOT_FOUND = 0x55;

    /** The OS blocked access to a file. */
    int ERROR_FILE_ACCESS_DENIED = 0x65;

    /** A data:// URI is too long to load at the top level. */
    int ERROR_DATA_URI_TOO_LONG = 0x75;

    // Proxy
    /** The proxy server refused the connection. */
    int ERROR_PROXY_CONNECTION_REFUSED = 0x26;

    /** The host name of the proxy server could not be resolved. */
    int ERROR_UNKNOWN_PROXY_HOST = 0x36;

    // Safebrowsing
    /** The requested URI was present in the "malware" blocklist. */
    int ERROR_SAFEBROWSING_MALWARE_URI = 0x27;

    /** The requested URI was present in the "unwanted" blocklist. */
    int ERROR_SAFEBROWSING_UNWANTED_URI = 0x37;

    /** The requested URI was present in the "harmful" blocklist. */
    int ERROR_SAFEBROWSING_HARMFUL_URI = 0x47;

    /** The requested URI was present in the "phishing" blocklist. */
    int ERROR_SAFEBROWSING_PHISHING_URI = 0x57;

    /** The error code, e.g. {@link #ERROR_MALFORMED_URI}. */
    int code();

    /** The error category, e.g. {@link #ERROR_CATEGORY_URI}. */
    int category();

    /**
     * The server certificate used. This can be useful if the error code is is e.g. {@link
     * #ERROR_SECURITY_BAD_CERT}.
     */
    @Nullable X509Certificate certificate();
}
