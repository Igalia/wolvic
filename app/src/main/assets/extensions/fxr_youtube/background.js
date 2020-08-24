const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Linux; Android 7.1.1; Quest) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/11.1.0.1.64.238873877 SamsungBrowser/4.0 Chrome/84.0.4147.125 Mobile VR Safari/537.36';
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
  