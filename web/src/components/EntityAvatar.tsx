import { Avatar } from "@mui/material";
import styles from "./EntityAvatar.module.css";

interface EntityAvatarProps {
    url?: string | null;
    name?: string | null;
    size?: "sm" | "lg";
}

/**
 * Avatar showing a logo image when available, otherwise the first character of
 * the name (or emoji) as a fallback. {@code size} defaults to small for dense
 * lists/tables; pass "lg" for prominent placements like the dashboard.
 */
export default function EntityAvatar({ url, name, size = "sm" }: EntityAvatarProps) {
    return (
        <Avatar src={url ?? undefined} className={`${styles.avatar} ${styles[size]}`}>
            {(name ?? "?").charAt(0)}
        </Avatar>
    );
}
