import { useState } from "react";
import { IconButton, Tooltip } from "@mui/material";
import CallMergeIcon from "@mui/icons-material/CallMerge";
import { App as AntApp, Modal, Select, Typography } from "antd";
import { accountsApi, type Account } from "../lib/api";
import { errorText } from "../lib/format";
import shared from "../styles/shared.module.css";

interface Props {
    account: Account;
    reload: () => void;
}

export default function MergeAccountButton({ account, reload }: Props) {
    const { message } = AntApp.useApp();
    const [open, setOpen] = useState(false);
    const [targets, setTargets] = useState<Account[]>([]);
    const [targetId, setTargetId] = useState<number | undefined>(undefined);

    const openModal = () => {
        setTargetId(undefined);
        accountsApi
            .list()
            .then((list) => setTargets(list.filter((candidate) => candidate.id !== account.id)))
            .catch(() => setTargets([]));
        setOpen(true);
    };

    const confirm = async () => {
        if (targetId === undefined) {
            return;
        }
        try {
            await accountsApi.merge(account.id, targetId);
            message.success("Accounts merged");
            setOpen(false);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <>
            <Tooltip title="Merge into another account">
                <IconButton size="small" onClick={openModal} aria-label="merge">
                    <CallMergeIcon fontSize="small" />
                </IconButton>
            </Tooltip>
            <Modal
                open={open}
                title={`Merge “${account.name}” into…`}
                okText="Merge"
                onOk={confirm}
                onCancel={() => setOpen(false)}
                okButtonProps={{ disabled: targetId === undefined }}
            >
                <Typography.Paragraph type="secondary">
                    Transactions move to the target account; “{account.name}” stays hidden as an import reference so
                    future imports keep matching.
                </Typography.Paragraph>
                <Select
                    showSearch
                    optionFilterProp="label"
                    className={shared.fullWidth}
                    placeholder="Target account"
                    value={targetId}
                    onChange={(value: number) => setTargetId(value)}
                    options={targets.map((candidate) => ({ label: candidate.name, value: candidate.id }))}
                />
            </Modal>
        </>
    );
}
