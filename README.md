# finance-tracker

[![CI](https://github.com/joao-borges/finance-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/joao-borges/finance-tracker/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-%E2%98%95-ffdd00)](https://buymeacoffee.com/joaoborges)

A self-hosted personal finance tracker. Auto-syncs transactions from
[SimpleFIN](https://beta-bridge.simplefin.org/), categorizes them with a
merchant-match rules engine, matches transfers/refunds, and tracks monthly
budgets. CSV import is a first-class fallback source feeding the same pipeline
(dedup → rules → review), so an account can ingest from a bank export and
SimpleFIN simultaneously without duplication.

**Stack:** Java 25 · Spring Boot 4.1 · Spring Data JPA (Hibernate) · Liquibase ·
PostgreSQL 18 · React 19 + Vite + TypeScript · Docker.

> **Disclaimer.** This is personal software, shared as-is under the
> [MIT license](LICENSE). You deploy it yourself, you connect it to your own
> financial accounts, and **you are solely responsible for your deployment and
> your data** — including securing the host, the database, the SimpleFIN access
> URL, and any backups. The authors accept no responsibility for what you do
> with this software or for anything that happens to data you feed it.

**[DESIGN.md](DESIGN.md)** is the design source of truth (data model, the
flag-semantics table, dedup tiers, matching); **[CLAUDE.md](CLAUDE.md)**
documents the code conventions.

## Layout

The Spring Boot app lives at the root; `web/` is the React UI.

```
finance-tracker/
├── pom.xml · mvnw · .mvn/      # Maven build (also builds + bakes in the UI)
├── Dockerfile                  # single container: UI + API
├── docker-compose.yml          # dev: Postgres (+ optional all-in-one `app`)
├── docker-compose.prod.yml     # prod: app on 127.0.0.1:8080 + internal Postgres
├── .env.example                # copy to .env (never commit the real one)
├── fixtures/                   # anonymized sample CSVs (for tests)
├── src/main/java/ca/joaoborges/finance/{account,category,transaction,budget,
│       rule,csv,ingest,simplefin,match,merchant,filter,dashboard,seed,auth,
│       webhook,common,config}
├── src/main/resources/db/changelog/   # Liquibase schema (source of truth)
└── web/                        # React + Vite + TS UI (pages, components, lib)
```

## Running locally (development)

Needs **JDK 25** and **Node 24** on the host. Only Postgres runs in Docker.

```bash
docker compose up -d db                     # Postgres only

./mvnw spring-boot:run                      # API on :8080 (or run FinanceApplication in the IDE)
cd web && npm install && npm run dev        # UI on :3000 with HMR
```

- UI (Vite dev server): http://localhost:3000 — `/api/*` is proxied to :8080
- API: http://localhost:8080 — health at `/actuator/health`, smoke test at `/api/ping`
- Sign-in is **disabled** in local dev (no `oauth` profile) — usable immediately

`spring-boot:run` / IDE runs skip the frontend build entirely — fast inner loop.

## Deploying (production)

`mvn package` builds the React app into the jar's `static/`, so one artifact
serves UI + API together on :8080 — one jar, one container:

```bash
cp .env.example .env && chmod 600 .env      # fill in secrets
docker compose -f docker-compose.prod.yml up -d --build
```

The app listens on `127.0.0.1:8080`; put your own reverse proxy (Caddy, nginx,
a Cloudflare tunnel…) in front for TLS and a public hostname. Postgres stays
internal to the compose network. Back up with a scheduled
`docker exec <db> pg_dump`. Liquibase migrates the schema on startup;
Hibernate is `validate`-only, so entity/schema drift fails fast at boot.

`scripts/deploy.sh` is a convenience rsync+rebuild for a remote docker host:
`SERVER=user@host ./scripts/deploy.sh`.

### Environment reference (`.env`)

| Variable | Purpose |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database (password required) |
| `SPRING_PROFILES_ACTIVE` | `oauth` enables Google sign-in (recommended for anything exposed) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Your Google OAuth client (required with the `oauth` profile) |
| `FINANCE_AUTH_ALLOWED_EMAILS` | Comma-separated Google emails allowed to sign in — **fail-closed**: an empty allowlist rejects everyone |
| `IMPORT_MIN_POSTED_DATE` | Optional hard floor (ISO date, UTC); transactions posted earlier are dropped by every ingest path |
| `DISCORD_WEBHOOK_URL` | Optional Discord webhook for import summaries, budget alerts, and SimpleFIN connection warnings |
| `FINANCE_SEED_FILE` | Optional path to a personal `seed-data.json` (category groups, categories, merchants, rules) seeded idempotently at startup — mount the file into the container; unset = no seeding |

## Google sign-in setup

1. In [Google Cloud Console](https://console.cloud.google.com/) create a
   project → **APIs & Services → Credentials → Create OAuth client ID**
   (type: *Web application*).
2. Add your redirect URI: `https://<your-domain>/login/oauth2/code/google`.
3. Put the client id/secret in `.env`, set `SPRING_PROFILES_ACTIVE=oauth`, and
   list every allowed Google account in `FINANCE_AUTH_ALLOWED_EMAILS`.

Anyone not on the allowlist is rejected at sign-in. There is no user-management
UI by design — the allowlist **is** the user list: add an email, restart, done.

## SimpleFIN setup

1. Create a SimpleFIN Bridge account (https://beta-bridge.simplefin.org/,
   ~US$15/yr) and connect your banks there.
2. In the app: **Imports → SimpleFIN → paste a setup token → Connect**. The
   token is exchanged once for a long-lived access URL stored only in the
   database — treat the database accordingly.
3. Syncs run daily at noon America/Vancouver by default
   (`finance.simplefin.sync-cron` / `finance.simplefin.sync-zone` to override)
   with a 7-day lookback. **Sync now** and a custom **date-range import** live
   on the same page. Bridge-side connection problems ("Auth required") are
   logged, counted on the import run, and pushed to Discord if configured.

## CSV import

**Imports → CSV** with a format picker. Shipped parsers: a generic
three-column format (`account,name,value`), Amex Canada, RBC, and PC Financial
activity exports. CSV rows flow through the exact same pipeline as SimpleFIN
(cutoff → dedup → rules → matching → review); dual content/statement hashing
means re-imports and CSV↔SimpleFIN overlaps quarantine (restorable from the
Duplicates page) instead of duplicating.

---

## Developer guide

### Adding a CSV parser

1. Write `csv/YourBankCsvParser.java`: a final class with a static
   `List<ParsedTransaction> parse(Reader reader)` — see `AmexCsvParser` for
   the idioms (header-based column access, sign normalization to
   **negative = outflow**, whitespace collapsing, a stable `accountName` that
   becomes the account's `import_ref`).
2. Add the constant to the backend `CsvFormat` enum and wire it into the
   switch in `CsvImportController.parse(...)`.
3. Mirror the constant in the `CsvFormat` type in `web/src/lib/api.ts` — the
   UI format picker picks it up from there.
4. Add a test to `BankCsvParsersTest` using a few **anonymized** lines of the
   real export — cover the date format, the sign convention, and quirks
   (BOM, blank lines, thousands separators).

Parsers only parse. All downstream behavior (dedup, rules, matching, review,
notifications) is shared in `ingest/` and must not be duplicated per format.

### Schema changes

Every schema change is a new numbered changelog under
`src/main/resources/db/changelog/changes/`, registered in
`db.changelog-master.xml`, paired with the matching entity change. Never let
Hibernate alter tables — `ddl-auto=validate` will fail the boot on drift, and
that's the point.

### Tests & build

```bash
./mvnw test                    # backend tests
cd web && npx tsc --noEmit     # UI typecheck (vite build alone does not type-check)
./mvnw clean package           # the full artifact: jar with UI baked in
```

CI runs all three on every PR and push to `main`.

### Conventions

`CLAUDE.md` has the code style (braces always, `final` by default, explicit
imports, DTOs via MapStruct, CSS Modules only, one React component per file)
and the two things easiest to get subtly wrong: the transaction **flag
semantics** and the **dedup tiers** — both specified in `DESIGN.md`. Read
those before touching `ingest/`, `match/`, or the budget math.

### Contributing

Fork-and-PR. `main` is protected: PRs require the owner's review and passing
CI. Never include real bank exports, SimpleFIN payloads, or personal data in
fixtures or tests — anonymize everything.

If this project is useful to you, you can
[buy me a coffee](https://buymeacoffee.com/joaoborges) ☕
