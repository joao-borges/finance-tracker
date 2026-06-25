import { useCallback, useEffect, useRef, useState } from "react";
import { Box, Button, Chip, IconButton, Typography } from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { App as AntApp } from "antd";
import dayjs from "dayjs";
import BudgetSection from "../components/BudgetSection";
import BudgetCategoryModal from "../components/BudgetCategoryModal";
import {
    budgetsApi,
    categoriesApi,
    categoryGroupsApi,
    merchantsApi,
    type BudgetLine,
    type BudgetSummary,
    type Category,
    type Merchant,
} from "../lib/api";
import { errorText, formatMoney } from "../lib/format";
import styles from "./BudgetPage.module.css";

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
    const [categories, setCategories] = useState<Category[]>([]);
    const [merchants, setMerchants] = useState<Merchant[]>([]);
    const [drillLine, setDrillLine] = useState<BudgetLine | null>(null);
    const [shownHidden, setShownHidden] = useState<Record<string, boolean>>({});
    const dirty = useRef<Map<number, number | null>>(new Map());

    const toggleHidden = (key: string) => setShownHidden((current) => ({ ...current, [key]: !current[key] }));
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isPast = dayjs(`${month}-01`).isBefore(dayjs().startOf("month"));

    const cancelPending = () => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
        dirty.current.clear();
    };

    const load = useCallback(() => {
        // Always load hidden categories; visibility is toggled per group in the UI.
        budgetsApi
            .summary(month, true)
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
        categoriesApi.list().then(setCategories).catch(() => setCategories([]));
        merchantsApi.list().then(setMerchants).catch(() => setMerchants([]));
    }, []);

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
            .setPlanned(month, entries, true)
            .then(setSummary)
            .catch((error: unknown) => message.error(errorText(error)))
            .finally(() => setSaving(false));
    }, [month, message]);

    const setOne = (categoryId: number, value: number | null) => {
        if (isPast) {
            return;
        }
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

    // Persist the group's collapsed state server-side so it loads the same everywhere.
    const toggleGroup = (groupId: number, collapsed: boolean) => {
        setSummary((current) =>
            current
                ? { ...current, groups: current.groups.map((g) => (g.groupId === groupId ? { ...g, collapsed } : g)) }
                : current,
        );
        categoryGroupsApi.update(groupId, { collapsed }).catch((error: unknown) => message.error(errorText(error)));
    };

    if (!summary) {
        return <Typography color="text.secondary">Loading…</Typography>;
    }

    return (
        <Box>
            <Box className={styles.toolbar}>
                <IconButton onClick={() => shiftMonth(-1)} aria-label="previous month">
                    <ChevronLeftIcon />
                </IconButton>
                <Typography variant="h5" className={styles.monthLabel}>
                    {dayjs(`${month}-01`).format("MMMM YYYY")}
                </Typography>
                <IconButton onClick={() => shiftMonth(1)} aria-label="next month">
                    <ChevronRightIcon />
                </IconButton>
                <Box className={styles.spacer} />
                <Chip
                    color={summary.leftToBudget < 0 ? "error" : "primary"}
                    label={`Left to budget ${formatMoney(summary.leftToBudget)}`}
                />
                <Button size="small" component="a" href={`/api/budgets/${month}/export`}>
                    Export PDF
                </Button>
                {!isPast && (
                    <>
                        <Button size="small" onClick={() => runAction(() => budgetsApi.copyPrevious(month, true))}>
                            Copy from previous month
                        </Button>
                        <Button size="small" color="error" onClick={() => runAction(() => budgetsApi.clear(month, true))}>
                            Clear all
                        </Button>
                    </>
                )}
            </Box>

            <Typography variant="body2" color="text.secondary" className={styles.status}>
                {isPast ? "Past month — read only" : saving ? "Saving…" : "Changes save automatically"}
            </Typography>

            <BudgetSection
                title="Income"
                subtitle={sectionSubtitle(summary.plannedIncome, summary.actualIncome)}
                lines={summary.income}
                planned={planned}
                onPlanned={setOne}
                onOpenCategory={setDrillLine}
                showHidden={!!shownHidden.income}
                onToggleHidden={() => toggleHidden("income")}
                readOnly={isPast}
            />

            {summary.groups
                .filter((group) => group.categories.some((line) => !line.hidden))
                .map((group) => {
                return (
                    <BudgetSection
                        key={group.groupId ?? group.groupName}
                        title={group.groupName}
                        subtitle={sectionSubtitle(group.planned, group.actual)}
                        lines={group.categories}
                        planned={planned}
                        onPlanned={setOne}
                        onOpenCategory={setDrillLine}
                        collapsible={group.groupId != null}
                        collapsed={group.collapsed}
                        onToggleCollapse={
                            group.groupId != null ? () => toggleGroup(group.groupId!, !group.collapsed) : undefined
                        }
                        showHidden={!!shownHidden[String(group.groupId)]}
                        onToggleHidden={() => toggleHidden(String(group.groupId))}
                        readOnly={isPast}
                    />
                );
            })}

            <BudgetCategoryModal
                line={drillLine}
                month={month}
                categories={categories}
                merchants={merchants}
                onClose={() => setDrillLine(null)}
                onChanged={load}
            />
        </Box>
    );
}
