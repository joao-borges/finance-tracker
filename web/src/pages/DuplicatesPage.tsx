import { useCallback, useEffect, useState } from "react";
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
import RestoreIcon from "@mui/icons-material/Restore";
import { App as AntApp } from "antd";
import EntityAvatar from "../components/EntityAvatar";
import { transactionsApi, type Transaction } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./DuplicatesPage.module.css";

export default function DuplicatesPage() {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(false);

    const load = useCallback(() => {
        setLoading(true);
        transactionsApi
            .duplicates()
            .then(setRows)
            .catch((error: unknown) => message.error(errorText(error)))
            .finally(() => setLoading(false));
    }, [message]);

    useEffect(load, [load]);

    const restore = async (id: number) => {
        try {
            await transactionsApi.restore(id);
            message.success("Restored");
            setRows((current) => current.filter((row) => row.id !== id));
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <Box>
            <Typography variant="h5" className={styles.title}>
                Duplicates
            </Typography>
            <Typography variant="body2" color="text.secondary" className={styles.subtitle}>
                Quarantined transactions detected as duplicates of existing ones. Restore one to bring it back and re-run
                the rules.
            </Typography>
            <TableContainer component={Paper}>
                <Table size="small" className={styles.table}>
                    <TableHead>
                        <TableRow>
                            <TableCell className={styles.colDate}>Date</TableCell>
                            <TableCell className={styles.colAccount}>Account</TableCell>
                            <TableCell>Merchant</TableCell>
                            <TableCell align="right" className={styles.colAmount}>Amount</TableCell>
                            <TableCell className={styles.colActions} />
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows.map((row) => {
                            return (
                                <TableRow key={row.id} hover>
                                    <TableCell className={shared.nowrap}>{formatDate(row.postedAt)}</TableCell>
                                    <TableCell>
                                        <div className={shared.iconRow}>
                                            <EntityAvatar url={row.accountLogoUrl} name={row.accountName} />
                                            <span className={shared.ellipsis}>{row.accountName}</span>
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <span className={shared.ellipsis}>{row.merchant ?? row.merchantName}</span>
                                    </TableCell>
                                    <TableCell align="right" className={shared.nowrap}>
                                        <span className={row.amount < 0 ? shared.amountNeg : shared.amountPos}>
                                            {formatMoney(row.amount, row.currency)}
                                        </span>
                                    </TableCell>
                                    <TableCell align="right">
                                        <Button size="small" startIcon={<RestoreIcon />} onClick={() => restore(row.id)}>
                                            Restore
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                        {!loading && rows.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={5}>
                                    <Typography color="text.secondary">No duplicates.</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
