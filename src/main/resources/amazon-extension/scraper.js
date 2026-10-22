// Injected into an Amazon tab by background.js (extension isolated world).
// Walks the order history newest-first and reads each order's printable
// invoice, which lists the items and every card charge Amazon made for the
// order. Requests run from the Amazon page itself, so they use the user's own
// signed-in session. Parsing works on text lines, not CSS classes, because
// Amazon's markup changes often and the invoice wording rarely does.
(() => {
    const MONEY = String.raw`(?:CDN\$|CA\$|C\$|US\$|\$)\s?(-?[\d,]+\.\d{2})`;
    const DATE = String.raw`([A-Z][a-z]+\.? \d{1,2}, \d{4})`;
    const ORDER_NUMBER = /\b(\d{3}-\d{7}-\d{7})\b/g;
    const ITEM_LINE = /^(\d+)\s*of:\s*(.*)$/i;
    const PRICE_LINE = new RegExp(`^${MONEY}$`);
    const CHARGE = new RegExp(
        String.raw`(?:(Visa|Master[Cc]ard|American Express|AmEx|Amex|Discover|JCB)\s+)?ending in\s+(\d{4}):?\s*`
            + DATE + String.raw`:?\s*` + MONEY,
        "g",
    );
    const BLOCK_TAGS = new Set([
        "ADDRESS", "ARTICLE", "ASIDE", "BLOCKQUOTE", "BR", "DD", "DIV", "DL", "DT", "FIELDSET", "FOOTER", "FORM",
        "H1", "H2", "H3", "H4", "H5", "H6", "HEADER", "HR", "LI", "MAIN", "NAV", "OL", "P", "PRE", "SECTION",
        "TABLE", "TBODY", "TD", "TFOOT", "TH", "THEAD", "TR", "UL",
    ]);
    const SKIPPED_TAGS = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE"]);
    const REQUEST_GAP_MS = 250;
    const MAX_PAGES_PER_YEAR = 100;

    class SignedOut extends Error {}

    const sleep = (ms) => {
        return new Promise((resolve) => setTimeout(resolve, ms));
    };

    const amount = (text) => {
        return Number(text.replace(/,/g, ""));
    };

    // "September 6, 2026" / "Sep. 6, 2026" → "2026-09-06"; null when unreadable.
    const isoDate = (text) => {
        const parsed = new Date(text.replace(".", ""));
        if (Number.isNaN(parsed.getTime())) {
            return null;
        }
        const month = String(parsed.getMonth() + 1).padStart(2, "0");
        const day = String(parsed.getDate()).padStart(2, "0");
        return `${parsed.getFullYear()}-${month}-${day}`;
    };

    // Visible text of a (parsed, unrendered) document as lines, breaking at
    // block elements and table cells — innerText needs layout, which a
    // DOMParser document never gets.
    const linesOf = (root) => {
        const lines = [""];
        const walk = (node) => {
            if (node.nodeType === Node.TEXT_NODE) {
                lines[lines.length - 1] += node.nodeValue;
                return;
            }
            if (node.nodeType !== Node.ELEMENT_NODE || SKIPPED_TAGS.has(node.tagName)) {
                return;
            }
            const block = BLOCK_TAGS.has(node.tagName);
            if (block) {
                lines.push("");
            }
            node.childNodes.forEach(walk);
            if (block) {
                lines.push("");
            }
        };
        walk(root);
        return lines.map((line) => line.replace(/\s+/g, " ").trim()).filter((line) => line !== "");
    };

    // One invoice's lines → { orderNumber, orderedOn, total, currency, items, charges }.
    const parseInvoiceLines = (lines, orderNumber, currency) => {
        const flat = lines.join(" ");
        const placed = new RegExp(String.raw`Order Placed:?\s*` + DATE, "i").exec(flat);
        const total = new RegExp(String.raw`(?:Order|Grand) Total:?\s*` + MONEY, "i").exec(flat);

        const items = [];
        lines.forEach((line, index) => {
            const match = ITEM_LINE.exec(line);
            if (!match) {
                return;
            }
            const title = match[2] || lines[index + 1] || "";
            let unitPrice = null;
            for (let next = index + 1; next < Math.min(lines.length, index + 10); next++) {
                if (ITEM_LINE.test(lines[next])) {
                    break;
                }
                const price = PRICE_LINE.exec(lines[next]);
                if (price) {
                    unitPrice = amount(price[1]);
                    break;
                }
            }
            if (title) {
                items.push({ title, quantity: Number(match[1]), unitPrice });
            }
        });

        const charges = [];
        for (const match of flat.matchAll(CHARGE)) {
            const chargedOn = isoDate(match[3]);
            const value = amount(match[4]);
            if (chargedOn && value > 0) {
                charges.push({
                    chargedOn,
                    amount: value,
                    cardLabel: `${match[1] ?? "Card"} ending in ${match[2]}`,
                    cardLast4: match[2],
                });
            }
        }

        return {
            orderNumber,
            orderedOn: placed ? isoDate(placed[1]) : null,
            total: total ? amount(total[1]) : null,
            currency: /US\$/.test(flat) ? "USD" : currency,
            items,
            charges,
        };
    };

    const fetchPage = async (path) => {
        const response = await fetch(path, { credentials: "include" });
        if (response.url.includes("/ap/signin")) {
            throw new SignedOut();
        }
        const html = await response.text();
        if (/id="ap_email"|name="signIn"/.test(html)) {
            throw new SignedOut();
        }
        return html;
    };

    // Order numbers on one page of the order history, in page order.
    const orderNumbersOn = (html) => {
        return [...new Set([...html.matchAll(ORDER_NUMBER)].map((match) => match[1]))];
    };

    globalThis.ftAmazonInvoice = { parseInvoiceLines };

    globalThis.ftScrapeAmazonOrders = async ({ since, currency }) => {
        const progress = (text) => {
            chrome.runtime.sendMessage({ type: "ft-progress", text }).catch(() => {});
        };
        const orders = [];
        const warnings = [];
        const seen = new Set();
        let reachedStart = false;
        try {
            const firstYear = Number(since.slice(0, 4));
            for (let year = new Date().getFullYear(); year >= firstYear && !reachedStart; year--) {
                for (let page = 0; page < MAX_PAGES_PER_YEAR && !reachedStart; page++) {
                    progress(`Reading ${year} orders, page ${page + 1}…`);
                    const listing = await fetchPage(`/your-orders/orders?timeFilter=year-${year}&startIndex=${page * 10}`);
                    const numbers = orderNumbersOn(listing).filter((number) => !seen.has(number));
                    if (numbers.length === 0) {
                        break;
                    }
                    for (const number of numbers) {
                        seen.add(number);
                        await sleep(REQUEST_GAP_MS);
                        progress(`Reading order ${number} (${orders.length + 1} so far)…`);
                        const invoice = await fetchPage(`/gp/css/summary/print.html?orderID=${number}`);
                        const parsed = new DOMParser().parseFromString(invoice, "text/html");
                        const order = parseInvoiceLines(linesOf(parsed.body), number, currency);
                        if (!order.orderedOn) {
                            warnings.push(`${number}: couldn't read the invoice`);
                            continue;
                        }
                        if (order.orderedOn < since) {
                            // The history is newest-first: everything after this is older still.
                            reachedStart = true;
                            break;
                        }
                        if (order.charges.length === 0) {
                            warnings.push(`${number}: no card charges yet (not shipped, or paid another way)`);
                        }
                        orders.push(order);
                    }
                }
            }
            return { ok: true, orders, warnings };
        } catch (failure) {
            if (failure instanceof SignedOut) {
                return { ok: false, reason: "signed-out" };
            }
            return { ok: false, error: failure instanceof Error ? failure.message : String(failure) };
        }
    };
})();
