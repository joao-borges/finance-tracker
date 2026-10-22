import { Chip, Paper } from "@mui/material";
import CalculateIcon from "@mui/icons-material/Calculate";
import { formatResult } from "../lib/calc";
import styles from "./CalculatorTray.module.css";

export interface TrayItem {
    id: number;
    title: string;
    result: number | null;
}

interface Props {
    items: TrayItem[];
    onRestore: (id: number) => void;
    onClose: (id: number) => void;
}

// Bottom-right dock of minimized calculators — one chip each, showing the
// current result so a parked number is readable without restoring it.
export default function CalculatorTray({ items, onRestore, onClose }: Props) {
    if (items.length === 0) {
        return null;
    }
    return (
        <div className={styles.tray}>
            {items.map((item) => {
                return (
                    <Paper key={item.id} elevation={6} className={styles.item}>
                        <Chip
                            icon={<CalculateIcon />}
                            label={item.result == null ? item.title : `${item.title} · ${formatResult(item.result)}`}
                            onClick={() => onRestore(item.id)}
                            onDelete={() => onClose(item.id)}
                            className={styles.chip}
                        />
                    </Paper>
                );
            })}
        </div>
    );
}
