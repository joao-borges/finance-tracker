// Talks to the Finance Tracker Amazon browser extension. Its content script
// (bridge.js) marks <html data-ft-amazon-extension="<version>"> on this page
// and relays window messages to the extension, which reads the orders from an
// Amazon tab using the user's own Amazon session and streams the result back.

import type { AmazonMarketplace, ScrapedAmazonOrder } from "./api";

export interface AmazonSyncResult {
    marketplace: string;
    orders: ScrapedAmazonOrder[];
    warnings: string[];
}

interface ExtensionMessage {
    source: "ft-amazon-extension";
    requestId: string;
    type: "progress" | "done" | "error";
    text?: string;
    error?: string;
    marketplace?: string;
    orders?: ScrapedAmazonOrder[];
    warnings?: string[];
}

/** The installed extension's version, or null when it isn't installed (or not enabled for this site). */
export function installedExtensionVersion(): string | null {
    return document.documentElement.dataset.ftAmazonExtension ?? null;
}

/** Ask the extension to read every Amazon order placed on or after {@code since} (YYYY-MM-DD). */
export function syncAmazonOrders(
    marketplace: AmazonMarketplace,
    since: string,
    onProgress: (text: string) => void,
): Promise<AmazonSyncResult> {
    // Not crypto.randomUUID(): that needs a secure context, and the app may be opened over plain http on the LAN.
    const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return new Promise((resolve, reject) => {
        const listener = (event: MessageEvent<ExtensionMessage>) => {
            const data = event.data;
            if (event.source !== window || data?.source !== "ft-amazon-extension" || data.requestId !== requestId) {
                return;
            }
            if (data.type === "progress") {
                onProgress(data.text ?? "");
                return;
            }
            window.removeEventListener("message", listener);
            if (data.type === "done") {
                resolve({ marketplace: data.marketplace ?? marketplace, orders: data.orders ?? [], warnings: data.warnings ?? [] });
            } else {
                reject(new Error(data.error ?? "The extension reported an error"));
            }
        };
        window.addEventListener("message", listener);
        window.postMessage({ source: "ft-app", type: "amazon-sync", requestId, marketplace, since }, window.location.origin);
    });
}
