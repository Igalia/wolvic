package org.mozilla.vrbrowser;

import org.mozilla.geckoview.GeckoSession.NavigationDelegate;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadError;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadErrorCategory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ErrorPages {

    public static final String ERROR_URL = "resource://android/assets/html/error/netError.xhtml";

    public static class ErrorData {
        public String mError;
        public String mErrorDescription;

        ErrorData(String error, String description) {
            mError = error;
            mErrorDescription = description;
        }

        public static ErrorData create(String error, String description) {
            return new ErrorData(error, description);
        }
    }

    public static String fromGeckoErrorToErrorURI(String aUri, @LoadErrorCategory int category, @LoadError int error) {
        ErrorData data = fromGeckoErrorToErrorData(category, error);

        String e;
        try {
            e = URLEncoder.encode(data.mError, "UTF-8");

        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();

            e = "generic";
        }

        String u;
        try {
            u = URLEncoder.encode(aUri, "UTF-8");

        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();

            u = "";
        }

        String d;
        try {
            d = URLEncoder.encode(data.mErrorDescription, "UTF-8");

        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();

            d = "generic";
        }

        String url = ERROR_URL + "?" +
                "e=" + e +
                "&u=" + u +
                "&d=" + d;

        return url;
    }

    public static boolean isLocalErrorUri(String aURI) {
        if (aURI.startsWith(ERROR_URL))
            return true;

        return false;
    }

    private static ErrorData fromGeckoErrorToErrorData(@LoadErrorCategory int category, @LoadError int error) {
        switch(category) {
            case NavigationDelegate.ERROR_CATEGORY_UNKNOWN: {
                return ErrorData.create("generic", "generic");
            }
            case NavigationDelegate.ERROR_CATEGORY_SECURITY: {
                switch (error) {
                    case NavigationDelegate.ERROR_SECURITY_SSL: {
                        return ErrorData.create("nssFailure2", "nssFailure2");
                    }
                    case NavigationDelegate.ERROR_SECURITY_BAD_CERT: {
                        return ErrorData.create("nssBadCert", "nssBadCert");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_NETWORK: {
                switch (error) {
                    case NavigationDelegate.ERROR_NET_INTERRUPT: {
                        return ErrorData.create("netInterrupt", "netInterrupt");
                    }
                    case NavigationDelegate.ERROR_NET_TIMEOUT: {
                        return ErrorData.create("netTimeout", "netTimeout");
                    }
                    case NavigationDelegate.ERROR_CONNECTION_REFUSED: {
                        return ErrorData.create("connectionFailure", "connectionFailure");
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_SOCKET_TYPE: {
                        return ErrorData.create("unknownSocketType", "unknownSocketType");
                    }
                    case NavigationDelegate.ERROR_REDIRECT_LOOP: {
                        return ErrorData.create("redirectLoop", "redirectLoop");
                    }
                    case NavigationDelegate.ERROR_OFFLINE: {
                        return ErrorData.create("netOffline", "netOffline");
                    }
                    case NavigationDelegate.ERROR_PORT_BLOCKED: {
                        return ErrorData.create("deniedPortAccess", "deniedPortAccess");
                    }
                    case NavigationDelegate.ERROR_NET_RESET: {
                        return ErrorData.create("netReset", "netReset");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_CONTENT: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNSAFE_CONTENT_TYPE: {
                        return ErrorData.create("unsafeContentType", "unsafeContentType");
                    }
                    case NavigationDelegate.ERROR_CORRUPTED_CONTENT: {
                        return ErrorData.create("corruptedContentErrorv2", "corruptedContentErrorv2");
                    }
                    case NavigationDelegate.ERROR_CONTENT_CRASHED: {
                        return ErrorData.create("corruptedContentErrorv2", "corruptedContentErrorv2");
                    }
                    case NavigationDelegate.ERROR_INVALID_CONTENT_ENCODING: {
                        return ErrorData.create("contentEncodingError", "contentEncodingError");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_URI: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNKNOWN_HOST: {
                        return ErrorData.create("dnsNotFound", "dnsNotFound");
                    }
                    case NavigationDelegate.ERROR_MALFORMED_URI: {
                        return ErrorData.create("malformedURI", "malformedURI");
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROTOCOL: {
                        return ErrorData.create("unknownProtocolFound", "unknownProtocolFound");
                    }
                    case NavigationDelegate.ERROR_FILE_NOT_FOUND: {
                        return ErrorData.create("fileNotFound", "fileNotFound");
                    }
                    case NavigationDelegate.ERROR_FILE_ACCESS_DENIED: {
                        return ErrorData.create("fileAccessDenied", "fileAccessDenied");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_PROXY: {
                switch (error) {
                    case NavigationDelegate.ERROR_PROXY_CONNECTION_REFUSED: {
                        return ErrorData.create("proxyConnectFailure", "proxyConnectFailure");
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROXY_HOST: {
                        return ErrorData.create("proxyResolveFailure", "proxyResolveFailure");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_SAFEBROWSING: {
                switch (error) {
                    case NavigationDelegate.ERROR_SAFEBROWSING_MALWARE_URI: {
                        return ErrorData.create("safeBrowsingMalwareUri", "safeBrowsingMalwareUri");
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_UNWANTED_URI: {
                        return ErrorData.create("safeBrowsingUnwantedUri", "safeBrowsingUnwantedUri");
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_HARMFUL_URI: {
                        return ErrorData.create("safeBrowsingHarmfulUri", "safeBrowsingHarmfulUri");
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_PHISHING_URI: {
                        return ErrorData.create("safeBrowsingPhishingUri", "safeBrowsingPhishingUri");
                    }
                    default: {
                        return ErrorData.create("generic", "generic");
                    }
                }
            }
            default: {
                return ErrorData.create("generic", "generic");
            }
        }
    }
}
