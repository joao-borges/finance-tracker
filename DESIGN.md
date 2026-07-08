# finance

A self-hosted personal finance tracker. Auto-syncs transactions from SimpleFIN, categorizes them with merchant-match rules, and tracks monthly budgets — built from scratch, owned end to end.

**Stack:** Spring Boot 4.1 (Java 25) · Spring Data JPA / Hibernate · Liquibase · PostgreSQL · React + Vite + TypeScript · Docker · `pg_dump` → NAS.

> **Persistence & migrations:** Spring Data JPA (Hibernate as the provider) for the data layer; **Liquibase** owns the schema. Keep Hibernate in validate-only mode (`spring.jpa.hibernate.ddl-auto=validate`) so Liquibase is the single source of truth and Hibernate never silently alters tables — entities and changelog stay in lockstep, and a mismatch fails fast at startup. Changelogs live in `api/src/main/resources/db/changelog/`.

---

## Scope

Deliberately narrow for phase 1. The discipline is: build the spine that handles *your* real RBC + Amex data correctly, ship it, use it, then add the nice-to-haves.

### Phase 1 (this build)
- SimpleFIN ingestion: pull accounts, balances, and **posted** transactions directly (no Firefly, no aggregator middleware)
- CSV import as a fallback source: per-format parsers (Simple, Amex CA, RBC, PC Financial) feeding the *same* pipeline (dedup, rules, review) — backup for sync outages and for backfilling history older than SimpleFIN's ~90-day window
- Google sign-in (OAuth2/OIDC) with an email allowlist, so the app can be exposed publicly via Cloudflare
- Accounts dashboard: balance pulled straight from the bank and displayed — no reconciliation
- Transactions list with filters
- Categories + category groups (Home, Kid, Car, …)
- Monthly budgets: enter income, allocate across expense categories, see planned vs spent
- Rules engine (simple): merchant **contains** match → recategorize, with an auto-approve toggle
- Review queue for everything not auto-approved
- Soft dedup with a restore UI
- Splits (manual amounts)
- Per-transaction "exclude from budget" flag
- Webhook notifications (Discord)
- Automated Postgres backup to NAS

### Phase 2 (built)
- **Transfer / CC-payment matching** and **refund matching** — see the Matching section.
- **Awaiting-refund flag** — exclude a known-future-refund purchase from the current budget.

### Explicitly deferred (phase 2+)
- Complex rules (AND/OR, amount/account conditions, multiple actions)
- Pending transactions (ignored entirely — posted only)
- Net worth chart (not wanted)
- **MCP server (bring-your-own-AI):** a read-only MCP server in front of the Postgres — tools to query transactions, budget status, and balances — so Claude (or any MCP client) can answer "what did I spend on dining this month" against real data. This is the one feature where building your own beats every commercial option in Canada: Era productizes exactly this but is locked to US aggregators, while you own the data layer and SimpleFIN covers your banks. Small addition once the core data model and the shared query predicates exist — it reuses them directly.

---

## Pre-flight (do before any code)

**SimpleFIN coverage: ✅ confirmed.** RBC, Amex CA, PC Financial, and Wealthsimple — all four institutions in use — are supported. The auto-sync premise holds; no CSV fallback needed. Sign up at https://beta-bridge.simplefin.org/ (~$15/yr).

**Capture real payloads (now the actual first step).** Once connected, save a few raw SimpleFIN `/accounts` JSON responses to `fixtures/`. You'll build dedup, date handling, and tests against your actual data shapes — including the date/timezone formats, which stop being a guessing game once you have samples. Note any per-institution quirks: PC Financial and Wealthsimple may shape descriptors or transaction ids differently than the big banks, and that's exactly what the dedup key and merchant-match rules need to be robust against.

---

## Data Model

Signed amounts throughout: **negative = outflow, positive = inflow** — matches SimpleFIN natively (debits arrive negative), so no sign juggling on import.

