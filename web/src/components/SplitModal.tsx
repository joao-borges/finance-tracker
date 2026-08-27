import { useEffect, useState } from "react";
import { App as AntApp, Button, InputNumber, Modal, Select, Typography } from "antd";
import { transactionsApi, type Category, type SplitLine, type Transaction } from "../lib/api";
import { errorText, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./SplitModal.module.css";

interface Row {
    // Magnitude only — the parent's sign is applied on save, so splitting an
    // expense can never accidentally produce an inflow.
    amount: number | null;
    categoryId?: number;
    tags: string[];
}

interface Props {
    transaction: Transaction | null;
    categories: Category[];
    tagOptions?: string[];
    open: boolean;
    onClose: () => void;
    onDone: () => void;
}

const blankRow = (): Row => ({ amount: null, categoryId: undefined, tags: [] });

// Split one transaction into N (amount, category, tags) legs. The parent is
// hidden and replaced by the children.
export default function SplitModal({ transaction, categories, tagOptions, open, onClose, onDone }: Props) {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Row[]>([]);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (transaction) {
            setRows([blankRow(), blankRow()]);
        }
    }, [transaction]);

    if (!transaction) {
        return null;
    }

    const total = Math.abs(transaction.amount);
    const sign = transaction.amount < 0 ? -1 : 1;
    const entered = rows.reduce((sum, row) => sum + (row.amount ?? 0), 0);
    const remaining = Math.round((total - entered) * 100) / 100;

    const setRow = (index: number, patch: Partial<Row>) => {
        setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
    };

    const save = async () => {
        const lines: SplitLine[] = rows
            .filter((row) => row.amount != null && row.amount > 0 && row.categoryId != null)
            .map((row) => ({
                amount: sign * Math.abs(row.amount as number),
                categoryId: row.categoryId as number,
                tags: row.tags,
            }));
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
        <Modal
            open={open}
            title="Split transaction"
            okText="Save split"
            confirmLoading={saving}
            onOk={save}
            onCancel={onClose}
            width={600}
        >
            <div className={styles.summary}>
                <Typography.Text strong className={styles.total}>
                    {formatMoney(total, transaction.currency)}
                </Typography.Text>
                <Typography.Text type="secondary">
                    {transaction.merchant ?? transaction.merchantName} · {transaction.accountName}
                </Typography.Text>
            </div>

            {rows.map((row, index) => {
                return (
                    <div key={index} className={styles.row}>
                        <InputNumber
                            className={styles.amount}
                            min={0}
                            step={0.01}
                            placeholder="0.00"
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
                        <Select
                            mode="tags"
                            allowClear
                            className={styles.tags}
                            placeholder="Tags"
                            value={row.tags}
                            onChange={(value: string[]) => setRow(index, { tags: value })}
                            options={(tagOptions ?? []).map((tag) => ({ label: tag, value: tag }))}
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

            <Button type="dashed" className={styles.addBtn} onClick={() => setRows((current) => [...current, blankRow()])}>
                + Add line
            </Button>

            <div className={remaining === 0 ? styles.remaining : `${styles.remaining} ${styles.remainingOff}`}>
                {remaining === 0
                    ? "Fully allocated"
                    : remaining > 0
                      ? `Left to allocate: ${formatMoney(remaining, transaction.currency)}`
                      : `Over by ${formatMoney(Math.abs(remaining), transaction.currency)}`}
            </div>
            <Typography.Text type="secondary" className={shared.secondary}>
                Amounts are portions of the total — the sign follows the original transaction.
            </Typography.Text>
        </Modal>
    );
}
