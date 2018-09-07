package org.mozilla.vrbrowser;

import android.content.Context;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadError;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadErrorCategory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class InternalPages {

    public static class LocalizedResources {
        public int titleRes;
        public int messageRes;

        LocalizedResources(int error, int description) {
            titleRes = error;
            messageRes = description;
        }

        public static LocalizedResources create(int error, int description) {
            return new LocalizedResources(error, description);
        }
    }

    private static LocalizedResources fromGeckoErrorToLocalizedResources(@LoadErrorCategory int category, @LoadError int error) {
        switch(category) {
            case NavigationDelegate.ERROR_CATEGORY_UNKNOWN: {
                return LocalizedResources.create(
                        R.string.error_generic_title,
                        R.string.error_generic_message);
            }
            case NavigationDelegate.ERROR_CATEGORY_SECURITY: {
                switch (error) {
                    case NavigationDelegate.ERROR_SECURITY_SSL: {
                        return LocalizedResources.create(
                                R.string.error_security_ssl_title,
                                R.string.error_security_ssl_message);
                    }
                    case NavigationDelegate.ERROR_SECURITY_BAD_CERT: {
                        return LocalizedResources.create(
                                R.string.error_security_bad_cert_title,
                                R.string.error_security_bad_cert_message);
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_NETWORK: {
                switch (error) {
                    case NavigationDelegate.ERROR_NET_INTERRUPT: {
                        return LocalizedResources.create(
                                R.string.error_net_interrupt_title,
                                R.string.error_net_interrupt_message);
                    }
                    case NavigationDelegate.ERROR_NET_TIMEOUT: {
                        return LocalizedResources.create(
                                R.string.error_net_timeout_title,
                                R.string.error_net_timeout_message);
                    }
                    case NavigationDelegate.ERROR_CONNECTION_REFUSED: {
                        return LocalizedResources.create(
                                R.string.error_connection_failure_title,
                                R.string.error_connection_failure_message);
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_SOCKET_TYPE: {
                        return LocalizedResources.create(
                                R.string.error_unknown_socket_type_title,
                                R.string.error_unknown_socket_type_message);
                    }
                    case NavigationDelegate.ERROR_REDIRECT_LOOP: {
                        return LocalizedResources.create(
                                R.string.error_redirect_loop_title,
                                R.string.error_redirect_loop_message);
                    }
                    case NavigationDelegate.ERROR_OFFLINE: {
                        return LocalizedResources.create(
                                R.string.error_offline_title,
                                R.string.error_offline_message);
                    }
                    case NavigationDelegate.ERROR_PORT_BLOCKED: {
                        return LocalizedResources.create(
                                R.string.error_port_blocked_title,
                                R.string.error_port_blocked_message);
                    }
                    case NavigationDelegate.ERROR_NET_RESET: {
                        return LocalizedResources.create(
                                R.string.error_net_reset_title,
                                R.string.error_net_reset_message);
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_CONTENT: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNSAFE_CONTENT_TYPE: {
                        return LocalizedResources.create(
                                R.string.error_unsafe_content_type_title,
                                R.string.error_unsafe_content_type_message);
                    }
                    case NavigationDelegate.ERROR_CORRUPTED_CONTENT: {
                        return LocalizedResources.create(
                                R.string.error_corrupted_content_title,
                                R.string.error_corrupted_content_message);
                    }
                    case NavigationDelegate.ERROR_CONTENT_CRASHED: {
                        return LocalizedResources.create(
                                R.string.error_content_crashed_title,
                                R.string.error_content_crashed_message);
                    }
                    case NavigationDelegate.ERROR_INVALID_CONTENT_ENCODING: {
                        return LocalizedResources.create(
                                R.string.error_invalid_content_encoding_title,
                                R.string.error_invalid_content_encoding_message);
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_URI: {
                switch (error) {
                    case NavigationDelegate.ERROR_UNKNOWN_HOST: {
                        return LocalizedResources.create(
                                R.string.error_unknown_host_title,
                                R.string.error_unknown_host_message);
                    }
                    case NavigationDelegate.ERROR_MALFORMED_URI: {
                        return LocalizedResources.create(
                                R.string.error_malformed_uri_title,
                                R.string.error_malformed_uri_message);
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROTOCOL: {
                        return LocalizedResources.create(
                                R.string.error_unknown_protocol_title,
                                R.string.error_unknown_protocol_message);
                    }
                    case NavigationDelegate.ERROR_FILE_NOT_FOUND: {
                        return LocalizedResources.create(
                                R.string.error_file_not_found_title,
                                R.string.error_file_not_found_message);
                    }
                    case NavigationDelegate.ERROR_FILE_ACCESS_DENIED: {
                        return LocalizedResources.create(
                                R.string.error_file_access_denied_title,
                                R.string.error_file_access_denied_message);
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_PROXY: {
                switch (error) {
                    case NavigationDelegate.ERROR_PROXY_CONNECTION_REFUSED: {
                        return LocalizedResources.create(
                                R.string.error_proxy_connection_refused_title,
                                R.string.error_proxy_connection_refused_message);
                    }
                    case NavigationDelegate.ERROR_UNKNOWN_PROXY_HOST: {
                        return LocalizedResources.create(
                                R.string.error_unknown_proxy_host_title,
                                R.string.error_unknown_proxy_host_message);
                    }
                }
            }
            case NavigationDelegate.ERROR_CATEGORY_SAFEBROWSING: {
                switch (error) {
                    case NavigationDelegate.ERROR_SAFEBROWSING_MALWARE_URI: {
                        return LocalizedResources.create(
                                R.string.error_safe_browsing_malware_uri_title,
                                R.string.error_safe_browsing_malware_uri_message);
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_UNWANTED_URI: {
                        return LocalizedResources.create(
                                R.string.error_safe_browsing_unwanted_uri_title,
                                R.string.error_safe_browsing_unwanted_uri_message);
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_HARMFUL_URI: {
                        return LocalizedResources.create(
                                R.string.error_safe_harmful_uri_title,
                                R.string.error_safe_harmful_uri_message);
                    }
                    case NavigationDelegate.ERROR_SAFEBROWSING_PHISHING_URI: {
                        return LocalizedResources.create(
                                R.string.error_safe_phishing_uri_title,
                                R.string.error_safe_phishing_uri_message);
                    }
                }
            }
            default: {
                return LocalizedResources.create(
                        R.string.error_generic_title,
                        R.string.error_generic_message);
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

    public static byte[] createErrorPage(Context context,
                        String uri,
                        PageResources resources,
                        @LoadErrorCategory int errorCategory,
                        @LoadError int errorType) {
        LocalizedResources localizedData = fromGeckoErrorToLocalizedResources(errorCategory, errorType);

        String html = readRawResourceString(context, resources.html);
        String css = readRawResourceString(context, resources.css);

        html = html
                .replace("%page-title%", context.getString(R.string.errorpage_title))
                .replace("%button%", context.getString(R.string.errorpage_refresh))
                .replace("%messageShort%", context.getString(localizedData.titleRes))
                .replace("%messageLong%", context.getString(localizedData.messageRes, uri))
                .replace("%css%", css);

        return html.getBytes();
    }

    public static byte[] createAboutPage(Context context,
                                         PageResources resources) {
        String html = readRawResourceString(context, resources.html);
        String css = readRawResourceString(context, resources.css);

        html = html
                .replace("%page-title%", context.getString(R.string.private_browsing_title))
                .replace("%page-body%", context.getString(R.string.private_browsing_body))
                .replace("%css%", css);

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
