import { useEffect, useState } from "react";
import {
    Box,
    Button,
    IconButton,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from "@mui/material";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { App as AntApp, Popconfirm, Select, Space } from "antd";
import { importApi, type CsvFormat, type ImportRun } from "../lib/api";
import { errorText } from "../lib/format";
import ImportRunModal from "../components/ImportRunModal";
import SimpleFinSection from "../components/SimpleFinSection";
import styles from "./ImportPage.module.css";

const FORMATS: { label: string; value: CsvFormat }[] = [
    { label: "Simple (account,name,value)", value: "SIMPLE" },
    { label: "Amex Canada", value: "AMEX" },
    { label: "RBC", value: "RBC" },
    { label: "PC Financial", value: "PC_FINANCIAL" },
];

export default function ImportPage() {
    const { message } = AntApp.useApp();
    const [runs, setRuns] = useState<ImportRun[]>([]);
    const [format, setFormat] = useState<CsvFormat>("SIMPLE");
    const [busy, setBusy] = useState(false);
    const [viewing, setViewing] = useState<ImportRun | null>(null);

    const reload = () => {
        importApi.history().then(setRuns).catch(() => setRuns([]));
    };

    useEffect(reload, []);

    const onFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) {
            return;
        }
        setBusy(true);
        try {
            const run = await importApi.uploadCsv(file, format);
            message.success(`Imported ${run.newCount} transactions across ${run.accountCount} account(s)`);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    const undo = async (run: ImportRun) => {
        setBusy(true);
        try {
            const result = await importApi.remove(run.id);
            message.success(`Deleted the import and its ${result.deleted} transaction(s)`);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Box>
            <Typography variant="h5" className={styles.title}>
                Import
            </Typography>

            <SimpleFinSection onSynced={reload} />

            <Typography variant="h6" className={styles.subheading}>
                CSV upload
            </Typography>
            <Space wrap className={styles.controls}>
                <Select
                    className={styles.formatSelect}
                    value={format}
                    onChange={(value: CsvFormat) => setFormat(value)}
                    options={FORMATS}
                />
                <Button variant="contained" component="label" startIcon={<UploadFileIcon />} disabled={busy}>
                    Upload CSV
                    <input hidden type="file" accept=".csv,text/csv" onChange={onFile} />
                </Button>
            </Space>
            <Typography color="text.secondary" className={styles.hint}>
                Pick the format that matches your bank's export, then upload its CSV.
            </Typography>

            <TableContainer component={Paper}>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>When</TableCell>
                            <TableCell>Source</TableCell>
                            <TableCell>File</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell align="right">New</TableCell>
                            <TableCell align="right">Accounts</TableCell>
                            <TableCell align="right">Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {runs.map((run) => {
                            return (
                                <TableRow key={run.id} hover>
                                    <TableCell>{new Date(run.startedAt).toLocaleString()}</TableCell>
                                    <TableCell>{run.source}</TableCell>
                                    <TableCell>{run.fileName ?? "—"}</TableCell>
                                    <TableCell>{run.status}</TableCell>
                                    <TableCell align="right">{run.newCount}</TableCell>
                                    <TableCell align="right">{run.accountCount}</TableCell>
                                    <TableCell align="right" className={styles.actions}>
                                        <Tooltip title="Show this import's transactions">
                                            <IconButton
                                                size="small"
                                                aria-label="show imported transactions"
                                                onClick={() => setViewing(run)}
                                            >
                                                <ReceiptLongIcon fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                        <Popconfirm
                                            title="Delete this import?"
                                            description="Every transaction it brought in goes with it, quarantined duplicates included. They are not blocked from being imported again."
                                            okText="Delete"
                                            okButtonProps={{ danger: true }}
                                            onConfirm={() => undo(run)}
                                        >
                                            <Tooltip title="Delete this import and its transactions">
                                                <IconButton
                                                    size="small"
                                                    color="error"
                                                    aria-label="delete import"
                                                    disabled={busy}
                                                >
                                                    <DeleteOutlinedIcon fontSize="small" />
                                                </IconButton>
                                            </Tooltip>
                                        </Popconfirm>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                        {runs.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={7}>
                                    <Typography color="text.secondary">No imports yet.</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>

            <ImportRunModal run={viewing} onClose={() => setViewing(null)} />
        </Box>
    );
}
