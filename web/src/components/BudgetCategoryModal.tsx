import { useCallback, useEffect, useState } from "react";
import { App as AntApp, Modal, Spin, Typography } from "antd";
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
import styles from "./BudgetCategoryModal.module.css";

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

    const title = line ? (
        <span>
            {line.icon ? `${line.icon} ` : ""}
            {line.name}
            <Typography.Text type="secondary" className={styles.meta}>
                {dayjs(`${month}-01`).format("MMMM YYYY")} · planned {formatMoney(line.planned ?? 0)} · actual{" "}
                {formatMoney(line.actual)}
            </Typography.Text>
        </span>
    ) : null;

    return (
        <Modal open={line !== null} title={title} footer={null} onCancel={onClose} width={960} destroyOnClose>
            {loading ? (
                <div className={styles.loading}>
                    <Spin />
                </div>
            ) : (
                <TransactionsTable rows={rows} onOpen={setEditing} accountIconOnly />
            )}
            <TransactionEditModal
                transaction={editing}
                categories={categories}
                merchants={merchants}
                onClose={() => setEditing(null)}
                onSaved={onSaved}
                onStructuralChange={onSaved}
            />
        </Modal>
    );
}
