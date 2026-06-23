import { useEffect, useState } from "react";
import { Box, Chip, Grid, Typography } from "@mui/material";
import { Alert } from "antd";
import AccountGroupCard from "../components/AccountGroupCard";
import { dashboardApi, type DashboardSummary } from "../lib/api";
import { errorText, formatMoney } from "../lib/format";

export default function Dashboard() {
    const [summary, setSummary] = useState<DashboardSummary | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        dashboardApi.summary().then(setSummary).catch((e: unknown) => setError(errorText(e)));
    }, []);

    if (error) {
        return <Alert type="error" message={`Backend unreachable: ${error}`} />;
    }
    if (!summary) {
        return <Typography color="text.secondary">Loading…</Typography>;
    }

    return (
        <Box>
            <Box sx={{ display: "flex", alignItems: "baseline", gap: 2, mb: 3, flexWrap: "wrap" }}>
                <Typography variant="h4">Overview</Typography>
                <Typography color="text.secondary">{summary.month}</Typography>
                <Box sx={{ flex: 1 }} />
                <Chip color="primary" label={`Net worth ${formatMoney(summary.netWorth)}`} />
                <Chip
                    color={summary.pendingReviewCount > 0 ? "warning" : "default"}
                    label={`${summary.pendingReviewCount} to review`}
                />
            </Box>

            {summary.budgetAlerts.length > 0 && (
                <Box sx={{ mb: 3, display: "flex", flexDirection: "column", gap: 1 }}>
                    {summary.budgetAlerts.map((alert) => {
                        return (
                            <Alert
                                key={alert.categoryId}
                                type={alert.level >= 100 ? "error" : "warning"}
                                showIcon
                                message={`${alert.categoryName}: ${alert.percent}% of budget (${formatMoney(alert.spent)} of ${formatMoney(alert.planned)})`}
                            />
                        );
                    })}
                </Box>
            )}

            <Grid container spacing={2}>
                {summary.accountGroups.map((group) => {
                    return (
                        <Grid key={group.label} size={{ xs: 12, md: 4 }}>
                            <AccountGroupCard group={group} />
                        </Grid>
                    );
                })}
            </Grid>
        </Box>
    );
}
