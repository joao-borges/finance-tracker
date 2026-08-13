import { useCallback, useEffect, useRef, useState } from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { App as AntApp, Button } from "antd";
import AddIcon from "@mui/icons-material/Add";
import SavedFilterControls from "../components/SavedFilterControls";
import TransactionFilterBar from "../components/TransactionFilterBar";
import TransactionsTable from "../components/TransactionsTable";
import TransactionEditModal from "../components/TransactionEditModal";
import AddTransactionModal from "../components/AddTransactionModal";
import {
    accountsApi,
    categoriesApi,
    merchantsApi,
    rulesApi,
    tagsApi,
    transactionsApi,
    type Account,
    type Category,
    type Merchant,
    type Rule,
    type Transaction,
    type TransactionFilters,
} from "../lib/api";
import { errorText } from "../lib/format";
import styles from "./TransactionsPage.module.css";

const PAGE_SIZE = 25;

export default function TransactionsPage() {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<Transaction[]>([]);
    const [filters, setFilters] = useState<TransactionFilters>({});
    const [page, setPage] = useState(0);
    const [hasNext, setHasNext] = useState(true);
    const [loading, setLoading] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [merchants, setMerchants] = useState<Merchant[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [rules, setRules] = useState<Rule[]>([]);
    const [tagOptions, setTagOptions] = useState<string[]>([]);
    const [loadedId, setLoadedId] = useState<number | undefined>(undefined);
    const [loadedName, setLoadedName] = useState("");
    const [editing, setEditing] = useState<Transaction | null>(null);
    const [adding, setAdding] = useState(false);
    const sentinelRef = useRef<HTMLDivElement | null>(null);

    const loadMerchants = useCallback(() => {
        merchantsApi.list().then(setMerchants).catch(() => setMerchants([]));
    }, []);

    const loadRules = useCallback(() => {
        rulesApi.list().then(setRules).catch(() => setRules([]));
    }, []);

    const loadTags = useCallback(() => {
        tagsApi.list().then(setTagOptions).catch(() => setTagOptions([]));
    }, []);

    const filtersKey = JSON.stringify(filters);

    useEffect(() => {
        accountsApi.list().then(setAccounts).catch(() => setAccounts([]));
        categoriesApi.list().then(setCategories).catch(() => setCategories([]));
        loadMerchants();
        loadRules();
        loadTags();
    }, [loadMerchants, loadRules, loadTags]);

    // Reset to the first page whenever the filters change.
    useEffect(() => {
        setPage(0);
    }, [filtersKey]);

    // Load the current page — replace on page 0, append for later pages.
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        transactionsApi
            .list(filters, page, PAGE_SIZE)
            .then((result) => {
                if (cancelled) {
                    return;
                }
                setHasNext(result.hasNext);
                setRows((previous) => (page === 0 ? result.content : [...previous, ...result.content]));
            })
            .catch((error: unknown) => {
                if (!cancelled) {
                    message.error(errorText(error));
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [page, filtersKey, reloadKey, message]);

    // Infinite scroll: load the next page when the sentinel scrolls into view.
    useEffect(() => {
        const element = sentinelRef.current;
        if (element === null) {
            return;
        }
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting && hasNext && !loading) {
                setPage((current) => current + 1);
            }
        });
        observer.observe(element);
        return () => observer.disconnect();
    }, [hasNext, loading]);

    const setLoaded = (id: number | undefined, name: string) => {
        setLoadedId(id);
        setLoadedName(name);
    };

    // Update just the edited row so the infinite-scroll list keeps its position.
    const onEdited = (updated: Transaction) => {
        setRows((current) => current.map((row) => (row.id === updated.id ? updated : row)));
        loadMerchants();
        loadRules();
        loadTags();
    };

    // Splits/unsplits change which rows exist, so reload the list from the top.
    const refresh = () => {
        setPage(0);
        setReloadKey((key) => key + 1);
        loadMerchants();
        loadRules();
    };

    return (
        <Box>
            <Box className={styles.header}>
                <Typography variant="h5" className={styles.title}>
                    Transactions
                </Typography>
                <SavedFilterControls
                    filters={filters}
                    loadedId={loadedId}
                    loadedName={loadedName}
                    setLoaded={setLoaded}
                    onApply={setFilters}
                />
                <Button type="primary" icon={<AddIcon fontSize="small" />} onClick={() => setAdding(true)}>
                    Add transaction
                </Button>
            </Box>

            <TransactionFilterBar
                filters={filters}
                accounts={accounts}
                merchants={merchants}
                categories={categories}
                tagOptions={tagOptions}
                onChange={(next) => setFilters((current) => ({ ...current, ...next }))}
                onClear={() => {
                    setFilters({});
                    setLoaded(undefined, "");
                }}
            />

            <TransactionsTable
                rows={rows}
                onOpen={setEditing}
                merchants={merchants}
                categories={categories}
                onUpdated={onEdited}
            />

            <Box ref={sentinelRef} className={styles.sentinel}>
                {loading && <CircularProgress size={24} />}
                {!loading && !hasNext && rows.length > 0 && (
                    <Typography variant="body2" color="text.secondary">
                        End of transactions
                    </Typography>
                )}
            </Box>

            <TransactionEditModal
                transaction={editing}
                categories={categories}
                merchants={merchants}
                tagOptions={tagOptions}
                rules={rules}
                onClose={() => setEditing(null)}
                onSaved={onEdited}
                onStructuralChange={refresh}
            />

            <AddTransactionModal
                open={adding}
                accounts={accounts}
                categories={categories}
                merchants={merchants}
                onClose={() => setAdding(false)}
                onCreated={refresh}
            />
        </Box>
    );
}
