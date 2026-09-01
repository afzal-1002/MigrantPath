# Phase 1 Completion Report — Repository & Infrastructure

Date: 2026-09-01

## Files created

75 tracked files (full list in git). Highlights by area:

- **Backend** (`backend/`): Spring Boot 4.1.1 / Java 25 Maven project (wrapper
  committed), package-by-feature skeleton (`common.web`, `config`, `health`), 5
  profile-specific `application*.yml` files, `SecurityConfig`, `CorsProperties`,
  `GlobalExceptionHandler`/`ApiError`, `PlatformStatusController`, and the Testcontainers
  test foundation (`TestcontainersConfiguration`, `BackendApplicationTests`,
  `PlatformStatusControllerTest`).
- **Frontend** (`frontend/`): Angular 22.1.6 standalone workspace with Angular Material,
  angular-eslint, Playwright; `core/services/platform-status.service.ts`,
  `layout/shell/`, `features/home/`, `features/not-found/`, environment config
  (`environment.ts` / `environment.development.ts`), `e2e/home.spec.ts`.
- **Infra**: root `docker-compose.yml` (Postgres 18 + Mailpit), `.env.example`,
  `.gitignore`.
- **CI**: `.github/workflows/ci.yml` (backend, frontend, and E2E jobs).
- **Docs**: `docs/development/LOCAL_SETUP.md` (new); `README.md` and `CLAUDE.md`
  rewritten for Phase 1 reality.

## Versions

