import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { IconButton, Paper, Tooltip } from "@mui/material";
import MinimizeIcon from "@mui/icons-material/Minimize";
import CloseIcon from "@mui/icons-material/Close";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { evaluate, formatResult } from "../lib/calc";
import styles from "./CalculatorWindow.module.css";

export interface Position {
    x: number;
    y: number;
}

interface Props {
    title: string;
    position: Position;
    zIndex: number;
    minimized: boolean;
    onMove: (position: Position) => void;
    onFocus: () => void;
    onMinimize: () => void;
    onClose: () => void;
    onResult: (value: number | null) => void;
}

interface TapeLine {
    expression: string;
    value: number;
}

const KEYS: string[][] = [
    ["C", "⌫", "%", "÷"],
    ["7", "8", "9", "×"],
    ["4", "5", "6", "−"],
    ["1", "2", "3", "+"],
    ["(", "0", ".", ")"],
];

const OPERATORS = new Set(["÷", "×", "−", "+", "%"]);
const TAPE_LIMIT = 6;
// Keep at least this much of the title bar on screen so a window can always be dragged back.
const GRAB_MARGIN = 48;

// One floating, draggable calculator. Stays mounted while minimized (hidden
// via CSS) so its expression and tape survive a minimize/restore.
export default function CalculatorWindow({
    title, position, zIndex, minimized, onMove, onFocus, onMinimize, onClose, onResult,
}: Props) {
    const [expression, setExpression] = useState("");
    const [tape, setTape] = useState<TapeLine[]>([]);
    const inputRef = useRef<HTMLInputElement>(null);
    const windowRef = useRef<HTMLDivElement>(null);
    const dragOffset = useRef<Position | null>(null);

    const result = evaluate(expression);
    const preview = result.ok ? result.value : null;

    useEffect(() => {
        onResult(preview);
    }, [preview, onResult]);

    // Position and stacking are per-instance runtime values, so they're set on
    // the element directly rather than through a style object.
    useLayoutEffect(() => {
        const element = windowRef.current;
        if (element) {
            element.style.left = `${position.x}px`;
            element.style.top = `${position.y}px`;
            element.style.zIndex = String(zIndex);
        }
    }, [position, zIndex]);

    useEffect(() => {
        if (!minimized) {
            inputRef.current?.focus();
        }
    }, [minimized]);

    const press = (key: string) => {
        if (key === "C") {
            setExpression("");
        } else if (key === "⌫") {
            setExpression((current) => current.slice(0, -1));
        } else {
            setExpression((current) => (current + (OPERATORS.has(key) ? ` ${key} ` : key)).trimStart());
        }
        inputRef.current?.focus();
    };

    const commit = () => {
        if (!result.ok) {
            return;
        }
        const committed = formatResult(result.value);
        if (committed !== expression.trim()) {
            setTape((current) => [{ expression: expression.trim(), value: result.value }, ...current].slice(0, TAPE_LIMIT));
        }
        setExpression(committed);
        inputRef.current?.focus();
    };

    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === "Enter" || event.key === "=") {
            event.preventDefault();
            commit();
        } else if (event.key === "Escape") {
            event.preventDefault();
            setExpression("");
        }
    };

    const copy = () => {
        if (preview != null) {
            navigator.clipboard.writeText(formatResult(preview)).catch(() => {});
        }
    };

    const startDrag = (event: PointerEvent<HTMLDivElement>) => {
        if ((event.target as HTMLElement).closest("button")) {
            return;
        }
        dragOffset.current = { x: event.clientX - position.x, y: event.clientY - position.y };
        event.currentTarget.setPointerCapture(event.pointerId);
        onFocus();
    };

    const drag = (event: PointerEvent<HTMLDivElement>) => {
        if (!dragOffset.current) {
            return;
        }
        const maxX = window.innerWidth - GRAB_MARGIN;
        const maxY = window.innerHeight - GRAB_MARGIN;
        onMove({
            x: Math.min(Math.max(event.clientX - dragOffset.current.x, GRAB_MARGIN - 280), maxX),
            y: Math.min(Math.max(event.clientY - dragOffset.current.y, 0), maxY),
        });
    };

    const endDrag = () => {
        dragOffset.current = null;
    };

    return (
        <Paper
            elevation={8}
            className={minimized ? `${styles.window} ${styles.hidden}` : styles.window}
            ref={windowRef}
            onPointerDown={onFocus}
            role="dialog"
            aria-label={title}
        >
            <div
                className={styles.titleBar}
                onPointerDown={startDrag}
                onPointerMove={drag}
                onPointerUp={endDrag}
                onPointerCancel={endDrag}
            >
                <span className={styles.title}>{title}</span>
                <Tooltip title="Minimize">
                    <IconButton size="small" onClick={onMinimize} aria-label="minimize">
                        <MinimizeIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Close">
                    <IconButton size="small" onClick={onClose} aria-label="close">
                        <CloseIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            </div>

            {tape.length > 0 && (
                <div className={styles.tape}>
                    {[...tape].reverse().map((line, index) => {
                        return (
                            <button
                                key={`${index}-${line.expression}`}
                                type="button"
                                className={styles.tapeLine}
                                title="Insert this result"
                                onClick={() => press(formatResult(line.value))}
                            >
                                <span className={styles.tapeExpression}>{line.expression}</span>
                                <span>= {formatResult(line.value)}</span>
                            </button>
                        );
                    })}
                </div>
            )}

            <input
                ref={inputRef}
                className={styles.input}
                value={expression}
                onChange={(event) => setExpression(event.target.value)}
                onKeyDown={onKeyDown}
                placeholder="0"
                spellCheck={false}
                autoComplete="off"
                aria-label="expression"
            />
            <div className={styles.preview}>
                <span className={result.ok || expression === "" ? styles.previewValue : styles.previewError}>
                    {result.ok ? `= ${formatResult(result.value)}` : result.error}
                </span>
                <Tooltip title="Copy result">
                    <span>
                        <IconButton size="small" onClick={copy} disabled={preview == null} aria-label="copy result">
                            <ContentCopyIcon fontSize="inherit" />
                        </IconButton>
                    </span>
                </Tooltip>
            </div>

            <div className={styles.keypad}>
                {KEYS.flat().map((key) => {
                    return (
                        <button
                            key={key}
                            type="button"
                            className={OPERATORS.has(key) || key === "C" || key === "⌫"
                                ? `${styles.key} ${styles.operator}`
                                : styles.key}
                            onClick={() => press(key)}
                        >
                            {key}
                        </button>
                    );
                })}
                <button type="button" className={`${styles.key} ${styles.equals}`} onClick={commit}>
                    =
                </button>
            </div>
        </Paper>
    );
}
