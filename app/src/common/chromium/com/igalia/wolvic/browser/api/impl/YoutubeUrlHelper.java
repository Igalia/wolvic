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

        // The consent page breaks with the parameter.
        if (url.getHost().startsWith("consent.")) {
            return url.getSpec();
        }

        // Already in desktop mode: return the spec verbatim so callers can rely on
        // this being idempotent and never re-trigger a navigation.
        Uri uri = Uri.parse(url.getSpec());
        if ("desktop".equals(uri.getQueryParameter("app"))) {
            return url.getSpec();
        }

        return ensureAppIsSetToDesktop(uri).toString();
    }

    /**
     * Whether a same document navigation to the URL needs the rewrite too. Watch pages do, as
     * the player picks the video formats, so the projection of 360 videos, when it is loaded;
     * and so does anything in the mobile site. The rest of the desktop site keeps being the
     * desktop site without it.
     */
    public static boolean needsRewriteInSameDocument(GURL url) {
        return url.getPath().equals("/watch") || url.getHost().equals("m.youtube.com");
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
