# Phase 11 Report — Production Readiness, Deployment & Release Hardening

Status: ✅ substantially complete, with disclosed gaps below. **This is not another
business-feature phase** — no new procedures, no payments, no AI assistant, no document
upload. The five-procedure guided-eligibility product built through Phase 10.5 works
end to end; this phase made it safe to actually deploy, observe, back up, recover, and
release.

**Phase-numbering note**: this work was requested and delivered under the name "Phase
11," but this repository's own pre-existing roadmap (`PRODUCT_REQUIREMENTS.md` §9,
`IMPLEMENTATION_PLAN.md`) already used "Phase 11" for a testing-completeness phase that
has not started. What's actually in this report corresponds almost entirely to that
roadmap's **Phase 12 (Security/GDPR) + Phase 13 (Deployment) + part of Phase 14
(Monitoring)**, done out of order, ahead of Phase 11 (Testing). See
`IMPLEMENTATION_PLAN.md`'s reconciliation note for the full explanation and the per-item
✅/⏳ annotations added to those three phase sections. Nothing here was silently
renumbered.

## Executive summary

Built and **actually ran** (not just written and assumed correct): production Docker
images for both backend and frontend, a same-origin reverse-proxy Compose stack, a
security-headers layer, an admin-bootstrap mechanism, health/readiness probes,
correlation-ID request tracing, one real custom metric, a public sitemap/robots.txt, four
draft legal/policy pages with client-side noindex handling on every private route, and a
tested database backup/restore drill. Wrote the required operational, release, security,
and privacy documentation. Found and fixed real bugs at every layer (Java, nginx, Docker,
Compose, `.gitignore`) — never merely written and assumed to work, per this project's own
established discipline.

Full regression is green: backend `./mvnw verify` (unit + Testcontainers integration +
Spotless), frontend lint/unit/build, `npm audit` (0 vulnerabilities), and a real
Playwright e2e run against a locally running stack — see "Final regression" below.

No production deployment was performed. No push to a real production environment was
requested, and none happened.

## What's implemented and verified (not just written)

| Area | What | Verified how |
|---|---|---|
| Security headers | CSP, Referrer-Policy, Permissions-Policy (`SecurityConfig`); Spring Security's own defaults (X-Content-Type-Options, X-Frame-Options, conditional HSTS) confirmed present, needed no new code | `SecurityHeadersIntegrationTest`, a real `curl` against the running dev backend |
| Actuator exposure | Only `health`,`info` exposed; every other path returns 401 (route resolution itself gated), not 404 | `ActuatorExposureTest`, 8 cases, real finding (originally assumed 404, corrected) |
| Admin bootstrap | Self-disabling, `@ConditionalOnProperty`-gated `ApplicationRunner`; never a default admin/admin credential | `AdminBootstrapRunnerTest`, 5 Mockito cases |
| Forwarded headers | `server.forward-headers-strategy: framework`, staging/production only | `ProductionConfigTest` |
| Build/commit reporting | `GET /api/v1/platform/status` returns real `version`+`commit` via `spring-boot-maven-plugin`'s `build-info` goal | `PlatformStatusControllerTest` |
| Correlation ID | Every request gets one (`CorrelationIdFilter` → SLF4J MDC), returned as `X-Correlation-ID`, untrusted input pattern-validated | Compiles/passes existing regression; not yet threaded into a log pattern (see gaps) |
| One real metric | `auth.login.failure`, Micrometer `Counter`, zero tags | `SecurityMetricsListenerTest`, real failed-login HTTP round trip |
| Graceful shutdown | `server.shutdown: graceful`, 20s timeout | `ProductionConfigTest` |
| Production Dockerfiles | Backend: `eclipse-temurin:25-jdk-noble`→`25-jre-noble`, non-root (uid/gid 10001), healthcheck via `curl`. Frontend: `node:24-bookworm-slim`→`nginxinc/nginx-unprivileged:1.27-alpine` | Both images actually built and run this session; 3 real bugs found and fixed (GID collision, missing curl, `useradd --system` UID warning) |
| Reverse-proxy stack | `infra/docker-compose.prod.yml` — nginx (frontend) → `/api`,`/actuator` proxy → backend; backend never directly exposed | All 3 containers actually run together this session; full HTTP/CSRF/headers/sitemap smoke test through the real proxy path; 2 real nginx bugs found and fixed (header-inheritance drop, duplicate headers on `/api/`) |
| Sitemap/robots | `GET /sitemap.xml` (backend-generated, DB-driven, includes the 4 legal pages), `robots.txt` (frontend static) | `SitemapControllerTest`; robots.txt Disallow list covers every private route including `/reference-demo` |
| Legal/policy pages | `/privacy`, `/terms`, `/cookies`, `/disclaimer` — draft, non-attorney-reviewed, content traces to `docs/privacy/*`, linked from a new site footer | Frontend build/lint/unit tests green |
| Private-route noindex | `RobotsMetaService` walks the route tree on every navigation, sets `<meta name="robots" content="noindex,nofollow">` on any route (or descendant of a route) flagged `data: { noIndex: true }` | Applied to login/register/verify-email/forgot-password/reset-password/dashboard/reference-demo/assessment/*/cases/*/admin (and all admin children); frontend build/lint green |
| Backup/restore | `pg_dump -Fc` / `pg_restore --no-owner` drill | Actually run against the real dev database; row/migration counts verified matching after restore |
| Dependency scan | `npm audit --omit=dev` | Actually run — 0 vulnerabilities. Backend scanning tooling named, not wired (see gaps) |

