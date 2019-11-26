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

browser.webRequest.onBeforeRequest.addListener(
	redirectUrl,
	{ urls: targetUrls, types: ["main_frame"]},
	["blocking"]
);
