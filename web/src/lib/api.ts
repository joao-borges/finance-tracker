// Typed API client. All calls hit the same origin under /api (proxied to the
// Spring Boot backend in dev). Repositories are never exposed directly — these
// map to the per-entity controllers. See PLAN.md for the endpoint sketch.

export type AccountType = "CHECKING" | "SAVINGS" | "CREDIT_CARD" | "LOAN" | "CASH";

export interface Account {
    id: number;
    name: string;
    importRef?: string | null;
    type: AccountType;
    currency: string;
    balance?: number | null;
    website?: string | null;
    logoUrl?: string | null;
    hidden?: boolean;
    archived?: boolean;
    mergedIntoId?: number | null;
    mergedIntoName?: string | null;
}

export interface Merchant {
    id: number;
    name: string;
    icon?: string | null;
    website?: string | null;
    logoUrl?: string | null;
}

export interface CategoryGroup {
    id: number;
    name: string;
    sortOrder?: number;
    icon?: string | null;
    color?: string | null;
}

export interface Category {
    id: number;
    name: string;
    icon?: string | null;
    income?: boolean;
    sortOrder?: number;
    archived?: boolean;
    alertThreshold?: number | null;
    groupId?: number | null;
    groupName?: string | null;
}

export interface Rule {
    id: number;
    name: string;
    merchantMatch: string;
    categoryId?: number | null;
    categoryName?: string | null;
    merchantId?: number | null;
    merchantName?: string | null;
    newMerchantName?: string | null;
    autoApprove?: boolean;
    priority?: number;
    enabled?: boolean;
    matchCount?: number;
}

export interface DashboardSummary {
    month: string;
    netWorth: number;
    pendingReviewCount: number;
    accountGroups: AccountGroup[];
    budgetAlerts: BudgetAlert[];
}

export interface AccountGroup {
    label: string;
    total: number;
    accounts: AccountSummary[];
}

export interface AccountSummary {
    id: number;
    name: string;
    type: AccountType;
    balance?: number | null;
    currency: string;
    logoUrl?: string | null;
}

export interface BudgetAlert {
    categoryId: number;
    categoryName: string;
    planned: number;
    spent: number;
    percent: number;
    level: number;
}

export interface ImportRun {
    id: number;
    source: string;
    status: string;
    startedAt: string;
    finishedAt?: string | null;
    fileName?: string | null;
    newCount: number;
    dedupCount: number;
    errorCount: number;
    accountCount: number;
}

async function http<T>(method: string, path: string, body?: unknown): Promise<T> {
    const res = await fetch(path, {
        method,
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 401) {
        // Session expired or not signed in — send the browser through Google login.
        window.location.href = "/oauth2/authorization/google";
        throw new Error("Not authenticated");
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `${res.status} ${res.statusText}`);
    }
    if (res.status === 204) {
        return undefined as T;
    }
    return (await res.json()) as T;
}

export interface Crud<T> {
    list(): Promise<T[]>;
    create(body: Partial<T>): Promise<T>;
    update(id: number, body: Partial<T>): Promise<T>;
}

function crud<T>(base: string): Crud<T> {
    return {
        list: () => http<T[]>("GET", base),
        create: (body) => http<T>("POST", base, body),
        update: (id, body) => http<T>("PATCH", `${base}/${id}`, body),
    };
}

export const accountsApi = {
    ...crud<Account>("/api/accounts"),
    merge: (id: number, targetId: number) => http<Account>("POST", `/api/accounts/${id}/merge`, { targetId }),
};
export const merchantsApi = crud<Merchant>("/api/merchants");
export const categoryGroupsApi = crud<CategoryGroup>("/api/category-groups");
export const categoriesApi = crud<Category>("/api/categories");
export const rulesApi = crud<Rule>("/api/rules");

export interface Transaction {
    id: number;
    postedAt: string;
    accountId: number;
    accountName: string;
    accountLogoUrl?: string | null;
    merchantName: string;
    merchantId?: number | null;
    merchant?: string | null;
    merchantLogoUrl?: string | null;
    merchantIcon?: string | null;
    categoryId?: number | null;
    categoryName?: string | null;
    categoryIcon?: string | null;
    amount: number;
    currency: string;
    source: string;
    needsReview: boolean;
    excludedFromBudget: boolean;
}

export interface TransactionFilters {
    from?: string;
    to?: string;
    accountIds?: number[];
    merchantIds?: number[];
    categoryIds?: number[];
    review?: boolean;
}

