import { useCallback, useEffect, useMemo, useState } from "react";
import { Box, Button, Chip, Paper, Typography } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { App as AntApp, Checkbox } from "antd";
import EntityAvatar from "../components/EntityAvatar";
import { matchesApi, type MatchSuggestion, type Transaction } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./MatchesPage.module.css";

/** Suggestions sharing a purchase render as one card (one-to-many refunds). */
interface SuggestionCard {
    key: string;
    type: "TRANSFER" | "REFUND";
    anchor: Transaction;
    rows: { suggestion: MatchSuggestion; other: Transaction }[];
}

function Leg({ tx }: { tx: Transaction }) {
    return (
        <div className={styles.leg}>
            <span className={shared.nowrap}>{formatDate(tx.postedAt)}</span>
            <EntityAvatar url={tx.accountLogoUrl} name={tx.accountName} />
            <span className={`${shared.ellipsis} ${styles.legMerchant}`}>{tx.merchant ?? tx.merchantName}</span>
            <span className={`${shared.nowrap} ${tx.amount < 0 ? shared.amountNeg : shared.amountPos}`}>
                {formatMoney(tx.amount, tx.currency)}
            </span>
        </div>
    );
}

export default function MatchesPage() {
    const { message } = AntApp.useApp();
    const [items, setItems] = useState<MatchSuggestion[]>([]);
    const [checked, setChecked] = useState<Set<number>>(new Set());
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        matchesApi.suggestions().then(setItems).catch((error: unknown) => message.error(errorText(error)));
    }, [message]);

    useEffect(load, [load]);

    // Group refund suggestions by their purchase leg; transfers stay one per card.
    const cards = useMemo(() => {
        const result: SuggestionCard[] = [];
        const refundCardByPurchase = new Map<number, SuggestionCard>();
        for (const item of items) {
            if (item.type === "REFUND") {
                const purchase = item.legA.amount < 0 ? item.legA : item.legB;
                const refund = purchase.id === item.legA.id ? item.legB : item.legA;
                const existing = refundCardByPurchase.get(purchase.id);
                if (existing) {
                    existing.rows.push({ suggestion: item, other: refund });
                } else {
                    const card: SuggestionCard = {
                        key: `refund-${purchase.id}`,
                        type: "REFUND",
                        anchor: purchase,
                        rows: [{ suggestion: item, other: refund }],
                    };
                    refundCardByPurchase.set(purchase.id, card);
                    result.push(card);
                }
            } else {
                result.push({
                    key: `transfer-${item.id}`,
                    type: "TRANSFER",
                    anchor: item.legA,
                    rows: [{ suggestion: item, other: item.legB }],
                });
            }
        }
        return result;
    }, [items]);

    // Everything starts selected whenever the list refreshes.
    useEffect(() => {
        setChecked(new Set(items.map((item) => item.id)));
    }, [items]);

    const toggle = (id: number) => {
        setChecked((current) => {
            const next = new Set(current);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

    const scan = async () => {
        setBusy(true);
        try {
            const result = await matchesApi.scan();
            message.success(`Scan complete — ${result.applied} auto-matched`);
            load();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    /** Confirm or dismiss every selected suggestion of a card, then re-sync the list. */
    const act = async (card: SuggestionCard, fn: (id: number) => Promise<unknown>, label: string) => {
        const selected = card.rows.filter((row) => checked.has(row.suggestion.id));
        if (selected.length === 0) {
            return;
        }
        setBusy(true);
        let done = 0;
        try {
            for (const row of selected) {
                await fn(row.suggestion.id);
                done++;
            }
            message.success(`${done > 1 ? `${done} ` : ""}${label}`);
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
            // Confirming a refund can prune sibling suggestions server-side, and a
            // failed call means the list is stale — re-sync either way.
            load();
        }
    };

    const selectedCount = (card: SuggestionCard) => {
        return card.rows.filter((row) => checked.has(row.suggestion.id)).length;
    };

    const selectedSum = (card: SuggestionCard) => {
        return card.rows
            .filter((row) => checked.has(row.suggestion.id))
            .reduce((sum, row) => sum + row.other.amount, 0);
    };

    return (
        <Box>
            <Box className={styles.header}>
                <Typography variant="h5" className={styles.title}>
                    Matches
                </Typography>
                <Button variant="outlined" startIcon={<SearchIcon />} onClick={scan} disabled={busy}>
                    Scan for matches
                </Button>
            </Box>
            <Typography variant="body2" color="text.secondary" className={styles.subtitle}>
                Suggested transfer and refund pairs the importer wasn't sure about. Confirm to link them (transfers leave
                the budget; refunds offset their category), or reject to dismiss.
            </Typography>

            {cards.map((card) => {
                const grouped = card.rows.length > 1;
                const count = selectedCount(card);
                return (
                    <Paper key={card.key} className={styles.card}>
                        <div className={styles.cardHeader}>
                            <Chip
                                size="small"
                                color="info"
                                label={card.type === "TRANSFER" ? "⇄ Transfer" : `↩ Refund${grouped ? ` × ${card.rows.length}` : ""}`}
                            />
                            {grouped && (
                                <Typography variant="body2" color="text.secondary" className={shared.nowrap}>
                                    {formatMoney(selectedSum(card), card.anchor.currency)} of{" "}
                                    {formatMoney(Math.abs(card.anchor.amount), card.anchor.currency)} selected
                                </Typography>
                            )}
                            <span className={styles.spacer} />
                            <Button
                                size="small"
                                variant="contained"
                                disabled={busy || count === 0}
                                onClick={() => act(card, matchesApi.confirm, "matched")}
                            >
                                Confirm{grouped ? ` (${count})` : ""}
                            </Button>
                            <Button
                                size="small"
                                color="error"
                                disabled={busy || count === 0}
                                onClick={() => act(card, matchesApi.dismiss, "dismissed")}
                            >
                                Reject{grouped ? ` (${count})` : ""}
                            </Button>
                        </div>
                        <Leg tx={card.anchor} />
                        {card.rows.map((row) => {
                            return grouped ? (
                                <div key={row.suggestion.id} className={styles.refundRow}>
                                    <Checkbox
                                        checked={checked.has(row.suggestion.id)}
                                        onChange={() => toggle(row.suggestion.id)}
                                    />
                                    <div className={styles.refundRowLeg}>
                                        <Leg tx={row.other} />
                                    </div>
                                </div>
                            ) : (
                                <Leg key={row.suggestion.id} tx={row.other} />
                            );
                        })}
                    </Paper>
                );
            })}

            {cards.length === 0 && (
                <Typography color="text.secondary">No suggested matches.</Typography>
            )}
        </Box>
    );
}