```
Account
  id, simplefin_id (unique), name
  import_ref (unique)  # stable key CSV imports match on; `name` is free to rename
  type (checking | savings | credit_card | loan | cash)
  currency
  balance              # pulled from bank, stored as-is for display
  balance_date         # as-of timestamp from SimpleFIN
  website              # optional; used to fetch a favicon for display
  logo_url             # resolved icon URL (favicon), for easy identification
  merged_into_id       # nullable self-FK — source card folded into a canonical account (hidden; txns reassigned)
  hidden, archived
  last_synced_at

CategoryGroup
  id, name, sort_order, icon, color

Category
  id, group_id, name, icon
  is_income (bool)     # income vs expense
  alert_threshold      # optional; monthly spend over this fires a yellow Discord alert
  sort_order, archived

Merchant                 # canonical merchant, distinct from the raw bank descriptor
  id, name (unique)
  icon                 # optional emoji, shown when there's no website logo
  website              # optional; used to fetch a favicon
  logo_url             # resolved icon URL (favicon), for easy identification

Transaction
  id, account_id
  source               # 'simplefin' | 'csv'
  simplefin_id         # nullable; the SimpleFIN transaction id (null for CSV rows)
  dedup_key            # simplefin: id + ':' + posted_ts  |  csv/no-id: content hash (account+date+amount+merchant)
  posted_at            # timestamp ('posted' from SimpleFIN, or parsed date column from CSV)
  amount               # signed; negative = outflow
  merchant             # raw descriptor (SimpleFIN payee/description, or CSV merchant column)
  merchant_id          # nullable FK — canonical Merchant once resolved (by a rule or manually)
  description          # optional cleaned/normalized form
  category_id          # nullable — everything lands uncategorized
  needs_review (bool)  # in the review queue
  excluded_from_budget (bool)
  awaiting_refund (bool)  # known-future refund — excluded from the current budget
  is_split (bool)      # this is a split PARENT
  split_parent_id      # nullable FK — set on split CHILDREN
  is_dedup (bool)      # quarantined duplicate
  matched_with_id      # nullable self-FK — paired transfer/refund leg
  match_type           # 'transfer' | 'refund' | null
  notes, currency
  import_run_id

MatchSuggestion          # medium-confidence pair awaiting Confirm/Reject
  id, leg_a_id, leg_b_id, type ('transfer'|'refund'), dismissed (bool), created_at

Rule
  id, name
  merchant_match       # substring, matched case-insensitive against merchant
  category_id          # target category for the recategorize action
  merchant_id          # nullable — also associate the txn to this canonical Merchant
  auto_approve (bool)
  priority             # lower = evaluated first; first match wins
  enabled (bool)
  last_matched_at, match_count

Budget
  id, month (YYYY-MM), category_id, planned_amount
  UNIQUE(month, category_id)

SavedFilter              # named, shared transactions filter (household)
  id, name (unique)
  from_date, to_date
  account_ids, merchant_ids, category_ids   # comma-separated id lists (no FKs, survive deletes)
  review               # nullable bool

ImportRun
  id, source           # 'simplefin' | 'csv'
  started_at, finished_at, status
  file_name            # for CSV runs
  new_count, dedup_count, error_count, account_count
```

---

## The flag semantics (get these exactly right from commit 1)

Every subtle bug in this app comes from one of these flags being mishandled in one of the two places it matters: **the transaction list** and **the budget sum**. The table is the spec — implement it as a shared query predicate, not ad-hoc `if`s scattered around.

| Flag | Shows in transaction list? | Counts in budget? | Notes |
|---|---|---|---|
| normal | ✅ | ✅ | |
| `is_split = true` (parent) | ❌ | ❌ | Replaced by its children. Visible only inside the split-detail UI. |
| `split_parent_id != null` (child) | ✅ | ✅ | Behaves like a normal transaction; has its own category. |
| `excluded_from_budget = true` | ✅ | ❌ | Still appears in lists **and** category summaries — just not in budget math. Set on both legs of a matched transfer. |
| `awaiting_refund = true` | ✅ | ❌ | Manual flag for a purchase you expect to be refunded later — drops out of the current budget immediately. |
| `is_dedup = true` | ❌ | ❌ | Hidden from normal lists; visible in the Duplicates review UI; restorable. |
| `needs_review = true` | ✅ | ✅ if categorized | Appears in review queue too. Uncategorized → counts toward no category line. |

Two predicates fall out of this, used everywhere:

```sql
-- Default transaction list
WHERE is_split = false AND is_dedup = false

-- Budget "actual spent" for a category in a month
WHERE category_id = :cat
  AND posted_at >= :monthStart AND posted_at < :monthEnd
  AND is_split = false
  AND is_dedup = false
  AND excluded_from_budget = false
  AND awaiting_refund = false
```

Splits never double-count because the parent (`is_split=true`) is excluded and only the children carry categories. That's the whole trick.

---

## SimpleFIN Ingestion

SimpleFIN is a thin JSON API — claim a setup token once, exchange it for a long-lived access URL, then `GET <access_url>/accounts` returns accounts (with balances) and their transactions. No need for any intermediary; the Spring Boot app calls it directly.

