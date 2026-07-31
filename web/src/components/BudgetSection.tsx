import { useState, type ReactNode } from "react";
import {
    Box,
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
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import VisibilityIcon from "@mui/icons-material/Visibility";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";
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
    collapsible?: boolean;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
    showHidden?: boolean;
    onToggleHidden?: () => void;
    readOnly?: boolean;
}

export default function BudgetSection({
    title,
    subtitle,
    lines,
    planned,
    onPlanned,
    onOpenCategory,
    collapsible,
    collapsed,
    onToggleCollapse,
    showHidden,
    onToggleHidden,
    readOnly,
}: Props) {
    const hasHidden = lines.some((line) => line.hidden);
    const visibleLines = showHidden ? lines : lines.filter((line) => !line.hidden);
    // Zero-planned categories fold into their own collapsed sub-section so the
    // month's real plan stays uncluttered; membership follows the SAVED plan so
    // a row doesn't jump sections mid-edit.
    const activeLines = visibleLines.filter((line) => (line.planned ?? 0) !== 0);
    const zeroLines = visibleLines.filter((line) => (line.planned ?? 0) === 0);
    const zeroAlert = zeroLines.some((line) => line.actual !== 0);
    const [zeroOpen, setZeroOpen] = useState(false);

    const renderLine = (line: BudgetLine) => {
        return (
            <TableRow key={line.categoryId} hover>
                <TableCell>
                    <Link
                        component="button"
                        type="button"
                        underline="hover"
                        color="inherit"
                        onClick={() => onOpenCategory?.(line)}
                        className={line.hidden ? `${styles.categoryLink} ${styles.hiddenName}` : styles.categoryLink}
                    >
                        {line.icon ? `${line.icon} ` : ""}
                        {line.name}
                        {line.hidden ? " (hidden)" : ""}
                    </Link>
                </TableCell>
                <TableCell>
                    <InputNumber
                        className={styles.plannedInput}
                        min={0}
                        step={10}
                        precision={2}
                        disabled={readOnly}
                        value={planned[line.categoryId] ?? null}
                        onChange={(value) => onPlanned(line.categoryId, value)}
                        prefix="$"
                    />
                </TableCell>
                <TableCell align="right" className={`${shared.nowrap} ${styles.colActual}`}>
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
    };

    return (
        <Box className={styles.section}>
            <Box className={styles.header}>
                <div className={styles.controls}>
                    {collapsible && (
                        <IconButton size="small" onClick={onToggleCollapse} aria-label={collapsed ? "expand" : "collapse"}>
                            {collapsed ? <ChevronRightIcon /> : <ExpandMoreIcon />}
                        </IconButton>
                    )}
                    {hasHidden && (
                        <Tooltip title={showHidden ? "Hide hidden categories" : "Show hidden categories"}>
                            <IconButton size="small" onClick={onToggleHidden} aria-label="toggle hidden categories">
                                {showHidden ? <VisibilityIcon fontSize="small" /> : <VisibilityOffIcon fontSize="small" />}
                            </IconButton>
                        </Tooltip>
                    )}
                </div>
                <Typography variant="h6" className={styles.title}>
                    {title}
                </Typography>
                {subtitle}
            </Box>
            {!collapsed && (
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
                        {activeLines.map(renderLine)}
                        {zeroLines.length > 0 && (
                            <TableRow hover className={styles.zeroHeader} onClick={() => setZeroOpen((open) => !open)}>
                                <TableCell colSpan={4}>
                                    <span className={styles.zeroTitle}>
                                        {zeroOpen ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                                        <Typography component="span" className={shared.secondary}>
                                            Zero-planned ({zeroLines.length})
                                        </Typography>
                                        {zeroAlert && (
                                            <Tooltip title="Transactions landed in zero-planned categories this month">
                                                <WarningAmberIcon fontSize="small" color="warning" />
                                            </Tooltip>
                                        )}
                                    </span>
                                </TableCell>
                            </TableRow>
                        )}
                        {zeroOpen && zeroLines.map(renderLine)}
                    </TableBody>
                </Table>
            </TableContainer>
            )}
        </Box>
    );
}
