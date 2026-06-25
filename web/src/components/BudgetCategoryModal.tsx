import { useCallback, useEffect, useState } from "react";
import {
    Box,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    Typography,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { App as AntApp } from "antd";
import dayjs from "dayjs";
import TransactionsTable from "./TransactionsTable";
import TransactionEditModal from "./TransactionEditModal";
import {
    transactionsApi,
    type BudgetLine,
    type Category,
    type Merchant,
    type Transaction,
} from "../lib/api";
import { errorText, formatMoney } from "../lib/format";

interface Props {
    line: BudgetLine | null;
    month: string;
    categories: Category[];
    merchants: Merchant[];
    onClose: () => void;
    onChanged: () => void;
}

// Period transactions for one budget category, with inline review/edit.
export default function BudgetCategoryModal({ line, month, categories, merchants, onClose, onChanged }: Props) {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(false);
    const [editing, setEditing] = useState<Transaction | null>(null);

    const load = useCallback(() => {
        if (!line) {
            return;
        }
        setLoading(true);
        transactionsApi
            .list(
                {
                    categoryIds: [line.categoryId],
                    from: `${month}-01`,
                    to: dayjs(`${month}-01`).endOf("month").format("YYYY-MM-DD"),
                },
                0,
                500,
            )
            .then((result) => setRows(result.content))
            .catch((error: unknown) => message.error(errorText(error)))
            .finally(() => setLoading(false));
    }, [line, month, message]);

    useEffect(load, [load]);

    const onSaved = () => {
        load();
        onChanged();
    };

    return (
        <Dialog open={line !== null} onClose={onClose} maxWidth="lg" fullWidth disableEnforceFocus>
            <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <Box sx={{ flex: 1 }}>
                    {line?.icon ? `${line.icon} ` : ""}
                    {line?.name}
                    {line && (
                        <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
                            {dayjs(`${month}-01`).format("MMMM YYYY")} · planned {formatMoney(line.planned ?? 0)} · actual{" "}
                            {formatMoney(line.actual)}
                        </Typography>
                    )}
                </Box>
                <IconButton onClick={onClose} aria-label="close">
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent dividers>
                {loading ? (
                    <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
                        <CircularProgress size={24} />
                    </Box>
                ) : (
                    <TransactionsTable rows={rows} onOpen={setEditing} />
                )}
                <TransactionEditModal
                    transaction={editing}
                    categories={categories}
                    merchants={merchants}
                    onClose={() => setEditing(null)}
                    onSaved={onSaved}
                />
            </DialogContent>
        </Dialog>
    );
}