**Implemented** in `simplefin/`: `SimpleFinClient` (claim + `fetchAccounts`, over the shared Apache-HttpClient `RestTemplate`), `SimpleFinSyncService` (`setup`/`sync`, the pipeline below), `SimpleFinScheduler` (daily at noon America/Vancouver by default — `@Scheduled` cron/zone overridable via `finance.simplefin.sync-cron`/`sync-zone`; separate bean so the `@Transactional` proxy applies), and `SimpleFinController` (`POST /api/simplefin/setup`, `GET /api/simplefin/status`, `POST /api/simplefin/sync`). The access URL is stored only in `simplefin_connection` — never returned by any endpoint or logged. The scheduled/"Sync now" run uses a 7-day lookback window (UTC) — wide on purpose, since a bank connection can go stale at the bridge for days and then catch up with transactions posted in the past (the `simplefin_id` dedup makes the overlap free); `POST /api/simplefin/sync?from=&to=` forces a custom range (used for the initial backfill and the Imports-UI range picker). `fetchAccounts` sends `start-date` (and optional `end-date`) since the bridge omits transactions without a start date. Bridge-side per-connection problems (the payload's `errors` array, e.g. "Auth required") are logged, counted on the `ImportRun`, and pushed to Discord — a dead bank connection must never fail silently.

### Sync flow (cron, daily at noon)
1. `GET /accounts?start-date=<epoch>` with the stored access URL.
2. For each account → upsert; update `balance`, `balance_date`, `last_synced_at`.
3. For each transaction in the payload:
   - **Skip if pending.** Posted-only. (SimpleFIN flags pending; drop those.)
   - **Skip if below the import floor** (`common/ImportCutoff`, `finance.import.min-posted-date`) — the daily window is small, but the floor still drops anything posted before it so the boundary day can't leak pre-cutoff transactions.
   - Build `dedup_key = simplefin_id + ':' + posted_ts`.
   - **Dedup decision** (see below).
   - If new → insert, `category_id = null`, `needs_review = true`.
4. Run the rules engine over the newly inserted transactions.
5. Fire notification events (needs-review count, large transactions, budget thresholds, import summary).
6. Write the `ImportRun` row.

SimpleFIN Bridge rate-limits to ~24 requests/day, so a daily sync is comfortably within budget; don't poll hot.

### Dedup (soft, restorable)
The job isn't "are these similar" — it's "did this exact record come back on the next overlapping sync." Two tiers:

- **Has `simplefin_id`:** key is `id + posted_ts`. If it already exists → it's a literal re-import from the overlapping window → **skip silently**. (Don't quarantine these; with a daily sync the overlapping window re-sends every transaction — pure noise.)
- **No `simplefin_id` (rare):** fall back to a content hash of `merchant + amount + account + date`. If that collides with a recent transaction → **quarantine**: insert with `is_dedup = true` so it's hidden but restorable. This is the case your soft-dedup instinct is actually for — the genuinely ambiguous one.

Why this split matters with your data: you have **two ABC Fitness $31.49 charges on the same day, same card**, plus Netflix/Nord subscriptions that re-bill at identical amounts monthly. A naive `merchant+amount+account` key (no date, no id) would eat the second ABC Fitness charge and flag every subscription's second month as a dup. The `id + timestamp` key distinguishes "same record returning" from "real second identical charge." The quarantine-don't-trash model then makes the rare fallback misfire harmless.

A **Duplicates UI** lists `is_dedup = true` rows with a Restore action that clears the flag and runs them through the rules engine like a normal transaction.

---

## CSV Import (fallback source)

Same destination, same pipeline. A CSV row becomes a `Transaction` exactly like a SimpleFIN one — it flows through dedup → rules → review identically. CSV is a second *source*, not a second system. This is the whole reason it stays cheap.

### Why keep it
- **Sync outage insurance.** SimpleFIN down, token lapsed, an institution temporarily failing — you can still get data in.
- **History backfill.** SimpleFIN only serves ~90 days. To seed older history, export CSV from the bank and import it once.
- **Odd cases.** A one-off account you don't want to connect, or a manual correction batch.

### Flow
1. User uploads a file and picks its **format** (`POST /api/imports/csv?format=SIMPLE|AMEX|RBC|PC_FINANCIAL`). One hardcoded parser per institution — no user-defined field mappings (dropped from scope; the four parsers cover the banks in use).
2. The format's parser normalizes each row to a `ParsedTransaction` (account name, merchant, signed amount where negative = outflow, posted date).
3. Each parsed row → `source = 'csv'`, `simplefin_id = null`, account found-or-created by name (`import_ref`).
4. **Dedup** (see cross-source note below), then insert new rows `category_id = null`, `needs_review = true`.
5. Run the rules engine over the new rows. Fire events. Write the `ImportRun` (`source = 'csv'`, `file_name`).

Parsing uses **Apache Commons CSV** — not hand-rolled — since bank CSVs are full of commas-in-descriptions and inconsistent quoting. A new institution = a new parser class + a `CsvFormat` enum value.

