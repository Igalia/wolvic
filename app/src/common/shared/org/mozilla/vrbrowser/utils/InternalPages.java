package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Base64;

import org.mozilla.vrbrowser.R;

import org.mozilla.geckoview.WebRequestError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import mozilla.components.browser.errorpages.ErrorPages;
import mozilla.components.browser.errorpages.ErrorType;

public class InternalPages {

    private static ErrorType fromGeckoErrorToErrorType(int error) {
        switch(error) {
            case WebRequestError.ERROR_SECURITY_SSL: {
                return ErrorType.ERROR_SECURITY_SSL;
            }
            case WebRequestError.ERROR_SECURITY_BAD_CERT: {
                return ErrorType.ERROR_SECURITY_BAD_CERT;
            }
            case WebRequestError.ERROR_NET_INTERRUPT: {
                return ErrorType.ERROR_NET_INTERRUPT;
            }
            case WebRequestError.ERROR_NET_TIMEOUT: {
                return ErrorType.ERROR_NET_TIMEOUT;
            }
            case WebRequestError.ERROR_CONNECTION_REFUSED: {
                return ErrorType.ERROR_CONNECTION_REFUSED;
            }
            case WebRequestError.ERROR_UNKNOWN_SOCKET_TYPE: {
                return ErrorType.ERROR_UNKNOWN_SOCKET_TYPE;
            }
            case WebRequestError.ERROR_REDIRECT_LOOP: {
                return ErrorType.ERROR_REDIRECT_LOOP;
            }
            case WebRequestError.ERROR_OFFLINE: {
                return ErrorType.ERROR_OFFLINE;
            }
            case WebRequestError.ERROR_PORT_BLOCKED: {
                return ErrorType.ERROR_PORT_BLOCKED;
            }
            case WebRequestError.ERROR_NET_RESET: {
                return ErrorType.ERROR_NET_RESET;
            }
            case WebRequestError.ERROR_UNSAFE_CONTENT_TYPE: {
                return ErrorType.ERROR_UNSAFE_CONTENT_TYPE;
            }
            case WebRequestError.ERROR_CORRUPTED_CONTENT: {
                return ErrorType.ERROR_CORRUPTED_CONTENT;
            }
            case WebRequestError.ERROR_CONTENT_CRASHED: {
                return ErrorType.ERROR_CONTENT_CRASHED;
            }
            case WebRequestError.ERROR_INVALID_CONTENT_ENCODING: {
                return ErrorType.ERROR_INVALID_CONTENT_ENCODING;
            }
            case WebRequestError.ERROR_UNKNOWN_HOST: {
                return ErrorType.ERROR_UNKNOWN_HOST;
            }
            case WebRequestError.ERROR_MALFORMED_URI: {
                return ErrorType.ERROR_MALFORMED_URI;
            }
            case WebRequestError.ERROR_UNKNOWN_PROTOCOL: {
                return ErrorType.ERROR_UNKNOWN_PROTOCOL;
            }
            case WebRequestError.ERROR_FILE_NOT_FOUND: {
                return ErrorType.ERROR_FILE_NOT_FOUND;
            }
            case WebRequestError.ERROR_FILE_ACCESS_DENIED: {
                return ErrorType.ERROR_FILE_ACCESS_DENIED;
            }
            case WebRequestError.ERROR_PROXY_CONNECTION_REFUSED: {
                return ErrorType.ERROR_PROXY_CONNECTION_REFUSED;
            }
            case WebRequestError.ERROR_UNKNOWN_PROXY_HOST: {
                return ErrorType.ERROR_UNKNOWN_PROXY_HOST;
            }
            case WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI: {
                return ErrorType.ERROR_SAFEBROWSING_MALWARE_URI;
            }
            case WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI: {
                return ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI;
            }
            case WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI: {
                return ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI;
            }
            case WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI: {
                return ErrorType.ERROR_SAFEBROWSING_PHISHING_URI;
            }
            case WebRequestError.ERROR_CATEGORY_UNKNOWN:
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

    public static String createErrorPageDataURI(Context context,
                                                String uri,
                                                int errorType) {
        String html = ErrorPages.INSTANCE.createErrorPage(
                context,
                fromGeckoErrorToErrorType(errorType),
                uri,
                R.raw.error_pages,
                R.raw.error_style);

        boolean showSSLAdvanced;
        switch (errorType) {
            case WebRequestError.ERROR_SECURITY_SSL:
            case WebRequestError.ERROR_SECURITY_BAD_CERT:
                showSSLAdvanced = true;
                break;
            default:
                showSSLAdvanced = false;
        }

        html = html
                .replace("%url%", uri)
                .replace("%advancedSSLStyle%", showSSLAdvanced ? "block" : "none");

        return "data:text/html;base64," + Base64.encodeToString(html.getBytes(), Base64.NO_WRAP);
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
