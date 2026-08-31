import { useEffect, useState } from "react";
import { Box, Button, Chip, Paper, TextField, Tooltip, Typography } from "@mui/material";
import MonitorHeartIcon from "@mui/icons-material/MonitorHeart";
import SyncIcon from "@mui/icons-material/Sync";
import LinkIcon from "@mui/icons-material/Link";
import LaunchIcon from "@mui/icons-material/Launch";
import { Alert, App as AntApp, DatePicker } from "antd";
import SimpleFinBridgeModal from "./SimpleFinBridgeModal";
import { simplefinApi, type SimpleFinHealth, type SimpleFinStatus } from "../lib/api";
import { errorText } from "../lib/format";
import styles from "./SimpleFinSection.module.css";

/** SimpleFIN connect + manual sync. The access URL never leaves the server. */
export default function SimpleFinSection({ onSynced }: { onSynced: () => void }) {
    const { message } = AntApp.useApp();
    const [status, setStatus] = useState<SimpleFinStatus | null>(null);
    const [statusError, setStatusError] = useState<string | null>(null);
    const [token, setToken] = useState("");
    const [range, setRange] = useState<[string, string] | null>(null);
    const [health, setHealth] = useState<SimpleFinHealth | null>(null);
    const [bridgeOpen, setBridgeOpen] = useState(false);
    const [busy, setBusy] = useState(false);

    const reload = () => {
        simplefinApi
            .status()
            .then((next) => {
                setStatus(next);
                setStatusError(null);
            })
            .catch((error: unknown) => {
                // Don't render a failed fetch as "Not connected" — that reads as
                // unconfigured when the real problem is auth/session/network.
                setStatus(null);
                setStatusError(errorText(error));
            });
    };

    useEffect(reload, []);

    const connect = async () => {
        if (!token.trim()) {
            return;
        }
        setBusy(true);
        try {
            const next = await simplefinApi.setup(token.trim());
            setStatus(next);
            setToken("");
            message.success("SimpleFIN connected");
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    const runHealthCheck = async () => {
        setBusy(true);
        try {
            setHealth(await simplefinApi.healthCheck());
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    const runSync = async (from?: string, to?: string) => {
        setBusy(true);
        try {
            const run = await simplefinApi.sync(from, to);
            message.success(`Synced ${run.newCount} new transaction(s) across ${run.accountCount} account(s)`);
            reload();
            onSynced();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Paper className={styles.paper}>
            <Box className={styles.header}>
                <Typography variant="h6">SimpleFIN</Typography>
                {status?.connected ? (
                    <Chip size="small" color="success" label="Connected" />
                ) : statusError ? (
                    <Tooltip title={statusError}>
                        <Chip size="small" color="error" label="Status unavailable — reload" onClick={reload} />
                    </Tooltip>
                ) : (
                    <Chip size="small" label="Not connected" />
                )}
                {status?.connected && status.lastSyncedAt && (
                    <Typography variant="body2" color="text.secondary">
                        Last sync: {new Date(status.lastSyncedAt).toLocaleString()}
                    </Typography>
                )}
            </Box>

            <Typography variant="body2" color="text.secondary" className={styles.hint}>
                Paste a SimpleFIN setup token to connect. "Sync now" pulls the recent window; syncs also run
                automatically once a day around noon. Use a date range to force-import a specific period.
                "Open bridge" opens SimpleFIN itself, for re-authenticating a bank connection.
            </Typography>

            <Box className={styles.controls}>
                <TextField
                    size="small"
                    label="Setup token"
                    value={token}
                    onChange={(event) => setToken(event.target.value)}
                    className={styles.tokenField}
                />
                <Button variant="outlined" startIcon={<LinkIcon />} onClick={connect} disabled={busy || !token.trim()}>
                    Connect
                </Button>
                <Button variant="contained" startIcon={<SyncIcon />} onClick={() => runSync()} disabled={busy || !status?.connected}>
                    Sync now
                </Button>
                <Button
                    variant="outlined"
                    startIcon={<MonitorHeartIcon />}
                    onClick={runHealthCheck}
                    disabled={busy || !status?.connected}
                >
                    Health check
                </Button>
                <Button
                    variant="outlined"
                    startIcon={<LaunchIcon />}
                    onClick={() => setBridgeOpen(true)}
                >
                    Open bridge
                </Button>
            </Box>

            {health && (
                <Alert
                    type={health.healthy ? "success" : "warning"}
                    showIcon
                    closable
                    onClose={() => setHealth(null)}
                    message={health.healthy ? "Bridge healthy" : "Bridge needs attention"}
                    description={<span className={styles.healthReport}>{health.report}</span>}
                    className={styles.healthAlert}
                />
            )}

            <Box className={`${styles.controls} ${styles.rangeRow}`}>
                <DatePicker.RangePicker
                    format="YYYY-MM-DD"
                    disabled={busy || !status?.connected}
                    onChange={(_dates, strings) =>
                        setRange(strings[0] && strings[1] ? [strings[0], strings[1]] : null)
                    }
                />
                <Button onClick={() => range && runSync(range[0], range[1])} disabled={busy || !status?.connected || !range}>
                    Import range
                </Button>
            </Box>

            {bridgeOpen && (
                <SimpleFinBridgeModal bridgeUrl={status?.bridgeUrl ?? null} onClose={() => setBridgeOpen(false)} />
            )}
        </Paper>
    );
}
