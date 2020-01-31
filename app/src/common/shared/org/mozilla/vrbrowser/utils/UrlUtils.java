/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.utils;

import android.content.Context;
import android.util.Base64;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
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

    private static Pattern domainPattern = Pattern.compile("^(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)?[a-z0-9]+([\\-\\.]{1}[a-z0-9]+)*\\.[a-z]{2,5}(:[0-9]{1,5})?(\\/.*)?$");
    public static boolean isDomain(String text) {
        return domainPattern.matcher(text).find();
    }

    public static boolean isPrivateAboutPage(@NonNull Context context,  @NonNull String uri) {
        InternalPages.PageResources pageResources = InternalPages.PageResources.create(R.raw.private_mode, R.raw.private_style);
        byte[] privatePageBytes = InternalPages.createAboutPage(context, pageResources);
        return uri.equals("data:text/html;base64," + Base64.encodeToString(privatePageBytes, Base64.NO_WRAP));
    }

    public static Boolean isHomeUri(@NonNull Context context, @Nullable String aUri) {
        return aUri != null && aUri.toLowerCase().startsWith(
                SettingsStore.getInstance(context).getHomepage()
        );
    }

    public static Boolean isDataUri(@NonNull String aUri) {
        return aUri.startsWith("data");
    }

    public static Boolean isBlankUri(@NonNull Context context, @NonNull String aUri) {
        return aUri.equals(context.getString(R.string.about_blank));
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
}
