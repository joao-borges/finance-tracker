import { useState, type ReactNode } from "react";
import { Select } from "antd";
import styles from "./InlineSelect.module.css";

export interface InlineSelectOption {
    label: string;
    value: number;
}

interface Props {
    display: ReactNode;
    value?: number;
    options: InlineSelectOption[];
    onSave: (value: number) => void;
}

/**
 * Read-only content that turns into a search-select on click. Picking an option
 * saves and reverts to text; clicking away cancels without saving.
 */
export default function InlineSelect({ display, value, options, onSave }: Props) {
    const [editing, setEditing] = useState(false);

    if (!editing) {
        return (
            <div className={styles.display} onClick={() => setEditing(true)}>
                {display}
            </div>
        );
    }
    return (
        <Select
            autoFocus
            defaultOpen
            showSearch
            size="small"
            optionFilterProp="label"
            className={styles.select}
            value={value}
            options={options}
            onChange={(next: number) => {
                setEditing(false);
                if (next !== value) {
                    onSave(next);
                }
            }}
            onBlur={() => setEditing(false)}
        />
    );
}
