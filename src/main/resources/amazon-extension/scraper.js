// Injected into an Amazon tab by background.js (extension isolated world).
// Walks the order history newest-first and reads each order's printable
// invoice for its date, total and items. Card charges come from Your Payments →
// Transactions, which lists every charge with its date, card and order number
// (current invoices no longer list them). Requests run from the Amazon page
// itself, so they use the user's own signed-in session. Parsing prefers
// Amazon's data-component markers and falls back to text lines, because the
// markup changes often and the wording rarely does.
(() => {
    const MONEY = String.raw`(?:CDN\$|CA\$|C\$|US\$|\$)\s?(-?[\d,]+\.\d{2})`;
    const DATE = String.raw`([A-Z][a-z]+\.? \d{1,2}, \d{4})`;
    const ORDER_NUMBER = /\b(\d{3}-\d{7}-\d{7})\b/g;
    // Amazon pages embed this example order number in their own scripts.
    const PLACEHOLDER_ORDER = /^000-/;
    const ITEM_LINE = /^(\d+)\s*of:\s*(.*)$/i;
    const PRICE_LINE = new RegExp(`^${MONEY}$`);
    const CHARGE = new RegExp(
        String.raw`(?:(Visa|Master[Cc]ard|American Express|AmEx|Amex|Discover|JCB)\s+)?ending in\s+(\d{4}):?\s*`
            + DATE + String.raw`:?\s*` + MONEY,
        "g",
    );
    // Your Payments → Transactions rows: a date heading, then per charge a
    // card line ("American Express ****1004"), a signed amount, optionally
    // "Pending", then one or more "Order #…" lines. Only the marketplace's own
    // dollar amounts are read — other stores' charges (R$, £…) have no order here.
    const DATE_LINE = new RegExp(`^${DATE}$`);
    const CARD_LINE = /^(.*?)\s*\*{4}(\d{4})$/;
    const AMOUNT_LINE = /^([+-])(?:CDN\$|CA\$|C\$|US\$|\$)\s?([\d,]+\.\d{2})$/;
    const CHARGE_ORDER_LINE = /^(Refund: )?Order #\s*(\d{3}-\d{7}-\d{7})$/;
    const NEXT_PAGE = 'input[name^="ppw-widgetEvent:DefaultNextPageNavigationEvent"]';
    const BLOCK_TAGS = new Set([
        "ADDRESS", "ARTICLE", "ASIDE", "BLOCKQUOTE", "BR", "DD", "DIV", "DL", "DT", "FIELDSET", "FOOTER", "FORM",
        "H1", "H2", "H3", "H4", "H5", "H6", "HEADER", "HR", "LI", "MAIN", "NAV", "OL", "P", "PRE", "SECTION",
        "TABLE", "TBODY", "TD", "TFOOT", "TH", "THEAD", "TR", "UL",
    ]);
    const SKIPPED_TAGS = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE"]);
    const REQUEST_GAP_MS = 250;
    const MAX_PAGES_PER_YEAR = 100;
    const MAX_PAYMENT_PAGES = 100;

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

    const textOf = (element) => {
        return (element?.textContent ?? "").replace(/\s+/g, " ").trim();
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

    // Current invoices mark each purchased item with data-component attributes;
    // quantity is blank when it is 1.
    const componentItems = (doc) => {
        return [...doc.querySelectorAll('[data-component="purchasedItems"]')]
            .map((element) => {
                const price = element.querySelector('[data-component="unitPrice"] .a-offscreen')
                    ?? element.querySelector('[data-component="unitPrice"]');
                const priceMatch = new RegExp(MONEY).exec(textOf(price));
                const quantity = Number.parseInt(textOf(element.querySelector('[data-component="quantity"]')), 10);
                return {
                    title: textOf(element.querySelector('[data-component="itemTitle"]')),
                    quantity: Number.isNaN(quantity) ? 1 : quantity,
                    unitPrice: priceMatch ? amount(priceMatch[1]) : null,
                };
            })
            .filter((item) => item.title !== "");
    };

    // Older invoices: "2 of: Title" followed by the unit price a few lines on.
    const lineItems = (lines) => {
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
        return items;
    };

    // Older invoices also listed each card charge ("Visa ending in 1234: May 2, 2026: $12.34").
    const invoiceCharges = (flat) => {
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
        return charges;
    };

    // One invoice document → { orderNumber, orderedOn, total, currency, items, charges }.
    const parseInvoice = (doc, orderNumber, currency) => {
        const lines = linesOf(doc.body);
        const flat = lines.join(" ");
        const placedText = new RegExp(String.raw`Order Placed:?\s*` + DATE, "i").exec(flat);
        const placed = isoDate(textOf(doc.querySelector('[data-component="orderDate"]')))
            ?? (placedText ? isoDate(placedText[1]) : null);
        const total = new RegExp(String.raw`(?:Order|Grand) Total:?\s*` + MONEY, "i").exec(flat);
        const items = componentItems(doc);
        return {
            orderNumber,
            orderedOn: placed,
            total: total ? amount(total[1]) : null,
            currency: /US\$/.test(flat) ? "USD" : currency,
            items: items.length > 0 ? items : lineItems(lines),
            charges: invoiceCharges(flat),
        };
    };

    // One Your Payments → Transactions page's lines → the card charges on it
    // (refunds skipped), each with the order number(s) it paid for.
    const parsePaymentLines = (lines) => {
        const charges = [];
        let day = null;
        let current = null;
        const close = () => {
            if (current && current.sign === "-" && current.orders.length > 0) {
                charges.push(current);
            }
            current = null;
        };
        for (const line of lines) {
            const date = DATE_LINE.exec(line);
            if (date) {
                close();
                day = isoDate(date[1]);
                continue;
            }
            const card = CARD_LINE.exec(line);
            if (card) {
                close();
                current = { chargedOn: day, cardName: card[1] || "Card", cardLast4: card[2], sign: null, amount: null, orders: [] };
                continue;
            }
            if (!current) {
                continue;
            }
            const value = AMOUNT_LINE.exec(line);
            if (value && current.amount === null) {
                current.sign = value[1];
                current.amount = amount(value[2]);
                continue;
            }
            const order = CHARGE_ORDER_LINE.exec(line);
            if (order && !order[1]) {
                current.orders.push(order[2]);
            }
        }
        close();
        return charges.filter((charge) => charge.chargedOn && charge.amount > 0);
    };

    const fetchPage = async (path, init) => {
        const response = await fetch(path, { credentials: "include", ...init });
        if (response.url.includes("/ap/signin")) {
            throw new SignedOut();
        }
        const html = await response.text();
        if (/id="ap_email"|name="signIn"/.test(html)) {
            throw new SignedOut();
        }
        return new DOMParser().parseFromString(html, "text/html");
    };

    // The next page of Your Payments is a form POST carrying the widget state.
    const nextPaymentPage = (doc) => {
        const button = doc.querySelector(NEXT_PAGE);
        if (!button?.form) {
            return null;
        }
        const body = new URLSearchParams();
        button.form.querySelectorAll('input[type="hidden"]').forEach((input) => {
            body.append(input.name, input.value);
        });
        body.append(button.name, button.value);
        return () => {
            return fetchPage(button.form.getAttribute("action"), {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body,
            });
        };
    };

    // Order number → card charges, for charges made on or after `since`.
    const readPayments = async (since, progress) => {
        const byOrder = new Map();
        let doc = await fetchPage("/cpe/yourpayments/transactions");
        for (let page = 1; page <= MAX_PAYMENT_PAGES; page++) {
            progress(`Reading card charges, page ${page}…`);
            const charges = parsePaymentLines(linesOf(doc.body));
            for (const charge of charges.filter((each) => each.chargedOn >= since)) {
                for (const orderNumber of charge.orders) {
                    const list = byOrder.get(orderNumber) ?? [];
                    list.push({
                        chargedOn: charge.chargedOn,
                        amount: charge.amount,
                        cardLabel: `${charge.cardName} ending in ${charge.cardLast4}`,
                        cardLast4: charge.cardLast4,
                    });
                    byOrder.set(orderNumber, list);
                }
            }
            const next = nextPaymentPage(doc);
            // Newest first: once a page reaches before `since`, the rest is older still.
            if (!next || charges.length === 0 || charges.some((charge) => charge.chargedOn < since)) {
                break;
            }
            await sleep(REQUEST_GAP_MS);
            doc = await next();
        }
        return byOrder;
    };

    // Order numbers on one page of the order history, in page order.
    const orderNumbersOn = (doc) => {
        return [...new Set([...doc.documentElement.outerHTML.matchAll(ORDER_NUMBER)].map((match) => match[1]))]
            .filter((number) => !PLACEHOLDER_ORDER.test(number));
    };

    globalThis.ftAmazonInvoice = { parseInvoice, parsePaymentLines, linesOf };

    globalThis.ftScrapeAmazonOrders = async ({ since, currency }) => {
        const progress = (text) => {
            chrome.runtime.sendMessage({ type: "ft-progress", text }).catch(() => {});
        };
        const orders = [];
        const warnings = [];
        const seen = new Set();
        let reachedStart = false;
        try {
            const payments = await readPayments(since, progress);
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
                        const order = parseInvoice(invoice, number, currency);
                        if (!order.orderedOn) {
                            warnings.push(`${number}: no invoice to read (digital or non-retail order?)`);
                            continue;
                        }
                        if (order.orderedOn < since) {
                            // The history is newest-first: everything after this is older still.
                            reachedStart = true;
                            break;
                        }
                        order.charges = payments.get(number) ?? order.charges;
                        if (order.total === null && order.charges.length === 0) {
                            // Cancelled orders keep their items but lose the order summary.
                            warnings.push(`${number}: cancelled, skipped`);
                            continue;
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
