package com.igalia.wolvic.browser.api.impl;

import org.chromium.url.GURL;

import java.util.ArrayList;

/**
 * In addition to desktop UA YouTube requires the query parameter "app=desktop" to display 360
 * videos correctly. This helper class implements the method to add this parameter to URL.
 */
public class YoutubeUrlHelper {
    public static GURL maybeRewriteYoutubeURL(GURL url) {
        if (!url.domainIs("youtube.com") && !url.domainIs("youtube-nocookie.com")) {
            return url;
        }

        if (!url.getPath().equals("/watch")) {
            return url;
        }
        
        // Java version of GURL doesn't expose the same ReplaceComponents functionality as the
        // native version, so we have to use string concatenation here.
        return new GURL(url.getScheme() + "://" + url.getHost() + url.getPath() + "?" +
                ensureAppIsSetToDesktop(url.getQuery()));
    }

    private static String ensureAppIsSetToDesktop(String query) {
        ArrayList<String> result = new ArrayList<>();
        String[] keyValuePairs = query.split("&");
        boolean foundAppKey = false;
        for (String param : keyValuePairs) {
            String[] keyValue = param.split("=");
            if (!keyValue[0].equals("app")) {
                result.add(param);
                continue;
            }

            if (foundAppKey) {
                // Do not add "app=desktop" twice
                continue;
            }

            foundAppKey = true;
            result.add("app=desktop");
        }
        if (!foundAppKey) {
            result.add("app=desktop");
        }
        return String.join("&", result);
    }
}
