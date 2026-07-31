import { useState, type ReactNode } from "react";
import { Input, InputNumber } from "antd";
import styles from "./InlineSelect.module.css";

interface Props {
    display: ReactNode;
    value: string | number | null | undefined;
    type: "text" | "number";
    onSave: (value: string | number) => void;
}

/**
 * Read-only content that turns into a text/number input on click. Enter or
 * clicking away saves when the value changed; Escape cancels.
 */
export default function InlineText({ display, value, type, onSave }: Props) {
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState<string | number | null>(null);

    const start = () => {
        setDraft(value ?? (type === "number" ? null : ""));
        setEditing(true);
    };

    const commit = () => {
        setEditing(false);
        if (draft === null || draft === value || String(draft) === String(value ?? "")) {
            return;
        }
        onSave(draft);
    };

    if (!editing) {
        return (
            <div className={styles.display} onClick={start}>
                {display}
            </div>
        );
    }
    if (type === "number") {
        return (
            <InputNumber
                autoFocus
                size="small"
                className={styles.select}
                value={typeof draft === "number" ? draft : draft === "" || draft === null ? null : Number(draft)}
                onChange={(next) => setDraft(next)}
                onPressEnter={commit}
                onBlur={commit}
                onKeyDown={(event) => {
                    if (event.key === "Escape") {
                        setEditing(false);
                    }
                }}
            />
        );
    }
    return (
        <Input
            autoFocus
            size="small"
            className={styles.select}
            value={draft === null ? "" : String(draft)}
            onChange={(event) => setDraft(event.target.value)}
            onPressEnter={commit}
            onBlur={commit}
            onKeyDown={(event) => {
                if (event.key === "Escape") {
                    setEditing(false);
                }
            }}
        />
    );
}
