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
import EntityAvatar from "./EntityAvatar";
import { formatDate, formatMoney } from "../lib/format";
import type { Transaction } from "../lib/api";
import shared from "../styles/shared.module.css";
import styles from "./TransactionsTable.module.css";

interface Props {
    rows: Transaction[];
    onOpen: (row: Transaction) => void;
}

export default function TransactionsTable({ rows, onOpen }: Props) {
    return (
        <TableContainer component={Paper}>
            <Table size="small" className={styles.table}>
                <TableHead>
                    <TableRow>
                        <TableCell className={styles.colDate}>Date</TableCell>
                        <TableCell className={styles.colAccount}>Account</TableCell>
                        <TableCell>Merchant</TableCell>
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
                                <TableCell className={shared.nowrap}>{formatDate(row.postedAt)}</TableCell>
                                <TableCell>
                                    <div className={shared.iconRow}>
                                        <EntityAvatar url={row.accountLogoUrl} name={row.accountName} />
                                        <span className={shared.ellipsis}>{row.accountName}</span>
                                    </div>
                                </TableCell>
                                <TableCell>
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
                                </TableCell>
                                <TableCell>
                                    {row.categoryName ? (
                                        <span className={`${shared.ellipsis} ${styles.categoryName}`}>
                                            {row.categoryIcon ? `${row.categoryIcon} ` : ""}
                                            {row.categoryName}
                                        </span>
                                    ) : (
                                        <Typography component="span" className={shared.secondary}>
                                            Uncategorized
                                        </Typography>
                                    )}
                                </TableCell>
                                <TableCell align="right" className={shared.nowrap}>
                                    <Typography
                                        component="span"
                                        className={row.amount < 0 ? shared.amountNeg : shared.amountPos}
                                    >
                                        {formatMoney(row.amount, row.currency)}
                                    </Typography>
                                </TableCell>
                                <TableCell className={shared.nowrap}>
                                    {row.needsReview ? (
                                        <Chip size="small" color="warning" label="Review" />
                                    ) : (
                                        <Chip size="small" color="success" label="Reviewed" />
                                    )}
                                    {row.excludedFromBudget && (
                                        <Chip size="small" label="Excluded" className={styles.excludedChip} variant="outlined" />
                                    )}
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
