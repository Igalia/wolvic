"use strict";

// Runs in the parent process with chrome privileges (built-in add-ons are
// privileged, so experiment_apis are allowed). GeckoView has no public page
// zoom API, but the top-level BrowsingContext supports the same reflow zoom
// that desktop Firefox uses for Ctrl +/-.
this.pageZoom = class extends ExtensionAPI {
  getAPI(context) {
    const { tabManager } = context.extension;

    return {
      pageZoom: {
        async setZoom(tabId, zoom) {
          tabManager.get(tabId).browser.browsingContext.fullZoom = zoom;
        },
      },
    };
  }
};
