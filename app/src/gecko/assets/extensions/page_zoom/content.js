// Top-level frames only (all_frames defaults to false). The app answers each
// connection with the zoom level saved for this page's host, and sends a new
// one whenever the user changes it.
function connect() {
  const port = browser.runtime.connectNative("pageZoom");
  port.onMessage.addListener(message => {
    if (message.type === "apply") {
      browser.runtime.sendMessage({ type: "apply", zoom: message.zoom });
    }
  });
}

connect();

// Ports are disconnected when a page enters the bfcache, so reconnect when it is restored.
window.addEventListener("pageshow", event => {
  if (event.persisted) {
    connect();
  }
});
