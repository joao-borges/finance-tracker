import { Avatar } from "@mui/material";

interface EntityAvatarProps {
    url?: string | null;
    name?: string | null;
    size?: number;
}

/**
 * Small avatar showing a logo image when available, otherwise the first
 * character of the name (or emoji) as a fallback. Used in lists for accounts
 * and merchants.
 */
export default function EntityAvatar({ url, name, size = 22 }: EntityAvatarProps) {
    return (
        <Avatar src={url ?? undefined} sx={{ width: size, height: size, fontSize: size * 0.6 }}>
            {(name ?? "?").charAt(0)}
        </Avatar>
    );
}
