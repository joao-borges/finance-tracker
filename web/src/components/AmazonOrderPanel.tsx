import { useEffect, useState } from "react";
import { Button, Checkbox, Tag, Typography } from "antd";
import type { AmazonItem, AmazonOrder, SplitSeed, Transaction } from "../lib/api";
import { splitSeedsFromItems } from "../lib/amazon";
import { formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./AmazonOrderPanel.module.css";

interface Props {
    transaction: Transaction;
    orders: AmazonOrder[];
    // Omitted when the transaction can't be split (it's already a split line).
    onSplit?: (seeds: SplitSeed[]) => void;
}

const itemKey = (order: AmazonOrder, index: number) => {
    return `${order.id}:${index}`;
};

// "What did this Amazon charge buy": the linked orders, their items, and every
// charge each order made — so a charge that covers part of an order (or several
// orders) can be read, and split one line per item.
export default function AmazonOrderPanel({ transaction, orders, onSplit }: Props) {
    const [selected, setSelected] = useState<Set<string>>(new Set());
    // The charged bank row: a split line answers for its parent.
    const chargedRowId = transaction.splitParentId ?? transaction.id;

    useEffect(() => {
        setSelected(new Set(orders.flatMap((order) => order.items.map((_, index) => itemKey(order, index)))));
    }, [orders]);

    const toggle = (key: string) => {
        setSelected((current) => {
            const next = new Set(current);
            if (next.has(key)) {
                next.delete(key);
            } else {
                next.add(key);
            }
            return next;
        });
    };

    const chosenItems: AmazonItem[] = orders.flatMap((order) => {
        return order.items.filter((_, index) => selected.has(itemKey(order, index)));
    });

    return (
        <div className={styles.panel}>
            {orders.map((order) => {
                return (
                    <div key={order.id} className={styles.order}>
                        <div className={styles.orderHeader}>
                            <Typography.Text strong>
                                <a href={order.url} target="_blank" rel="noreferrer">
                                    Order {order.orderNumber}
                                </a>
                            </Typography.Text>
                            <Typography.Text type="secondary" className={shared.nowrap}>
                                {formatDate(order.orderedOn)}
                                {order.total != null && ` · ${formatMoney(order.total, order.currency ?? transaction.currency)}`}
                            </Typography.Text>
                        </div>

                        {order.items.map((item, index) => {
                            const key = itemKey(order, index);
                            const text = (
                                <span className={styles.itemText}>
                                    <span className={shared.ellipsis} title={item.title}>
                                        {item.quantity > 1 ? `${item.quantity} × ` : ""}
                                        {item.title}
                                    </span>
                                    {item.unitPrice != null && (
                                        <span className={`${shared.nowrap} ${shared.secondary}`}>
                                            {formatMoney(item.unitPrice * item.quantity, order.currency ?? transaction.currency)}
                                        </span>
                                    )}
                                </span>
                            );
                            return onSplit ? (
                                <Checkbox key={key} checked={selected.has(key)} onChange={() => toggle(key)} className={styles.item}>
                                    {text}
                                </Checkbox>
                            ) : (
                                <div key={key} className={styles.item}>
                                    {text}
                                </div>
                            );
                        })}
                        {order.items.length === 0 && (
                            <Typography.Text type="secondary">No items could be read from this invoice.</Typography.Text>
                        )}

                        <div className={styles.charges}>
                            {order.charges.map((charge) => {
                                const thisOne = charge.transactionId === chargedRowId;
                                return (
                                    <Tag key={charge.id} color={thisOne ? "processing" : charge.transactionId ? "default" : "warning"}>
                                        {formatDate(charge.chargedOn)} · {formatMoney(charge.amount, order.currency ?? transaction.currency)}
                                        {charge.cardLast4 ? ` · …${charge.cardLast4}` : ""}
                                        {thisOne ? " · this charge" : charge.transactionId ? "" : " · not matched"}
                                    </Tag>
                                );
                            })}
                        </div>
                    </div>
                );
            })}

            {onSplit && (
                <>
                    <Button
                        block
                        disabled={chosenItems.length === 0}
                        onClick={() => onSplit(splitSeedsFromItems(chosenItems, transaction.amount))}
                    >
                        Split by selected items ({chosenItems.length})…
                    </Button>
                    <Typography.Text type="secondary" className={styles.hint}>
                        Invoice prices are before tax; the split lines are scaled to add up to this charge.
                    </Typography.Text>
                </>
            )}
        </div>
    );
}
