import { useEffect, useState } from "react";
import { App as AntApp, Input, InputNumber, Modal, Select, Space, Typography } from "antd";
import EmojiField from "./EmojiField";
import { budgetsApi, type CategoryGroup } from "../lib/api";
import { errorText } from "../lib/format";
import shared from "../styles/shared.module.css";

interface Props {
    open: boolean;
    month: string;
    groups: CategoryGroup[];
    onClose: () => void;
    onCreated: () => void;
}

/**
 * A budget line for a genuine one-off (a single payment to a friend). The
 * category it creates belongs to this month alone: it never shows in another
 * month's budget, and never in the Categories page.
 */
export default function OneTimeCategoryModal({ open, month, groups, onClose, onCreated }: Props) {
    const { message } = AntApp.useApp();
    const [name, setName] = useState("");
    const [groupId, setGroupId] = useState<number | undefined>(undefined);
    const [amount, setAmount] = useState<number | null>(null);
    const [icon, setIcon] = useState("");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (open) {
            setName("");
            setGroupId(undefined);
            setAmount(null);
            setIcon("");
        }
    }, [open]);

    const save = async () => {
        if (name.trim() === "") {
            message.error("Give the line a name");
            return;
        }
        if (amount === null || amount <= 0) {
            message.error("Enter a planned amount greater than zero");
            return;
        }
        setSaving(true);
        try {
            await budgetsApi.addOneTimeCategory(
                month,
                { name: name.trim(), groupId: groupId ?? null, plannedAmount: amount, icon: icon.trim() || null },
                true,
            );
            message.success("One-time line added");
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
            title="Add a one-time budget line"
            okText="Add"
            confirmLoading={saving}
            onOk={save}
            onCancel={onClose}
            width={460}
        >
            <Space direction="vertical" size="middle" className={shared.fullWidth}>
                <Typography.Text type="secondary">
                    Exists for {month} only — it won't appear in other months or in the Categories page.
                </Typography.Text>
                <div>
                    <Typography.Text strong>Name</Typography.Text>
                    <Input
                        placeholder="e.g. Pay Friend Jaime"
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                    />
                </div>
                <div>
                    <Typography.Text strong>Group</Typography.Text>
                    <Select
                        showSearch
                        allowClear
                        optionFilterProp="label"
                        className={shared.fullWidth}
                        placeholder="Which group it sits under"
                        value={groupId}
                        onChange={(value?: number) => setGroupId(value)}
                        options={groups.map((group) => ({ label: group.name, value: group.id }))}
                    />
                </div>
                <div>
                    <Typography.Text strong>Planned amount</Typography.Text>
                    <InputNumber
                        className={shared.fullWidth}
                        min={0}
                        step={0.01}
                        placeholder="0.00"
                        prefix="$"
                        value={amount}
                        onChange={(value) => setAmount(value)}
                    />
                </div>
                <div>
                    <Typography.Text strong>Icon</Typography.Text>
                    <EmojiField value={icon} onChange={setIcon} />
                </div>
            </Space>
        </Modal>
    );
}
