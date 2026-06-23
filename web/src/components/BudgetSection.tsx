import type { ReactNode } from "react";
import {
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import { InputNumber } from "antd";
import { formatMoney } from "../lib/format";
import type { BudgetLine } from "../lib/api";

interface Props {
    title: string;
    subtitle?: ReactNode;
    lines: BudgetLine[];
    planned: Record<number, number | null>;
    onPlanned: (categoryId: number, value: number | null) => void;
}

export default function BudgetSection({ title, subtitle, lines, planned, onPlanned }: Props) {
    return (
        <Box sx={{ mb: 3 }}>
            <Box sx={{ display: "flex", alignItems: "baseline", mb: 1, gap: 2 }}>
                <Typography variant="h6" sx={{ flex: 1 }}>
                    {title}
                </Typography>
                {subtitle}
            </Box>
            <TableContainer component={Paper}>
                <Table size="small" sx={{ tableLayout: "fixed", width: "100%" }}>
                    <TableHead>
                        <TableRow>
                            <TableCell>Category</TableCell>
                            <TableCell sx={{ width: 160 }}>Planned</TableCell>
                            <TableCell align="right" sx={{ width: 140 }}>Actual</TableCell>
                            <TableCell align="right" sx={{ width: 140 }}>Remaining</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {lines.map((line) => {
                            return (
                                <TableRow key={line.categoryId} hover>
                                    <TableCell>
                                        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "block" }}>
                                            {line.icon ? `${line.icon} ` : ""}
                                            {line.name}
                                        </span>
                                    </TableCell>
                                    <TableCell>
                                        <InputNumber
                                            style={{ width: 130 }}
                                            min={0}
                                            step={10}
                                            value={planned[line.categoryId] ?? null}
                                            onChange={(value) => onPlanned(line.categoryId, value)}
                                            prefix="$"
                                        />
                                    </TableCell>
                                    <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                        {formatMoney(line.actual)}
                                    </TableCell>
                                    <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                        <Typography
                                            component="span"
                                            sx={{ color: line.remaining < 0 ? "error.main" : "text.primary" }}
                                        >
                                            {formatMoney(line.remaining)}
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
