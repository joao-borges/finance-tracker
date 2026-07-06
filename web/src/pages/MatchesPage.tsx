import { useCallback, useEffect, useState } from "react";
import { Box, Button, Chip, Paper, Typography } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { App as AntApp } from "antd";
import EntityAvatar from "../components/EntityAvatar";
import { matchesApi, type MatchSuggestion, type Transaction } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./MatchesPage.module.css";

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
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        matchesApi.suggestions().then(setItems).catch((error: unknown) => message.error(errorText(error)));
    }, [message]);

    useEffect(load, [load]);

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

    const act = async (id: number, fn: (id: number) => Promise<unknown>, label: string) => {
        try {
            await fn(id);
            setItems((current) => current.filter((item) => item.id !== id));
            message.success(label);
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            // Confirming a refund can prune sibling suggestions server-side, and a
            // failed confirm means the list is stale — re-sync either way.
            load();
        }
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

            {items.map((item) => {
                return (
                    <Paper key={item.id} className={styles.card}>
                        <div className={styles.cardHeader}>
                            <Chip
                                size="small"
                                color="info"
                                label={item.type === "TRANSFER" ? "⇄ Transfer" : "↩ Refund"}
                            />
                            <span className={styles.spacer} />
                            <Button size="small" variant="contained" onClick={() => act(item.id, matchesApi.confirm, "Matched")}>
                                Confirm
                            </Button>
                            <Button size="small" color="error" onClick={() => act(item.id, matchesApi.dismiss, "Dismissed")}>
                                Reject
                            </Button>
                        </div>
                        <Leg tx={item.legA} />
                        <Leg tx={item.legB} />
                    </Paper>
                );
            })}

            {items.length === 0 && (
                <Typography color="text.secondary">No suggested matches.</Typography>
            )}
        </Box>
    );
}
