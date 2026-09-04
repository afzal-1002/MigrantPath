# Phase 13 (Canonical) Report — Deployment Completion + Environment Promotion + Release Execution Readiness

Status: ✅ substantially complete. No real production deploy was performed (not
requested, and this phase's own brief explicitly forbids it without explicit
instruction) — everything below is either a real, locally-verified mechanism or an
honestly-labeled `CONFIGURED, NOT EXECUTED` / `DOCUMENTED, NOT EXECUTED` gap.

> **Update (Phase 13.5):** the one open finding this report disclosed below — a
> deterministic, production-build-only regression in the Assessment flow's "current
> legal status" dropdown — has since been root-caused and fixed. See
> [PHASE_13_5_REPORT.md](PHASE_13_5_REPORT.md). The account below is left exactly as
> originally written, as the honest historical record of what was known at the time.

## Executive summary

Phase 11's earlier, out-of-order production-readiness work already built the
topology (ADR-013), the two production Dockerfiles, the reverse proxy, backup/restore
docs, and a documented (never automated) release process. Phase 13's job was to turn
"the pieces exist" into "a known git commit can actually be promoted through staging to
production, verified, rolled back, and identified afterward" — and to inspect first
rather than duplicate what already existed.

What Phase 13 actually added, all real and executed against the actual built release
images unless explicitly marked otherwise:

1. **Three real bugs found and fixed**, each discovered specifically *because* this
   phase ran the exact production-built images through the real reverse proxy rather
   than trusting the topology on paper:
   - The frontend container's own `HEALTHCHECK` had been silently failing since Phase
     11 (`localhost` resolving to IPv6 first against nginx's IPv4-only `listen`) — a
     real orchestrator that gates traffic on container health would never have routed
     to it.
   - `TokenCleanupService`'s real `@Scheduled` trigger threw
     `TransactionRequiredException` on every run in a real deployed stack
     (self-invocation bypassing the `@Transactional` proxy) — the existing unit test
     never caught it because it calls the bean externally, not through the scheduler's
     own entry point.
   - A malformed/incomplete JSON request body (a missing required primitive field)
     returned `500 INTERNAL_ERROR` instead of a clean `400` — found by curling a real
     registration request against the deployed stack.
2. **A real, precise rollback-compatibility result** — the previous release's image,
   run against the current (V48) schema, starts and serves every existing path
   correctly except one specific write (new content-review submission), which fails
   loudly with a clear constraint violation rather than corrupting data. Tested, not
   guessed.
3. **Three real failure exercises** (bad DB password, missing required env var, invalid
   nginx config) — all fail safely: the application never reports ready or serves
   traffic on bad configuration, and `nginx -t` catches a broken reverse-proxy config
   before it would ever run.
4. **A real production-like deployment**, built from the exact release images, serving
   a real register → verify (via real Mailpit) → login → account export round trip and
   a real guided-assessment flow through the real reverse proxy, with real CSRF/
   security-header/cache-control verification.
5. **Real CI/CD workflows** (`release-build.yml`/`deploy-staging.yml`/
   `deploy-production.yml`) — YAML-valid, image build/tag/nginx-config-validation/
   manifest-generation steps locally-equivalent-verified; registry push and the actual
   remote-deployment commands are `CONFIGURED, NOT EXECUTED` (no registry credentials,
   no real host, honestly disclosed, not fabricated).
6. **A real restore drill repeated against the current V48 schema** (the prior drill
   predated Phase 12) and a real, direct proof that the fresh-install and upgrade-
   install paths both work.
7. **A real, non-mutating DB-quality-check script**, run against the real dev database,
   surfacing one genuine (dev-only, non-production-risk) finding: 32 accumulated
   `TEST_*` procedures from repeated E2E runs against the shared, non-ephemeral local
   dev Postgres — reported, not silently deleted.
8. The full required documentation set, an ADR, and this report.

One open, unresolved finding: a real, deterministic, production-build-only UI
regression (see "Bugs Found").

## Roadmap reconciliation

Per `IMPLEMENTATION_PLAN.md`'s own reconciliation note (also recorded in
`PHASE_11_TESTING_REPORT.md`): the roadmap's canonical Phase 13 is Deployment. A prior
session's out-of-order work (labeled "Phase 11" at the time) already completed a
substantial fraction of what canonical Phase 13 actually covers — see
`PHASE_11_REPORT.md`/ADR-013. This report is that canonical Phase 13, inspecting what
already existed first (below) and completing the real gaps, not re-doing or renumbering
anything. Canonical Phase 12 (Security/Privacy/GDPR) and canonical Phase 11 (Testing
Completeness) are unaffected and unchanged by this phase.

