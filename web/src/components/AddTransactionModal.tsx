import { useEffect, useState } from "react";
import { App as AntApp, DatePicker, Input, InputNumber, Modal, Segmented, Select, Space, Switch, Typography } from "antd";
import dayjs from "dayjs";
import {
    transactionsApi,
    type Account,
    type Category,
    type ManualTransactionInput,
    type Merchant,
} from "../lib/api";
import { browserTimeZone, errorText } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./AddTransactionModal.module.css";

type Direction = "expense" | "income";

interface Props {
    open: boolean;
    accounts: Account[];
    categories: Category[];
    merchants: Merchant[];
    onClose: () => void;
    onCreated: () => void;
}

export default function AddTransactionModal({ open, accounts, categories, merchants, onClose, onCreated }: Props) {
    const { message } = AntApp.useApp();
    const [accountId, setAccountId] = useState<number | undefined>(undefined);
    const [date, setDate] = useState<dayjs.Dayjs>(dayjs());
    const [direction, setDirection] = useState<Direction>("expense");
    const [amount, setAmount] = useState<number | null>(null);
    const [description, setDescription] = useState("");
    const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
    const [merchantId, setMerchantId] = useState<number | undefined>(undefined);
    const [excluded, setExcluded] = useState(false);
    const [awaiting, setAwaiting] = useState(false);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!open) {
            return;
        }
        setAccountId(accounts.length === 1 ? accounts[0].id : undefined);
        setDate(dayjs());
        setDirection("expense");
        setAmount(null);
        setDescription("");
        setCategoryId(undefined);
        setMerchantId(undefined);
        setExcluded(false);
        setAwaiting(false);
    }, [open, accounts]);

    const save = async () => {
        if (accountId === undefined) {
            message.error("Pick an account");
            return;
        }
        if (amount === null || amount <= 0) {
            message.error("Enter an amount greater than zero");
            return;
        }
        if (description.trim() === "") {
            message.error("Enter a description");
            return;
        }
        setSaving(true);
        try {
            const signed = direction === "expense" ? -Math.abs(amount) : Math.abs(amount);
            const body: ManualTransactionInput = {
                accountId,
                date: date.format("YYYY-MM-DD"),
                timeZone: browserTimeZone(),
                description: description.trim(),
                amount: signed,
                categoryId: categoryId ?? null,
                merchantId: merchantId ?? null,
                excludedFromBudget: excluded,
                awaitingRefund: awaiting,
            };
            await transactionsApi.create(body);
            message.success("Transaction added");
            onCreated();
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
            title="Add transaction"
            okText="Add"
            confirmLoading={saving}
            onOk={save}
            onCancel={onClose}
            width={520}
            destroyOnClose
        >
            <Space direction="vertical" size="middle" className={shared.fullWidth}>
                <div>
                    <Typography.Text strong>Account</Typography.Text>
                    <Select
                        showSearch
                        optionFilterProp="label"
                        className={shared.fullWidth}
                        placeholder="Choose account"
                        value={accountId}
                        onChange={(value: number) => setAccountId(value)}
                        options={accounts.map((account) => ({ label: account.name, value: account.id }))}
                    />
                </div>

                <div className={styles.row}>
                    <div className={styles.dateField}>
                        <Typography.Text strong>Date</Typography.Text>
                        <DatePicker
                            className={shared.fullWidth}
                            value={date}
                            allowClear={false}
                            onChange={(value) => setDate(value ?? dayjs())}
                        />
                    </div>
                    <div className={styles.amountField}>
                        <Typography.Text strong>Amount</Typography.Text>
                        <div className={styles.amountRow}>
                            <Segmented
                                value={direction}
                                onChange={(value) => setDirection(value as Direction)}
                                options={[
                                    { label: "Expense", value: "expense" },
                                    { label: "Income", value: "income" },
                                ]}
                            />
                            <InputNumber
                                className={styles.amountInput}
                                min={0}
                                step={0.01}
                                placeholder="0.00"
                                value={amount}
                                onChange={(value) => setAmount(value)}
                            />
                        </div>
                    </div>
                </div>

                <div>
                    <Typography.Text strong>Description</Typography.Text>
                    <Input
                        placeholder="e.g. Farmers market"
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                    />
                </div>

                <div>
                    <Typography.Text strong>Category</Typography.Text>
                    <Select
                        showSearch
                        allowClear
                        optionFilterProp="label"
                        className={shared.fullWidth}
                        placeholder="Optional — left blank goes to review"
                        value={categoryId}
                        onChange={(value?: number) => setCategoryId(value)}
                        options={categories.map((category) => ({
                            label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                            value: category.id,
                        }))}
                    />
                </div>

                <div>
                    <Typography.Text strong>Merchant</Typography.Text>
                    <Select
                        showSearch
                        allowClear
                        optionFilterProp="label"
                        className={shared.fullWidth}
                        placeholder="Optional"
                        value={merchantId}
                        onChange={(value?: number) => setMerchantId(value)}
                        options={merchants.map((merchant) => ({
                            label: `${merchant.icon ? merchant.icon + " " : ""}${merchant.name}`,
                            value: merchant.id,
                        }))}
                    />
                </div>

                <Space direction="vertical">
                    <span>
                        <Switch checked={excluded} onChange={(value) => setExcluded(value)} /> &nbsp; Exclude from budget
                    </span>
                    <span>
                        <Switch checked={awaiting} onChange={(value) => setAwaiting(value)} /> &nbsp; Awaiting refund
                        (won't count until refunded)
                    </span>
                </Space>
            </Space>
        </Modal>
    );
}
