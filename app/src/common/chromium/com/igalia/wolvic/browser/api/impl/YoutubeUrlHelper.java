package com.igalia.wolvic.browser.api.impl;

import android.net.Uri;
import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

import java.net.URI;

import org.chromium.url.GURL;

import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * In addition to desktop UA YouTube requires the query parameter "app=desktop" to display 360
 * videos correctly. This helper class implements the method to add this parameter to URL.
 */
public class YoutubeUrlHelper {
    public static String maybeRewriteYoutubeURL(GURL url) {
        if (!url.domainIs("youtube.com") && !url.domainIs("youtube-nocookie.com")) {
            return url.getSpec();
        }

        if (!url.getPath().equals("/watch")) {
            return url.getSpec();
        }

        return ensureAppIsSetToDesktop(Uri.parse(url.getSpec())).toString();
    }

    private static Uri ensureAppIsSetToDesktop(Uri uri) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String param : uri.getQueryParameterNames()) {
            if (param.equals("app")) {
                // skip existing app parameter, because we'll rewrite it later
                continue;
            }
            builder.appendQueryParameter(param, uri.getQueryParameter(param));
        }

        builder.appendQueryParameter("app", "desktop");
        return builder.build();
    }
}
