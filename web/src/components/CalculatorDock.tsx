import { useCallback, useState } from "react";
import { createPortal } from "react-dom";
import { IconButton, Tooltip } from "@mui/material";
import CalculateIcon from "@mui/icons-material/Calculate";
import CalculatorWindow, { type Position } from "./CalculatorWindow";
import CalculatorTray from "./CalculatorTray";

interface Calculator {
    id: number;
    title: string;
    position: Position;
    z: number;
    minimized: boolean;
}

// Above MUI modals (1300) and antd modals (1000), so a calculator stays usable
// while a split or edit dialog is open.
const BASE_Z = 1350;
const WINDOW_WIDTH = 280;
const CASCADE = 28;

// AppBar launcher plus every open calculator. Any number can be open at once;
// each floats, drags by its title bar, and minimizes into the bottom-right
// tray. Rendered through a portal so the windows aren't trapped in the
// AppBar's stacking context. Desktop only — the shell doesn't mount it on mobile.
export default function CalculatorDock() {
    const [calculators, setCalculators] = useState<Calculator[]>([]);
    const [results, setResults] = useState<Record<number, number | null>>({});
    const [nextId, setNextId] = useState(1);

    const topZ = calculators.reduce((max, calculator) => Math.max(max, calculator.z), BASE_Z);

    const open = () => {
        const visible = calculators.filter((calculator) => !calculator.minimized).length;
        const offset = (visible % 8) * CASCADE;
        setCalculators((current) => [...current, {
            id: nextId,
            title: `Calculator ${nextId}`,
            position: {
                x: Math.max(window.innerWidth - WINDOW_WIDTH - 32 - offset, 16),
                y: 80 + offset,
            },
            z: topZ + 1,
            minimized: false,
        }]);
        setNextId((id) => id + 1);
    };

    const patch = (id: number, change: Partial<Calculator>) => {
        setCalculators((current) => current.map((calculator) => (calculator.id === id ? { ...calculator, ...change } : calculator)));
    };

    const focus = (id: number) => {
        setCalculators((current) => {
            const target = current.find((calculator) => calculator.id === id);
            const highest = current.reduce((max, calculator) => Math.max(max, calculator.z), BASE_Z);
            if (!target || target.z === highest) {
                return current;
            }
            return current.map((calculator) => (calculator.id === id ? { ...calculator, z: highest + 1 } : calculator));
        });
    };

    const close = (id: number) => {
        setCalculators((current) => current.filter((calculator) => calculator.id !== id));
        setResults((current) => {
            const { [id]: _removed, ...rest } = current;
            return rest;
        });
    };

    const reportResult = useCallback((id: number, value: number | null) => {
        setResults((current) => (current[id] === value ? current : { ...current, [id]: value }));
    }, []);

    return (
        <>
            <Tooltip title="New calculator">
                <IconButton color="inherit" onClick={open} aria-label="new calculator">
                    <CalculateIcon />
                </IconButton>
            </Tooltip>
            {createPortal(
                <>
                    {calculators.map((calculator) => {
                        return (
                            <CalculatorWindow
                                key={calculator.id}
                                title={calculator.title}
                                position={calculator.position}
                                zIndex={calculator.z}
                                minimized={calculator.minimized}
                                onMove={(position) => patch(calculator.id, { position })}
                                onFocus={() => focus(calculator.id)}
                                onMinimize={() => patch(calculator.id, { minimized: true })}
                                onClose={() => close(calculator.id)}
                                onResult={(value) => reportResult(calculator.id, value)}
                            />
                        );
                    })}
                    <CalculatorTray
                        items={calculators
                            .filter((calculator) => calculator.minimized)
                            .map((calculator) => ({
                                id: calculator.id,
                                title: calculator.title,
                                result: results[calculator.id] ?? null,
                            }))}
                        onRestore={(id) => {
                            patch(id, { minimized: false });
                            focus(id);
                        }}
                        onClose={close}
                    />
                </>,
                document.body,
            )}
        </>
    );
}