| | Project target | Locally installed / resolved |
|---|---|---|
| Java | 25 | **21.0.10 was the machine default** (JAVA_HOME); a project-local Temurin **25.0.4.1** was downloaded to `C:\Users\a877912\.jdks\` and used for every build in this report — no system change made |
| Spring Boot | 4.1.x | 4.1.1 (GA, confirmed on Maven Central) |
| Maven | wrapper-pinned | 3.9.16 (bootstrapped by `mvnw`, wrapper 3.3.4, `distributionType=only-script` — no system Maven needed or found) |
| Spring Framework | (transitive) | 7.0.9 |
| Flyway | (transitive) | 12.4.0 |
| PostgreSQL | 18 | 18.6 (Debian pgdg, in Docker) |
| Node.js | Angular-22-compatible LTS | 24.18.0 (machine default, no change needed) |
| npm | — | 11.16.0 |
| Angular | 22 | 22.1.6 (CLI, core, build) |
| Angular Material | 22 | 22.1.4 |
| angular-eslint | 22 | 22.2.0 |
| TypeScript | Angular-22-supported | ~6.0.2 (auto-selected by `ng new`) |
| Playwright | — | 1.62.1 (chromium browser installed) |
| Docker | — | 29.7.2 (Desktop had to be started manually — it was not running at session start) |
| Docker Compose | — | v5.3.1 |

## Commands verified (actually executed, not assumed)

```
./mvnw -v                              # Maven 3.9.16 / Java 25.0.4.1 confirmed
./mvnw verify                          # BUILD SUCCESS, 4/4 tests, Spotless check passing
./mvnw spotless:apply                  # used once to fix initial tab-vs-2-space formatting
./mvnw spring-boot:run                 # started against real Dockerized Postgres, ran twice
docker compose config                  # validated
docker compose up -d                   # postgres + mailpit, both reached "healthy"
docker compose ps                      # confirmed healthy
npm install / ng add @angular/material / ng add angular-eslint
npx ng lint                            # "All files pass linting"
npx ng test                            # BUILD SUCCESS, 4/4 tests (Vitest)
npx ng build                           # production bundle, 254 kB initial (budget: 500 kB warn / 1 MB error)
npx playwright install chromium
npx playwright test                    # 2/2 passed, run twice (once per backend restart)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/platform/status
curl -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/does-not-exist   # 403, confirmed
```

## Tests

| Suite | Result |
|---|---|
| Backend unit/context test (`BackendApplicationTests`) | 1/1 pass |
| Backend integration test (`PlatformStatusControllerTest`, real Postgres 18 via Testcontainers, full Spring Security filter chain) | 3/3 pass |
| Backend format check (Spotless) | pass |
| Frontend unit tests (Vitest) | 4/4 pass |
| Frontend lint (ESLint via angular-eslint) | pass |
| Frontend production build | pass (254 kB initial, well under budget) |
| Playwright E2E (chromium) | 2/2 pass, **including the real cross-origin connectivity assertion actually observed "API connected," not skipped** |

## Infrastructure

- **PostgreSQL**: running in Docker (`postgres:18`, actual version 18.6), UTF8
  encoding and `Etc/UTC` timezone by default (matches the documented UTC policy with no
  extra config needed), healthcheck passing.
- **Flyway**: executes successfully against the real database on every backend start;
  0 migrations applied, by design — Phase 1 intentionally ships no domain schema
  (IMPLEMENTATION_PLAN.md §1 / brief §7).
- **Backend↔database connectivity**: confirmed via live HikariCP + Hibernate logs and
  via the Testcontainers integration test.
- **Backend↔frontend connectivity**: confirmed twice — via `curl` against the running
  backend, and via a real headless-Chromium Playwright run hitting the Angular dev
  server, which itself calls the backend cross-origin (`localhost:4200` →
  `localhost:8080`), proving the CORS configuration actually works in a real browser,
  not just in theory.
- **Security baseline**: unmapped endpoints return `403` (denied by design, not merely
  "not found"); `/actuator/health` and `/api/v1/platform/status` are the only public
  GET endpoints, confirmed by test and by manual curl.

## Deviations from Phase 0 design (and why)

1. **Java 25 not present on this machine by default** (only 21 and 19 were). Per the
   explicit instruction not to silently downgrade, a project-local Temurin 25.0.4.1 was
   downloaded to a user-scoped directory rather than touching the system JDK — every
   build in this report ran against real Java 25. This is a machine gap, not a project
   spec change; documented in `docs/development/LOCAL_SETUP.md` with the fix.
2. **`spring-boot-starter-parent` version is `4.1.1`, not `4.1.1.RELEASE`.** Spring
   Initializr's UI displays the legacy `.RELEASE` suffix as a label, but the actual GA
   artifact on Maven Central for Boot 3+ has no suffix. Corrected in `pom.xml`; this is
   a coordinate fix, not a version change — still Spring Boot 4.1.1 GA.
3. **`@angular/animations` / `provideAnimationsAsync` deliberately not added.** Angular
   22 deprecated the entire animations package in favor of native `animate.enter` /
   `animate.leave` template bindings. Angular Material renders correctly without it.
   Documented in `app.config.ts`; native animations can be added later where a concrete
   UI need justifies them.
4. **`docker-compose.yml`'s Postgres volume mounts at `/var/lib/postgresql`, not
   `/var/lib/postgresql/data`.** The official `postgres:18+` images changed their
   expected mount point (pg_ctlcluster-style versioned subdirectories); the old path
   makes the container refuse to start. This is an upstream image behavior change,
   caught and fixed during verification, not a deviation from our own design intent.
5. **A few Spring Boot 4.1-internal reorganizations** were adapted to, not designed
   around: test-scope starters are now split per feature (e.g.
   `spring-boot-starter-webmvc-test`), and `@AutoConfigureMockMvc` moved to
   `org.springframework.boot.webmvc.test.autoconfigure`. Also,
   `org.testcontainers.postgresql.PostgreSQLContainer` (the module Initializr now wires
   up) is non-generic, unlike the classic `org.testcontainers.containers.PostgreSQLContainer<SELF>`.
   None of these change the target versions — they're just how Boot 4.1/Testcontainers
   are actually shaped today, discovered by actually compiling against them rather than
   assumed from memory.
6. **Angular's default unit-test runner is Vitest**, not Karma/Jasmine — an
   Angular-CLI-22 default, kept as-is per the instruction to use current official
   tooling rather than a remembered older default.

None of these required deviating from the Phase 0-approved target stack (Java 25 /
Spring Boot 4.1.x / PostgreSQL 18 / Angular 22) — every one of them was a same-target
implementation detail.

## Known issues

1. **This specific machine has pre-existing OS-level `DB_USERNAME=root` /
   `DB_PASSWORD=root` environment variables** (unrelated to this project — likely left
   by another tool). Since these are real environment variables, they silently
   override the backend's `${DB_USERNAME:foreigner_warsaw}`-style local-profile
   defaults, producing a confusing Postgres auth failure the first time the backend is
   run natively. Not a defect in the repository; documented prominently in
   `docs/development/LOCAL_SETUP.md` with the exact override commands, since anyone
   developing on this machine will hit it too.
2. **`.github/workflows/ci.yml` has not been executed on a real GitHub Actions
   runner** — there is no GitHub remote connected in this environment. Every step in it
   was verified locally with the equivalent command (see "Commands verified" above),
   but the workflow file itself should be treated as unverified-in-CI until the first
   real PR.
3. **Occasional Windows file-lock on `mvnw clean`** (`Failed to delete
   ...target\...`), most likely a transient antivirus/indexer handle on a just-written
   file. Workaround (manual `rm -rf backend/target`) documented; not a code issue, and
   did not affect any actual build or test result once worked around.
4. **No git commits exist yet.** `git init` was run (needed for `.gitignore`/CI to mean
   anything), but nothing has been committed — commits weren't part of this request,
   and committing is treated as an explicit-ask action. All 75 files are staged-ready
   (`git add -A` was dry-run tested, not executed for real).

## Phase 2 readiness

**READY.**

Every Phase 1 acceptance criterion (brief §33) was checked against a real, executed
command rather than assumed:
repository structure, backend/frontend existence and correct versions, PostgreSQL 18
running, Flyway executing, backend↔database connectivity, backend tests (unit +
Testcontainers integration), frontend build/lint/tests, Playwright baseline, local CORS
(proven in a real browser), health endpoints, frontend↔backend connectivity,
`.env.example`, no committed secrets, CI workflow present, and updated
README/LOCAL_SETUP documentation. Nothing here blocks starting Phase 2 (Authentication
+ Users).
