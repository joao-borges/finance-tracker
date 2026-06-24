import { useEffect, useState } from "react";
import { Box, Button, Chip, Paper, TextField, Typography } from "@mui/material";
import SyncIcon from "@mui/icons-material/Sync";
import LinkIcon from "@mui/icons-material/Link";
import { App as AntApp } from "antd";
import { simplefinApi, type SimpleFinStatus } from "../lib/api";
import { errorText } from "../lib/format";

/** SimpleFIN connect + manual sync. The access URL never leaves the server. */
export default function SimpleFinSection({ onSynced }: { onSynced: () => void }) {
    const { message } = AntApp.useApp();
    const [status, setStatus] = useState<SimpleFinStatus | null>(null);
    const [token, setToken] = useState("");
    const [busy, setBusy] = useState(false);

    const reload = () => {
        simplefinApi.status().then(setStatus).catch(() => setStatus(null));
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

    const sync = async () => {
        setBusy(true);
        try {
            const run = await simplefinApi.sync();
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
        <Paper sx={{ p: 2, mb: 3 }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
                <Typography variant="h6">SimpleFIN</Typography>
                {status?.connected ? (
                    <Chip size="small" color="success" label="Connected" />
                ) : (
                    <Chip size="small" label="Not connected" />
                )}
                {status?.connected && status.lastSyncedAt && (
                    <Typography variant="body2" color="text.secondary">
                        Last sync: {new Date(status.lastSyncedAt).toLocaleString()}
                    </Typography>
                )}
            </Box>

            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Paste a SimpleFIN setup token to connect, or sync now. Syncs also run automatically twice a day.
            </Typography>

            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                <TextField
                    size="small"
                    label="Setup token"
                    value={token}
                    onChange={(event) => setToken(event.target.value)}
                    sx={{ flex: 1, minWidth: 280 }}
                />
                <Button variant="outlined" startIcon={<LinkIcon />} onClick={connect} disabled={busy || !token.trim()}>
                    Connect
                </Button>
                <Button variant="contained" startIcon={<SyncIcon />} onClick={sync} disabled={busy || !status?.connected}>
                    Sync now
                </Button>
            </Box>
        </Paper>
    );
}
