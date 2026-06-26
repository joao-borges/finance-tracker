# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A self-hosted personal finance tracker. Auto-syncs transactions from **SimpleFIN**, categorizes them with a merchant-match rules engine, and tracks monthly budgets. CSV import is a fallback source feeding the *same* ingest pipeline (dedup → rules → review).

**`DESIGN.md` is the design source of truth** — scope, data model, the flag-semantics table, dedup tiers, and build order all live there. Read it before making design decisions; this file only covers *how* to write code in the repo.

Two things from `DESIGN.md` are load-bearing and easy to get subtly wrong:

- **The flag-semantics table** (`DESIGN.md`, "The flag semantics" section). `is_split` / `split_parent_id` / `excluded_from_budget` / `is_dedup` / `needs_review` behave differently in the two places that matter — the transaction list and the budget sum. Implement them as **shared query predicates** in `common/`, never as ad-hoc `if`s scattered around.
- **Dedup is two-tier and soft.** SimpleFIN `id + posted_ts` → silent skip; no-id/CSV content hash → *quarantine* (`is_dedup = true`, restorable), never auto-drop. Same-day identical legitimate charges (two ABC Fitness $31.49) must survive.

## Technology Stack

- Java 25, Spring Boot 4.1 (Web, Validation)
- **Lombok** (`@Getter`/`@Setter`/`@NoArgsConstructor`, etc.) — used throughout. Declared as a `maven-compiler-plugin` `annotationProcessorPath` because JDK 23+ no longer runs classpath-only processors; excluded from the fat jar.
- Spring Data JPA / Hibernate (provider) — **`spring.jpa.hibernate.ddl-auto=validate`**
- **Liquibase** owns the schema (changelogs in `src/main/resources/db/changelog/`); Hibernate never alters tables
- PostgreSQL
- React + Vite + TypeScript (frontend in `web/`)
- Docker for Postgres; the app builds to one self-contained jar (UI baked in) / single container
- `pg_dump` → NAS for backups

## Build and Run

Local dev runs **on the host** (IDE-friendly); only Postgres is containerized.
Needs JDK 25 + Node 24.

```bash
docker compose up -d db                 # Postgres only

./mvnw spring-boot:run                  # API on :8080 (or run FinanceApplication in the IDE)
cd web && npm install && npm run dev    # UI on :3000, HMR; /api proxied to :8080

./mvnw clean package                    # one self-contained jar (UI baked into static/)
docker compose --profile full up --build  # prod-like single container (UI + API) + Postgres
```

The frontend is built into the jar by `frontend-maven-plugin` +
`maven-resources-plugin`, bound to **`prepare-package`** so `spring-boot:run` and
IDE runs stay fast and node-free — only `mvn package` builds the UI (skip it with
`-Dskip.frontend=true`). The production app therefore serves the SPA and the REST
API from a single process on :8080; in dev the Vite server on :3000 proxies
`/api` to the backend.

**Import floor.** `finance.import.min-posted-date` (`IMPORT_MIN_POSTED_DATE` env, ISO date, UTC) is a hard floor enforced by `common/ImportCutoff` on every ingest path (CSV + SimpleFIN) — transactions posted earlier are dropped before persistence. Blank = no floor.

**Bootstrap.** `scripts/initial-setup.sh` is an idempotent, re-runnable bootstrap that drives a *running* instance over its REST API: optional SimpleFIN connect+sync, PC Financial CSV import, account shaping (rename/type/logo/merge/hide), and the initial monthly budget. Needs `curl` + `jq`.

## Architecture

The Spring Boot app is the project root; `web/` holds the React UI. Java package root: `src/main/java/ca/joaoborges/finance/`. Packages mirror the domain (see `DESIGN.md` repo layout):

