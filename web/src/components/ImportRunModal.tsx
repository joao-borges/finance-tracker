import { useEffect, useState } from "react";
import { App as AntApp, Modal, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { importApi, type ImportRun, type Transaction } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./ImportRunModal.module.css";

interface Props {
    run: ImportRun | null;
    onClose: () => void;
}

/**
 * What one import run actually brought in — the audit trail behind a row in the
 * history, and the way to check an import before undoing it. Quarantined
 * duplicates are listed too: they are the run's doing as much as the live rows.
 */
export default function ImportRunModal({ run, onClose }: Props) {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!run) {
            return;
        }
        let cancelled = false;
        setLoading(true);
        importApi
            .transactions(run.id)
            .then((result) => {
                if (!cancelled) {
                    setRows(result);
                }
            })
            .catch((error: unknown) => {
                if (!cancelled) {
                    message.error(errorText(error));
                    setRows([]);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [run, message]);

    if (!run) {
        return null;
    }

    const columns: ColumnsType<Transaction> = [
        {
            title: "Date",
            dataIndex: "postedAt",
            width: 130,
            render: (value: string) => {
                return <span className={shared.nowrap}>{formatDate(value)}</span>;
            },
        },
        {
            title: "Account",
            dataIndex: "accountName",
            width: 180,
            ellipsis: true,
        },
        {
            title: "Description",
            dataIndex: "merchantName",
            ellipsis: true,
            render: (value: string, row: Transaction) => {
                return (
                    <span>
                        {row.merchant ?? value}
                        {row.dedup && <Tag className={styles.flag}>duplicate</Tag>}
                    </span>
                );
            },
        },
        {
            title: "Category",
            dataIndex: "categoryName",
            width: 160,
            ellipsis: true,
            render: (value: string | null) => {
                return value ?? <span className={shared.secondary}>—</span>;
            },
        },
        {
            title: "Amount",
            dataIndex: "amount",
            width: 120,
            align: "right",
            render: (value: number, row: Transaction) => {
                return (
                    <span className={value < 0 ? shared.amountNeg : shared.amountPos}>
                        {formatMoney(value, row.currency)}
                    </span>
                );
            },
        },
    ];

    const net = rows.reduce((sum, row) => (row.dedup ? sum : sum + row.amount), 0);

    return (
        <Modal
            open
            title={`Import ${run.fileName ?? run.source} — ${new Date(run.startedAt).toLocaleString()}`}
            onCancel={onClose}
            footer={null}
            width={900}
        >
            {loading ? (
                <div className={shared.centerPad}>
                    <Spin />
                </div>
            ) : (
                <>
                    <Typography.Text className={styles.summary}>
                        <strong>{rows.length}</strong> row{rows.length === 1 ? "" : "s"} · net {formatMoney(net)}
                    </Typography.Text>
                    <Table
                        rowKey="id"
                        size="small"
                        columns={columns}
                        dataSource={rows}
                        pagination={{ pageSize: 25, showSizeChanger: false }}
                        scroll={{ x: true }}
                    />
                </>
            )}
        </Modal>
    );
}
