import { Box, Card, CardContent, List, ListItem, ListItemAvatar, ListItemText, Typography } from "@mui/material";
import EntityAvatar from "./EntityAvatar";
import { formatMoney } from "../lib/format";
import type { AccountGroup } from "../lib/api";

interface Props {
    group: AccountGroup;
}

export default function AccountGroupCard({ group }: Props) {
    return (
        <Card variant="outlined">
            <CardContent>
                <Box sx={{ display: "flex", alignItems: "baseline", mb: 1 }}>
                    <Typography variant="h6" sx={{ flex: 1 }}>
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
                                <ListItemAvatar sx={{ minWidth: 36 }}>
                                    <EntityAvatar url={account.logoUrl} name={account.name} />
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
