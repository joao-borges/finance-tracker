import {
    Box,
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
import type { CSSProperties } from "react";
import EntityAvatar from "./EntityAvatar";
import { formatDate, formatMoney } from "../lib/format";
import type { Transaction } from "../lib/api";

interface Props {
    rows: Transaction[];
    onOpen: (row: Transaction) => void;
}

// Truncate-with-ellipsis for flexible cells (works because the table is fixed-layout).
const ELLIPSIS: CSSProperties = { overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", minWidth: 0 };

export default function TransactionsTable({ rows, onOpen }: Props) {
    return (
        <TableContainer component={Paper}>
            <Table size="small" sx={{ tableLayout: "fixed", width: "100%" }}>
                <TableHead>
                    <TableRow>
                        <TableCell sx={{ width: 110, whiteSpace: "nowrap" }}>Date</TableCell>
                        <TableCell sx={{ width: 180 }}>Account</TableCell>
                        <TableCell>Merchant</TableCell>
                        <TableCell sx={{ width: 160 }}>Category</TableCell>
                        <TableCell align="right" sx={{ width: 120, whiteSpace: "nowrap" }}>Amount</TableCell>
                        <TableCell sx={{ width: 150 }}>Status</TableCell>
                        <TableCell align="right" sx={{ width: 56 }} />
                    </TableRow>
                </TableHead>
                <TableBody>
                    {rows.map((row) => {
                        return (
                            <TableRow key={row.id} hover>
                                <TableCell sx={{ whiteSpace: "nowrap" }}>{formatDate(row.postedAt)}</TableCell>
                                <TableCell>
                                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                                        <EntityAvatar url={row.accountLogoUrl} name={row.accountName} />
                                        <span style={ELLIPSIS}>{row.accountName}</span>
                                    </Box>
                                </TableCell>
                                <TableCell>
                                    <Tooltip title={`Statement: ${row.merchantName}`}>
                                        <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                                            {row.merchantLogoUrl ? (
                                                <EntityAvatar url={row.merchantLogoUrl} name={row.merchant ?? row.merchantName} />
                                            ) : row.merchantIcon ? (
                                                <span style={{ fontSize: 18 }}>{row.merchantIcon}</span>
                                            ) : (
                                                <EntityAvatar name={row.merchant ?? row.merchantName} />
                                            )}
                                            <span style={ELLIPSIS}>{row.merchant ?? row.merchantName}</span>
                                        </Box>
                                    </Tooltip>
                                </TableCell>
                                <TableCell>
                                    {row.categoryName ? (
                                        <span style={{ ...ELLIPSIS, display: "block" }}>
                                            {row.categoryIcon ? `${row.categoryIcon} ` : ""}
                                            {row.categoryName}
                                        </span>
                                    ) : (
                                        <Typography component="span" color="text.secondary">
                                            Uncategorized
                                        </Typography>
                                    )}
                                </TableCell>
                                <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                    <Typography
                                        component="span"
                                        sx={{
                                            color: row.amount < 0 ? "error.main" : "success.main",
                                            fontVariantNumeric: "tabular-nums",
                                        }}
                                    >
                                        {formatMoney(row.amount, row.currency)}
                                    </Typography>
                                </TableCell>
                                <TableCell sx={{ whiteSpace: "nowrap" }}>
                                    {row.needsReview ? (
                                        <Chip size="small" color="warning" label="Review" />
                                    ) : (
                                        <Chip size="small" color="success" label="Reviewed" />
                                    )}
                                    {row.excludedFromBudget && (
                                        <Chip size="small" label="Excluded" sx={{ ml: 0.5 }} variant="outlined" />
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
                                <Typography color="text.secondary">No transactions match.</Typography>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </TableContainer>
    );
}
