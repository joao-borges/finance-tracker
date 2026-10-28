import { useCallback, useEffect, useMemo, useState } from "react";
import { Box, Button, FormControlLabel, Switch, Typography } from "@mui/material";
import LinkIcon from "@mui/icons-material/Link";
import { App as AntApp } from "antd";
import AmazonExtensionCard from "../components/AmazonExtensionCard";
import AmazonSyncCard from "../components/AmazonSyncCard";
import AmazonOrdersTable from "../components/AmazonOrdersTable";
import AmazonLinkModal from "../components/AmazonLinkModal";
import { amazonApi, type AmazonCharge, type AmazonOrder } from "../lib/api";
import { installedExtensionVersion } from "../lib/amazonExtension";
import { errorText } from "../lib/format";
import styles from "./AmazonPage.module.css";

// Amazon order details: install the extension, sync orders through it, and
// review how each card charge was matched to a bank transaction.
export default function AmazonPage() {
    const { message } = AntApp.useApp();
    const [orders, setOrders] = useState<AmazonOrder[]>([]);
    const [latestVersion, setLatestVersion] = useState<string | null>(null);
    const [installedVersion, setInstalledVersion] = useState<string | null>(installedExtensionVersion);
    const [unmatchedOnly, setUnmatchedOnly] = useState(false);
    const [linking, setLinking] = useState<{ charge: AmazonCharge; order: AmazonOrder } | null>(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        amazonApi.orders().then(setOrders).catch((error: unknown) => message.error(errorText(error)));
    }, [message]);

    useEffect(load, [load]);

    useEffect(() => {
        amazonApi.extension().then((info) => setLatestVersion(info.version)).catch(() => {});
        // The content script marks the page at document_start; re-check once in
        // case this page was rendered before it ran.
        const timer = window.setTimeout(() => setInstalledVersion(installedExtensionVersion()), 500);
        return () => window.clearTimeout(timer);
    }, []);

    const shown = useMemo(() => {
        return unmatchedOnly
            ? orders.filter((order) => order.charges.some((charge) => charge.transactionId == null))
            : orders;
    }, [orders, unmatchedOnly]);

    const unmatchedCount = orders.flatMap((order) => order.charges).filter((charge) => charge.transactionId == null).length;

    const rematch = async () => {
        setBusy(true);
        try {
            const result = await amazonApi.rematch();
            message.success(`${result.matched} charge(s) matched`);
            load();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    const unlink = async (charge: AmazonCharge) => {
        try {
            await amazonApi.unlink(charge.id);
            message.success("Charge unlinked");
            load();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <Box>
            <Box className={styles.header}>
                <Typography variant="h5" className={styles.title}>
                    Amazon
                </Typography>
            </Box>
            <Typography variant="body2" color="text.secondary" className={styles.subtitle}>
                Amazon charges cards per shipment, not per order, so one bank “Amazon” row can be part of an order or
                several of them. Syncing reads your order invoices and links each card charge to its transaction — the
                transaction list then shows what it bought, and its editor can split it by item.
            </Typography>

            <AmazonExtensionCard installedVersion={installedVersion} latestVersion={latestVersion} />
            <AmazonSyncCard installed={installedVersion != null} orders={orders} onImported={load} />

            <Box className={styles.toolbar}>
                <Typography variant="subtitle1" className={styles.title}>
                    Orders ({orders.length}) · {unmatchedCount} unmatched charge(s)
                </Typography>
                <FormControlLabel
                    control={<Switch checked={unmatchedOnly} onChange={(event) => setUnmatchedOnly(event.target.checked)} />}
                    label="Only unmatched"
                />
                <Button variant="outlined" startIcon={<LinkIcon />} onClick={rematch} disabled={busy || unmatchedCount === 0}>
                    Re-match
                </Button>
            </Box>
            <AmazonOrdersTable
                orders={shown}
                onLink={(charge, order) => setLinking({ charge, order })}
                onUnlink={unlink}
            />

            <AmazonLinkModal
                charge={linking?.charge ?? null}
                order={linking?.order ?? null}
                onClose={() => setLinking(null)}
                onLinked={load}
            />
        </Box>
    );
}
