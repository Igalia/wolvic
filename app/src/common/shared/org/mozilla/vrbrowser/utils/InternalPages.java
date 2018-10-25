package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Base64;

import org.mozilla.geckoview.GeckoSession.NavigationDelegate;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadError;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadErrorCategory;
import org.mozilla.vrbrowser.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import mozilla.components.browser.errorpages.ErrorPages;
import mozilla.components.browser.errorpages.ErrorType;

public class InternalPages {

    private static ErrorType fromGeckoErrorToErrorType(@LoadErrorCategory int category, @LoadError int error) {
        switch(category) {
            case NavigationDelegate.ERROR_CATEGORY_UNKNOWN: {
                return ErrorType.UNKNOWN;
            }
            case NavigationDelegate.ERROR_CATEGORY_SECURITY: {
                switch (error) {
                    case NavigationDelegate.ERROR_SECURITY_SSL: {
                        return ErrorType.ERROR_SECURITY_SSL;
                    }
                    case NavigationDelegate.ERROR_SECURITY_BAD_CERT: {
                        return ErrorType.ERROR_SECURITY_BAD_CERT;
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_NETWORK: {
                switch (error) {
                    case NavigationDelegate.ERROR_NET_INTERRUPT: {
                        return ErrorType.ERROR_NET_INTERRUPT;
                    }
                    case NavigationDelegate.ERROR_NET_TIMEOUT: {
                        return ErrorType.ERROR_NET_TIMEOUT;
                    }
                    case NavigationDelegate.ERROR_CONNECTION_REFUSED: {
                        return ErrorType.ERROR_CONNECTION_REFUSED;
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_SOCKET_TYPE: {
                        return ErrorType.ERROR_UNKNOWN_SOCKET_TYPE;
                    }
                    case NavigationDelegate.ERROR_REDIRECT_LOOP: {
                        return ErrorType.ERROR_REDIRECT_LOOP;
                    }
                    case NavigationDelegate.ERROR_OFFLINE: {
                        return ErrorType.ERROR_OFFLINE;
                    }
                    case NavigationDelegate.ERROR_PORT_BLOCKED: {
                        return ErrorType.ERROR_PORT_BLOCKED;
                    }
                    case NavigationDelegate.ERROR_NET_RESET: {
                        return ErrorType.ERROR_NET_RESET;
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_CONTENT: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNSAFE_CONTENT_TYPE: {
                        return ErrorType.ERROR_UNSAFE_CONTENT_TYPE;
                    }
                    case NavigationDelegate.ERROR_CORRUPTED_CONTENT: {
                        return ErrorType.ERROR_CORRUPTED_CONTENT;
                    }
                    case NavigationDelegate.ERROR_CONTENT_CRASHED: {
                        return ErrorType.ERROR_CONTENT_CRASHED;
                    }
                    case NavigationDelegate.ERROR_INVALID_CONTENT_ENCODING: {
                        return ErrorType.ERROR_INVALID_CONTENT_ENCODING;
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_URI: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNKNOWN_HOST: {
                        return ErrorType.ERROR_UNKNOWN_HOST;
                    }
                    case NavigationDelegate.ERROR_MALFORMED_URI: {
                        return ErrorType.ERROR_MALFORMED_URI;
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROTOCOL: {
                        return ErrorType.ERROR_UNKNOWN_PROTOCOL;
                    }
                    case NavigationDelegate.ERROR_FILE_NOT_FOUND: {
                        return ErrorType.ERROR_FILE_NOT_FOUND;
                    }
                    case NavigationDelegate.ERROR_FILE_ACCESS_DENIED: {
                        return ErrorType.ERROR_FILE_ACCESS_DENIED;
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_PROXY: {
                switch (error) {
                    case NavigationDelegate.ERROR_PROXY_CONNECTION_REFUSED: {
                        return ErrorType.ERROR_CONNECTION_REFUSED;
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROXY_HOST: {
                        return ErrorType.ERROR_UNKNOWN_PROXY_HOST;
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_SAFEBROWSING: {
                switch (error) {
                    case NavigationDelegate.ERROR_SAFEBROWSING_MALWARE_URI: {
                        return ErrorType.ERROR_SAFEBROWSING_MALWARE_URI;
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_UNWANTED_URI: {
                        return ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI;
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_HARMFUL_URI: {
                        return ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI;
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_PHISHING_URI: {
                        return ErrorType.ERROR_SAFEBROWSING_PHISHING_URI;
                    }
                }
            }
            default: {
                return ErrorType.UNKNOWN;
            }
        }
    }

    public static class PageResources {
        int html;
        int css;

        private PageResources(int aHtml, int aCss) {
            html = aHtml;
            css = aCss;
        }

        public static PageResources create(int html, int css) {
            return new PageResources(html, css);
        }
    }

    public static String createErrorPage(Context context,
                                         String uri,
                                         @LoadErrorCategory int errorCategory,
                                         @LoadError int errorType) {
        // TODO: browser-error pages component needs to accept a uri parameter
        String html = ErrorPages.INSTANCE.createErrorPage(
                context,
                fromGeckoErrorToErrorType(errorCategory, errorType),
                uri,
                R.raw.error_pages,
                R.raw.error_style);

        return "data:text/html;base64," + Base64.encodeToString(html.getBytes(), Base64.DEFAULT);
    }

    public static byte[] createAboutPage(Context context,
                                         PageResources resources) {
        String html = readRawResourceString(context, resources.html);
        String css = readRawResourceString(context, resources.css);

        String pageBody = context.getString(R.string.private_browsing_body, context.getString(R.string.app_name));
        html = html
                .replace("%pageTitle%", context.getString(R.string.private_browsing_title))
                .replace("%pageBody%", pageBody)
                .replace("%css%", css)
                .replace("%privateBrowsingSupportUrl%", context.getString(R.string.private_browsing_support_url));

        return html.getBytes();
    }

    private static String readRawResourceString(Context context, int resource) {
        StringBuilder total = new StringBuilder();
        try {
            BufferedReader stream = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(resource)));
            String line;
            while ((line = stream.readLine()) != null) {
                total.append(line).append('\n');
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return total.toString();
    }
}
