import { useEffect, useState } from "react";
import { App as AntApp, Empty, Modal, Radio, Spin, Typography } from "antd";
import EntityAvatar from "./EntityAvatar";
import { amazonApi, type AmazonCharge, type AmazonOrder, type Transaction } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./AmazonLinkModal.module.css";

interface Props {
    charge: AmazonCharge | null;
    order: AmazonOrder | null;
    onClose: () => void;
    onLinked: () => void;
}

// Pick the bank transaction an unmatched Amazon charge became. Candidates are
// Amazon-looking outflows around the charge date, closest amount first.
export default function AmazonLinkModal({ charge, order, onClose, onLinked }: Props) {
    const { message } = AntApp.useApp();
    const [candidates, setCandidates] = useState<Transaction[] | null>(null);
    const [selected, setSelected] = useState<number | undefined>(undefined);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setCandidates(null);
        setSelected(undefined);
        if (!charge) {
            return;
        }
        amazonApi.candidates(charge.id)
            .then((rows) => {
                setCandidates(rows);
                const exact = rows.find((row) => Math.abs(row.amount + charge.amount) < 0.005);
                setSelected(exact?.id);
            })
            .catch((error: unknown) => message.error(errorText(error)));
    }, [charge, message]);

    if (!charge || !order) {
        return null;
    }

    const save = async () => {
        if (selected == null) {
            return;
        }
        setSaving(true);
        try {
            await amazonApi.link(charge.id, selected);
            message.success("Charge linked");
            onLinked();
            onClose();
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            open
            title={`Link ${formatMoney(charge.amount, order.currency ?? "CAD")} charge of ${formatDate(charge.chargedOn)}`}
            okText="Link"
            okButtonProps={{ disabled: selected == null }}
            confirmLoading={saving}
            onOk={save}
            onCancel={onClose}
            width={600}
        >
            <Typography.Paragraph type="secondary">
                Order {order.orderNumber}
                {charge.cardLabel ? ` · ${charge.cardLabel}` : ""}
            </Typography.Paragraph>
            {candidates == null ? (
                <div className={shared.centerPad}>
                    <Spin />
                </div>
            ) : candidates.length === 0 ? (
                <Empty description="No Amazon transactions around that date" />
            ) : (
                <Radio.Group value={selected} onChange={(event) => setSelected(event.target.value as number)} className={styles.list}>
                    {candidates.map((candidate) => {
                        return (
                            <Radio key={candidate.id} value={candidate.id} className={styles.option}>
                                <span className={styles.row}>
                                    <span className={shared.nowrap}>{formatDate(candidate.postedAt)}</span>
                                    <EntityAvatar url={candidate.accountLogoUrl} name={candidate.accountName} />
                                    <span className={`${shared.ellipsis} ${styles.merchant}`}>
                                        {candidate.merchant ?? candidate.merchantName}
                                        {candidate.amazonSummary ? ` — already: ${candidate.amazonSummary}` : ""}
                                    </span>
                                    <span className={`${shared.nowrap} ${shared.amountNeg}`}>
                                        {formatMoney(candidate.amount, candidate.currency)}
                                    </span>
                                </span>
                            </Radio>
                        );
                    })}
                </Radio.Group>
            )}
        </Modal>
    );
}
