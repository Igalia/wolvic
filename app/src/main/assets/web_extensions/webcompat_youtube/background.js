const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12) AppleWebKit/602.1.21 (KHTML, like Gecko) Version/9.2 Safari/602.1.21';
const targetUrls = [
    "https://*.youtube.com/*",
    "https://*.youtube-nocookie.com/*"
];

/**
 * 1. Disable YouTube's Polymer layout (which makes YouTube very slow in non-Chrome browsers)
 *    via a query-string parameter in the URL.
 * 2. Rewrite YouTube URLs from `m.youtube.com` -> `youtube.com` (to avoid serving YouTube's
 *    video pages intended for mobile phones, as linked from Google search results).
 */
function redirectUrl(req) {
    let redirect = false;
    const url = new URL(req.url);
    if (url.host.startsWith("m.")) {
        url.host = url.host.replace("m.", "www.");
        redirect = true;
    }
    if (!url.searchParams.get("disable_polymer")) {
        url.searchParams.set("disable_polymer", "1");
        redirect = true;
    }
    if (!redirect) {
        return null;
    }
    return { redirectUrl: url.toString() };
}

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

browser.webRequest.onBeforeRequest.addListener(
    redirectUrl,
    { urls: targetUrls, types: ["main_frame"]},
    ["blocking"]
);

browser.webRequest.onBeforeSendHeaders.addListener(
    overrideUA,
    { urls: targetUrls },
    ["blocking", "requestHeaders"]
  );
  