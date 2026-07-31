import { useState } from "react";
import {
    Chip,
    IconButton,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from "@mui/material";
import FactCheckIcon from "@mui/icons-material/FactCheck";
import EditIcon from "@mui/icons-material/Edit";
import { App as AntApp } from "antd";
import EntityAvatar from "./EntityAvatar";
import InlineSelect from "./InlineSelect";
import { transactionsApi, type Category, type Merchant, type Transaction, type TransactionUpdate } from "../lib/api";
import { errorText, formatDate, formatMoney } from "../lib/format";
import shared from "../styles/shared.module.css";
import styles from "./TransactionsTable.module.css";

interface Props {
    rows: Transaction[];
    onOpen: (row: Transaction) => void;
    // Inline editing (merchant/category selects, clickable Review chip) is active
    // only when the lookup lists and the update callback are provided.
    merchants?: Merchant[];
    categories?: Category[];
    onUpdated?: (updated: Transaction) => void;
    // In tight layouts (the budget drill-down modal), show only the account logo
    // so the merchant column has room.
    accountIconOnly?: boolean;
}

export default function TransactionsTable({ rows, onOpen, merchants, categories, onUpdated, accountIconOnly }: Props) {
    const { message } = AntApp.useApp();
    const [flashCategoryRow, setFlashCategoryRow] = useState<number | null>(null);

    const markReviewed = (row: Transaction) => {
        // Every budget-relevant transaction needs a category before review —
        // off-budget accounts are exempt (blank category is their design).
        if (!row.categoryId && !row.accountOffBudget) {
            setFlashCategoryRow(row.id);
            window.setTimeout(() => setFlashCategoryRow(null), 1300);
            return;
        }
        save(row.id, { needsReview: false }, "Marked reviewed");
    };

    const save = async (id: number, body: TransactionUpdate, label: string) => {
        try {
            const updated = await transactionsApi.update(id, body);
            onUpdated?.(updated);
            message.success(label);
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    const merchantDisplay = (row: Transaction) => {
        return (
            <Tooltip title={`Statement: ${row.merchantName}`}>
                <div className={shared.iconRow}>
                    {row.merchantLogoUrl ? (
                        <EntityAvatar url={row.merchantLogoUrl} name={row.merchant ?? row.merchantName} />
                    ) : row.merchantIcon ? (
                        <span className={styles.merchantIcon}>{row.merchantIcon}</span>
                    ) : (
                        <EntityAvatar name={row.merchant ?? row.merchantName} />
                    )}
                    <span className={shared.ellipsis}>{row.merchant ?? row.merchantName}</span>
                </div>
            </Tooltip>
        );
    };

    const categoryDisplay = (row: Transaction) => {
        if (row.categoryName) {
            return (
                <span className={`${shared.ellipsis} ${styles.categoryName}`}>
                    {row.categoryIcon ? `${row.categoryIcon} ` : ""}
                    {row.categoryName}
                </span>
            );
        }
        if (row.accountOffBudget) {
            // Off-budget accounts deliberately have no category — show blank,
            // not "Uncategorized" (the cell stays clickable to override).
            return <span className={styles.blankCategory} />;
        }
        return (
            <Typography component="span" className={shared.secondary}>
                Uncategorized
            </Typography>
        );
    };

    return (
        <TableContainer component={Paper}>
            <Table size="small" className={styles.table}>
                <TableHead>
                    <TableRow>
                        <TableCell className={styles.colDate}>Date</TableCell>
                        <TableCell className={accountIconOnly ? styles.colAccountIcon : styles.colAccount}>
                            {accountIconOnly ? "" : "Account"}
                        </TableCell>
                        <TableCell className={shared.nowrap}>Merchant</TableCell>
                        <TableCell className={styles.colCategory}>Category</TableCell>
                        <TableCell align="right" className={styles.colAmount}>Amount</TableCell>
                        <TableCell className={styles.colStatus}>Status</TableCell>
                        <TableCell align="right" className={styles.colActions} />
                    </TableRow>
                </TableHead>
                <TableBody>
                    {rows.map((row) => {
                        return (
                            <TableRow key={row.id} hover>
                                <TableCell className={shared.nowrap}>
                                    {formatDate(row.postedAt)}
                                    {row.dateAdjusted && row.sourcePostedAt && (
                                        <Tooltip title={`Date adjusted — bank date: ${formatDate(row.sourcePostedAt)}`}>
                                            <span className={styles.dateAdjusted}>✱</span>
                                        </Tooltip>
                                    )}
                                </TableCell>
                                <TableCell>
                                    {accountIconOnly ? (
                                        <Tooltip title={row.accountName}>
                                            <span>
                                                <EntityAvatar url={row.accountLogoUrl} name={row.accountName} />
                                            </span>
                                        </Tooltip>
                                    ) : (
                                        <div className={shared.iconRow}>
                                            <EntityAvatar url={row.accountLogoUrl} name={row.accountName} />
                                            <span className={shared.ellipsis}>{row.accountName}</span>
                                        </div>
                                    )}
                                </TableCell>
                                <TableCell>
                                    {merchants && onUpdated ? (
                                        <InlineSelect
                                            display={merchantDisplay(row)}
                                            value={row.merchantId ?? undefined}
                                            options={merchants.map((merchant) => ({
                                                label: `${merchant.icon ? merchant.icon + " " : ""}${merchant.name}`,
                                                value: merchant.id,
                                            }))}
                                            onSave={(merchantId) => save(row.id, { merchantId }, "Merchant updated")}
                                        />
                                    ) : (
                                        merchantDisplay(row)
                                    )}
                                </TableCell>
                                <TableCell>
                                    <div className={row.id === flashCategoryRow ? shared.flashError : undefined}>
                                    {categories && onUpdated ? (
                                        <InlineSelect
                                            display={categoryDisplay(row)}
                                            value={row.categoryId ?? undefined}
                                            options={categories.map((category) => ({
                                                label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                                                value: category.id,
                                            }))}
                                            onSave={(categoryId) => save(row.id, { categoryId }, "Category updated")}
                                        />
                                    ) : (
                                        categoryDisplay(row)
                                    )}
                                    </div>
                                </TableCell>
                                <TableCell align="right" className={shared.nowrap}>
                                    <Typography
                                        component="span"
                                        className={row.amount < 0 ? shared.amountNeg : shared.amountPos}
                                    >
                                        {formatMoney(row.amount, row.currency)}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <div className={styles.statusCell}>
                                        {row.matchType === "TRANSFER" && <Chip size="small" color="info" label="⇄ Transfer" />}
                                        {row.matchType === "REFUND" && <Chip size="small" color="info" label="↩ Refund" />}
                                        {row.awaitingRefund && (
                                            <Chip size="small" color="warning" variant="outlined" label="Awaiting refund" />
                                        )}
                                        {row.needsReview ? (
                                            onUpdated ? (
                                                <Tooltip title="Mark reviewed">
                                                    <Chip
                                                        size="small"
                                                        color="warning"
                                                        label="Review"
                                                        onClick={() => markReviewed(row)}
                                                    />
                                                </Tooltip>
                                            ) : (
                                                <Chip size="small" color="warning" label="Review" />
                                            )
                                        ) : (
                                            <Chip size="small" color="success" label="Reviewed" />
                                        )}
                                        {row.excludedFromBudget && !row.matchType && (
                                            <Chip size="small" label="Excluded" variant="outlined" />
                                        )}
                                    </div>
                                </TableCell>
                                <TableCell align="right">
                                    {row.needsReview ? (
                                        <Tooltip title="Review">
                                            <IconButton size="small" color="primary" onClick={() => onOpen(row)}>
                                                <FactCheckIcon fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                    ) : (
                                        <Tooltip title="Edit">
                                            <IconButton size="small" onClick={() => onOpen(row)}>
                                                <EditIcon fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                </TableCell>
                            </TableRow>
                        );
                    })}
                    {rows.length === 0 && (
                        <TableRow>
                            <TableCell colSpan={7}>
                                <Typography className={shared.secondary}>No transactions match.</Typography>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </TableContainer>
    );
}
