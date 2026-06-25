import { useEffect, useState } from "react";
import { Box, IconButton, Tooltip, Typography } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";
import EntityAvatar from "./EntityAvatar";
import { authApi, type Me } from "../lib/api";
import styles from "./UserMenu.module.css";

// Signed-in account in the app bar: avatar + first name, email on hover, sign-out.
// Renders nothing when auth is disabled (local dev).
export default function UserMenu() {
    const [me, setMe] = useState<Me | null>(null);

    useEffect(() => {
        authApi.me().then(setMe).catch(() => setMe(null));
    }, []);

    if (!me?.authenticated) {
        return null;
    }

    const firstName = me.givenName || me.name?.split(" ")[0] || me.email || "Account";

    return (
        <Box className={styles.root}>
            <Tooltip title={me.email ?? ""}>
                <Box className={styles.user}>
                    <EntityAvatar url={me.picture} name={firstName} />
                    <Typography variant="body2" className={styles.name}>
                        {firstName}
                    </Typography>
                </Box>
            </Tooltip>
            <Tooltip title="Sign out">
                <IconButton color="inherit" size="small" onClick={() => authApi.logout()} aria-label="sign out">
                    <LogoutIcon fontSize="small" />
                </IconButton>
            </Tooltip>
        </Box>
    );
}
