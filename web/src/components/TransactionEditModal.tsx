import { useEffect, useState } from "react";
import { App as AntApp, Button, Checkbox, Divider, Input, Modal, Radio, Select, Space, Switch, Typography } from "antd";
import {
    merchantsApi,
    rulesApi,
    rulesExtraApi,
    transactionsApi,
    type Category,
    type Merchant,
    type Transaction,
    type TransactionUpdate,
} from "../lib/api";
import EmojiField from "./EmojiField";
import SplitModal from "./SplitModal";
import { errorText, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./TransactionEditModal.module.css";

type MerchantMode = "existing" | "new" | "keep";

interface Props {
    transaction: Transaction | null;
    categories: Category[];
    merchants: Merchant[];
    onClose: () => void;
    onSaved: (updated: Transaction) => void;
    onStructuralChange?: () => void;
}

export default function TransactionEditModal({ transaction, categories, merchants, onClose, onSaved, onStructuralChange }: Props) {
    const { message } = AntApp.useApp();
    const [splitOpen, setSplitOpen] = useState(false);
    const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
    const [merchantMode, setMerchantMode] = useState<MerchantMode>("existing");
    const [existingMerchantId, setExistingMerchantId] = useState<number | undefined>(undefined);
    const [newName, setNewName] = useState("");
    const [newIcon, setNewIcon] = useState("");
    const [newWebsite, setNewWebsite] = useState("");
    const [reviewed, setReviewed] = useState(true);
    const [excluded, setExcluded] = useState(false);
    const [makeRule, setMakeRule] = useState(false);
    const [ruleMatch, setRuleMatch] = useState("");
    const [ruleAuto, setRuleAuto] = useState(true);
    const [applyRule, setApplyRule] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!transaction) {
            return;
        }
        setCategoryId(transaction.categoryId ?? undefined);
        setMerchantMode("existing");
        setExistingMerchantId(transaction.merchantId ?? undefined);
        setNewName(transaction.merchant ?? transaction.merchantName);
        setNewIcon("");
        setNewWebsite("");
        setReviewed(true);
        setExcluded(transaction.excludedFromBudget);
        setMakeRule(false);
        setRuleMatch(transaction.merchantName);
        setRuleAuto(true);
        setApplyRule(true);
    }, [transaction]);

    if (!transaction) {
        return null;
    }

    const save = async () => {
        if (makeRule && categoryId === undefined) {
            message.error("Pick a category to create a rule");
            return;
        }
        setSaving(true);
        try {
            let merchantId: number | undefined;
            if (merchantMode === "existing") {
                merchantId = existingMerchantId;
            } else if (merchantMode === "new" && newName.trim() !== "") {
                const created = await merchantsApi.create({
                    name: newName.trim(),
                    icon: newIcon.trim() || null,
                    website: newWebsite.trim() || null,
                });
                merchantId = created.id;
            }

            const body: TransactionUpdate = {
                categoryId: categoryId ?? undefined,
                merchantId,
                needsReview: !reviewed,
                excludedFromBudget: excluded,
            };
            const updated = await transactionsApi.update(transaction.id, body);

            if (makeRule && ruleMatch.trim() !== "" && categoryId !== undefined) {
                const rule = await rulesApi.create({
                    name: ruleMatch.trim(),
                    merchantMatch: ruleMatch.trim(),
                    categoryId,
                    merchantId: merchantId ?? null,
                    autoApprove: ruleAuto,
                    enabled: true,
                    priority: 0,
                });
                if (applyRule && rule.id) {
                    const result = await rulesExtraApi.apply(rule.id);
                    message.success(`Saved · rule applied to ${result.applied} transaction(s)`);
                } else {
                    message.success("Saved · rule created");
                }
            } else {
                message.success("Saved");
            }
            onSaved(updated);
            onClose();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setSaving(false);
        }
    };

    const unsplit = async () => {
        if (transaction.splitParentId == null) {
            return;
        }
        try {
            await transactionsApi.unsplit(transaction.splitParentId);
            message.success("Split undone");
            onStructuralChange?.();
            onClose();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <>
        <Modal
            open
            title={transaction.needsReview ? "Review transaction" : "Edit transaction"}
            okText="Save"
            confirmLoading={saving}
            onOk={save}
            onCancel={onClose}
            width={520}
        >
            <Typography.Paragraph type="secondary" className={styles.firstParagraph}>
                {new Date(transaction.postedAt).toLocaleDateString()} · {transaction.accountName} ·{" "}
                {formatMoney(transaction.amount, transaction.currency)}
            </Typography.Paragraph>
            <Typography.Paragraph className={styles.statementParagraph}>
                <strong>Statement:</strong> {transaction.merchantName}
            </Typography.Paragraph>

            <Typography.Text strong>Category</Typography.Text>
            <Select
                showSearch
                optionFilterProp="label"
                className={styles.categorySelect}
                placeholder="Choose category"
                value={categoryId}
                onChange={(value: number) => setCategoryId(value)}
                options={categories.map((category) => ({
                    label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                    value: category.id,
                }))}
            />

            <Divider />
            <Typography.Text strong>Merchant</Typography.Text>
            <Radio.Group
                className={styles.merchantRadioGroup}
                value={merchantMode}
                onChange={(event) => setMerchantMode(event.target.value as MerchantMode)}
            >
                <Radio value="existing">Existing</Radio>
                <Radio value="new">New</Radio>
                <Radio value="keep">Keep</Radio>
            </Radio.Group>
            {merchantMode === "existing" && (
                <Select
                    showSearch
                    optionFilterProp="label"
                    className={shared.fullWidth}
                    placeholder="Choose merchant"
                    value={existingMerchantId}
                    onChange={(value: number) => setExistingMerchantId(value)}
                    options={merchants.map((merchant) => ({
                        label: `${merchant.icon ? merchant.icon + " " : ""}${merchant.name}`,
                        value: merchant.id,
                    }))}
                />
            )}
            {merchantMode === "new" && (
                <Space direction="vertical" className={shared.fullWidth}>
                    <Input placeholder="Merchant name" value={newName} onChange={(event) => setNewName(event.target.value)} />
                    <EmojiField value={newIcon} onChange={setNewIcon} />
                    <Input placeholder="Website (optional, for logo)" value={newWebsite} onChange={(event) => setNewWebsite(event.target.value)} />
                </Space>
            )}
            {merchantMode === "keep" && (
                <Typography.Text type="secondary">
                    Keeps {transaction.merchant ? `“${transaction.merchant}”` : "the current merchant"} unchanged.
                </Typography.Text>
            )}

            <Divider />
            <Space direction="vertical">
                <span>
                    <Switch checked={reviewed} onChange={(value) => setReviewed(value)} /> &nbsp; Mark reviewed
                </span>
                <span>
                    <Switch checked={excluded} onChange={(value) => setExcluded(value)} /> &nbsp; Exclude from budget
                </span>
            </Space>

            <Divider />
            <Checkbox checked={makeRule} onChange={(event) => setMakeRule(event.target.checked)}>
                Create a rule from this transaction
            </Checkbox>
            {makeRule && (
                <Space direction="vertical" className={styles.ruleSpace}>
                    <div>
                        <Typography.Text type="secondary">Match when statement contains</Typography.Text>
                        <Input value={ruleMatch} onChange={(event) => setRuleMatch(event.target.value)} />
                    </div>
                    <Checkbox checked={ruleAuto} onChange={(event) => setRuleAuto(event.target.checked)}>
                        Auto-approve matches
                    </Checkbox>
                    <Checkbox checked={applyRule} onChange={(event) => setApplyRule(event.target.checked)}>
                        Apply to existing uncategorized transactions now
                    </Checkbox>
                    <Typography.Text type="secondary">
                        The rule sets the category{merchantMode !== "keep" ? " and merchant" : ""} chosen above.
                    </Typography.Text>
                </Space>
            )}

            <Divider />
            {transaction.splitParentId == null ? (
                <Button block onClick={() => setSplitOpen(true)}>
                    Split transaction…
                </Button>
            ) : (
                <Button block danger onClick={unsplit}>
                    Un-split (restore original)
                </Button>
            )}
        </Modal>

        <SplitModal
            transaction={transaction}
            categories={categories}
            open={splitOpen}
            onClose={() => setSplitOpen(false)}
            onDone={() => {
                onStructuralChange?.();
                onClose();
            }}
        />
        </>
    );
}
