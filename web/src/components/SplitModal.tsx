import { useEffect, useState } from "react";
import { App as AntApp, Button, InputNumber, Modal, Select, Typography } from "antd";
import { transactionsApi, type Category, type SplitLine, type Transaction } from "../lib/api";
import { errorText, formatMoney } from "../lib/format";
import styles from "./SplitModal.module.css";

interface Row {
    amount: number | null;
    categoryId?: number;
}

interface Props {
    transaction: Transaction | null;
    categories: Category[];
    open: boolean;
    onClose: () => void;
    onDone: () => void;
}

// Split one transaction into N (amount, category) legs. The parent is hidden and
// replaced by the children. Amounts don't have to sum to the parent (soft check).
export default function SplitModal({ transaction, categories, open, onClose, onDone }: Props) {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Row[]>([]);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (transaction) {
            setRows([
                { amount: transaction.amount, categoryId: transaction.categoryId ?? undefined },
                { amount: null, categoryId: undefined },
            ]);
        }
    }, [transaction]);

    if (!transaction) {
        return null;
    }

    const sum = rows.reduce((total, row) => total + (row.amount ?? 0), 0);
    const remaining = Math.round((transaction.amount - sum) * 100) / 100;
    const setRow = (index: number, patch: Partial<Row>) => {
        setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
    };

    const save = async () => {
        const lines: SplitLine[] = rows
            .filter((row) => row.amount != null && row.categoryId != null)
            .map((row) => ({ amount: row.amount as number, categoryId: row.categoryId as number }));
        if (lines.length === 0) {
            message.error("Add at least one line with an amount and a category");
            return;
        }
        setSaving(true);
        try {
            await transactionsApi.split(transaction.id, lines);
            message.success("Transaction split");
            onDone();
            onClose();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal open={open} title="Split transaction" okText="Save split" confirmLoading={saving} onOk={save} onCancel={onClose} width={560}>
            <Typography.Paragraph type="secondary">
                {transaction.accountName} · {formatMoney(transaction.amount, transaction.currency)} ·{" "}
                {transaction.merchant ?? transaction.merchantName}
            </Typography.Paragraph>
            {rows.map((row, index) => {
                return (
                    <div key={index} className={styles.row}>
                        <InputNumber
                            className={styles.amount}
                            value={row.amount}
                            onChange={(value) => setRow(index, { amount: value })}
                            prefix="$"
                        />
                        <Select
                            showSearch
                            optionFilterProp="label"
                            className={styles.category}
                            placeholder="Category"
                            value={row.categoryId}
                            onChange={(value: number) => setRow(index, { categoryId: value })}
                            options={categories.map((category) => ({
                                label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                                value: category.id,
                            }))}
                        />
                        <Button
                            type="text"
                            danger
                            disabled={rows.length <= 1}
                            onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                        >
                            ✕
                        </Button>
                    </div>
                );
            })}
            <Button type="dashed" className={styles.addBtn} onClick={() => setRows((current) => [...current, { amount: null }])}>
                + Add line
            </Button>
            <div className={remaining === 0 ? styles.remaining : `${styles.remaining} ${styles.remainingOff}`}>
                Remaining: {formatMoney(remaining)}
            </div>
        </Modal>
    );
}
