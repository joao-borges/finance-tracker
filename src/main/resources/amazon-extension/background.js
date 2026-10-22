// Service worker. On a sync request from the app (via bridge.js) it opens —
// or reuses — an Amazon tab, injects scraper.js there so requests carry the
// user's own Amazon session, and hands the parsed orders back to the app.
// Nothing is stored or sent anywhere else: the app's page posts the result to
// its own API.

const MARKETPLACES = {
    "www.amazon.ca": "CAD",
    "www.amazon.com": "USD",
};
const LOAD_TIMEOUT_MS = 30000;

// Progress lines from a scraping tab, routed to the port that asked for it.
const reporters = new Map();

chrome.runtime.onMessage.addListener((message, sender) => {
    if (message?.type === "ft-progress" && sender.tab) {
        reporters.get(sender.tab.id)?.(message.text);
    }
});

chrome.runtime.onConnect.addListener((port) => {
    if (port.name !== "ft-sync") {
        return;
    }
    port.onMessage.addListener(async (message) => {
        if (message?.type !== "sync") {
            return;
        }
        try {
            const result = await sync(message, (text) => port.postMessage({ type: "progress", text }));
            port.postMessage({ type: "done", ...result });
        } catch (failure) {
            port.postMessage({ type: "error", error: failure instanceof Error ? failure.message : String(failure) });
        }
    });
});

const waitForLoad = (tabId) => {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            chrome.tabs.onUpdated.removeListener(listener);
            reject(new Error("Amazon took too long to load"));
        }, LOAD_TIMEOUT_MS);
        const listener = (updatedId, change) => {
            if (updatedId === tabId && change.status === "complete") {
                clearTimeout(timer);
                chrome.tabs.onUpdated.removeListener(listener);
                resolve();
            }
        };
        chrome.tabs.onUpdated.addListener(listener);
    });
};

async function sync({ marketplace, since }, report) {
    const currency = MARKETPLACES[marketplace];
    if (!currency) {
        throw new Error(`Unsupported marketplace: ${marketplace}`);
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(since ?? "")) {
        throw new Error("A start date (YYYY-MM-DD) is required");
    }
    const origin = `https://${marketplace}`;
    const [existing] = await chrome.tabs.query({ url: `${origin}/*` });
    let tab = existing;
    let created = false;
    if (!tab) {
        report(`Opening ${marketplace} in a background tab…`);
        tab = await chrome.tabs.create({ url: `${origin}/gp/css/order-history`, active: false });
        created = true;
        await waitForLoad(tab.id);
    }
    reporters.set(tab.id, report);
    try {
        await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["scraper.js"] });
        const [injection] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            func: (options) => globalThis.ftScrapeAmazonOrders(options),
            args: [{ since, currency }],
        });
        const result = injection?.result;
        if (result?.reason === "signed-out") {
            // Leave the tab open and in front so the user can sign in.
            created = false;
            await chrome.tabs.update(tab.id, { active: true, url: `${origin}/gp/css/order-history` });
            throw new Error(`Sign in to ${marketplace} in the tab that just opened, then sync again.`);
        }
        if (!result?.ok) {
            throw new Error(result?.error ?? "Reading the Amazon orders failed");
        }
        return { marketplace, orders: result.orders, warnings: result.warnings };
    } finally {
        reporters.delete(tab.id);
        if (created) {
            chrome.tabs.remove(tab.id).catch(() => {});
        }
    }
}
