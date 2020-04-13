/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Patterns;
import android.webkit.URLUtil;

import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;


// This class refers from mozilla-mobile/focus-android
public class UrlUtils {

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

    private static Pattern domainPattern = Pattern.compile("^(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)?[a-zA-Z0-9]+([\\-\\.]{1}[a-zA-Z0-9]+)*\\.[a-zA-Z]{2,5}(:[0-9]{1,5})?(\\/[^ ]*)?$");
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

    public static Boolean isBlankUri(@Nullable Context context, @Nullable String aUri) {
        return context != null && aUri != null && aUri.equals(context.getString(R.string.about_blank));
    }

    public static String titleBarUrl(@Nullable String aUri) {
        if (aUri == null) {
            return "";
        }

        if (URLUtil.isValidUrl(aUri)) {
            try {
                URI uri = URI.create(aUri);
                URL url = new URL(
                        uri.getScheme() != null ? uri.getScheme() : "",
                        uri.getAuthority() != null ? uri.getAuthority() : "",
                        "");
                return url.toString();

            } catch (MalformedURLException | IllegalArgumentException e) {
                return "";
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

    public static final String ABOUT_PRIVATE = "about://privatebrowsing";

    public static boolean isPrivateUrl(@Nullable String url) {
        return url != null && url.equalsIgnoreCase(ABOUT_PRIVATE);
    }

    public static boolean isAboutPage(@Nullable String url) {
        return isHistoryUrl(url) || isBookmarksUrl(url) || isDownloadsUrl(url) || isPrivateUrl(url);
    }

    public static boolean isContentFeed(Context aContext, @Nullable String url) {
        String feed = aContext.getString(R.string.homepage_url);
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
}
