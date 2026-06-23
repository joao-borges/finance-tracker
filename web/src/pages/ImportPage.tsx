import { useEffect, useState } from "react";
import {
    Box,
    Button,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { App as AntApp, Select, Space } from "antd";
import { importApi, type CsvFormat, type ImportRun } from "../lib/api";
import { errorText } from "../lib/format";

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

    return (
        <Box>
            <Typography variant="h5" sx={{ mb: 2 }}>
                Import
            </Typography>

            <Space wrap style={{ marginBottom: 12 }}>
                <Select
                    style={{ minWidth: 240 }}
                    value={format}
                    onChange={(value: CsvFormat) => setFormat(value)}
                    options={FORMATS}
                />
                <Button variant="contained" component="label" startIcon={<UploadFileIcon />} disabled={busy}>
                    Upload CSV
                    <input hidden type="file" accept=".csv,text/csv" onChange={onFile} />
                </Button>
            </Space>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
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
                                </TableRow>
                            );
                        })}
                        {runs.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={6}>
                                    <Typography color="text.secondary">No imports yet.</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