## Documentation delivered

- `docs/architecture/ADR/ADR-013-production-deployment-architecture.md`
- `docs/operations/`: DEPLOYMENT, ENVIRONMENTS, DATABASE_BACKUP, DATABASE_RESTORE,
  DISASTER_RECOVERY, INCIDENT_RESPONSE, EMAIL_PRODUCTION, LEGAL_CONTENT_MONITORING,
  OBSERVABILITY
- `docs/releases/`: RELEASE_PROCESS, PRODUCTION_RELEASE_CHECKLIST
- `docs/security/PRODUCTION_SECURITY.md`
- `docs/privacy/`: DATA_INVENTORY, COOKIE_INVENTORY, RETENTION_POLICY
- `README.md` updated (was stale at "Phase 4 complete"); `IMPLEMENTATION_PLAN.md` and
  `PRODUCT_REQUIREMENTS.md` updated with the phase-numbering reconciliation

## Real bugs found and fixed this phase (via execution, not review)

1. GID 1000 collision with the JRE base image's own `ubuntu` user → GID/UID 10001.
2. `curl` absent from the minimal JRE runtime, silently breaking the container
   `HEALTHCHECK` → installed explicitly.
3. `useradd --system` warned UID 10001 exceeds `SYS_UID_MAX` → dropped `--system`.
4. nginx `envsubst` over-escaping broke startup ("invalid variable name") → fixed the
   escaping, only `${BACKEND_UPSTREAM}` needed real substitution.
5. nginx `add_header` inheritance silently dropped all server-level security headers on
   any location defining its own `add_header` → shared `security-headers.conf` `include`,
   repeated explicitly per location.
