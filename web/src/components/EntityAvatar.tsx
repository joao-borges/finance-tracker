import { Avatar } from "@mui/material";
import styles from "./EntityAvatar.module.css";

interface EntityAvatarProps {
    url?: string | null;
    name?: string | null;
}

/**
 * Small avatar showing a logo image when available, otherwise the first
 * character of the name (or emoji) as a fallback. Used in lists for accounts
 * and merchants.
 */
export default function EntityAvatar({ url, name }: EntityAvatarProps) {
    return (
        <Avatar src={url ?? undefined} className={styles.avatar}>
            {(name ?? "?").charAt(0)}
        </Avatar>
    );
}
