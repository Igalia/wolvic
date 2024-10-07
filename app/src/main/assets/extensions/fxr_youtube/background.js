const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Linux; Android 7.1.1; Quest) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/13.0.0.7.16.261647641 SamsungBrowser/4.0 Chrome/87.0.4280.66 Mobile VR Safari/537.36';
const targetUrls = [
    "https://*.youtube.com/*",
    "https://*.youtube-nocookie.com/*"
];

/**
 * Override UA. This is required to get the equirectangular video formats from Youtube.
 * Otherwise youtube uses custom 360 controls.
 */
function overrideUA(req) {
    for (const header of req.requestHeaders) {
        if (header.name.toLowerCase() === "user-agent") {
            header.value = CUSTOM_USER_AGENT;
        }
    }
    return { requestHeaders: req.requestHeaders };
}

browser.webRequest.onBeforeSendHeaders.addListener(
    overrideUA,
    { urls: targetUrls },
    ["blocking", "requestHeaders"]
);

/**
 * Always force a redirect  to the desktop site, otherwise we don't get 360 videos.
 */
function redirect(details) {
    const uri = new URL(details.url);
    const found = targetUrls.filter(function(pattern) {
      return new RegExp(pattern).test(uri);
    })

    if (found) {
        // We should not add the app param to the timedtext requests (captions),
        // otherwise they will fail.
        if (uri.href.includes(".com/api/timedtext?")) {
            return { };
        }

        // Add app=desktop to the query string if it's not already there. Don't do it for
        // the consent page as it breaks the request.
        if (!uri.searchParams.has("app") && !uri.hostname.startsWith("consent.youtube.com")) {
            uri.searchParams.append('app', 'desktop');
            return { redirectUrl: uri.href };
        }
    }

    return { };
}

browser.webRequest.onBeforeRequest.addListener(
    redirect,
    { urls: targetUrls },
    ["blocking"]
);
