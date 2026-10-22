import { Chip, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip } from "@mui/material";
import type { AmazonCharge, AmazonOrder } from "../lib/api";
import { formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./AmazonOrdersTable.module.css";

interface Props {
    orders: AmazonOrder[];
    onLink: (charge: AmazonCharge, order: AmazonOrder) => void;
    onUnlink: (charge: AmazonCharge) => void;
}

// Imported Amazon orders: items, and each card charge with the bank
// transaction it was matched to. An unmatched charge opens the link picker.
export default function AmazonOrdersTable({ orders, onLink, onUnlink }: Props) {
    const chargeChip = (charge: AmazonCharge, order: AmazonOrder) => {
        const label = `${formatDate(charge.chargedOn)} · ${formatMoney(charge.amount, order.currency ?? "CAD")}`;
        if (charge.transactionId) {
            return (
                <Tooltip
                    key={charge.id}
                    title={`${charge.manual ? "Linked by hand" : "Matched"} to ${charge.transactionAccountName ?? "a transaction"}${
                        charge.transactionPostedAt ? ` on ${formatDate(charge.transactionPostedAt)}` : ""}`}
                >
                    <Chip size="small" color="success" variant="outlined" label={`✓ ${label}`} onDelete={() => onUnlink(charge)} />
                </Tooltip>
            );
        }
        return (
            <Tooltip key={charge.id} title={charge.cardLabel ?? "Card charge"}>
                <Chip
                    size="small"
                    color="warning"
                    variant="outlined"
                    label={`${label} · Link…`}
                    onClick={() => onLink(charge, order)}
                />
            </Tooltip>
        );
    };

    return (
        <TableContainer>
            <Table size="small" className={styles.table}>
                <TableHead>
                    <TableRow>
                        <TableCell className={styles.colDate}>Ordered</TableCell>
                        <TableCell>Items</TableCell>
                        <TableCell className={styles.colTotal} align="right">Total</TableCell>
                        <TableCell className={styles.colCharges}>Charges</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {orders.map((order) => {
                        return (
                            <TableRow key={order.id}>
                                <TableCell className={shared.nowrap}>
                                    <a href={order.url} target="_blank" rel="noreferrer" title={`Order ${order.orderNumber}`}>
                                        {formatDate(order.orderedOn)}
                                    </a>
                                </TableCell>
                                <TableCell>
                                    {order.items.length === 0 && <span className={shared.secondary}>No items read</span>}
                                    {order.items.map((item, index) => {
                                        return (
                                            <div key={index} className={shared.ellipsis} title={item.title}>
                                                {item.quantity > 1 ? `${item.quantity} × ` : ""}
                                                {item.title}
                                            </div>
                                        );
                                    })}
                                </TableCell>
                                <TableCell align="right" className={shared.nowrap}>
                                    {order.total != null ? formatMoney(order.total, order.currency ?? "CAD") : "—"}
                                </TableCell>
                                <TableCell>
                                    <div className={styles.charges}>
                                        {order.charges.length === 0 && <span className={shared.secondary}>None yet</span>}
                                        {order.charges.map((charge) => chargeChip(charge, order))}
                                    </div>
                                </TableCell>
                            </TableRow>
                        );
                    })}
                </TableBody>
            </Table>
        </TableContainer>
    );
}
