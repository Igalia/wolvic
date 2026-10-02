// Experiment APIs are only available here, not in content scripts, so the
// content script forwards the zoom level and we resolve its tab.
browser.runtime.onMessage.addListener((message, sender) => {
  if (message.type !== "apply" || !sender.tab) {
    return;
  }
  browser.pageZoom.setZoom(sender.tab.id, message.zoom).catch(e => {
    console.error(`[page-zoom] setZoom failed for tab=${sender.tab.id}: ${e}`);
  });
});