function queryString(filters: TransactionFilters): string {
    const params = new URLSearchParams();
    if (filters.from) {
        params.set("from", filters.from);
    }
    if (filters.to) {
        params.set("to", filters.to);
    }
    if (filters.accountIds && filters.accountIds.length > 0) {
        params.set("accountIds", filters.accountIds.join(","));
    }
    if (filters.merchantIds && filters.merchantIds.length > 0) {
        params.set("merchantIds", filters.merchantIds.join(","));
    }
    if (filters.categoryIds && filters.categoryIds.length > 0) {
        params.set("categoryIds", filters.categoryIds.join(","));
    }
    if (filters.review !== undefined) {
        params.set("review", String(filters.review));
    }
    const qs = params.toString();
    return qs ? `?${qs}` : "";
}

export interface TransactionUpdate {
    categoryId?: number | null;
    merchantId?: number | null;
    newMerchantName?: string | null;
    needsReview?: boolean | null;
    excludedFromBudget?: boolean | null;
}

export interface Page<T> {
    content: T[];
    page: number;
    size: number;
    hasNext: boolean;
    total: number;
}

export const transactionsApi = {
    list: (filters: TransactionFilters = {}, page = 0, size = 25) => {
        const qs = queryString(filters);
        const sep = qs ? "&" : "?";
        return http<Page<Transaction>>("GET", `/api/transactions${qs}${sep}page=${page}&size=${size}`);
    },
    update: (id: number, body: TransactionUpdate) => http<Transaction>("PATCH", `/api/transactions/${id}`, body),
};

export interface SavedFilter {
    id: number;
    name: string;
    fromDate?: string | null;
    toDate?: string | null;
    accountIds?: number[] | null;
    merchantIds?: number[] | null;
    categoryIds?: number[] | null;
    review?: boolean | null;
}

export const savedFiltersApi = {
    list: () => http<SavedFilter[]>("GET", "/api/saved-filters"),
    create: (body: Partial<SavedFilter>) => http<SavedFilter>("POST", "/api/saved-filters", body),
    update: (id: number, body: Partial<SavedFilter>) => http<SavedFilter>("PATCH", `/api/saved-filters/${id}`, body),
    remove: (id: number) => http<void>("DELETE", `/api/saved-filters/${id}`),
};

export const dashboardApi = {
    summary: () => http<DashboardSummary>("GET", "/api/dashboard/summary"),
};

export interface BudgetLine {
    categoryId: number;
    name: string;
    icon?: string | null;
    planned: number;
    actual: number;
    remaining: number;
}

export interface BudgetGroup {
    groupId?: number | null;
    groupName: string;
    planned: number;
    actual: number;
    categories: BudgetLine[];
}

export interface BudgetSummary {
    month: string;
    plannedIncome: number;
    actualIncome: number;
    plannedExpense: number;
    actualExpense: number;
    leftToBudget: number;
    income: BudgetLine[];
    groups: BudgetGroup[];
}

export interface BudgetEntry {
    categoryId: number;
    plannedAmount: number | null;
}

export const budgetsApi = {
    summary: (month: string) => http<BudgetSummary>("GET", `/api/budgets/${month}/summary`),
    setPlanned: (month: string, entries: BudgetEntry[]) => http<BudgetSummary>("PUT", `/api/budgets/${month}`, entries),
    clear: (month: string) => http<BudgetSummary>("DELETE", `/api/budgets/${month}`),
    copyPrevious: (month: string) => http<BudgetSummary>("POST", `/api/budgets/${month}/copy-previous`),
};

export const rulesExtraApi = {
    apply: (id: number) => http<{ applied: number }>("POST", `/api/rules/${id}/apply`),
};

export type CsvFormat = "SIMPLE" | "AMEX" | "RBC" | "PC_FINANCIAL";

export const importApi = {
    history: () => http<ImportRun[]>("GET", "/api/imports"),
    uploadCsv: async (file: File, format: CsvFormat): Promise<ImportRun> => {
        const form = new FormData();
        form.append("file", file);
        const res = await fetch(`/api/imports/csv?format=${format}`, { method: "POST", body: form });
        if (!res.ok) {
            throw new Error((await res.text()) || `${res.status} ${res.statusText}`);
        }
        return (await res.json()) as ImportRun;
    },
};

export interface SimpleFinStatus {
    connected: boolean;
    lastSyncedAt: string | null;
}

export const simplefinApi = {
    status: () => http<SimpleFinStatus>("GET", "/api/simplefin/status"),
    setup: (token: string) => http<SimpleFinStatus>("POST", "/api/simplefin/setup", { token }),
    sync: () => http<ImportRun>("POST", "/api/simplefin/sync"),
};

export interface Me {
    authenticated: boolean;
    email?: string | null;
    name?: string | null;
    picture?: string | null;
}

export const authApi = {
    me: () => http<Me>("GET", "/api/me"),
    logout: async () => {
        await fetch("/logout", { method: "POST" });
        window.location.href = "/";
    },
};