### Simplified CSV (bootstrap — built first)
Before the full `ImportSource`-mapped flow exists, there's a minimal importer to exercise the **rules engine** end to end. Fixed header `account,name,value`:
- `account` → find-or-create an `Account` by name,
- `name` → the transaction's raw `merchant`,
- `value` → the signed `amount` (`+12.34` inflow, `-12.34` outflow).

No date column, so `posted_at` defaults to import time. Each parsed row flows through the **same** ingest → rules → review path, then writes an `ImportRun`. This is the seam used to unit-test the rules logic against realistic merchant strings.

### The one real wrinkle: cross-source dedup
A transaction can arrive **both** ways — SimpleFIN syncs it, and later you import a CSV that overlaps the same dates. CSV rows have no `simplefin_id`, so they can't match on the SimpleFIN key. Both paths must therefore **also** compute the content-hash key (`account + date + amount + normalized-merchant`), and dedup checks against *both* keys regardless of source:

- A CSV row whose content hash matches an existing SimpleFIN transaction → **quarantine** (`is_dedup = true`), restorable. Don't silently merge — the merchant strings often differ slightly between the SimpleFIN feed and the bank's CSV export, so treat it as "probably the same, let me confirm" rather than guessing.
- This is exactly the ambiguous case the soft-dedup model exists for. Lean on it.

Practically: store both `dedup_key` (source-native) and a always-computed `content_hash` column, and check incoming rows against existing `content_hash` values within a small date window. The earlier ABC-Fitness caution applies double here — same-day identical legitimate charges must survive, so the hash window is "flag for review," never "auto-drop."

---

## Rules Engine (phase 1)

Intentionally tiny. One condition type, one action, one toggle.

- **Condition:** `merchant` contains `merchant_match` (case-insensitive substring). Use *contains*, not exact — raw descriptors arrive like `AMZN MKTP CA*1A2B3` and `TIM HORTONS #4521` with per-charge tails, so exact matching would need a rule per variation.
- **Action:** set `category_id`, and optionally associate the transaction to a canonical `Merchant` (`merchant_id`).
- **Toggle:** `auto_approve`.

### Evaluation
For a given transaction, walk enabled rules by `priority` ascending; **first match wins** (no chaining in phase 1).
- Match + `auto_approve = true` → set category (+ merchant if set), `needs_review = false`.
- Match + `auto_approve = false` → set category/merchant (as a suggestion), leave `needs_review = true`.
- No match → stays uncategorized, `needs_review = true`.

### Retroactive apply (required)
Everything lands uncategorized and rules only fire on new transactions, so a rule written next week does nothing for the backlog. **`POST /api/rules/{id}/apply`** runs that one rule against all `category_id IS NULL` (and optionally all `needs_review`) transactions. Without this, the engine always feels one step behind and you re-categorize the backlog by hand — the exact toil you're killing. A few lines; high payoff.

> Phase 2 is where AND/OR conditions, amount/account triggers, and multiple actions go. The schema (`merchant_match` as a single string) will need to generalize to a conditions array then — fine, that's a migration, not a rewrite.

---

## Merchants

A transaction stores the **raw** descriptor the bank sent (`merchant` column, mapped as `merchantName` in code) — `AMZN MKTP CA*1A2B3`, `TIM HORTONS #4521`, etc. That string is what rules match against and it never changes.

Separately, a **`Merchant`** is the canonical, deduplicated payee you actually care about ("Amazon", "Tim Hortons"). A transaction optionally links to one via `merchant_id`. Linking happens two ways:

- **By a rule.** A rule's action can set `merchant_id` alongside `category_id`. When writing a rule you can pick an existing merchant or type a new name — the latter creates the `Merchant` on the fly (handled at rule-save time), so the engine itself only ever sets an existing `merchant_id`.
- **Manually**, from the transaction UI (phase 2+).

**Logos for easy identification.** A `Merchant` (and an `Account`) can carry a `website`; the app resolves a favicon from it and stores `logo_url` so lists can show a recognizable icon. Phase 1 resolves the icon URL from a favicon service (`https://www.google.com/s2/favicons?domain=<host>`); downloading and caching the bytes is a later enhancement.

---

## Setup CRUD (architecture rule)

The setup pages (Accounts, Merchants, Categories, Category Groups, Rules) must **not** expose Spring Data repositories over HTTP. Every entity has its own repository annotated `@RepositoryRestResource(exported = false)` (and `spring.data.rest.detection-strategy=annotated`, so nothing is auto-exposed), and a thin `@RestController` per entity exposing exactly the endpoints needed — list (`GET`), create (`POST`), update (`PATCH`). Controllers speak DTOs, never raw entities. This keeps the HTTP surface deliberate and reviewable.

