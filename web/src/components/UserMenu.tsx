import { useEffect, useState } from "react";
import { Box, IconButton, Tooltip, Typography } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";
import { authApi, type Me } from "../lib/api";
import styles from "./UserMenu.module.css";

// Signed-in account + sign-out, shown in the app bar. Renders nothing when auth
// is disabled (local dev).
export default function UserMenu() {
    const [me, setMe] = useState<Me | null>(null);

    useEffect(() => {
        authApi.me().then(setMe).catch(() => setMe(null));
    }, []);

    if (!me?.authenticated) {
        return null;
    }

    return (
        <Box className={styles.root}>
            <Typography variant="body2" className={styles.email}>
                {me.email}
            </Typography>
            <Tooltip title="Sign out">
                <IconButton color="inherit" size="small" onClick={() => authApi.logout()} aria-label="sign out">
                    <LogoutIcon fontSize="small" />
                </IconButton>
            </Tooltip>
        </Box>
    );
}
