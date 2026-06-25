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
import { Link } from "@mui/material";
import { formatMoney } from "../lib/format";
import type { BudgetLine } from "../lib/api";
import shared from "../styles/shared.module.css";
import styles from "./BudgetSection.module.css";

interface Props {
    title: string;
    subtitle?: ReactNode;
    lines: BudgetLine[];
    planned: Record<number, number | null>;
    onPlanned: (categoryId: number, value: number | null) => void;
    onOpenCategory?: (line: BudgetLine) => void;
    readOnly?: boolean;
}

export default function BudgetSection({ title, subtitle, lines, planned, onPlanned, onOpenCategory, readOnly }: Props) {
    return (
        <Box className={styles.section}>
            <Box className={styles.header}>
                <Typography variant="h6" className={styles.title}>
                    {title}
                </Typography>
                {subtitle}
            </Box>
            <TableContainer component={Paper}>
                <Table size="small" className={styles.table}>
                    <TableHead>
                        <TableRow>
                            <TableCell>Category</TableCell>
                            <TableCell className={styles.colPlanned}>Planned</TableCell>
                            <TableCell align="right" className={styles.colActual}>Actual</TableCell>
                            <TableCell align="right" className={styles.colRemaining}>Remaining</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {lines.map((line) => {
                            return (
                                <TableRow key={line.categoryId} hover>
                                    <TableCell>
                                        <Link
                                            component="button"
                                            type="button"
                                            underline="hover"
                                            color="inherit"
                                            onClick={() => onOpenCategory?.(line)}
                                            className={styles.categoryLink}
                                        >
                                            {line.icon ? `${line.icon} ` : ""}
                                            {line.name}
                                        </Link>
                                    </TableCell>
                                    <TableCell>
                                        <InputNumber
                                            className={styles.plannedInput}
                                            min={0}
                                            step={10}
                                            disabled={readOnly}
                                            value={planned[line.categoryId] ?? null}
                                            onChange={(value) => onPlanned(line.categoryId, value)}
                                            prefix="$"
                                        />
                                    </TableCell>
                                    <TableCell align="right" className={shared.nowrap}>
                                        {formatMoney(line.actual)}
                                    </TableCell>
                                    <TableCell align="right" className={shared.nowrap}>
                                        <Typography
                                            component="span"
                                            className={line.remaining < 0 ? shared.negative : undefined}
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