**Entity ↔ DTO mapping is MapStruct** (compile-time, no reflection), one `@Mapper` per entity sharing `common/MapStructConfig` (`componentModel = spring`, unmapped targets ignored). The pattern:
- `toDto(entity)` flattens relations to ids + display names (e.g. `category.id`/`category.name` → `categoryId`/`categoryName`).
- `toEntity(dto)` builds a new entity **via the Lombok builder**, so `@Builder.Default` values apply; it ignores `id`, server-managed fields, and relations.
- `update(@MappingTarget entity, dto)` with `nullValuePropertyMappingStrategy = IGNORE` gives PATCH semantics for free (null fields left untouched).
- Controllers still own what mappers can't: resolving relations (group/category/merchant lookups) and derived fields (favicon `logoUrl`).

MapStruct coexists with Lombok via the `lombok-mapstruct-binding` annotation-processor path (ordered Lombok → binding → MapStruct), required because JDK 23+ only runs processors declared on `annotationProcessorPaths`.

---

## Splits

UI takes a parent transaction and lets you enter N rows of `(amount, category)`. No auto-calculation — you type the amounts (a running "remaining" helper is nice but optional).

On save:
- Set parent `is_split = true`.
- Create one child Transaction per row: same `account_id`, same `merchant`, same `posted_at`, `split_parent_id = parent.id`, its own `amount` and `category_id`.
- Parent vanishes from lists and budget (per the flag table); children behave normally.

Optionally validate that child amounts sum to the parent amount, but don't enforce — you might legitimately want to split a refund differently. A soft warning beats a hard block.

---

## Matching (refunds & transfers)

Two transactions are *paired* into one logical event. Both kinds are detected by `match/MatchingService` after the rules engine on every ingest (CSV + SimpleFIN), plus a manual **scan** for the backlog. High-confidence pairs are applied automatically; weaker ones become a `MatchSuggestion` for the Matches page. A pair is stored as `matched_with_id` + `match_type` on both legs; **unmatch** unlinks them and returns both to review.

Ingest matching is **bidirectional**: a new inflow searches back for its purchase, and a new outflow lets already-ingested unmatched inflows claim it — so payload order (refund arriving before its purchase) never loses a match. Same-account refund evidence outranks a weak cross-account transfer coincidence; only a *high-confidence* transfer preempts the refund check. All candidate ordering is deterministic (chronological with id tiebreaks), so re-running a scan is idempotent.

### Transfers (incl. credit-card payments)
- **Detect:** opposite signs, equal `|amount|`, two *different* accounts, posted within ±5 days, and neither already matched.
- **Auto** when there's a payment signal — the inflow leg's account is a credit card, or a descriptor contains `payment`/`transfer`/`e-transfer`/etc. Otherwise → suggestion.
- **Category by where the +cash lands:** inflow account is `CREDIT_CARD` → **Credit Card Payment**, else **Transfer**.
- **Effect:** both legs `excluded_from_budget = true`, `needs_review = false` (internal money movement, never spend).

### Refunds (partials allowed, one-to-many)
- **One-to-many:** a purchase can be refunded by several separate refunds (a multi-item order refunded item-by-item). Each refund points at the purchase (`matched_with_id`); the purchase stays *open* (`matched_with` null) and a new refund matches only against its **remaining** un-refunded amount (`-purchase − Σ matched refunds`).
- **Detect:** an inflow on the **same account** + **same merchant** as an earlier purchase, `refund ≤ remaining`, within ≤90 days.
- **Target choice** when several purchases fit: exact-remainder match first, then exact-original-amount, then nearest preceding date (id tiebreak). Open suggestions **soft-reserve** their amount against the purchase, so multiple refunds spread across plausible purchases instead of all piling onto the largest one.
- **One open suggestion per refund:** a better target *replaces* the refund's existing suggestion (purchases can still carry several — one per refund). A dismissed pair is never re-suggested, and its candidate is skipped so the runner-up can surface.
- **Auto** on exact amount + same canonical merchant; partial/looser → suggestion.
- **Guarded apply:** confirming re-validates against the purchase's *hard* remainder (409 if stale), manual refund matches are validated the same way (400), and every apply prunes sibling suggestions that no longer fit the new remainder — a purchase can never be over-refunded.
- **Effect:** the refund inherits the purchase's `category_id` so the `+` nets the `−` in that category — *unless* the purchase is `awaiting_refund`, in which case **both legs are excluded** (a known refund that should never have counted), and the flag resolves.

