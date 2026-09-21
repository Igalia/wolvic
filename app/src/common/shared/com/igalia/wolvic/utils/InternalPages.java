package com.igalia.wolvic.utils;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.api.WWebRequestError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import mozilla.components.browser.errorpages.ErrorType;

import org.chromium.net.NetError;

public class InternalPages {

    private static ErrorType fromSessionErrorToErrorType(int error) {
        switch(error) {
            case WWebRequestError.ERROR_SECURITY_SSL: {
                return ErrorType.ERROR_SECURITY_SSL;
            }
            case NetError.ERR_CERT_DATE_INVALID:
            case WWebRequestError.ERROR_SECURITY_BAD_CERT: {
                return ErrorType.ERROR_SECURITY_BAD_CERT;
            }
            case WWebRequestError.ERROR_NET_INTERRUPT: {
                return ErrorType.ERROR_NET_INTERRUPT;
            }
            case WWebRequestError.ERROR_NET_TIMEOUT: {
                return ErrorType.ERROR_NET_TIMEOUT;
            }
            case WWebRequestError.ERROR_CONNECTION_REFUSED: {
                return ErrorType.ERROR_CONNECTION_REFUSED;
            }
            case WWebRequestError.ERROR_UNKNOWN_SOCKET_TYPE: {
                return ErrorType.ERROR_UNKNOWN_SOCKET_TYPE;
            }
            case WWebRequestError.ERROR_REDIRECT_LOOP: {
                return ErrorType.ERROR_REDIRECT_LOOP;
            }
            case WWebRequestError.ERROR_OFFLINE: {
                return ErrorType.ERROR_OFFLINE;
            }
            case WWebRequestError.ERROR_PORT_BLOCKED: {
                return ErrorType.ERROR_PORT_BLOCKED;
            }
            case WWebRequestError.ERROR_NET_RESET: {
                return ErrorType.ERROR_NET_RESET;
            }
            case WWebRequestError.ERROR_UNSAFE_CONTENT_TYPE: {
                return ErrorType.ERROR_UNSAFE_CONTENT_TYPE;
            }
            case WWebRequestError.ERROR_CORRUPTED_CONTENT: {
                return ErrorType.ERROR_CORRUPTED_CONTENT;
            }
            case WWebRequestError.ERROR_CONTENT_CRASHED: {
                return ErrorType.ERROR_CONTENT_CRASHED;
            }
            case WWebRequestError.ERROR_INVALID_CONTENT_ENCODING: {
                return ErrorType.ERROR_INVALID_CONTENT_ENCODING;
            }
            case WWebRequestError.ERROR_UNKNOWN_HOST: {
                return ErrorType.ERROR_UNKNOWN_HOST;
            }
            case WWebRequestError.ERROR_MALFORMED_URI: {
                return ErrorType.ERROR_MALFORMED_URI;
            }
            case WWebRequestError.ERROR_UNKNOWN_PROTOCOL: {
                return ErrorType.ERROR_UNKNOWN_PROTOCOL;
            }
            case WWebRequestError.ERROR_FILE_NOT_FOUND: {
                return ErrorType.ERROR_FILE_NOT_FOUND;
            }
            case WWebRequestError.ERROR_FILE_ACCESS_DENIED: {
                return ErrorType.ERROR_FILE_ACCESS_DENIED;
            }
            case WWebRequestError.ERROR_PROXY_CONNECTION_REFUSED: {
                return ErrorType.ERROR_PROXY_CONNECTION_REFUSED;
            }
            case WWebRequestError.ERROR_UNKNOWN_PROXY_HOST: {
                return ErrorType.ERROR_UNKNOWN_PROXY_HOST;
            }
            case WWebRequestError.ERROR_SAFEBROWSING_MALWARE_URI: {
                return ErrorType.ERROR_SAFEBROWSING_MALWARE_URI;
            }
            case WWebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI: {
                return ErrorType.ERROR_SAFEBROWSING_UNWANTED_URI;
            }
            case WWebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI: {
                return ErrorType.ERROR_SAFEBROWSING_HARMFUL_URI;
            }
            case WWebRequestError.ERROR_SAFEBROWSING_PHISHING_URI: {
                return ErrorType.ERROR_SAFEBROWSING_PHISHING_URI;
            }
            case WWebRequestError.ERROR_CATEGORY_UNKNOWN:
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
                                                @Nullable String uri,
                                                int sessionError) {

        return "data:text/html;base64," + Base64.encodeToString(createErrorPageData(context, uri, sessionError), Base64.NO_WRAP);
    }

    public static byte[] createErrorPageData(Context context,
                                             @Nullable String uri,
                                             int sessionError) {
        String html = readRawResourceString(context, R.raw.error_pages);
        String css = readRawResourceString(context, R.raw.error_style);

        boolean showSSLAdvanced;
        switch (sessionError) {
            case NetError.ERR_CERT_DATE_INVALID:
            case WWebRequestError.ERROR_SECURITY_SSL:
            case WWebRequestError.ERROR_SECURITY_BAD_CERT:
                showSSLAdvanced = true;
                break;
            default:
                showSSLAdvanced = false;
        }

        ErrorType errorType = fromSessionErrorToErrorType(sessionError);
        html = html
                .replace("%button%", context.getString(errorType.getRefreshButtonRes()))
                .replace("%messageShort%", context.getString(errorType.getTitleRes()))
                .replace("%messageLong%", context.getString(errorType.getMessageRes(), uri))
                .replace("<ul>", "<ul role=\"presentation\">")
                .replace("%css%", css)
                .replace("%advancedSSLStyle%", showSSLAdvanced ? "block" : "none")
                .replace("%pageTitle%", uri != null ? uri : "");

        if (uri != null) {
            html = html.replace("%url%", uri);
        }

        return html.getBytes();
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
