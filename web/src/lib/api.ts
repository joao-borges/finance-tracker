// Typed API client. All calls hit the same origin under /api (proxied to the
// Spring Boot backend in dev). Repositories are never exposed directly — these
// map to the per-entity controllers. See DESIGN.md for the endpoint sketch.

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
    institutionId?: number | null;
    institutionName?: string | null;
    offBudget?: boolean;
    hidden?: boolean;
    archived?: boolean;
    mergedIntoId?: number | null;
    mergedIntoName?: string | null;
}

export interface Institution {
    id: number;
    name: string;
    website?: string | null;
    logoUrl?: string | null;
    offBudget?: boolean;
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
    collapsed?: boolean;
}

export interface Category {
    id: number;
    name: string;
    icon?: string | null;
    income?: boolean;
    oneTimeMonth?: string | null;
    sortOrder?: number;
    archived?: boolean;
    hidden?: boolean;
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
    shiftToNextMonth?: boolean;
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

// Only navigate once per page load: several requests usually fail together
// when a session dies, and each must not queue its own navigation (or, at "/",
// reload in a loop).
let redirectingToLogin = false;

/**
 * Send the browser to the app root, which re-triggers sign-in (Spring Security
 * / the SSO proxy redirect a document request, unlike an XHR). Returns a
 * promise that never settles: the page is navigating away, so callers should
 * neither render an error toast nor act on a result.
 */
function redirectToLogin(): Promise<never> {
    if (!redirectingToLogin) {
        redirectingToLogin = true;
        window.location.assign("/");
    }
    return new Promise<never>(() => {});
}

/**
 * True when a response means "you are not signed in" rather than a real error.
 * Besides 401/403, an SSO proxy in front of the app (e.g. Cloudflare Access)
 * answers an expired session with its own login PAGE — HTML, status 200 — which
 * would otherwise blow up in JSON.parse and surface as a bogus error.
 */
function isAuthFailure(res: Response, payload?: string): boolean {
    if (res.status === 401 || res.status === 403) {
        return true;
    }
    const contentType = res.headers.get("content-type") ?? "";
    return payload !== undefined && !contentType.includes("json") && /^\s*<(!doctype|html)/i.test(payload);
}

async function http<T>(method: string, path: string, body?: unknown): Promise<T> {
    const res = await fetch(path, {
        method,
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (isAuthFailure(res)) {
        return redirectToLogin();
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `${res.status} ${res.statusText}`);
    }
    // Tolerate empty bodies (204, or void endpoints that return 200 with no JSON).
    const payload = await res.text();
    if (isAuthFailure(res, payload)) {
        return redirectToLogin();
    }
    if (!payload) {
        return undefined as T;
    }
    try {
        return JSON.parse(payload) as T;
    } catch {
        throw new Error(`Unexpected response from ${path}`);
    }
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
export const institutionsApi = crud<Institution>("/api/institutions");
export const merchantsApi = crud<Merchant>("/api/merchants");
export const categoryGroupsApi = crud<CategoryGroup>("/api/category-groups");
export const categoriesApi = crud<Category>("/api/categories");
export const rulesApi = {
    ...crud<Rule>("/api/rules"),
    remove: (id: number) => http<void>("DELETE", `/api/rules/${id}`),
};

export interface Transaction {
    id: number;
    postedAt: string;
    sourcePostedAt?: string | null;
    dateAdjusted?: boolean;
    accountId: number;
    accountName: string;
    accountLogoUrl?: string | null;
    accountOffBudget?: boolean;
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
    awaitingRefund: boolean;
    tags?: string[];
    matchType?: MatchType | null;
    matchedWithId?: number | null;
    splitParentId?: number | null;
}

export type MatchType = "TRANSFER" | "REFUND";

export interface TransactionFilters {
    from?: string;
    to?: string;
    accountIds?: number[];
    merchantIds?: number[];
    categoryIds?: number[];
    tags?: string[];
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
    if (filters.tags && filters.tags.length > 0) {
        params.set("tags", filters.tags.join(","));
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
    awaitingRefund?: boolean | null;
    postedAt?: string | null;
    timeZone?: string | null;
    tags?: string[] | null;
}

export interface ManualTransactionInput {
    accountId: number;
    date: string;
    timeZone?: string;
    description: string;
    amount: number;
    categoryId?: number | null;
    merchantId?: number | null;
    excludedFromBudget?: boolean;
    awaitingRefund?: boolean;
}

export interface Page<T> {
    content: T[];
    page: number;
    size: number;
    hasNext: boolean;
    total: number;
}

export interface SplitLine {
    amount: number;
    categoryId: number;
    tags?: string[];
}

export interface FilterTotals {
    count: number;
    net: number;
    inflow: number;
    outflow: number;
}

export const transactionsApi = {
    list: (filters: TransactionFilters = {}, page = 0, size = 25) => {
        const qs = queryString(filters);
        const sep = qs ? "&" : "?";
        return http<Page<Transaction>>("GET", `/api/transactions${qs}${sep}page=${page}&size=${size}`);
    },
    create: (body: ManualTransactionInput) => http<Transaction>("POST", "/api/transactions", body),
    summary: (filters: TransactionFilters = {}) =>
        http<FilterTotals>("GET", `/api/transactions/summary${queryString(filters)}`),
    update: (id: number, body: TransactionUpdate) => http<Transaction>("PATCH", `/api/transactions/${id}`, body),
    duplicates: () => http<Transaction[]>("GET", "/api/transactions/duplicates"),
    restore: (id: number) => http<Transaction>("POST", `/api/transactions/${id}/restore`),
    split: (id: number, splits: SplitLine[]) => http<Transaction>("POST", `/api/transactions/${id}/split`, { splits }),
    unsplit: (id: number) => http<Transaction>("POST", `/api/transactions/${id}/unsplit`),
    unmatch: (id: number) => http<Transaction>("POST", `/api/transactions/${id}/unmatch`),
    remove: (id: number) => http<void>("DELETE", `/api/transactions/${id}`),
};

export interface MatchSuggestion {
    id: number;
    type: MatchType;
    legA: Transaction;
    legB: Transaction;
}

export const matchesApi = {
    suggestions: () => http<MatchSuggestion[]>("GET", "/api/matches/suggestions"),
    confirm: (id: number) => http<void>("POST", `/api/matches/suggestions/${id}/confirm`),
    dismiss: (id: number) => http<void>("POST", `/api/matches/suggestions/${id}/dismiss`),
    match: (aId: number, bId: number, type: MatchType) => http<Transaction>("POST", "/api/matches", { aId, bId, type }),
    scan: () => http<{ applied: number }>("POST", "/api/matches/scan"),
};

export interface SavedFilter {
    id: number;
    name: string;
    fromDate?: string | null;
    toDate?: string | null;
    accountIds?: number[] | null;
    merchantIds?: number[] | null;
    categoryIds?: number[] | null;
    tags?: string[] | null;
    review?: boolean | null;
}

export const savedFiltersApi = {
    list: () => http<SavedFilter[]>("GET", "/api/saved-filters"),
    create: (body: Partial<SavedFilter>) => http<SavedFilter>("POST", "/api/saved-filters", body),
    update: (id: number, body: Partial<SavedFilter>) => http<SavedFilter>("PATCH", `/api/saved-filters/${id}`, body),
    remove: (id: number) => http<void>("DELETE", `/api/saved-filters/${id}`),
};

export const tagsApi = {
    list: () => http<string[]>("GET", "/api/tags"),
};

export const dashboardApi = {
    summary: () => http<DashboardSummary>("GET", "/api/dashboard/summary"),
};

export interface BudgetLine {
    categoryId: number;
    name: string;
    icon?: string | null;
    hidden: boolean;
    planned: number;
    actual: number;
    remaining: number;
}

export interface BudgetGroup {
    groupId?: number | null;
    groupName: string;
    collapsed: boolean;
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

const hiddenQuery = (includeHidden: boolean) => (includeHidden ? "?includeHidden=true" : "");

export const budgetsApi = {
    summary: (month: string, includeHidden = false) =>
        http<BudgetSummary>("GET", `/api/budgets/${month}/summary${hiddenQuery(includeHidden)}`),
    setPlanned: (month: string, entries: BudgetEntry[], includeHidden = false) =>
        http<BudgetSummary>("PUT", `/api/budgets/${month}${hiddenQuery(includeHidden)}`, entries),
    clear: (month: string, includeHidden = false) =>
        http<BudgetSummary>("DELETE", `/api/budgets/${month}${hiddenQuery(includeHidden)}`),
    copyPrevious: (month: string, includeHidden = false) =>
        http<BudgetSummary>("POST", `/api/budgets/${month}/copy-previous${hiddenQuery(includeHidden)}`),
    pullPreviousShortfall: (month: string, includeHidden = false) =>
        http<BudgetSummary>("POST", `/api/budgets/${month}/pull-previous-shortfall${hiddenQuery(includeHidden)}`),
    addOneTimeCategory: (
        month: string,
        body: { name: string; groupId?: number | null; plannedAmount: number; icon?: string | null },
        includeHidden = false,
    ) => http<BudgetSummary>("POST", `/api/budgets/${month}/one-time-category${hiddenQuery(includeHidden)}`, body),
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
        if (isAuthFailure(res)) {
            return redirectToLogin();
        }
        if (!res.ok) {
            throw new Error((await res.text()) || `${res.status} ${res.statusText}`);
        }
        const payload = await res.text();
        if (isAuthFailure(res, payload)) {
            return redirectToLogin();
        }
        return JSON.parse(payload) as ImportRun;
    },
};

export interface SimpleFinStatus {
    connected: boolean;
    lastSyncedAt: string | null;
    // Origin of the bridge this connection points at (no credentials, no path),
    // so the UI can open the bridge's own site. Null when not connected.
    bridgeUrl: string | null;
}

export interface SimpleFinHealth {
    healthy: boolean;
    report: string;
}

export const simplefinApi = {
    status: () => http<SimpleFinStatus>("GET", "/api/simplefin/status"),
    healthCheck: () => http<SimpleFinHealth>("POST", "/api/simplefin/health-check"),
    setup: (token: string) => http<SimpleFinStatus>("POST", "/api/simplefin/setup", { token }),
    // No range = recent window (same as the daily scheduled sync). Pass from/to
    // (YYYY-MM-DD) to force a custom range.
    sync: (from?: string, to?: string) => {
        const params = new URLSearchParams();
        if (from) {
            params.set("from", from);
        }
        if (to) {
            params.set("to", to);
        }
        const qs = params.toString();
        return http<ImportRun>("POST", `/api/simplefin/sync${qs ? `?${qs}` : ""}`);
    },
};

export interface Me {
    authenticated: boolean;
    email?: string | null;
    name?: string | null;
    givenName?: string | null;
    picture?: string | null;
}

export const authApi = {
    me: () => http<Me>("GET", "/api/me"),
    logout: async () => {
        await fetch("/logout", { method: "POST" });
        window.location.href = "/";
    },
};
