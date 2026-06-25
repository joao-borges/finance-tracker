import { useState, type ReactNode } from "react";
import { Box, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from "@mui/material";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";
import SettingsBrightnessIcon from "@mui/icons-material/SettingsBrightness";
import PaletteIcon from "@mui/icons-material/Palette";
import CheckIcon from "@mui/icons-material/Check";
import { COLOR_SCHEMES, useAppTheme, type Mode } from "./AppTheme";
import styles from "./ThemeControls.module.css";

const MODE_OPTIONS: { key: Mode; label: string; icon: ReactNode }[] = [
    { key: "light", label: "Light", icon: <LightModeIcon fontSize="small" /> },
    { key: "dark", label: "Dark", icon: <DarkModeIcon fontSize="small" /> },
    { key: "system", label: "System", icon: <SettingsBrightnessIcon fontSize="small" /> },
];

export default function ThemeControls() {
    const { mode, setMode, colorKey, setColorKey, effective } = useAppTheme();
    const [modeAnchor, setModeAnchor] = useState<HTMLElement | null>(null);
    const [colorAnchor, setColorAnchor] = useState<HTMLElement | null>(null);

    return (
        <Box className={styles.controls}>
            <Tooltip title="Color scheme">
                <IconButton color="inherit" onClick={(event) => setColorAnchor(event.currentTarget)}>
                    <PaletteIcon />
                </IconButton>
            </Tooltip>
            <Tooltip title="Theme mode">
                <IconButton color="inherit" onClick={(event) => setModeAnchor(event.currentTarget)}>
                    {effective === "dark" ? <DarkModeIcon /> : <LightModeIcon />}
                </IconButton>
            </Tooltip>

            <Menu anchorEl={modeAnchor} open={modeAnchor !== null} onClose={() => setModeAnchor(null)}>
                {MODE_OPTIONS.map((option) => {
                    return (
                        <MenuItem
                            key={option.key}
                            selected={mode === option.key}
                            onClick={() => {
                                setMode(option.key);
                                setModeAnchor(null);
                            }}
                        >
                            <ListItemIcon>{option.icon}</ListItemIcon>
                            <ListItemText>{option.label}</ListItemText>
                            {mode === option.key && <CheckIcon fontSize="small" />}
                        </MenuItem>
                    );
                })}
            </Menu>

            <Menu anchorEl={colorAnchor} open={colorAnchor !== null} onClose={() => setColorAnchor(null)}>
                {COLOR_SCHEMES.map((scheme) => {
                    return (
                        <MenuItem
                            key={scheme.key}
                            selected={colorKey === scheme.key}
                            onClick={() => {
                                setColorKey(scheme.key);
                                setColorAnchor(null);
                            }}
                        >
                            <ListItemIcon>
                                <Box className={styles.swatch} data-scheme={scheme.key} />
                            </ListItemIcon>
                            <ListItemText>{scheme.label}</ListItemText>
                            {colorKey === scheme.key && <CheckIcon fontSize="small" />}
                        </MenuItem>
                    );
                })}
            </Menu>
        </Box>
    );
}
