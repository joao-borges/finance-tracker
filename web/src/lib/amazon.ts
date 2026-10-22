// Helpers for Amazon order details (non-component).

import type { AmazonItem, SplitSeed } from "./api";

const cents = (value: number): number => {
    return Math.round(value * 100) / 100;
};

/**
 * One split line per item. Invoice prices are pre-tax and a charge often covers
 * only part of an order, so when every chosen item has a price the lines are
 * scaled to add up to the charge exactly (the last line absorbs the rounding).
 * If any price is missing, known prices are kept as-is and the rest are left
 * blank to type in.
 */
export function splitSeedsFromItems(items: AmazonItem[], chargeTotal: number): SplitSeed[] {
    const label = (item: AmazonItem) => {
        return item.quantity > 1 ? `${item.quantity} × ${item.title}` : item.title;
    };
    const bases = items.map((item) => (item.unitPrice == null ? null : item.unitPrice * item.quantity));
    const sum = bases.reduce<number>((total, base) => total + (base ?? 0), 0);
    if (bases.some((base) => base == null) || sum <= 0) {
        return items.map((item, index) => {
            const base = bases[index];
            return { amount: base == null ? null : cents(base), label: label(item) };
        });
    }
    const scale = Math.abs(chargeTotal) / sum;
    const amounts = bases.map((base) => cents((base as number) * scale));
    const drift = cents(Math.abs(chargeTotal) - amounts.reduce((total, amount) => total + amount, 0));
    amounts[amounts.length - 1] = cents(amounts[amounts.length - 1] + drift);
    return items.map((item, index) => ({ amount: amounts[index], label: label(item) }));
}
