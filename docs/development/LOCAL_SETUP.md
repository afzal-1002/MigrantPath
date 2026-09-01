# Local Development Setup

Exact commands to get from a fresh clone to a running application. If any step here
stops working, this document is out of date - fix it, don't route around it silently.

## Prerequisites

- **Java 25** (Temurin recommended). Verify with `java -version`.
- **Node.js** current LTS or newer (Angular 22 requires it - `node -v`).
- **Docker Desktop** (or another Docker Engine + Compose v2).
- **Git**.

Maven is *not* a prerequisite - the committed Maven Wrapper (`backend/mvnw`,
`backend/mvnw.cmd`) downloads the exact project-pinned Maven version on first run.

### If your machine doesn't have Java 25 yet

Don't let the project silently target an older Java version instead. Either install a
system-wide Temurin 25 JDK, or download a self-contained build and point `JAVA_HOME` at
it just for this project:

```bash
# Example: a portable install under your user profile, no admin rights needed
curl -L -o temurin25.zip "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"
# extract, then per-shell:
export JAVA_HOME="/path/to/jdk-25.x.x"
export PATH="$JAVA_HOME/bin:$PATH"
```

## First-time setup

```bash
cp .env.example .env
docker compose up -d
```

This starts PostgreSQL 18 and Mailpit (a local SMTP catcher with a web UI at
http://localhost:8025). Confirm both are healthy:

```bash
docker compose ps
```

### ⚠️ A real gotcha this project's own setup hit

Some machines already have OS-level environment variables named `DB_USERNAME` /
`DB_PASSWORD` (or similar) set globally by an unrelated tool. Since the backend's local
profile reads `${DB_USERNAME:foreigner_warsaw}`-style placeholders, an **existing**
environment variable always wins over the YAML default - it is not a bug in the
project's config, but it silently breaks the "just works" local setup with a confusing
`FATAL: password authentication failed` error. If you hit that:

```bash
# Check first:
echo $DB_USERNAME  # or, on Windows PowerShell: $env:DB_USERNAME

# If it's set to something unexpected, override it explicitly for this shell before
# starting the backend:
export DB_USERNAME=foreigner_warsaw
export DB_PASSWORD=foreigner_warsaw_local_dev_only
```

Also note: **`.env` is read automatically by `docker compose` only.** Spring Boot does
not load `.env` files on its own when you run the backend natively (`mvnw
spring-boot:run`) - the values in `.env` matter for the Postgres/Mailpit containers;
for the backend process itself, either export the same values into your shell first,
or rely on the `local` profile's built-in fallback defaults (which match `.env.example`
exactly, so as long as no conflicting OS-level variable exists, no export is needed).

## Backend

```bash
cd backend
./mvnw spring-boot:run
# Windows Command Prompt / PowerShell: .\mvnw.cmd spring-boot:run
```

Runs with the `local` Spring profile by default is NOT automatic - set it explicitly if
your IDE/shell doesn't already:

```bash
export SPRING_PROFILES_ACTIVE=local   # PowerShell: $env:SPRING_PROFILES_ACTIVE = "local"
```

Verify it started:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/platform/status
```

Swagger UI (local/staging only, disabled in production): http://localhost:8080/swagger-ui.html

### Backend tests

```bash
cd backend
./mvnw verify
```

Runs unit tests, the Testcontainers-backed PostgreSQL integration test (needs Docker
running - it starts its own disposable Postgres container, independent of the
`docker compose` one above), and the Spotless format check. If Spotless fails on
formatting (not logic), auto-fix with:

```bash
./mvnw spotless:apply
```

> **Known Windows quirk**: `./mvnw clean` occasionally fails with a file-lock error
> (`Failed to delete ... target\...`) right after a previous build, most likely an
> antivirus/indexer transiently holding a handle on the just-written `target/`
> directory. If this happens, just remove `backend/target` manually and re-run - it is
> not a code or configuration problem.

## Frontend

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200 - the home page checks connectivity to the backend
(`http://localhost:8080` in the `development` environment config) and shows either
"API connected" or "API not reachable."

### Frontend tests, lint, build

```bash
cd frontend
npm run lint
npm test -- --no-watch
npm run build
```

### End-to-end tests (Playwright)

```bash
cd frontend
npx playwright install chromium   # first time only
npm run e2e
```

Playwright starts the Angular dev server itself. For the connectivity test to actually
exercise the backend (rather than skip), the backend must already be running
separately (see above) - CI starts both explicitly (`.github/workflows/ci.yml`).

## Database migrations

**Every schema change goes through a Flyway migration file** in
`backend/src/main/resources/db/migration/` - never hand-edit a shared database, and
never use `spring.jpa.hibernate.ddl-auto=create`/`update` outside a very specifically
justified test (see docs/architecture/ADR/ADR-002-postgresql.md). Phase 1 ships with no
domain migrations yet - the schema is intentionally empty until Phase 2 introduces the
first real tables.

## Project structure

See [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §1 and §3 for the full module
layout. In short: `backend/` is a Spring Boot modular monolith (package-by-feature),
`frontend/` is a standalone-component Angular app (`core`/`shared`/`layout`/`features`),
`infra/` holds Docker/deployment assets, and `docs/` holds everything else, starting
with [PRODUCT_REQUIREMENTS.md](../product/PRODUCT_REQUIREMENTS.md).
