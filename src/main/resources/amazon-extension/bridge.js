// Content script on the Finance Tracker page. Announces the extension (a data
// attribute the app reads) and relays a sync request from the page to the
// background worker, streaming progress and the final result back via
// window.postMessage. Only messages from this same page are honoured.
(() => {
    const version = chrome.runtime.getManifest().version;
    document.documentElement.dataset.ftAmazonExtension = version;

    window.addEventListener("message", (event) => {
        if (event.source !== window || event.origin !== window.location.origin) {
            return;
        }
        const data = event.data;
        if (!data || data.source !== "ft-app" || data.type !== "amazon-sync") {
            return;
        }
        let finished = false;
        const reply = (payload) => {
            window.postMessage({ source: "ft-amazon-extension", requestId: data.requestId, ...payload }, window.location.origin);
        };
        const port = chrome.runtime.connect({ name: "ft-sync" });
        port.onMessage.addListener((message) => {
            reply(message);
            if (message.type === "done" || message.type === "error") {
                finished = true;
                port.disconnect();
            }
        });
        port.onDisconnect.addListener(() => {
            if (!finished) {
                reply({ type: "error", error: chrome.runtime.lastError?.message ?? "The extension stopped unexpectedly" });
            }
        });
        port.postMessage({ type: "sync", marketplace: data.marketplace, since: data.since });
    });
})();
