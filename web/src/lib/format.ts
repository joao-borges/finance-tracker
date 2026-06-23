// Shared, component-free formatting helpers.

export function errorText(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

export function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "2-digit" });
}

export function formatMoney(value: number | null | undefined, currency = "CAD"): string {
    if (value === null || value === undefined) {
        return "—";
    }
    return value.toLocaleString(undefined, { style: "currency", currency });
}