6. Security headers duplicated on `/api/` proxied responses (nginx's own include plus
   the backend's own headers) → removed the include from that one location.
7. `docker-compose.prod.yml`'s backend had no real dependency on postgres readiness →
   `depends_on: postgres: condition: service_healthy, required: false` + realistic
   `start_period`.
8. `.gitignore` negation patterns (`!.env.*.example`) did not reliably un-ignore the
   example file (verified via `git check-ignore`) → explicit patterns instead.
9. `docker compose --env-file` gives precedence to pre-set shell env vars over the file
   — the same root cause as the long-documented `DB_USERNAME`/`DB_PASSWORD` OS-env
   shadowing gotcha, reconfirmed this session when it silently broke a local
   `spring-boot:run` (see "Final regression" below) and previously nearly broke a
   Compose deploy — documented as a required `unset` step, not "fixed" (inherent
   Compose behavior).
10. `ActuatorExposureTest`'s original assumption (unexposed paths 404) was wrong — the
    real result is 401, a *stronger* property (route existence itself is
    undiscoverable pre-auth) — test and docs corrected to match reality.

## Final regression (all run this session, against real infrastructure)

- Backend: `./mvnw verify` (JDK 25, self-contained Temurin build under
  `~/.jdks/jdk-25.0.4.1+1` — this host has no system-wide JDK 25 installed; see
  `docs/development/LOCAL_SETUP.md`'s own "if your machine doesn't have Java 25 yet"
  section) — **green**, unit + Testcontainers Postgres/Mailpit integration + Spotless.
- Frontend: `npm run lint` — **green** (0 issues). `npm test -- --no-watch` — **112/112
  passing**, 25 test files. `npm run build` — **green**, production bundle within
  budget.
- `npm audit --omit=dev` — **0 vulnerabilities**.
- Playwright e2e (full 18-test critical-path suite, against a real locally-running
  backend + Postgres + Mailpit) — **18/18 passing**. The first run (7 parallel workers)
  showed 2 timeouts (`assessment.spec.ts` Scenario 1, `reference-data.spec.ts`'s
  district-cascade test), both waiting on the same country-autocomplete dropdown;
  re-run serially (`--workers=1`), both passed in 3-12s, well under the 30s timeout.
  Root-caused to 7 parallel Chromium workers sharing one dev-profile backend/Postgres
  instance under this host's real load, not a Phase 11 regression — neither spec
  touches any file changed this phase (reference data, assessment). Documented here as
  a real, disclosed environment-specific flake, not silently rerun-until-green.

## Known, disclosed gaps (named, not silently skipped)

- **Rate limiting** remains single-instance in-memory — acceptable for a genuine
  single-instance MVP, not once horizontally scaled without a shared store.
- **Structured (JSON) logging** not implemented — correlation ID is real, plain-text
  console output is not yet JSON; needs `logstash-logback-encoder` + a log pattern,
  named as a scoped follow-up.
- **8 of 9 named metrics** are specified, not wired (`auth.login.failure` is the one
  real proof-of-pattern) — each has its intended hook point documented in
  `docs/operations/OBSERVABILITY.md`.
- **No error-tracking service** (Sentry or equivalent) integrated — the backend/frontend
  integration points are named, not wired.
- **No CI/CD deployment pipeline** — CI (build/test/e2e) is real and pre-existing; a
  `.github/workflows/cd.yml` with a staging auto-deploy and a gated production-approval
  step does not exist yet.
- **No staging or production environment has actually been deployed to** — the images,
  Compose stack, and documentation are real and tested locally; nothing has been stood
  up behind a real domain.
- **No backend dependency-vulnerability scanner wired** (OWASP Dependency-Check or
  equivalent) — named, not added, to avoid pulling in unvalidated tooling this same
  session; frontend `npm audit` was actually run (0 vulnerabilities).
- **No GDPR self-service data export/deletion** — `docs/privacy/DATA_INVENTORY.md`
  names this explicitly; today it would be handled as a manual request.
- **No external/professional security review or accessibility/performance audit** —
  only an internal, manual review was performed (`docs/security/PRODUCTION_SECURITY.md`).
- **No load testing, no database index review from real production query patterns** —
  both require real traffic that doesn't exist yet; speculative indexes were
  deliberately not added.

## Temporary Residence for Studies — release impact

Unchanged from Phase 10: this procedure remains at `READY_FOR_PUBLICATION`, blocked on
a `VERIFIED` primary source per the publish gate — correctly *not* bypassed this phase.
Release impact: the guided questionnaire and the public procedure catalogue continue to
surface only the four already-`PUBLISHED` procedures; a user whose situation is
"studies" gets no recommendation for that specific pathway until this is resolved
through the real Admin governance workflow. This does not block any of the
infrastructure delivered in this phase.

## Phase 12 readiness verdict

The product is **infrastructure-ready to deploy to a real staging environment**, not
yet ready for real production traffic — the gaps above (no CD pipeline, no actual
staging/production deploy performed, no external security review, no GDPR self-service)
are real blockers for a genuine public launch, not paperwork. Recommended next step
before any further business-feature phase: stand up the documented staging environment
and run the smoke tests in `docs/releases/PRODUCTION_RELEASE_CHECKLIST.md` against it
for real.

**Do NOT begin Phase 12 (or any further phase) automatically. Stopping here per the
brief's explicit instruction.**