### Awaiting-refund flag
Manual, set on the Categories/transaction UI ahead of the refund: a purchase you *know* will be refunded weeks/months out. It sets `awaiting_refund = true`, which the budget predicate excludes — so it drops out of the current month immediately, without waiting. When the matching refund finally lands, the refund step (above) excludes both legs and clears the flag.

### Tiers & control
- **Auto** (high confidence) — applied silently, shown with a `⇄ Transfer` / `↩ Refund` chip in the list.
- **Suggested** (medium) — the **Matches** page (Confirm applies it, Reject dismisses so it isn't proposed again).
- **Manual** — pair two transactions explicitly; **Unmatch** undoes any match.
Dedup/split rows are skipped; a manually-set or already-matched row is never auto-touched.

---

## Budgets

Monthly. Page mirrors the Monarch layout you sent: an Income section (planned vs actual) and expense **groups** containing categories, each row showing planned / actual / remaining.

- **Planned** comes from the `Budget` rows for that `month`.
- **Actual (expense)** = `-SUM(amount)` over the budget predicate above (negate so spend reads positive).
- **Actual (income)** = `SUM(amount)` over `is_income` categories, same predicate.
- **Remaining** = planned − actual.
- **Left to budget** = planned income − sum(planned expense).

Uncategorized transactions contribute to no category line by definition; surface a count somewhere so they don't silently distort your sense of the month.

**Hidden categories.** A category can be `hidden` (set only on the Categories page) to declutter the budget page — distinct from `archived`. Hidden categories are excluded from the budget summary (lines and totals) unless a **Show hidden** toggle is on (`?includeHidden=true`). Giving a category a planned amount **auto-unhides** it; setting the amount back to 0 does *not* re-hide it (hiding stays a manual Categories-page action).

---

## Accounts

Pull `balance` + `balance_date` from SimpleFIN and display them. That's the whole feature. No opening balance, no running-balance computation, no reconciliation — you explicitly traded all of that away by trusting the bank's number, which is the single biggest complexity cut in this design.

Group the dashboard like the screenshot: Cash, Credit Cards, Loans. Respect `hidden`.

---

## Notifications (Discord)

**Discord only — deliberately not configurable.** The earlier configurable-endpoint design (multiple endpoints, event subscriptions, raw/discord formats, a `WebhookEndpoint` entity) was dropped as over-built for a personal app. One Discord channel webhook URL, supplied via config (`finance.discord.webhook-url` ← env `DISCORD_WEBHOOK_URL`, never committed); blank disables notifications.

**What fires:**
- **Import summary** — after every import (`IngestService`): file, total imported, per-account breakdown, reviewed-vs-needs-review split.
- **Over budget (red embed)** — when categorized spend crosses a category's monthly budget, with the category, the month's spend, and the overflow amount.
- **Threshold reached (yellow embed)** — when monthly spend crosses a category's configured `alert_threshold`.

Budget/threshold alerts are driven by `budget/BudgetAlertService.checkAfterSpend`, called from the ingest loop and from inline categorization; it alerts **only on the crossing** (compares spend before vs after the change) so it never re-alerts a category that was already over. All sent fire-and-forget over the shared Apache-HttpClient `RestTemplate` (wrapped in `CompletableFuture.runAsync`) so a slow/broken webhook never blocks ingestion.

---

## Authentication (Google sign-in)

The app is exposed publicly through a **Cloudflare tunnel**, so it needs its own auth. Spring Security `oauth2Login` with **Google** (OIDC), session-cookie based — no passwords stored, no user table.

- **Allowlist, fail-closed.** `auth/AllowlistOidcUserService` accepts a sign-in only if the Google email is in `finance.auth.allowed-emails` (`FINANCE_AUTH_ALLOWED_EMAILS`, comma-separated). Empty list ⇒ nobody gets in. This is the whole "multi-user" story for the household — Joao + Amanda, by email.
- **Profile-gated.** The Google client lives in `application-oauth.yml`, loaded only with `SPRING_PROFILES_ACTIVE=oauth`. Without it (local dev), no client is registered and `config/SecurityConfig` leaves everything open — so the app runs credential-free locally. With it, the whole app requires sign-in.
- **SPA-friendly.** `/api/**` returns **401** when unauthenticated (the SPA's fetch wrapper turns that into a redirect to `/oauth2/authorization/google`); browser page loads redirect to Google directly. `GET /api/me` exposes the signed-in account for the UI; `POST /logout` ends the session.
- **Behind Cloudflare.** `server.forward-headers-strategy=framework` so OAuth2 redirect URIs use the public https host, not the internal `http://localhost`. The Google OAuth client's authorized redirect URI is `https://<public-host>/login/oauth2/code/google`.
- **CSRF.** The Spring CSRF filter is off; a `SameSite=Lax` session cookie is the baseline (blocks cross-site cookie-bearing POSTs) — acceptable for a single-household app.

## Performance & scaling

The data set grows indefinitely on **one table only** — `transactions`. Everything else (accounts, categories, groups, merchants, rules, budgets) stays small and bounded. Tune for that asymmetry.

### Indexes (tuned to actual query shapes; not blanket-added)
Indexing has a write cost, and the import path is the write-heavy part, so each index is justified against a real query. On `transactions`:
- `(account_id)`, `(posted_at)`, `(content_hash)`, `(dedup_key)`, `(merchant_id)` — single-column, from the initial schema (FK navigation, recency sort, dedup lookups).
- **`(category_id, posted_at)`** composite — the budget "actual spend" query filters by category and a month range; the composite serves that range scan *and* category-only lookups (leading column). Replaced the standalone `category_id` index.
- **Partial `(posted_at) WHERE needs_review AND NOT is_split AND NOT is_dedup`** — the review queue/count runs on every dashboard load; a partial index over just the review rows stays tiny as the table grows.

Deliberately **not** added (verified unnecessary at current scale): a "visible" partial on `posted_at` (the plain `posted_at` index already serves recency; split/dedup removes only a few rows) and a `category_id IS NULL` index (retroactive rule apply is a rare manual action — a scan is acceptable). Revisit with `EXPLAIN ANALYZE` once there's real volume.

### Second-level cache (in-memory)
Hibernate L2 cache via **JCache + EhCache 3** (in-process, `jakarta` classifier), enabled **only** for the small, read-heavy, rarely-changing reference entities: `Account`, `Merchant`, `Category`, `CategoryGroup`, `Rule`. These are read constantly (rule evaluation, FK navigation during mapping, dashboard) and change rarely — ideal cache candidates. `Transaction`, `Budget`, and `ImportRun` are **not** cached (write-heavy / unbounded; caching them would just churn).

Regions are **created explicitly, one per cacheable entity**, in `config/CacheConfiguration` — a `JCacheManagerCustomizer` builds each EhCache region (heap-bounded, TTL) and a `HibernatePropertiesCustomizer` hands that same `CacheManager` to Hibernate (`ConfigSettings.CACHE_MANAGER`). Region names are constants in `common/CacheRegions`; each entity opts in with `@Cache(usage = NONSTRICT_READ_WRITE, region = CacheRegions.X)` (non-strict is fine — these change rarely and don't need write locks). `hibernate.javax.cache.missing_cache_strategy=fail` makes a typo'd/uncreated region fail fast at boot rather than silently skip caching.

At current scale the practical win is modest (Postgres already keeps these tiny tables in shared buffers); the value grows under concurrent load and keeps repeated `findById` lookups off the DB. Add `hibernate.generate_statistics` to measure hit rates if needed.

---

## API Sketch (phase 1)

Implemented unless marked _(planned)_.

```
# CSV import
GET    /api/imports                       # ImportRun history
POST   /api/imports/csv?format=SIMPLE|AMEX|RBC|PC_FINANCIAL   # upload + ingest

# Accounts
GET    /api/accounts?includeMerged=false  # canonical/standalone (merged sources hidden)
POST   /api/accounts                       # manual create
PATCH  /api/accounts/:id                   # rename/hide/archive (import_ref preserved)
POST   /api/accounts/:id/merge {targetId}  # fold a source card into a canonical account

# Merchants / Categories / Category groups / Rules (per-entity controllers; repos not exposed)
GET/POST/PATCH         /api/merchants
GET/POST/PATCH         /api/categories
GET/POST/PATCH         /api/category-groups
GET/POST/PATCH         /api/rules
POST   /api/rules/:id/apply               # retroactive run over uncategorized

# Transactions  (visible rows only; newest first; paginated)
GET    /api/transactions?from=&to=&accountIds=&merchantIds=&categoryIds=&review=&page=&size=
POST   /api/transactions                  # manual single entry (source MANUAL; rules run if no category; never auto-matched)
PATCH  /api/transactions/:id              # categorize, link/create merchant, approve, exclude-from-budget
POST   /api/transactions/:id/split        # split into (amount, category) children
POST   /api/transactions/:id/unsplit      # undo a split
GET    /api/transactions/duplicates       # quarantined dups
POST   /api/transactions/:id/restore      # un-quarantine + re-run rules
POST   /api/transactions/:id/unmatch      # unlink a transfer/refund pair
GET/POST/PATCH/DELETE  /api/saved-filters # named shared filters

# Matching (transfers & refunds)
GET    /api/matches/suggestions           # medium-confidence pairs to review
POST   /api/matches/suggestions/:id/confirm
POST   /api/matches/suggestions/:id/dismiss
POST   /api/matches {aId,bId,type}        # manual pair
POST   /api/matches/scan                  # backfill existing transactions

# Auth (Google sign-in; active only with the "oauth" profile)
GET    /api/me                            # signed-in account (authenticated flag, email, name)
GET    /oauth2/authorization/google       # start sign-in
POST   /logout                            # end session

# Dashboard
GET    /api/dashboard/summary             # account groups, review count, budget alerts

# Budgets
GET    /api/budgets/:month/summary        # planned vs actual vs remaining, grouped
PUT    /api/budgets/:month                # set planned amounts (bulk upsert)

# Phase 2+ (planned): full-text search
```

---

## Backup (non-negotiable)

`prodrigestivill/postgres-backup-local` (or a plain `pg_dump` cron) writing to a NAS-mounted volume:

```yaml
backup:
  image: prodrigestivill/postgres-backup-local
  restart: always
  volumes:
    - /mnt/nas/finance-backups:/backups      # JoaoDrive or TerraMaster share
  environment:
    POSTGRES_HOST: db
    POSTGRES_DB: finance
    POSTGRES_USER: finance
    POSTGRES_PASSWORD: ${DB_PASSWORD}
    SCHEDULE: "@daily"
    BACKUP_KEEP_DAYS: "14"
    BACKUP_KEEP_WEEKS: "8"
    BACKUP_KEEP_MONTHS: "12"
```

Test a restore once before trusting it. An untested backup isn't a backup.

---

## Repo layout

```
finance/
├── README.md
├── docker-compose.yml                # api, db, web, cron, backup
├── .env.example
├── fixtures/                         # real SimpleFIN payloads + sample bank CSVs for tests
├── api/                              # Spring Boot 4.1
│   ├── pom.xml
│   └── src/main/java/ca/joaoborges/finance/
│       ├── account/
│       ├── transaction/             # incl. dedup, split, exclusion logic
│       ├── ingest/                  # shared pipeline: dedup → rules → review
│       ├── category/
│       ├── budget/
│       ├── rule/                    # match + retroactive apply
│       ├── simplefin/               # client, sync job, payload mapping
│       ├── csv/                     # per-format parsers (Simple/Amex/RBC/PC) → ParsedTransaction
│       ├── webhook/                 # dispatcher + discord formatter
│       ├── match/                   # transfer/refund matching: service, suggestions, controller
│       ├── auth/                    # Google sign-in: allowlist user service + /api/me
│       └── common/                  # shared query predicates (the flag table!)
│   └── src/main/resources/
│       └── db/changelog/            # Liquibase changelogs (schema source of truth)
└── web/                             # React + Vite + TS
    └── src/
        ├── pages/  (Accounts, Transactions, Budget, Rules, Review, Duplicates, Import)
        └── lib/api.ts
```

---

## Build order

1. **Capture real SimpleFIN payloads** into `fixtures/` (coverage already confirmed). Grab a few bank CSV exports too. Build everything downstream against these.
2. Spring Boot skeleton + Postgres + Liquibase changelog for the schema above. Docker compose up.
3. SimpleFIN client + sync job + the shared ingest pipeline (dedup → rules → review). Run against real payloads in `fixtures/`. This is the spine — get dedup right here, with tests, before building UI on top.
4. Transactions list + categorize action (the shared flag predicate lives here).
5. Categories + groups CRUD.
6. Rules engine + retroactive apply.
7. Budget page + summary math.
8. Splits + exclude-from-budget.
9. Duplicates UI + restore.
10. **CSV import** — per-format parsers feeding the same pipeline from step 3. Test the cross-source dedup case explicitly: import a CSV overlapping data already synced from SimpleFIN, assert the overlap quarantines rather than duplicates.
11. Webhooks + Discord formatter.
12. Backup service + restore test.

---

## Open decisions

1. ~~**Multi-user?**~~ **Resolved:** Google sign-in with an email allowlist (Joao + Amanda). No `owner` column or per-user data partitioning — it's a shared household view, gated by who can log in. See the Authentication section.
2. **Currency.** Amex sometimes posts USD. Phase 1 can assume CAD and store a per-transaction currency for later, or handle conversion now. Cheapest: store it, ignore it, revisit.
3. **Rule match scope.** Match on raw `merchant` only, or also a normalized form (strip trailing store/order numbers)? Normalizing makes *contains* rules even more robust — worth a small util.
4. **Large-transaction threshold.** One global number, or per-account? Start global.

---

## Status

🟢 Unblocked. SimpleFIN coverage confirmed (RBC, Amex CA, PC Financial, Wealthsimple). Next: capture real payloads into `fixtures/`, then scaffold the Spring Boot skeleton + first migration.
