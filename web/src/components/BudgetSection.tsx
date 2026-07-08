import type { ReactNode } from "react";
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
                        {visibleLines.map((line) => {
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
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
            )}
        </Box>
    );
}