- **`account/`** — accounts + balances pulled from the bank, displayed as-is (no reconciliation).
- **`transaction/`** — transaction model + split, exclusion, and dedup logic.
- **`ingest/`** — the shared pipeline (dedup → rules → review) that both SimpleFIN and CSV feed.
- **`category/`** — categories + category groups.
- **`budget/`** — monthly budgets + planned-vs-actual summary math.
- **`rule/`** — merchant-match evaluation + retroactive apply.
- **`simplefin/`** — client, sync job, payload mapping.
- **`csv/`** — per-format parsers (`SimpleCsvParser`, `AmexCsvParser`, `RbcCsvParser`, `PcFinancialCsvParser`) → `ParsedTransaction`; `CsvImportController`.
- **`filter/`** — `SavedFilter` (named shared transactions filters).
- **`webhook/`** — `DiscordNotifier` (Discord-only, fire-and-forget; fires after each import).
- **`merchant/`** — canonical merchants + favicon (logo) resolution.
- **`dashboard/`** — landing-page summary (account groups, review count, budget alerts).
- **`match/`** — transfer/refund matching (`MatchingService`): pairs legs (`matched_with_id`/`match_type`), auto-applies high-confidence, proposes `MatchSuggestion`s; runs in the ingest pipeline after rules. See DESIGN.md "Matching".
- **`seed/`** — `DataSeeder` (an idempotent `ApplicationRunner`) that ensures the curated category groups, categories, and rules in `resources/seed/seed-data.json` (parsed from the operator's Monarch export) exist at startup.
- **`auth/`** — Google sign-in (OIDC). `AllowlistOidcUserService` enforces the `finance.auth.allowed-emails` allowlist (fail-closed); `MeController` exposes `GET /api/me`. The security chain is `config/SecurityConfig`, gated by the `oauth` profile — no profile ⇒ auth off (local dev). See DESIGN.md "Authentication".
- **`common/`** — **shared query predicates (the flag table)** and other cross-cutting helpers.

Keep controllers thin (HTTP boundary, `@Valid` DTOs); push behavior into the domain packages. CSV and SimpleFIN must share the ingest pipeline — CSV is a second *source*, not a second system.

## API & data-access conventions (project rules)

- **Never expose repositories over HTTP.** Every entity has its own Spring Data repository annotated `@RepositoryRestResource(exported = false)`, and `spring.data.rest.detection-strategy=annotated` so nothing is auto-exposed. All HTTP access goes through a thin `@RestController` per entity exposing exactly what's needed (typically list `GET`, create `POST`, update `PATCH`).
- **Controllers speak DTOs, never entities.** DTOs are `record`s with `@Builder`. Entity↔DTO mapping is **MapStruct** — one `@Mapper(config = MapStructConfig.class)` per entity (`toDto` flattens relations to ids/names; `toEntity` builds via the Lombok builder; `update(@MappingTarget …)` with null-ignore gives PATCH semantics). Controllers still resolve relations (repo lookups) and derived fields (favicon). List/read endpoints that touch lazy associations are `@Transactional(readOnly = true)`.
- **Performance:** index `transactions` to real query shapes only (it's the one unbounded table). The Hibernate L2 cache is **JCache + EhCache 3**, regions created explicitly in `config/CacheConfiguration` (one per cacheable entity); entities opt in with `@Cache(usage = NONSTRICT_READ_WRITE, region = CacheRegions.X)` — only small read-heavy reference entities, never `Transaction`/`Budget`/`ImportRun`. Adding a cacheable entity means adding its region name to `common/CacheRegions` AND the create-list in `CacheConfiguration` (else boot fails fast). See DESIGN.md "Performance & scaling".
- **Dependency injection is constructor-based via Lombok.** Spring services and controllers declare their collaborators as `private final` fields and use `@RequiredArgsConstructor` — no field injection, no `@Autowired`.
- **Bind related query params into a filter record, not a long parameter list.** An endpoint with several optional filters takes one command object (e.g. `TransactionFilter` — a `record` Spring binds from query params) that owns its own logic (e.g. `toSpecification()`), rather than a method with many `@RequestParam`s.
- **Outbound HTTP uses the shared `RestTemplate` (Apache HttpClient 5).** One `RestTemplate` bean is configured in `config/RestClientConfig` (connect/response timeouts, content-compression disabled to avoid a brotli native-lib dep); inject it — don't `new` a JDK `HttpClient` per caller. Fire-and-forget callers (e.g. `DiscordNotifier`) wrap the call in `CompletableFuture.runAsync` so a slow endpoint never blocks a request/import thread.

## Persistence & migrations

- **Liquibase is the single source of truth for schema.** Every schema change is a changelog under `db/changelog/`; never let Hibernate alter tables (`ddl-auto=validate` fails fast on entity/schema drift).
- Entities and changelogs stay in lockstep — a mismatch should fail at startup, by design.

## Code Style

The entire codebase (Java and the TypeScript in `web/`) is **English-only** — identifiers, comments, log/UI strings. Conventions follow the operator's standard Java style, shared with the `bc-parks-monitor` and `file-manager` projects. Apply to new and edited code.

- **Braces always, multi-line — never a one-liner.** Every `if`/`else`/`for`/`while`/`try`/`catch`/method/lambda block is opening brace, newline, body line(s), newline, closing brace:
  ```java
  if (cond) {
      doThing();
  }
  ```
  No braceless control flow and no single-line braced bodies (`if (x) { y; }`, `T m() { return x; }` are both disallowed). Applies equally to the TS/TSX in `web/` — `if (!res.ok) throw …` and `if (x) { y; return }` are both banned; expand to a multi-line braced block. The one exception is a genuinely **empty** body, which stays `{}` (`record Foo(...) {}`, an empty best-effort `catch (Exception ignored) {}`). `} else {` / `} catch {` stay cuddled.
- **Explicit imports only — no star imports.** Import exactly the classes used; prefer import + simple name over an inline fully-qualified name (except when two classes share a simple name).
- **Blank lines for structure.** One blank line between members; one right after a top-level class's opening brace and one right before its closing brace.
- **`final` by default.** Locals and parameters are `final` unless reassigned — including catch parameters (`catch (final Exception ignored)`) and enhanced-for variables, but not classic mutated for-counters. Data/holder classes are `final` (prefer `record`s); method declarations are never `final`. In `web/` the equivalent is `const` by default — `let` only for a genuinely reassigned binding.
- **Indentation is 4 spaces, never tabs.**
- **Descriptive catch names.** Name the caught exception for what the handler does — `ignored`, `logged`, `wrapped`, `rethrown` — never a bare `e`/`ex`. Deliberate best-effort no-ops are `catch (Exception ignored) {}`; don't add a log just to "handle" them, and do log the cases that actually matter.
- **Prefer `record`** for immutable data-only types (DTOs), with `@Builder` where it reads better. JPA `@Entity` classes can't be records (Hibernate needs mutable beans) — write those as Lombok classes: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, with `@Builder.Default` on every field that has an initializer. Construct entities via their builder (don't rely on no-arg field-init, which `@Builder.Default` moves into the builder). Don't put `@Data`/`@EqualsAndHashCode`/`@ToString` on entities (they recurse through lazy associations). Use streams/lambdas where they read more clearly than the loop.
- **No dead declarations.** Remove unused imports; don't declare `throws X` a body can't actually throw.
- **No confirmation comments.** Don't add a comment whose only purpose is to note that one of these rules was followed.
- **Clean up what you touch.** If a change introduces a new warning (compiler, etc.), fix it in the same change — don't defer it. Does not extend to pre-existing issues you weren't asked to touch.
- **Frontend (`web/`):** function components + arrow callbacks. **One React component per file** — a file exports exactly one component (named after the file). Extract reusable pieces (rows, cards, filter bars, modals, avatars) into their own files under `components/` to keep page files small and focused; pages should orchestrate, not inline large JSX trees. Shared non-component helpers (formatters, etc.) live in `lib/`.
- **No inline CSS — CSS Modules only.** No `sx={{…}}` (MUI) and no `style={{…}}` (antd/DOM) style objects. Each component has a co-located `<Component>.module.css`; import it (`import styles from "./X.module.css"`) and reference classes via `className`. Non-style props (`color=`, `variant=`, `align=`, `size=`, antd numeric `width=`) are fine — they're not inline CSS. Cross-cutting classes (ellipsis, nowrap, secondary text, signed-money colors, icon rows, full-width inputs) live once in `styles/shared.module.css` and are reused — never duplicate them per component. **Never hardcode colors:** use the semantic theme variables in `index.css` (`--text-secondary`, `--error`, `--success`, `--warning`, `--border`, `--color-primary`), which `theme/AppTheme` flips for dark mode (via `data-theme` on `<html>`) and points at the selected accent. Convert conditional color logic to a conditional `className`, not a style object.

## Testing

The dedup logic in `ingest/` is the spine — get it under test against real SimpleFIN payloads in `fixtures/` before building UI on top. Explicitly test the cross-source dedup case: import a CSV overlapping data already synced from SimpleFIN and assert the overlap quarantines (`is_dedup = true`) rather than duplicates.
