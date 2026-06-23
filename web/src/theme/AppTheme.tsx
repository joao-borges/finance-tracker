import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { createTheme, CssBaseline, ThemeProvider as MuiThemeProvider, useMediaQuery } from "@mui/material";
import { App as AntApp, ConfigProvider, theme as antdTheme } from "antd";

export type Mode = "light" | "dark" | "system";

export interface ColorScheme {
    key: string;
    label: string;
    primary: string;
}

// Color schemes are applied to BOTH MUI (palette.primary) and antd (colorPrimary)
// so the mixed UI stays consistent. Persisted per-browser in localStorage.
export const COLOR_SCHEMES: ColorScheme[] = [
    { key: "blue", label: "Blue", primary: "#1677ff" },
    { key: "indigo", label: "Indigo", primary: "#4f46e5" },
    { key: "green", label: "Green", primary: "#16a34a" },
    { key: "teal", label: "Teal", primary: "#0d9488" },
    { key: "purple", label: "Purple", primary: "#7c3aed" },
    { key: "orange", label: "Orange", primary: "#ea580c" },
    { key: "rose", label: "Rose", primary: "#e11d48" },
];

interface ThemeContextValue {
    mode: Mode;
    setMode: (mode: Mode) => void;
    colorKey: string;
    setColorKey: (key: string) => void;
    effective: "light" | "dark";
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useAppTheme(): ThemeContextValue {
    const value = useContext(ThemeContext);
    if (value === null) {
        throw new Error("useAppTheme must be used within AppTheme");
    }
    return value;
}

const MODE_STORAGE_KEY = "ft.mode";
const COLOR_STORAGE_KEY = "ft.color";

export default function AppTheme({ children }: { children: ReactNode }) {
    const [mode, setModeState] = useState<Mode>(() => (localStorage.getItem(MODE_STORAGE_KEY) as Mode) ?? "system");
    const [colorKey, setColorKeyState] = useState<string>(() => localStorage.getItem(COLOR_STORAGE_KEY) ?? "blue");

    const systemPrefersDark = useMediaQuery("(prefers-color-scheme: dark)");
    const effective: "light" | "dark" = mode === "system" ? (systemPrefersDark ? "dark" : "light") : mode;
    const scheme = COLOR_SCHEMES.find((candidate) => candidate.key === colorKey) ?? COLOR_SCHEMES[0];

    const setMode = useCallback((next: Mode) => {
        setModeState(next);
        localStorage.setItem(MODE_STORAGE_KEY, next);
    }, []);

    const setColorKey = useCallback((next: string) => {
        setColorKeyState(next);
        localStorage.setItem(COLOR_STORAGE_KEY, next);
    }, []);

    const muiTheme = useMemo(
        () => createTheme({ palette: { mode: effective, primary: { main: scheme.primary } } }),
        [effective, scheme.primary],
    );

    const antdConfig = useMemo(
        () => ({
            algorithm: effective === "dark" ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
            token: { colorPrimary: scheme.primary },
        }),
        [effective, scheme.primary],
    );

    const value = useMemo<ThemeContextValue>(
        () => ({ mode, setMode, colorKey, setColorKey, effective }),
        [mode, setMode, colorKey, setColorKey, effective],
    );

    return (
        <ThemeContext.Provider value={value}>
            <MuiThemeProvider theme={muiTheme}>
                <CssBaseline />
                <ConfigProvider theme={antdConfig}>
                    <AntApp>{children}</AntApp>
                </ConfigProvider>
            </MuiThemeProvider>
        </ThemeContext.Provider>
    );
}
