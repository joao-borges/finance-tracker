# finance

A self-hosted personal finance tracker. Auto-syncs transactions from SimpleFIN,
categorizes them with merchant-match rules, and tracks monthly budgets.

See **[PLAN.md](PLAN.md)** for the full design (scope, data model, the
flag-semantics table, dedup tiers, build order) and **[CLAUDE.md](CLAUDE.md)**
for code-style conventions.

## Stack

- **API:** Java 25 · Spring Boot 4.1 · Spring Data JPA / Hibernate · Liquibase (XML changelogs) · PostgreSQL 18
- **Web:** React 19 · Vite 8 · TypeScript 6 (dev server on :3000; baked into the jar for a single deployable)
- **Infra:** Postgres in Docker; the app builds to one self-contained jar / single container

## Layout

The Spring Boot app lives at the root; `web/` is the React UI.

```
finance-tracker/
├── pom.xml · mvnw · .mvn/      # Maven build (also builds + bakes in the UI)
├── Dockerfile                  # single container: UI + API
├── docker-compose.yml          # Postgres (+ optional all-in-one `app`)
├── .env.example                # copy to .env
├── fixtures/                   # real SimpleFIN payloads + sample CSVs (for tests)
├── src/main/java/ca/joaoborges/finance/{account,category,transaction,
│       budget,rule,csv,webhook,ingest,simplefin,common}
├── src/main/resources/db/changelog/   # Liquibase schema (source of truth)
└── web/                        # React + Vite + TS UI
    └── src/{pages,lib}
```

## Local dev (run from your IDE — only Postgres in Docker)

Needs **JDK 25** and **Node 24** on the host.

```bash
cp .env.example .env
docker compose up -d db                     # Postgres only

./mvnw spring-boot:run                      # API on :8080 (or run FinanceApplication in the IDE)
cd web && npm install && npm run dev        # UI on :3000 with HMR / browser autoreload
```

- UI (Vite dev server): http://localhost:3000 — `/api/*` is proxied to :8080
- API: http://localhost:8080 — health at `/actuator/health`, smoke test at `/api/ping`
- Postgres: localhost:5432 (`finance` / `finance`)

The Vite dev server does **not** build the frontend into the jar, and
`spring-boot:run` / IDE runs skip the frontend build entirely — fast inner loop.

## Single deployable (UI baked into the jar)

`mvn package` builds the React app and bundles it into the jar's `static/`, so
the running app serves the UI and the API together on :8080 — one artifact, one
container.

```bash
# One self-contained jar:
./mvnw clean package                        # add -Dskip.frontend=true to skip the UI build
java -jar target/finance-0.1.0-SNAPSHOT.jar # serves UI + API on :8080

# Or the prod-like single container (UI + API), plus Postgres:
docker compose --profile full up --build
```

Liquibase runs the changelog on startup; Hibernate is `validate`-only, so a
mismatch between the JPA entities and the schema fails fast at boot.

## Tests

```bash
./mvnw test
cd web && npm run build        # type-checks via tsc, then builds
```

## Next steps (per PLAN.md build order)

1. Capture real SimpleFIN payloads into `fixtures/`.
2. Build the ingest spine (dedup → rules → review) against those fixtures.
3. Transactions list + the shared flag predicate (already stubbed in
   `common/TransactionPredicates`).
