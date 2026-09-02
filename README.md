# Foreigner Warsaw

A guided-eligibility and case-tracking web application for foreigners living in or
moving to Warsaw, Poland — architected so additional Polish cities (and eventually other
countries) can be enabled later without rewriting core logic.

**Status: Phase 3 complete** (authentication + user management, plus public reference/
geographic data — countries, EU/EEA/EFTA/Schengen classification, Polish regions, Warsaw
districts, authorities and offices). Immigration procedures, the questionnaire engine,
and every other business feature arrive in later phases — see
[docs/product/IMPLEMENTATION_PLAN.md](docs/product/IMPLEMENTATION_PLAN.md).

## Prerequisites

- Java 25
- Node.js (current LTS or newer)
- Docker (Desktop or Engine + Compose v2)
- Git

## First-time setup

```bash
cp .env.example .env
docker compose up -d
```

Then, in separate terminals:

```bash
# Backend
cd backend
export SPRING_PROFILES_ACTIVE=local   # PowerShell: $env:SPRING_PROFILES_ACTIVE = "local"
./mvnw spring-boot:run
```

```bash
# Frontend
cd frontend
npm install
npm start
```

Open http://localhost:4200 — the home page confirms it can reach the backend. See
**[docs/development/LOCAL_SETUP.md](docs/development/LOCAL_SETUP.md)** for the full
walkthrough, including a real environment gotcha this project's own setup hit
(pre-existing `DB_USERNAME`/`DB_PASSWORD` OS environment variables shadowing the local
defaults).

## Tests and builds

```bash
# Backend: unit + Testcontainers-backed PostgreSQL integration tests + format check
cd backend && ./mvnw verify

# Frontend: lint, unit tests, production build
cd frontend && npm run lint && npm test -- --no-watch && npm run build

# End-to-end (Playwright) — backend + frontend should already be running
cd frontend && npm run e2e
```

## Try it

Register at http://localhost:4200/register, grab the verification link from Mailpit
(http://localhost:8025), sign in, land on `/dashboard`. See
[docs/development/LOCAL_SETUP.md](docs/development/LOCAL_SETUP.md#trying-the-auth-flow-locally)
for the full walkthrough.

## Health / diagnostics

- `GET /actuator/health` — infrastructure health check (Docker/uptime monitoring).
- `GET /api/v1/platform/status` — what the frontend calls to prove "API connected";
  returns `{ status, application, version }`, nothing sensitive.
- `GET /swagger-ui.html` — API docs (local/staging only; disabled in production).
- `GET /api/v1/reference/{countries,authorities,offices}` (+ nested regions/cities/
  districts) — public, read-only reference data; see
  [docs/reference/REFERENCE_DATA_SOURCES.md](docs/reference/REFERENCE_DATA_SOURCES.md).
  http://localhost:4200/reference-demo exercises all of it through real Angular
  components (country picker, region→city→district cascade) — a Phase 3 verification
  page, not a product route.

## Database migrations

Every schema change is a Flyway migration under
`backend/src/main/resources/db/migration/` — never hand-edited, never
`ddl-auto=create`/`update` outside a justified test. See
[ADR-002](docs/architecture/ADR/ADR-002-postgresql.md).

## Start here (documentation)

- [Product Requirements](docs/product/PRODUCT_REQUIREMENTS.md) — problem, scope, MVP
- [Procedure Catalogue](docs/product/PROCEDURE_CATALOGUE.md) — researched immigration/
  administrative procedures, jurisdiction tags, MVP set, sourcing status
- [Assessment Decision Tree](docs/product/ASSESSMENT_DECISION_TREE.md) — the guided
  questionnaire's full question/branching design
- [Implementation Plan](docs/product/IMPLEMENTATION_PLAN.md) — the full phase-by-phase
  task breakdown
- [Architecture](docs/architecture/ARCHITECTURE.md) — modular monolith design, rules
  engine, API, versioning, jurisdiction model
- [Database Design](docs/database/DATABASE.md) — the authoritative schema: every
  entity, the Active-Version Predicate, provenance chains, ER diagrams
- [Architecture Decision Records](docs/architecture/ADR/) — why each foundational
  choice was made
- [Local Setup](docs/development/LOCAL_SETUP.md) — exact from-zero developer commands

## Stack

Java 25 · Spring Boot 4.1.x · PostgreSQL 18 · Flyway — Angular 22 · Angular Material —
Docker Compose for local dev. See [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
for the full list and rationale.

## Repository layout

```
backend/    Spring Boot application (Java 25, Maven, package-by-feature)
frontend/   Angular application (standalone components)
infra/      Docker Compose and production deployment assets
docs/       product, architecture, database, API, and per-procedure documentation
scripts/    developer tooling
```

## Important principle

Legal/administrative content (which permit applies, what documents are required, fees,
thresholds, offices) is modeled as versioned, sourced database data — never hard-coded
in application code. See [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) §7–§8 and
[ADR-003](docs/architecture/ADR/ADR-003-rules-engine.md) /
[ADR-004](docs/architecture/ADR/ADR-004-versioned-legal-content.md).

This application provides informational guidance based on official public sources. It
is not a government authority and does not provide legal advice.
