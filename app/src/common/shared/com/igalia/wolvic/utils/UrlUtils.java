/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.utils;

import android.content.Context;
import android.util.Base64;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;
import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.search.SearchEngineWrapper;
import com.igalia.wolvic.telemetry.TelemetryService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

// This class refers from mozilla-mobile/focus-android
public class UrlUtils {

    public static String UNKNOWN_MIME_TYPE = "application/octet-stream";
    public static String EXTENSION_MIME_TYPE = "application/x-xpinstall";
    public static boolean isUnderTest = false;
    public static String TEST_SEARCH_URL = "http://testsearchengine.com/search?q=";

    public static String stripCommonSubdomains(@Nullable String host) {
        if (host == null) {
            return null;
        }

        // In contrast to desktop, we also strip mobile subdomains,
        // since its unlikely users are intentionally typing them
        int start = 0;

        if (host.startsWith("www.")) {
            start = 4;
        } else if (host.startsWith("mobile.")) {
            start = 7;
        } else if (host.startsWith("m.")) {
            start = 2;
        }

        return host.substring(start);
    }

    public static String stripProtocol(@Nullable String host) {
        if (host == null) {
            return "";
        }

        if (host.startsWith("data:")) {
            return "";
        }

        String result;
        int index = host.indexOf("://");
        if (index >= 0) {
            result = host.substring(index + 3);
        } else {
            result = host;
        }

        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    private static Pattern domainPattern = Pattern.compile("^(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)?[a-zA-Z0-9]+([\\-\\.]{1}[a-zA-Z0-9]+)*\\.[a-zA-Z]{2,24}(:[0-9]{1,5})?(\\/[^ ]*)?$");
    public static boolean isDomain(String text) {
        return domainPattern.matcher(text).find();
    }

    private static Pattern ipPattern = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])(:[0-9]+)?(/[^ ]*)?");
    private static Pattern localhostPattern = Pattern.compile("^(localhost)(:[0-9]+)?(/[^ ]*)?", Pattern.CASE_INSENSITIVE);
    public static boolean isIPUri(@Nullable String aUri) {
        if (aUri == null) {
            return false;
        }
        String uri = stripProtocol(aUri).trim();
        return localhostPattern.matcher(uri).find() || ipPattern.matcher(uri).find();
    }

    public static boolean isLocalIP(@Nullable String aUri) {
        if (!isIPUri(aUri)) {
            return false;
        }
        String uri = stripProtocol(aUri).trim();
        return uri.startsWith("10.") ||
               uri.startsWith("172.") ||
               uri.startsWith("192.168.") || //
               localhostPattern.matcher(uri).find();
    }

    public static boolean isPrivateAboutPage(@Nullable Context context,  @Nullable String uri) {
        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        byte[] privatePageBytes = InternalPages.createAboutPage(context, pageResources);
        return uri != null && uri.equals("data:text/html;base64," + Base64.encodeToString(privatePageBytes, Base64.NO_WRAP));
    }

    public static Boolean isHomeUri(@Nullable Context context, @Nullable String aUri) {
        return aUri != null && context != null && aUri.toLowerCase().startsWith(
                SettingsStore.getInstance(context).getHomepage()
        );
    }

    public static Boolean isDataUri(@Nullable String aUri) {
        return aUri != null && aUri.startsWith("data");
    }

    public static Boolean isFileUri(@Nullable String aUri) {
        return aUri != null && aUri.startsWith("file");
    }

    public static Boolean isBlobUri(@Nullable String aUri) {
        return aUri != null && aUri.startsWith("blob:");
    }

    public static Boolean isBlankUri(@Nullable Context context, @Nullable String aUri) {
        return context != null && aUri != null && aUri.equals(context.getString(R.string.about_blank));
    }

    public static @NonNull String getMimeTypeFromUrl(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (StringUtils.isEmpty(extension))
            return UNKNOWN_MIME_TYPE;
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return StringUtils.isEmpty(mimeType) ? UNKNOWN_MIME_TYPE : mimeType;
    }

    public static String titleBarUrl(@Nullable String aUri) {
        if (aUri == null) {
            return "";
        }

        if (URLUtil.isValidUrl(aUri)) {
            if (UrlUtils.isFileUri(aUri)) {
                File file = new File(aUri);
                return file.getName();

            } else {
                try {
                    URI uri = parseUri(aUri);
                    URL url = new URL(
                            uri.getScheme() != null ? uri.getScheme() : "",
                            uri.getAuthority() != null ? uri.getAuthority() : "",
                            "");
                    return url.toString();

                } catch (MalformedURLException | URISyntaxException e) {
                    return "";
                }
            }

        } else {
            return aUri;
        }
    }

    public static final String ABOUT_HISTORY = "about://history";

    public static boolean isHistoryUrl(@Nullable String url) {
        return url != null && url.equalsIgnoreCase(ABOUT_HISTORY);
    }

    public static final String ABOUT_BOOKMARKS = "about://bookmarks";

    public static boolean isBookmarksUrl(@Nullable String url) {
        return url != null && url.equalsIgnoreCase(ABOUT_BOOKMARKS);
    }

    public static final String ABOUT_DOWNLOADS = "about://downloads";

    public static boolean isDownloadsUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }

        return url.equalsIgnoreCase(ABOUT_DOWNLOADS);
    }

    public static final String ABOUT_ADDONS = "about://addons";

    public static boolean isAddonsUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }

        return url.equalsIgnoreCase(ABOUT_ADDONS);
    }

    public static final String WEB_EXTENSION_URL = "moz-extension://";

    public static boolean isWebExtensionUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }

        return url.startsWith(WEB_EXTENSION_URL);
    }

    public static final String ABOUT_PRIVATE = "about://privatebrowsing";

    public static boolean isPrivateUrl(@Nullable String url) {
        return url != null && url.equalsIgnoreCase(ABOUT_PRIVATE);
    }

    public static boolean isAboutPage(@Nullable String url) {
        return isHistoryUrl(url) || isBookmarksUrl(url) || isDownloadsUrl(url) || isAddonsUrl(url) || isPrivateUrl(url);
    }

    public static boolean isContentFeed(Context aContext, @Nullable String url) {
        String feed = aContext.getString(R.string.HOMEPAGE_URL);
        return UrlUtils.getHost(feed).equalsIgnoreCase(UrlUtils.getHost(url));
    }

    public static String getHost(String uri) {
        try {
            URL url = new URL(uri);
            return url.getHost();
        } catch (MalformedURLException e) {
            return uri;
        }
    }

    public static URI parseUri(String aUri) throws URISyntaxException {
        try {
            return new URI(aUri);
        } catch (URISyntaxException e) {
            if (!StringUtils.isEmpty(aUri) && StringUtils.charCount(aUri, '#') >= 2) {
                // Browsers are able to handle URLs with double # by ignoring everything after the
                // second # occurrence. But Java implementation considers it an invalid URL.
                // Remove everything after the second #.
                int index = aUri.indexOf("#", aUri.indexOf("#") + 1);
                return parseUri(aUri.substring(0, index));
            }
            throw e;
        }
    }

    private static String searchURLForText(@NonNull Context context, @NonNull String text) {
        return !isUnderTest ? SearchEngineWrapper.get(context).getSearchURL(text) : TEST_SEARCH_URL + text;
    }

    public static String urlForText(@NonNull Context context, @NonNull String text, @NonNull WSession.UrlUtilsVisitor visitor) {
        String url = text.trim();
        URI uri;
        try {
            uri = parseUri(url);
            if (!uri.isAbsolute()) {
                if (!isDomain(url) && !isIPUri(url))
                    return searchURLForText(context, url);
                uri = parseUri("http://" + url);
            }
            // This catches the special case of passing an URL with an invalid IP address
            if (uri.getHost() == null && uri.getAuthority() != null)
                return searchURLForText(context, url);
        } catch (URISyntaxException e) {
            return searchURLForText(context, url);
        }

        if (!isEngineSupportedScheme(uri, visitor)) {
            TelemetryService.urlBarEvent(false);
            return searchURLForText(context, url);
        }

        TelemetryService.urlBarEvent(true);
        return uri.toString();
    }

    // TODO Ideally we should use something like org.mozilla.fenix.components.AppLinksInterceptor for this
    public static boolean isEngineSupportedScheme(@NonNull URI uri, @NonNull WSession.UrlUtilsVisitor visitor) {
        String scheme = uri.getScheme();
        return scheme != null && visitor.isSupportedScheme(scheme);
    }
}
