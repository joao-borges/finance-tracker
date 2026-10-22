import { Button, Chip, Paper, Typography } from "@mui/material";
import DownloadIcon from "@mui/icons-material/Download";
import { amazonApi } from "../lib/api";
import styles from "./AmazonExtensionCard.module.css";

interface Props {
    installedVersion: string | null;
    latestVersion: string | null;
}

// Download + install instructions for the Amazon browser extension, with its
// detected state. The zip is generated for this app's address, so the
// extension only ever talks to this site.
export default function AmazonExtensionCard({ installedVersion, latestVersion }: Props) {
    const outdated = installedVersion != null && latestVersion != null && installedVersion !== latestVersion;

    return (
        <Paper className={styles.card}>
            <div className={styles.header}>
                <Typography variant="subtitle1" className={styles.title}>
                    Browser extension
                </Typography>
                {installedVersion == null ? (
                    <Chip size="small" label="Not detected" />
                ) : outdated ? (
                    <Chip size="small" color="warning" label={`v${installedVersion} — update to v${latestVersion}`} />
                ) : (
                    <Chip size="small" color="success" label={`Installed · v${installedVersion}`} />
                )}
                <Button
                    variant={installedVersion == null || outdated ? "contained" : "outlined"}
                    startIcon={<DownloadIcon />}
                    component="a"
                    href={amazonApi.extensionZipUrl()}
                >
                    Download{latestVersion ? ` v${latestVersion}` : ""}
                </Button>
            </div>
            <Typography variant="body2" color="text.secondary">
                Reads your Amazon order invoices from your own signed-in Amazon session and hands them to this page —
                nothing goes anywhere else. Works in Chrome, Edge and Brave.
            </Typography>
            {(installedVersion == null || outdated) && (
                <ol className={styles.steps}>
                    <li>Download the zip and unzip it somewhere permanent.</li>
                    <li>
                        Open <code>chrome://extensions</code> (or <code>edge://extensions</code>) and turn on{" "}
                        <strong>Developer mode</strong>.
                    </li>
                    <li>
                        {outdated
                            ? "Unzip over the old folder, then press the extension's reload ↻ button."
                            : <>Click <strong>Load unpacked</strong> and pick the <code>finance-amazon-extension</code> folder.</>}
                    </li>
                    <li>Reload this page.</li>
                </ol>
            )}
        </Paper>
    );
}
