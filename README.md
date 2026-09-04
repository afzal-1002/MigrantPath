# Foreigner Warsaw

A guided-eligibility and case-tracking web application for foreigners living in or
moving to Warsaw, Poland — architected so additional Polish cities (and eventually other
countries) can be enabled later without rewriting core logic.

**Status: canonical roadmap complete through Phase 15 (Release Candidate / Launch
Readiness).** The full guided-eligibility product flow works end to end —
registration, the guided questionnaire, the deterministic rules engine, personalized
recommendations, and case tracking with a checklist — for five real, sourced Warsaw
procedures, backed by versioned legal content managed through a real admin governance
workflow (draft → review → approve → publish). Production deployment (Docker Compose,
reverse proxy, backups, CI/CD), security/privacy hardening (GDPR export/deletion,
CSRF, CSP, role-based admin), and full operational observability (structured logging,
Prometheus metrics, health/readiness, an alert catalogue) are all real and verified —
see [docs/product/PROJECT_STATUS.md](docs/product/PROJECT_STATUS.md) for the
authoritative current summary and
[docs/releases/FINAL_GO_NO_GO.md](docs/releases/FINAL_GO_NO_GO.md) for exactly what
remains an external (hosting/domain/legal-review) blocker to a real public launch. See
[docs/product/IMPLEMENTATION_PLAN.md](docs/product/IMPLEMENTATION_PLAN.md) for the full
phase-by-phase history.

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
- `GET /api/v1/procedures` / `GET /api/v1/procedures/{code}` — public, read-only
  procedure content, resolved through the Active-Version Predicate (only currently
  published content is ever returned). http://localhost:4200/procedures is the Angular
  "Browse procedures" page. Content management (draft → review → approve → publish) is
  behind the internal `/api/v1/internal/content/**` API and the real Admin UI at
  http://localhost:4200/admin, both gated to CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN roles.

## Production deployment

Real, tested production Dockerfiles and a Compose stack exist under `backend/`,
`frontend/`, and `infra/` — same-origin reverse-proxy topology (nginx → static Angular
build + `/api`/`/actuator` proxy → Spring Boot; no microservices, no Kubernetes). See
[docs/operations/DEPLOYMENT.md](docs/operations/DEPLOYMENT.md) for the real, step-by-step
process and [ADR-013](docs/architecture/ADR/ADR-013-production-deployment-architecture.md)
for why it's shaped this way. Never deploy to production without going through
[docs/releases/PRODUCTION_RELEASE_CHECKLIST.md](docs/releases/PRODUCTION_RELEASE_CHECKLIST.md).

Further operational reference: environments
([ENVIRONMENTS.md](docs/operations/ENVIRONMENTS.md)), backup/restore
([DATABASE_BACKUP.md](docs/operations/DATABASE_BACKUP.md),
[DATABASE_RESTORE.md](docs/operations/DATABASE_RESTORE.md)), disaster recovery and
incident response
([DISASTER_RECOVERY.md](docs/operations/DISASTER_RECOVERY.md),
[INCIDENT_RESPONSE.md](docs/operations/INCIDENT_RESPONSE.md)), observability
(structured logging, metrics, alerting, dashboards —
[OBSERVABILITY.md](docs/operations/OBSERVABILITY.md),
[METRICS.md](docs/operations/METRICS.md),
[DIAGNOSTICS.md](docs/operations/DIAGNOSTICS.md)), the release process
([RELEASE_PROCESS.md](docs/releases/RELEASE_PROCESS.md)), and the security review
([PRODUCTION_SECURITY.md](docs/security/PRODUCTION_SECURITY.md)).

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
