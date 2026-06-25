import { Box, Card, CardContent, List, ListItem, ListItemAvatar, ListItemText, Typography } from "@mui/material";
import EntityAvatar from "./EntityAvatar";
import { formatMoney } from "../lib/format";
import type { AccountGroup } from "../lib/api";
import styles from "./AccountGroupCard.module.css";

interface Props {
    group: AccountGroup;
}

export default function AccountGroupCard({ group }: Props) {
    return (
        <Card variant="outlined">
            <CardContent>
                <Box className={styles.header}>
                    <Typography variant="h6" className={styles.title}>
                        {group.label}
                    </Typography>
                    <Typography variant="subtitle1">{formatMoney(group.total)}</Typography>
                </Box>
                <List dense disablePadding>
                    {group.accounts.map((account) => {
                        return (
                            <ListItem
                                key={account.id}
                                disableGutters
                                secondaryAction={<Typography variant="body2">{formatMoney(account.balance)}</Typography>}
                            >
                                <ListItemAvatar className={styles.avatar}>
                                    <EntityAvatar url={account.logoUrl} name={account.name} size="lg" />
                                </ListItemAvatar>
                                <ListItemText primary={account.name} />
                            </ListItem>
                        );
                    })}
                    {group.accounts.length === 0 && (
                        <Typography variant="body2" color="text.secondary">
                            No accounts.
                        </Typography>
                    )}
                </List>
            </CardContent>
        </Card>
    );
}
