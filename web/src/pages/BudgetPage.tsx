import { useCallback, useEffect, useState } from "react";
import { Box, Button, Chip, IconButton, Typography } from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { App as AntApp } from "antd";
import dayjs from "dayjs";
import BudgetSection from "../components/BudgetSection";
import { budgetsApi, type BudgetSummary } from "../lib/api";
import { errorText, formatMoney } from "../lib/format";

function collectPlanned(summary: BudgetSummary): Record<number, number | null> {
    const planned: Record<number, number | null> = {};
    [...summary.income, ...summary.groups.flatMap((group) => group.categories)].forEach((line) => {
        planned[line.categoryId] = line.planned;
    });
    return planned;
}

function sectionSubtitle(planned: number, actual: number) {
    return (
        <Typography variant="body2" color="text.secondary">
            planned {formatMoney(planned)} · actual {formatMoney(actual)}
        </Typography>
    );
}

export default function BudgetPage() {
    const { message } = AntApp.useApp();
    const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
    const [summary, setSummary] = useState<BudgetSummary | null>(null);
    const [planned, setPlanned] = useState<Record<number, number | null>>({});
    const [saving, setSaving] = useState(false);

    const load = useCallback(() => {
        budgetsApi
            .summary(month)
            .then((result) => {
                setSummary(result);
                setPlanned(collectPlanned(result));
            })
            .catch((error: unknown) => message.error(errorText(error)));
    }, [month, message]);

    useEffect(load, [load]);

    const setOne = (categoryId: number, value: number | null) => {
        setPlanned((current) => ({ ...current, [categoryId]: value }));
    };

    const save = async () => {
        setSaving(true);
        try {
            const entries = Object.entries(planned).map(([id, plannedAmount]) => ({
                categoryId: Number(id),
                plannedAmount,
            }));
            const result = await budgetsApi.setPlanned(month, entries);
            setSummary(result);
            setPlanned(collectPlanned(result));
            message.success("Budget saved");
        } catch (error: unknown) {
            message.error(errorText(error));
        } finally {
            setSaving(false);
        }
    };

    const shiftMonth = (delta: number) => setMonth(dayjs(`${month}-01`).add(delta, "month").format("YYYY-MM"));

    if (!summary) {
        return <Typography color="text.secondary">Loading…</Typography>;
    }

    return (
        <Box>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 3, flexWrap: "wrap" }}>
                <IconButton onClick={() => shiftMonth(-1)} aria-label="previous month">
                    <ChevronLeftIcon />
                </IconButton>
                <Typography variant="h5" sx={{ minWidth: 170, textAlign: "center" }}>
                    {dayjs(`${month}-01`).format("MMMM YYYY")}
                </Typography>
                <IconButton onClick={() => shiftMonth(1)} aria-label="next month">
                    <ChevronRightIcon />
                </IconButton>
                <Box sx={{ flex: 1 }} />
                <Chip
                    color={summary.leftToBudget < 0 ? "error" : "primary"}
                    label={`Left to budget ${formatMoney(summary.leftToBudget)}`}
                />
                <Button variant="contained" onClick={save} disabled={saving}>
                    Save
                </Button>
            </Box>

            <BudgetSection
                title="Income"
                subtitle={sectionSubtitle(summary.plannedIncome, summary.actualIncome)}
                lines={summary.income}
                planned={planned}
                onPlanned={setOne}
            />

            {summary.groups.map((group) => {
                return (
                    <BudgetSection
                        key={group.groupId ?? group.groupName}
                        title={group.groupName}
                        subtitle={sectionSubtitle(group.planned, group.actual)}
                        lines={group.categories}
                        planned={planned}
                        onPlanned={setOne}
                    />
                );
            })}
        </Box>
    );
}
