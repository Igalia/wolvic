package com.igalia.wolvic.browser.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Map;

public interface WWebResponse {

    /** The default read timeout for the {@link #body} stream. */
    long DEFAULT_READ_TIMEOUT_MS = 30000;

    /** The URI for the request or response. */
    @NonNull String uri();

    /** An unmodifiable Map of headers. Defaults to an empty instance. */
    @NonNull Map<String, String> headers();

    /** The HTTP status code for the response, e.g. 200. */
    int statusCode();

    /** A boolean indicating whether or not this response is the result of a redirection. */
    boolean redirected();

    /** Whether or not this response was delivered via a secure connection. */
    boolean isSecure();

    /** The server certificate used with this response, if any. */
    @Nullable X509Certificate certificate();

    /**
     * An {@link InputStream} containing the response body, if available. Attention: the stream must
     * be closed whenever the app is done with it, even when the body is ignored. Otherwise the
     * connection will not be closed until the stream is garbage collected.
     */
     @Nullable InputStream body();
}