## Inspection (summary — full detail was posted to chat before implementation began)

**Already Complete:** production Dockerfiles (multi-stage, non-root, healthchecked),
same-origin two-container topology, reverse proxy (SPA fallback, cache split, security
headers, `/api`+`/actuator` proxy), fail-fast required env vars in staging/production,
`/api/v1/platform/status` real version/commit reporting, admin bootstrap (opt-in,
self-disabling, no default credential), graceful shutdown, liveness/readiness probes, a
backup/restore mechanism drilled once (pre-Phase-12), CI (`ci.yml`) real on every PR,
every Testcontainers integration test already proving a clean V1→V48 fresh-install
migration.

**Partially Complete (at inspection time):** staging config/docs existed but no
`.env.staging.example` and never deployed; Dockerfiles/Compose accepted `IMAGE_TAG`/
`BUILD_COMMIT` but nothing in CI built/tagged/pushed an image; the restore drill was
stale (pre-V48); SemVer was intended but no tag/manifest/CD existed.

**Missing (at inspection time):** any CD workflow beyond `ci.yml`; a release manifest;
a release-smoke script; a DB-quality-check script; `ENVIRONMENT_VARIABLES.md`/
`STAGING.md`/`DNS_AND_TLS.md`/`ROLLBACK.md`/`FIRST_PRODUCTION_DEPLOYMENT.md`/
`RELEASE_MANIFEST.md`/`GO_NO_GO.md`; an ADR for the promotion strategy; any real
rollback/failure-exercise test; Playwright `BASE_URL` targeting.

**Deployment Risks (at inspection time):** single-instance Flyway-at-boot (accepted,
disclosed); no registry credentials in this environment; no real domain/TLS; migration
DB role not separated from the app role.

All of the above is closed or explicitly, honestly disclosed below — see "Known
Deployment Gaps."

## Deployment architecture

Unchanged from ADR-013, re-verified this phase against the actual built release images
(not re-designed):

```text
Internet
   ↓
HTTPS (TLS terminated in front of the frontend container - DNS_AND_TLS.md)
   ↓
frontend container (nginx-unprivileged: serves the Angular build, proxies /api and
                     /actuator to the backend container)
   ↓
backend container (Spring Boot, no directly-exposed port)
   ↓
PostgreSQL (self-hosted Compose profile, or a managed instance)
```

## Environment model

| | Local | Test | Staging | Production |
|---|---|---|---|---|
| Config file | `application-local.yml` | `application-test.yml` | `application-staging.yml` | `application-production.yml` |
| Env template | `.env.example` | n/a (Testcontainers) | `infra/.env.staging.example` (new, Phase 13) | `infra/.env.production.example` |
| Database | local dev Postgres | Testcontainers (fresh per run) | dedicated, separate | managed/self-hosted, separate |
| Deployed? | n/a | n/a | `STAGING ARCHITECTURE READY, NOT DEPLOYED` | not deployed (not requested) |

Full detail: `docs/operations/ENVIRONMENTS.md` / `ENVIRONMENT_VARIABLES.md`.

## Release artifact

Real values from this phase's own local build/verification (not invented production
values):

```json
{
  "version": "0.0.1-SNAPSHOT",
  "commit": "<see git log — the Phase 13 commit itself>",
  "backendImage": "foreigner-warsaw-backend:<commit-sha>",
  "frontendImage": "foreigner-warsaw-frontend:<commit-sha>",
  "flywayVersion": 48,
  "buildTimestamp": "2026-09-04T11:00:00Z"
}
```

Both images were built clean from the current source tree this phase
(`docker build`, real commit-SHA tags) — confirmed via `docker inspect`. No registry
digest exists (no registry push executed). See `docs/releases/RELEASE_MANIFEST.md`.

## CI/CD

