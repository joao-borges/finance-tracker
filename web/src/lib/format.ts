// Shared, component-free formatting helpers.

export function errorText(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

export function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "2-digit" });
}

/** The browser's IANA timezone — sent with every date the user picks, so the
 * server can persist the intended calendar day as a real instant. */
export function browserTimeZone(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function formatMoney(value: number | null | undefined, currency = "CAD"): string {
    if (value === null || value === undefined) {
        return "—";
    }
    return value.toLocaleString(undefined, { style: "currency", currency });
}
