/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.utils;

import androidx.annotation.Nullable;

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
}