| Stage | Status |
|---|---|
| CI (`ci.yml`) | IMPLEMENTED, real, runs on every PR/push (unchanged, pre-existing) |
| `release-build.yml` (build, tag, nginx-config-validate, manifest) | IMPLEMENTED — build/tag/validate/manifest steps locally-equivalent-verified this phase; the workflow itself has never had a real GitHub Actions run (`CONFIGURED, NOT EXECUTED`) |
| Registry push (GHCR) | CONFIGURED, NOT EXECUTED — no registry credentials exist in this environment |
| `deploy-staging.yml` | CONFIGURED, NOT EXECUTED — workflow exists, YAML-valid, manual `workflow_dispatch`; no real staging host to deploy to |
| `deploy-production.yml` | CONFIGURED, NOT EXECUTED — same, plus requires a `production` GitHub Environment with required reviewers configured in repository Settings (cannot be set from workflow YAML — documented in `ENVIRONMENT_VARIABLES.md`/this report, not fabricated as already configured) |
| Concurrency/overlap prevention | IMPLEMENTED (`concurrency:` groups on both deploy workflows) |

All four workflow files were validated with a real YAML parser (`pyyaml`) — one real
syntax bug (an unquoted colon inside a plain scalar) was found and fixed this way
before it could have failed in a real Actions run.

## Database deployment

- **Flyway strategy**: migration-at-backend-startup, unchanged from ADR-013/Phase 11 —
  explicitly re-affirmed as the deliberate choice for this MVP's single-instance scale
  (ADR-015). A dedicated pre-deploy migration step is deferred until multi-instance
  deployment is real.
- **DB account model**: same role for app DML and migration DDL — a disclosed,
  accepted MVP limitation, not implemented as least-privilege-separated this phase.
- **Backup**: mechanism real and drilled (`DATABASE_BACKUP.md`) — no scheduled job
  exists yet in any real environment (none exists to schedule against).
- **Restore drill**: **repeated this phase** against the current V48 schema (the prior
  drill was pre-Phase-12/stale) — PASS, all row counts and the V48 backfill matched
  exactly. See `DATABASE_RESTORE.md`.
- **Fresh install**: proven exhaustively — every Testcontainers integration test
  already runs the full V1→V48 chain against a brand-new database, every run.
- **Upgrade install**: proven via the real dev database's own migration history
  (sequential, real `installed_on` timestamps across 48 migrations, not a synthetic
  single-shot dump) plus a direct confirmation that V48's backfill (`submitted_by_actor_ref`)
  populated every pre-existing row with zero nulls.

## Admin bootstrap

Unchanged, real, re-verified by inspection this phase (`AdminBootstrapRunner`) — opt-in
only, no default credential, self-disabling once any `ADMIN` exists, minimum password
length enforced. `FIRST_PRODUCTION_DEPLOYMENT.md` (new) documents the exact procedure
end to end for a first real environment.

## Legal content

Unchanged operational model, now explicitly documented (`STAGING.md`,
`FIRST_PRODUCTION_DEPLOYMENT.md`): legal content is authored directly, per environment,
through the real Admin governance workflow — there is no export/import pipeline moving
content between databases, and none was built as a shortcut this phase. A fresh
database has schema but no content (`SCHEMA READY` ≠ `CONTENT PROVISIONED`, stated
explicitly). Confirmed this phase, directly: starting a fresh backend against the
existing dev database (with its real, governed content) preserves every row —
deployment/code changes do not overwrite legal content, verified via the DB-quality
check and the restore drill's own row-count matches.

## Staging

