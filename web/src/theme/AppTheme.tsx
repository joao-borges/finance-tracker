import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { createTheme, CssBaseline, StyledEngineProvider, ThemeProvider as MuiThemeProvider, useMediaQuery } from "@mui/material";
import { App as AntApp, ConfigProvider, theme as antdTheme } from "antd";

export type Mode = "light" | "dark" | "system";

/*
 * The shape scale, mirrored from the --radius-* custom properties in index.css.
 * antd and MUI both need it as numbers at theme-build time, so the values are
 * duplicated here rather than read from CSS. Change both together.
 */
const RADIUS = {
    xs: 6,
    sm: 10,
    md: 14,
    lg: 18,
    xl: 24,
    pill: 999,
} as const;

/*
 * Mirrors --font-sans in index.css. Both libraries ship a different default
 * (MUI: Roboto/Helvetica/Arial, antd: the system UI stack), which is why MUI and
 * antd text rendered in two typefaces before this was set explicitly.
 */
const FONT_SANS = [
    '"Inter"',
    "ui-sans-serif",
    "system-ui",
    "-apple-system",
    "BlinkMacSystemFont",
    '"Segoe UI"',
    "Roboto",
    '"Helvetica Neue"',
    "Arial",
    "sans-serif",
].join(", ");

/*
 * MUI buttons/inputs are ~36px tall against antd's 32px default, so a row mixing
 * the two sat visibly off. Both libraries are pinned to the same control height.
 */
const CONTROL_HEIGHT = 36;

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
        () =>
            createTheme({
                palette: { mode: effective, primary: { main: scheme.primary } },
                shape: { borderRadius: RADIUS.sm },
                typography: {
                    fontFamily: FONT_SANS,
                    // Tighter tracking and heavier headings — MUI's defaults were
                    // metric-tuned for Roboto, which this app never loads.
                    h4: { fontWeight: 600, letterSpacing: "-0.021em" },
                    h5: { fontWeight: 600, letterSpacing: "-0.018em" },
                    h6: { fontWeight: 600, letterSpacing: "-0.014em" },
                    subtitle1: { fontWeight: 550 },
                    subtitle2: { fontWeight: 550 },
                    button: { fontWeight: 550, letterSpacing: 0 },
                },
                components: {
                    // Overriding the "rounded" slot (not "root") deliberately leaves
                    // square Papers — the AppBar — flush with the viewport edge.
                    MuiPaper: {
                        styleOverrides: {
                            rounded: {
                                borderRadius: RADIUS.lg,
                            },
                            // MUI's stock elevations are dense, high-contrast stacks
                            // from an older material spec. Swapping in the shared
                            // shadow tokens both softens them and lets dark mode
                            // deepen them, since the tokens are CSS variables.
                            elevation1: {
                                boxShadow: "var(--shadow-sm)",
                            },
                            elevation2: {
                                boxShadow: "var(--shadow-md)",
                            },
                            elevation3: {
                                boxShadow: "var(--shadow-md)",
                            },
                            elevation4: {
                                boxShadow: "var(--shadow-lg)",
                            },
                        },
                    },
                    MuiCard: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.lg,
                            },
                        },
                    },
                    MuiButton: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.sm,
                                textTransform: "none",
                                minHeight: CONTROL_HEIGHT,
                                boxShadow: "none",
                                transition: "background-color var(--transition-fast), box-shadow var(--transition-fast)",
                            },
                            // Flat by default, lifting only on hover, reads current;
                            // MUI's default is a permanent drop shadow on every
                            // contained button.
                            contained: {
                                "&:hover": {
                                    boxShadow: "var(--shadow-sm)",
                                },
                            },
                        },
                    },
                    MuiToggleButton: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.sm,
                                textTransform: "none",
                            },
                        },
                    },
                    MuiChip: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.pill,
                            },
                        },
                    },
                    MuiOutlinedInput: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.sm,
                            },
                        },
                    },
                    MuiAlert: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.md,
                            },
                        },
                    },
                    MuiDialog: {
                        styleOverrides: {
                            paper: {
                                borderRadius: RADIUS.xl,
                            },
                        },
                    },
                    // Sidebar / saved-filter rows read as pills; the horizontal margin
                    // is what keeps a rounded row from colliding with the drawer edge.
                    MuiListItemButton: {
                        styleOverrides: {
                            root: {
                                borderRadius: RADIUS.md,
                            },
                        },
                    },
                    // The transaction list is the surface you look at most, so it
                    // carries the typographic work: a muted, slightly tracked-out
                    // header against lighter rules, rather than header text at the
                    // same weight and colour as the data underneath it.
                    MuiTableCell: {
                        styleOverrides: {
                            head: {
                                fontWeight: 600,
                                fontSize: "0.78rem",
                                letterSpacing: "0.03em",
                                textTransform: "uppercase",
                                color: "var(--text-secondary)",
                                borderBottomColor: "var(--border)",
                            },
                            body: {
                                borderBottomColor: "var(--border)",
                            },
                        },
                    },
                    MuiTableRow: {
                        styleOverrides: {
                            root: {
                                transition: "background-color var(--transition-fast)",
                            },
                        },
                    },
                },
            }),
        [effective, scheme.primary],
    );

    const antdConfig = useMemo(
        () => ({
            algorithm: effective === "dark" ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
            token: {
                colorPrimary: scheme.primary,
                fontFamily: FONT_SANS,
                borderRadiusXS: RADIUS.xs,
                borderRadiusSM: RADIUS.xs,
                borderRadius: RADIUS.sm,
                borderRadiusLG: RADIUS.md,
                controlHeight: CONTROL_HEIGHT,
                // Soft, layered elevation in place of antd's tighter default
                // shadows, so antd popovers/dropdowns sit at the same visual
                // depth as MUI's Papers.
                boxShadow: "var(--shadow-md)",
                boxShadowSecondary: "var(--shadow-lg)",
                boxShadowTertiary: "var(--shadow-sm)",
            },
            components: {
                Modal: { borderRadiusLG: RADIUS.xl },
                Tag: { borderRadiusSM: RADIUS.pill },
                Segmented: { borderRadius: RADIUS.sm, borderRadiusSM: RADIUS.xs },
            },
        }),
        [effective, scheme.primary],
    );

    // Expose the effective mode + accent to CSS modules via the theme variables
    // in index.css, so static CSS classes track dark mode and the chosen scheme.
    useEffect(() => {
        const root = document.documentElement;
        root.setAttribute("data-theme", effective);
        root.style.setProperty("--color-primary", scheme.primary);
    }, [effective, scheme.primary]);

    const value = useMemo<ThemeContextValue>(
        () => ({ mode, setMode, colorKey, setColorKey, effective }),
        [mode, setMode, colorKey, setColorKey, effective],
    );

    return (
        <ThemeContext.Provider value={value}>
            {/* injectFirst puts MUI's runtime styles first so our CSS Modules win. */}
            <StyledEngineProvider injectFirst>
                <MuiThemeProvider theme={muiTheme}>
                    <CssBaseline />
                    <ConfigProvider theme={antdConfig}>
                        <AntApp>{children}</AntApp>
                    </ConfigProvider>
                </MuiThemeProvider>
            </StyledEngineProvider>
        </ThemeContext.Provider>
    );
}
