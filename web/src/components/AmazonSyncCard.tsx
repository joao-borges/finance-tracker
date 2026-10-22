import { useState } from "react";
import { Button, Paper, Typography } from "@mui/material";
import SyncIcon from "@mui/icons-material/Sync";
import { App as AntApp, DatePicker, Select } from "antd";
import dayjs from "dayjs";
import { amazonApi, type AmazonImportResult, type AmazonMarketplace } from "../lib/api";
import { syncAmazonOrders } from "../lib/amazonExtension";
import { errorText } from "../lib/format";
import styles from "./AmazonSyncCard.module.css";

interface Props {
    installed: boolean;
    onImported: () => void;
}

const MARKETPLACE_KEY = "ft.amazon.marketplace";
// Far enough back to catch late charges on orders that shipped in pieces.
const DEFAULT_LOOKBACK_DAYS = 60;

const MARKETPLACES: { value: AmazonMarketplace; label: string }[] = [
    { value: "www.amazon.ca", label: "amazon.ca" },
    { value: "www.amazon.com", label: "amazon.com" },
];

const storedMarketplace = (): AmazonMarketplace => {
    try {
        const stored = localStorage.getItem(MARKETPLACE_KEY);
        return MARKETPLACES.some((option) => option.value === stored) ? (stored as AmazonMarketplace) : "www.amazon.ca";
    } catch (ignored) {
        return "www.amazon.ca";
    }
};

// Runs a sync through the extension, then posts what it read to the API,
// which stores the orders and links each charge to its bank transaction.
export default function AmazonSyncCard({ installed, onImported }: Props) {
    const { message } = AntApp.useApp();
    const [marketplace, setMarketplace] = useState<AmazonMarketplace>(storedMarketplace);
    const [since, setSince] = useState(() => dayjs().subtract(DEFAULT_LOOKBACK_DAYS, "day"));
    const [busy, setBusy] = useState(false);
    const [progress, setProgress] = useState("");
    const [warnings, setWarnings] = useState<string[]>([]);
    const [result, setResult] = useState<AmazonImportResult | null>(null);

    const chooseMarketplace = (value: AmazonMarketplace) => {
        setMarketplace(value);
        try {
            localStorage.setItem(MARKETPLACE_KEY, value);
        } catch (ignored) {}
    };

    const sync = async () => {
        setBusy(true);
        setWarnings([]);
        setResult(null);
        setProgress("Asking the extension…");
        try {
            const scraped = await syncAmazonOrders(marketplace, since.format("YYYY-MM-DD"), setProgress);
            setProgress(`Saving ${scraped.orders.length} order(s)…`);
            const imported = await amazonApi.importOrders(scraped.marketplace, scraped.orders);
            setWarnings(scraped.warnings);
            setResult(imported);
            message.success(`${imported.orders} order(s) read · ${imported.matched} charge(s) matched`);
            onImported();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
            setProgress("");
        }
    };

    return (
        <Paper className={styles.card}>
            <div className={styles.controls}>
                <Typography variant="subtitle1" className={styles.title}>
                    Sync orders
                </Typography>
                <Select value={marketplace} onChange={chooseMarketplace} options={MARKETPLACES} disabled={busy} />
                <span className={styles.label}>placed since</span>
                <DatePicker
                    value={since}
                    allowClear={false}
                    disabled={busy}
                    disabledDate={(day) => day.isAfter(dayjs())}
                    onChange={(value) => {
                        if (value) {
                            setSince(value);
                        }
                    }}
                />
                <Button variant="contained" startIcon={<SyncIcon />} onClick={sync} disabled={!installed || busy}>
                    {busy ? "Syncing…" : "Sync"}
                </Button>
            </div>
            {!installed && (
                <Typography variant="body2" color="text.secondary">
                    Install the extension above to sync.
                </Typography>
            )}
            {progress && (
                <Typography variant="body2" color="text.secondary">
                    {progress}
                </Typography>
            )}
            {result && (
                <Typography variant="body2">
                    {result.orders} order(s) read ({result.newOrders} new) · {result.charges} charge(s) ·{" "}
                    {result.matched} newly matched · {result.unmatched} still unmatched
                </Typography>
            )}
            {warnings.length > 0 && (
                <ul className={styles.warnings}>
                    {warnings.map((warning) => {
                        return <li key={warning}>{warning}</li>;
                    })}
                </ul>
            )}
        </Paper>
    );
}