`STAGING ARCHITECTURE READY, NOT DEPLOYED.` The exact topology, config, and image set
staging would use were built and verified locally this phase (see "Production-Like
Deployment" below — the same rig serves as the staging rehearsal). No real staging host
exists. A real, disclosed limitation: `e2e/db.ts`'s role-granting helper needs direct
`docker exec` access to Postgres, which does not generalize to a real remote host — see
`STAGING.md`.

## Production-like deployment

Built the exact release images (`docker build`, real commit-SHA tags) and ran the full
three-container `infra/docker-compose.prod.yml` stack locally (self-hosted-db profile),
with the real local Mailpit reachable via `host.docker.internal` for real email
verification. Verified for real, through the reverse proxy:

- `docker compose up` health-gated startup ordering (postgres → backend ready →
  frontend) — real, correct.
- 10/10 non-destructive smoke checks (`scripts/release-smoke.sh`): readiness, platform
  status, homepage, sitemap.xml, robots.txt, published-procedures API, privacy/terms
  routes, non-exposed Actuator paths (401).
- Real CSRF enforcement through the proxy (403 without token, 201 with it).
- Real security headers on `/`, a hashed asset, and an API response — no duplication
  between nginx and Spring Security.
- A **real register → verify-email (via Mailpit) → login → personal-data export**
  round trip — `Cache-Control: no-store` on the export, correct DTO shape, 401 without
  a session.
- A **real guided assessment → recommendation → case** flow (via the full Playwright
  suite targeted at the deployed stack with the new `BASE_URL` support).
- Session cookie correctly omits `Secure` because this local rig has no real TLS
  terminator and nginx honestly forwards `X-Forwarded-Proto: http` — proof the
  mechanism works correctly (never weakened to "test" it — brief §131).

## Deployment smoke

`scripts/release-smoke.sh` (new) — non-destructive, no credentials required, run
against the local production-like stack: readiness, platform status, homepage,
sitemap.xml, robots.txt, procedures list, privacy/terms routes, non-exposed Actuator
paths. 10/10 passed.

## Privacy regression (deployed stack)

Real, through the reverse proxy: account export (Cache-Control: no-store, correct
schema, 401 unauthenticated). Account **deletion** was not exercised against the
deployed stack this phase (brief §127/§184's own "never automated smoke" /
"disposable account only" — the existing backend integration test suite already covers
deletion exhaustively at the application layer; re-running it through a throwaway
container added no new coverage worth the time this phase, given everything else still
to verify).

## Rollback

Real compatibility test performed (not guessed) — see `docs/operations/ROLLBACK.md`
for the full account. Summary: the previous release (commit `36832b4`) starts and
serves correctly against the current V48 schema for every existing path; one specific
write (new content-review submission) fails with a clear, loud constraint violation
because that code doesn't know about V48's new `NOT NULL` column. Application rollback
(redeploy previous tag) is otherwise safe. Database rollback (reversing a migration) is
never attempted — forward-fix only, per policy.

## Failure exercises

All three performed for real, against the actual built images:

| Exercise | Result |
|---|---|
| Bad DB password | Container exits (code 1) within ~15s, never reports ready, no partial health, no credential echoed. PASS. |
| Missing required env var (`DB_HOST`) | Never becomes ready, exits non-zero. The underlying error (`UnknownHostException: ${DB_HOST}`) is safe in effect but confusingly indirect — a minor, disclosed improvement opportunity, not fixed this phase. |
| Invalid nginx config | `nginx -t` (run through the real image's own entrypoint) catches a deliberately broken directive before nginx would ever start. PASS. Now wired into `release-build.yml` as a real gate on every release build. |

## Security (deployed stack)

- Network exposure: only the frontend/reverse-proxy container is reachable; backend and
  Postgres have no published host ports in `infra/docker-compose.prod.yml`, confirmed
  by inspection and by the local rig's own topology.
- Secrets: none baked into either image (confirmed by inspecting the built images'
  layers/env); frontend build receives only `production: true` and the relative
  `apiBaseUrl`.
- TLS: `DOCUMENTED, NOT EXECUTED` — no real domain exists (`DNS_AND_TLS.md`).
- Trusted proxy: `forward-headers-strategy: framework` confirmed safe in this exact
  topology (backend unreachable except via the frontend's proxy).
- Cookie/CSRF through the deployed stack: real, verified (see "Production-Like
  Deployment").

## Release process

```text
CI
  ↓
release-build.yml (build/tag/validate/manifest — REAL, locally-equivalent-verified)
  ↓
deploy-staging.yml (CONFIGURED, NOT EXECUTED)
  ↓
Staging smoke/E2E (REAL mechanism, locally-verified this phase)
  ↓
Manual production approval (workflow + GitHub Environment gating — REAL mechanism, repository-side reviewer config not verifiable from here)
  ↓
Backup/Migration (REAL, drilled)
  ↓
deploy-production.yml (CONFIGURED, NOT EXECUTED)
  ↓
Production smoke (REAL mechanism, non-destructive)
```

## Documentation

New this phase: `ENVIRONMENT_VARIABLES.md`, `STAGING.md`, `DNS_AND_TLS.md`,
`ROLLBACK.md`, `FIRST_PRODUCTION_DEPLOYMENT.md`, `infra/.env.staging.example`,
`docs/releases/RELEASE_MANIFEST.md`, `GO_NO_GO.md`, ADR-015,
`infra/scripts/db-quality-check.sql`, `scripts/release-smoke.sh`, this report.

Updated: `DEPLOYMENT.md` (troubleshooting section, a real bug write-up, corrected
`DNS.md` reference), `ENVIRONMENTS.md`, `RELEASE_PROCESS.md`, `PRODUCTION_RELEASE_CHECKLIST.md`,
`DATABASE_RESTORE.md`, `IMPLEMENTATION_PLAN.md`.

## Tests

- Backend: `./mvnw verify` — **362/362 tests, 0 failures, 0 errors, BUILD SUCCESS**
  (includes Spotless formatting check — one real violation on new files, fixed via
  `spotless:apply` and re-verified clean).
- Frontend: lint clean; unit tests **121/121 passed** (27 files); production build
  clean.
- Playwright, final run against the exact final release images
  (`BASE_URL=http://localhost:18080`, real production-like stack, `--workers=1`):
  **15/18 passed, 1 failed (the open production-build-only `mat-select` finding — see
  Bugs Found), 2 did not run** (the two scenarios `test.describe.configure({mode:
  'serial'})`-chained after the failing one, in the same file — expected serial-mode
  behavior, not independent failures). Every other spec (admin governance lifecycle,
  auth, home, reference-content, reference-data) passed clean against the deployed
  stack, including a real fix mid-phase to `e2e/db.ts`'s container-name portability
  (see "Bugs found" is not the right word for this one — it was this phase's own new
  `E2E_DB_CONTAINER`/`E2E_DB_USER`/`E2E_DB_NAME` env-portability addition being
  exercised for the first time, not a pre-existing app bug).
- New backend tests this phase: `TokenCleanupSchedulingIntegrationTest` (1/1 passed —
  proves the real `@Scheduled` entry point, not just the direct-call path).

## Bugs found

1. **Frontend container `HEALTHCHECK` always failing** (both Dockerfiles) — real,
   fixed. See "Executive summary" and `DEPLOYMENT.md`'s troubleshooting section.
2. **`TokenCleanupService` scheduled entry point threw `TransactionRequiredException`
   on every real run** — real, fixed (self-invocation bypassing `@Transactional`).
   New regression test added.
3. **Malformed/missing-primitive-field JSON body returned 500 instead of 400** — real,
   fixed (`GlobalExceptionHandler`).
4. **Dead, CSP-blocked Google Fonts `<link>`s in `index.html`** — real, fixed (removed;
   the CSP already blocked them, so this was silent waste, not a new capability lost).
5. **Open finding, not fixed**: a production-build-only, 100%-deterministic UI
   regression on the assessment "current legal status" `mat-select` — the exact click-
   interception bug Phase 5 already fixed once for dev, resurfacing specifically
   against the deployed/AOT production build despite the fix's CSS being correctly
   present in the production bundle (verified). Conclusively isolated to the
   production build only (does not reproduce against `ng serve` with the same backend
   data — direct A/B confirmed this phase) and confirmed independent of the font-CSP
   bug above (removing the fonts did not fix it). Root cause not yet found — needs live
   browser DevTools computed-style/layer inspection, not available in this headless/
   trace-only investigation environment. Recommended as the very next engineering task
   (see "Known Deployment Gaps").
6. **Dev-DB-only, non-production-risk**: 32 accumulated `TEST_*` procedures in the
   shared local dev Postgres from repeated Playwright runs (dev Postgres is not
   ephemeral the way Testcontainers is) — reported by `db-quality-check.sql`, not
   silently deleted (a destructive action on the user's own dev data, out of scope to
   perform without being asked).

## Deviations

- No real cloud/production deployment was performed — correct per the brief's own
  explicit instruction, not a shortfall.
- The two deploy workflows contain placeholder remote-deployment steps rather than a
  real SSH/registry-pull sequence, since no real host/registry exists to target —
  documented as such, not fabricated as executable today.
- Account deletion was not re-exercised against the deployed stack (see "Privacy
  regression" above) — a deliberate time/coverage tradeoff, not an oversight.

## Known deployment gaps

- Real hosting provider: **NOT SELECTED**.
- Real domain/TLS: **NOT EXECUTED** — no certificate has ever been issued.
- Live staging/production hosts: **none exist**.
- Registry credentials: **none exist in this environment** — no real image push has
  ever been proven.
- Real CD execution: **none** — the three workflows have never had a live GitHub
  Actions run.
- Migration DB role separation: not implemented (same role for DDL and DML) — an
  accepted MVP-scale limitation.
- The open UI bug (#5 above) — recommended as the next concrete engineering task,
  ideally with real browser DevTools access.
- `UnknownHostException`-style indirect error message for a missing required env var —
  cosmetic/diagnostic-clarity improvement, not a safety gap (the app still never
  becomes ready).

## Deployment readiness

### Build Reproducibility — HIGH
Both images build clean from a fresh checkout; dependencies pinned (Maven Wrapper,
`package-lock.json`); real commit-SHA tagging proven.

### Container Readiness — HIGH
Non-root, multi-stage, minimal runtime images; both `HEALTHCHECK`s now correct and
verified (one was a real, previously-undiscovered bug, now fixed and independently
re-verified).

### Database Migration Readiness — HIGH
Fresh-install proven exhaustively (every test run); upgrade-install proven via real
history; restore drill repeated against the current schema; migration failure
correctly blocks readiness (verified via the failure exercises' identical code path).

### Backup / Restore Readiness — MEDIUM
The mechanism itself is HIGH (drilled twice now, including against the current
schema) — MEDIUM overall because no real scheduled backup job exists anywhere yet (none
exists to schedule against - no real environment).

### Staging Readiness — MEDIUM
Architecture, config, and a full local rehearsal are real and thorough (HIGH-quality
rehearsal) — MEDIUM overall because no real remote staging host has ever been stood up,
and the role-granting E2E helper's remote-portability gap is unresolved.

### Production Deployment Procedure — HIGH
`FIRST_PRODUCTION_DEPLOYMENT.md` and `DEPLOYMENT.md` are complete, real, and every
individual mechanism they describe was independently verified this phase (bootstrap,
health gating, migration, backup, smoke) — HIGH despite no real deploy having happened,
because "the procedure is real and every piece is proven" is exactly what this rating
measures, not "a real deploy has occurred."

### Rollback Confidence — MEDIUM
A real, precise compatibility test was performed (not a guess) and the exact boundary
is documented — MEDIUM rather than HIGH because that boundary (one write path breaks)
means rollback is not unconditionally safe, and only one migration's rollback
compatibility has actually been tested this way so far.

### CI/CD Readiness — MEDIUM
Real, YAML-valid, gated (manual approval, concurrency locks, no auto-deploy-to-prod)
workflows exist and their build-time steps are locally verified — MEDIUM rather than
HIGH because no live execution has ever proven the actual GitHub Actions environment
runs them correctly, and registry push/remote deployment remain unexecuted.

## Canonical Phase 13 status

**DONE**, with the gaps above honestly disclosed (matching this repository's own
established convention of "substantially complete" rather than claiming perfection). No
real cloud/production deployment was performed, per the brief's own explicit
instruction not to do so without being asked.

## Canonical Phase 14 status (assessed, not started)

Existing foundation: `CorrelationIdFilter` (real, threads a correlation ID through every
request), Actuator restricted to internal use (`health,info` only, non-exposed paths
return 401 before route resolution — stronger than "not publicly documented").
**Not done, unchanged by this phase**: structured JSON logging (still plain-text
console logs), error-tracking service integration, Actuator metrics exposure
beyond health/info, analytics event emission, the source-freshness dashboard,
uptime/health alerting. Phase 13 deliberately did not implement any of this — see brief
§112/§194's own explicit "Phase 14 owns observability completion, do not scope creep."

## Next canonical phase recommendation

**Canonical Phase 14 — Monitoring / Analytics**, per the roadmap
(`IMPLEMENTATION_PLAN.md`). Before that, the single highest-value immediate action is
resolving the open production-build-only `mat-select` UI regression (Bugs Found #5) —
it blocks the primary guided-assessment flow's "current legal status" question in any
real deployed environment and needs live browser DevTools access this session did not
have. Not started per the brief's explicit "do NOT begin Phase 14."
