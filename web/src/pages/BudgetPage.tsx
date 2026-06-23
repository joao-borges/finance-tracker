import { useCallback, useEffect, useRef, useState } from "react";
import { Box, Button, Chip, IconButton, Typography } from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { App as AntApp } from "antd";
import dayjs from "dayjs";
import BudgetSection from "../components/BudgetSection";
import { budgetsApi, type BudgetSummary } from "../lib/api";
import { errorText, formatMoney } from "../lib/format";

const SAVE_DEBOUNCE_MS = 600;

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
    const dirty = useRef<Map<number, number | null>>(new Map());
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const cancelPending = () => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
        dirty.current.clear();
    };

    const load = useCallback(() => {
        budgetsApi
            .summary(month)
            .then((result) => {
                setSummary(result);
                setPlanned(collectPlanned(result));
            })
            .catch((error: unknown) => message.error(errorText(error)));
    }, [month, message]);

    useEffect(() => {
        cancelPending();
        load();
    }, [load]);

    useEffect(() => {
        return () => cancelPending();
    }, []);

    const flush = useCallback(() => {
        if (dirty.current.size === 0) {
            return;
        }
        const entries = Array.from(dirty.current.entries()).map(([categoryId, plannedAmount]) => ({
            categoryId,
            plannedAmount,
        }));
        dirty.current.clear();
        setSaving(true);
        budgetsApi
            .setPlanned(month, entries)
            .then(setSummary)
            .catch((error: unknown) => message.error(errorText(error)))
            .finally(() => setSaving(false));
    }, [month, message]);

    const setOne = (categoryId: number, value: number | null) => {
        setPlanned((current) => ({ ...current, [categoryId]: value }));
        dirty.current.set(categoryId, value);
        if (timer.current) {
            clearTimeout(timer.current);
        }
        timer.current = setTimeout(flush, SAVE_DEBOUNCE_MS);
    };

    const runAction = async (action: () => Promise<BudgetSummary>) => {
        cancelPending();
        setSaving(true);
        try {
            const result = await action();
            setSummary(result);
            setPlanned(collectPlanned(result));
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
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1, flexWrap: "wrap" }}>
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
                <Button size="small" onClick={() => runAction(() => budgetsApi.copyPrevious(month))}>
                    Copy from previous month
                </Button>
                <Button size="small" color="error" onClick={() => runAction(() => budgetsApi.clear(month))}>
                    Clear all
                </Button>
            </Box>

            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, height: 20 }}>
                {saving ? "Saving…" : "Changes save automatically"}
            </Typography>

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
