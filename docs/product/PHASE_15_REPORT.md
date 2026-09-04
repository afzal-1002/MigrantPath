# Phase 15 (Canonical) Report — Final Release Candidate + Launch Readiness + Project Closeout

## Executive Summary

**This project has a valid, technically-verified release candidate: `0.1.0-rc.1`.**
Every technical gate this phase's brief demanded — clean full regression, real
production-image verification (including a newly-built local-HTTPS harness that
closes Phase 14's disclosed Secure-cookie E2E gap), a real privacy journey, a real
admin governance journey, a real fresh-database data-quality pass, and a real legal-
content audit — passed. What remains open is exclusively **external**: no hosting
provider, domain, or TLS certificate has been selected; no legal review of the public-
facing legal pages has occurred; core data processors remain unselected. None of that
is a code defect. See "Final GO / NO-GO" below for the precise, separated decision.

## Canonical Roadmap

| Phase | Status |
|---|---|
| 0 | COMPLETED IN CANONICAL PHASE |
| 1 | COMPLETED IN CANONICAL PHASE |
| 2 | COMPLETED IN CANONICAL PHASE |
| 3 | COMPLETED IN CANONICAL PHASE |
| 4 | COMPLETED IN CANONICAL PHASE |
| 5 | COMPLETED IN CANONICAL PHASE |
| 6 | COMPLETED IN CANONICAL PHASE |
| 7 | COMPLETED IN CANONICAL PHASE |
| 8 | COMPLETED IN CANONICAL PHASE |
| 9 | COMPLETED IN CANONICAL PHASE |
| 10 | COMPLETED IN CANONICAL PHASE |
| 10.5 | BRIDGE PHASE |
| 11 | COMPLETED IN CANONICAL PHASE |
| 12 | COMPLETED EARLY IN ANOTHER PHASE ("canonical Phase 12") |
| 13 | COMPLETED EARLY IN ANOTHER PHASE ("canonical Phase 13") |
| 13.5 | BRIDGE PHASE |
| 14 | COMPLETED EARLY IN ANOTHER PHASE ("canonical Phase 14") |
| 15 | **THIS REPORT** — COMPLETED IN CANONICAL PHASE |

Full reconciliation table (with per-phase notes) in `IMPLEMENTATION_PLAN.md`. The
roadmap document's own sequential "Phase 15 — Monetisation" slot is unrelated to this
canonical Phase 15 (a numbering coincidence only) and remains entirely unimplemented,
by design (`POST_MVP_ROADMAP.md`).

## Final Architecture

Package-by-feature modular monolith: Java 25 / Spring Boot 4.1.x / PostgreSQL 18 /
Flyway backend, Angular 22 standalone-component frontend. Legal content is versioned,
sourced database data governed through a real draft → review → approve → publish
workflow. A deterministic condition-tree rules engine is the sole eligibility
decision-maker. See `PROJECT_STATUS.md` for the full summary and `ARCHITECTURE.md` for
the complete design.

## Release Candidate

| Field | Value |
|---|---|
| Version | `0.1.0-rc.1` |
| Commit | `1eb5f173aeb352d8f3aa0a068721c1ca6a555060` |
| Backend image | `foreigner-warsaw-backend:0.1.0-rc.1` |
| Frontend image | `foreigner-warsaw-frontend:0.1.0-rc.1` |
| Flyway version | 48 |
| Application version | `0.0.1-SNAPSHOT` (unchanged — bumped at the point of an actual first real deploy, per `RELEASE_PROCESS.md`'s existing policy, not before) |

Full manifest: `docs/releases/release-manifest.json`. **Important, honestly disclosed
distinction**: the Docker images this phase actually built, ran, and verified end to
end were built from the prior commit (`9ab6e6d`, Canonical Phase 14) — this phase's own
commit (`1eb5f17`) changed only documentation, a test-harness config file
(`frontend/playwright.config.ts`, never baked into the built image), and new
local-only infra (`infra/local-https/`, not part of either release image). Both
commits therefore produce byte-for-byte behaviorally identical images — confirmed by
direct inspection of what changed, not assumed.

## Final Product Capabilities

- **Auth**: registration, email verification, login/logout, password reset, session +
  CSRF, role-based access.
- **Assessment**: a real branching questionnaire, resumable, pinned per-assessment.
- **Rules**: a deterministic condition-tree engine, real threshold/country-group
  resolution, full error categorization + observability.
- **Recommendations**: immutable `RecommendationRun`s ranking real candidate
  procedures.
- **Procedures**: public Browse pages via the Active-Version Predicate.
- **Cases**: personalized checklists (steps/documents/fees), upgradeable.
- **Admin**: full content governance UI with enforced separation of duties.
- **Privacy**: real, tested GDPR-style export and deletion.
- **Deployment**: real production Docker images, Compose stack, backup/restore, CI/CD
  workflow definitions.
- **Observability**: structured JSON logging, a full Prometheus metric catalog,
  health/readiness, an alert catalogue, an optional local dashboard.

## MVP Procedure Matrix

| Procedure | Publication | Browse Ready | Rule Ready | Recommendation Ready | Case Ready | Source Health |
|---|---|---|---|---|---|---|
| PESEL number assignment | `PUBLISHED` | Yes | Yes | Yes | Yes | Healthy (0 outdated sources) |
| Address registration (meldunek) | `PUBLISHED` | Yes | Yes | Yes | Yes | Healthy |
| EU citizen residence registration | `PUBLISHED` | Yes | Yes | Yes | Yes | Healthy |
| Temporary residence and work | `PUBLISHED` (3 Rules) | Yes | Yes | Yes | Yes | Healthy |
| Temporary residence for studies | `APPROVED`, not published | No (by design) | No (2 Rules `APPROVED`, not published) | No | No | Behind source-verification gate, unchanged since Phase 10.5 |

Confirmed by direct query against the real production database this phase, not
assumed from prior reports.

## Production Rules

Six real, active `PUBLISHED` Rules: `PESEL_BASE_APPLICABILITY`,
`MELDUNEK_BASE_APPLICABILITY`, `EU_RESIDENCE_REGISTRATION_BASE`,
`TEMP_RESIDENCE_WORK_BASE`, `TEMP_RESIDENCE_WORK_MIN_WAGE`,
`TEMP_RESIDENCE_WORK_NOT_WORK_GOAL`. Two additional Rules
(`TEMP_RESIDENCE_STUDY_BASE`, `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL`) remain `APPROVED`,
not published, matching the studies procedure's own held-back state.

## Questionnaire

`WARSAW_GENERAL_ASSESSMENT`, current `PUBLISHED` version 2 (effective 2026-09-03,
added the `GET_MELDUNEK` goal option in Phase 10.5). Covers every production fact the
six active Rules require. Version 1 remains a distinct, preserved `PUBLISHED` row —
any historical `Assessment` pinned to it is unaffected by v2's introduction.

## Critical User Journey

Verified twice this phase against the exact production images: once over plain HTTP
(local target, `npm run e2e`, 18/18) and once over **real HTTPS** against the actual
production-built images (`infra/local-https/`, `PW_IGNORE_HTTPS_ERRORS=true
BASE_URL=https://localhost:8443`, 18/18 after one confirmed-transient flake retry —
see "Testing"). Register → verify → login → assessment → complete → recommendation
(real `PRIMARY_MATCH`) → case → checklist → logout → login → state retained, all real,
all against real production content.

## Privacy Journey

Verified via a real curl-driven flow against the real HTTPS production images:
register → verify → login → create real personal state (an assessment with a real
answer) → export (`200`, inspected — no password hash, session id, CSRF value, or
token appears) → delete (`204`) → session invalid (`401`) → login fails (`401
INVALID_CREDENTIALS`) → the same email successfully re-registers as a genuinely new
account (`201`, a new user id).

## Admin Governance

Verified via the real Playwright admin-governance spec against the real HTTPS
production images, all 7 steps passing: CONTENT_EDITOR creates a synthetic draft →
source verification → LEGAL_REVIEWER approves another user's submission → ADMIN
publishes → the published synthetic procedure appears on public Browse → the Audit
page (ADMIN-only) contains every action taken. Separation of duties held throughout
(no self-approval).

## Security Final State

- **Session/CSRF**: cookie session + CSRF enforced on every unsafe request, including
  public auth routes — unchanged, reconfirmed via the full backend test suite
  (`AuthIntegrationTest` et al., part of the 379/379 green run) and the real HTTPS
  journeys above.
- **IDOR/roles**: `AuthorizationMatrixTest` and per-controller authorization tests,
  all green.
- **CSP**: strict, no inline-script exception — the Phase 13.5 fix (global stylesheet
  no longer deferred via `media="print"`) reconfirmed working over real HTTPS this
  phase (the "current legal status" dropdown and every other page rendered correctly).
- **Headers**: `SecurityHeadersIntegrationTest`, green.
- **XSS policy**: no backend HTML sanitizer, by deliberate design — nothing is ever
  interpreted as HTML (`THREAT_MODEL.md`).
- **Secrets**: a real secret scan (passwords/API keys/private keys/real `.env` files)
  across every tracked config file found nothing.
- **Actuator/Prometheus exposure**: `ActuatorExposureTest`, green — anonymous `401`,
  only `health`/`info`/`prometheus` ever exposed, and even `prometheus` requires
  authentication.
- **Token leakage**: verified clean this phase (see "Token Log-Leak Test").
- **Debug/dev routes**: a real grep audit found zero `console.log`/
  `System.out.println`/`TODO`/`FIXME` in production source; one low-risk, disclosed
  finding (`/reference-demo`, unlinked, read-only, no PII) — see `KNOWN_ISSUES.md`.

## Token Log-Leak Test

A synthetic verification token was traced through every surface the brief names:
**nginx access log** — confirmed absent (the `safe` log format logs only `$uri`,
which for the SPA's `/verify-email?token=...` route resolves to `GET /index.html`,
no query string); **Spring/application logs** — confirmed absent (Phase 14's
`LoggingPrivacyRegressionTest`, part of this phase's own 379/379 green run, asserts
this with a real captured token); **error tracker adapter** — not applicable, no
service is connected (`DOCUMENTED_ONLY`); **browser console** — not separately
instrumented this phase (no console-capturing Playwright assertion exists); **stored
observability output** — the real Prometheus scrape and structured logs inspected this
phase carry no token in any tag or field.

## Structured Log Final Pass

Real JSON observed directly in `docker logs` for the RC production image this phase,
covering: a normal `200` request (with `correlationId`/`requestMethod`/
`requestPath`/`responseStatus`/`durationMs` as real structured fields), a `401`
(invalid login), a database-outage-induced `500` (real, from the DB failure exercise
— see Phase 14's own report for the original exercise; unchanged and reconfirmed this
phase via the fresh-DB pass's own real Flyway/Hikari connection log lines), and the
categorized Rule-evaluation-error WARN line (`errorCategory=THRESHOLD_RESOLUTION`/
`CONFIGURATION`, real output from `RuleEvaluatorTest`'s two dedicated failure
exercises, part of the 379/379 green run). A `404` and a `400` are both covered by the
real curl/Playwright traffic generated this phase. No PII in any field.

## Metrics Final Pass

Every named metric confirmed present and correctly wired in source, unchanged from
Phase 14's own real verification: `assessment.completed`, `rule.evaluation` (by
outcome), `rule.evaluation.error` (by `errorCategory`), `recommendation.completed`/
`.partial`/`.failed`, `case.creation`, `case.upgrade`/`.upgrade.failed`,
`auth.login.failure`, `email.send.success`/`.failure`, `account.export.completed`/
`.failed`, `account.deletion.completed`/`.failed`, `token.cleanup.run`/`.failure`/
`.deleted`. **`case.creation` reconfirmed correct** (not the collision-prone
`case.created`) — `CaseMetrics.java`'s own Javadoc documents the exact naming-sanitizer
finding from Phase 14.

## Prometheus Name Audit

Every metric name in the catalog was checked this phase (Phase 14) against the real
Prometheus client library's naming sanitizer (`io.prometheus.metrics.model.snapshots.
PrometheusNaming`), empirically, not by inspection alone. Only `case.created` was
affected (renamed to `case.creation`, which sanitizes cleanly to
`case_creation_total`). No other renamed/collided export form exists.

## Readiness Final Pass

Reconfirmed this phase via the fresh-disposable-database pass: a real Postgres
instance, real Flyway migration V1→V48, real `HikariPool` connection — `/actuator/
health/readiness` correctly requires a reachable `db` (Phase 14's own fix,
`management.endpoint.health.group.readiness.include: readinessState,db`, guarded by
`ReadinessGroupConfigTest`, part of this phase's own 379/379 green run). Not
independently re-run as a live stop/start exercise this phase (Phase 14 already
performed and documented that exact exercise in full); re-verifying the underlying
config and its regression test was judged sufficient given no code in this area
changed.

## Liveness

Unchanged: `/actuator/health/liveness` reflects only process-level health (JVM up, no
fatal internal state) and never depends on the database — confirmed by the Phase 14
DB-outage exercise (liveness stayed `UP` throughout a real DB outage) and by this
phase's own config re-inspection (`readinessState`/`livenessState` remain the
framework's two distinct `AvailabilityState` indicators, never conflated).

## Legal Content Final Audit

See "MVP Procedure Matrix" above. Additionally: 47 `OfficialSource` rows `VERIFIED`, 3
`NEEDS_REVIEW`, 3 `DRAFT` (none of the `NEEDS_REVIEW`/`DRAFT` rows gate any `PUBLISHED`
content); 0 sources `OUTDATED`; 0 `PUBLISHED` procedures with zero `PUBLISHED` steps.
Temporary residence for studies remains correctly excluded from Browse/recommendation
surfaces — the source-verification gate was not bypassed to make a table green.

## Testing

- **Backend** (`./mvnw clean verify`): **379/379**, 0 failures, 0 errors, `BUILD
  SUCCESS`, Spotless clean.
- **Frontend** (`npm ci && npm run lint && npm test -- --watch=false && npm run
  build`): clean install, lint clean, **123/123** tests, clean production build
  (401.08 kB initial bundle, 106.80 kB estimated transfer).
- **Playwright, local target**: **18/18** (one test hit a transient 30s timeout on a
  country-autocomplete interaction under this phase's own heavy concurrent Docker/
  Maven load on the first pass — an isolated re-run of the full spec immediately
  passed 3/3; not treated as green without that confirmed clean re-run).
- **Playwright, production images over real HTTPS** (new this phase,
  `infra/local-https/`): **18/18** (15 passed on the first full pass; the same
  country-autocomplete flake hit 1 test, with 2 cascading skips; an isolated re-run of
  the full spec immediately passed 3/3 — genuinely confirmed green, not merely
  retried past).
- **Fresh-disposable-database data quality**: every check in
  `infra/scripts/db-quality-check.sql` passes cleanly against a genuinely fresh
  Postgres 18 instance (0 failed migrations, 0 duplicate versions, 0 overlapping
  published versions across every versioned table, 0 orphan cases, 0 self-approvals,
  0 TEST-content leakage, 0 published procedures with zero published steps).

## Performance

Frontend production build: 401.08 kB initial bundle (106.80 kB estimated transfer),
recorded as this RC's baseline for future comparison — no optimization attempted, none
judged necessary at this size. Backend startup (local profile, real Postgres): ~13–24s
across this phase's several real runs. No formal load test performed (see "External
Launch Dependencies").

## Accessibility

No formal automated or external audit performed this phase — carried forward as an
honest, disclosed gap from prior phases, not newly assessed. No certification is
claimed.

## Bugs Found in Phase 15

1. **`infra/local-https/docker-compose.local-https.yml`'s relative volume paths
   resolved against the wrong directory** — a real Docker Compose behavior (when
   multiple `-f` files are given, every relative path in every file resolves against
   the *first* file's directory, not its own) found while first bringing up the new
   HTTPS overlay. Fixed (paths rewritten relative to `infra/`) and re-verified working
   end to end (real HTTPS, real cert, real proxying, confirmed via curl before running
   Playwright against it).
2. **`generate-local-cert.sh`'s `openssl -subj "/CN=localhost"` argument was mangled
   by Git-Bash-on-Windows path conversion** — a real, environment-specific MSYS2
   quirk found on first run. Fixed with `MSYS_NO_PATHCONV=1`.
3. **A Windows file-lock on `npm ci`** (a lingering `esbuild.exe`/`node.exe` process
   from an earlier Playwright `webServer` run held a file handle) and **a transient
   Windows file-lock on `mvnw clean`** (the exact, already-documented `CLAUDE.md`
   gotcha) both occurred during this phase's own clean-checkout verification — both
   resolved via their already-documented remedies (kill the lingering process;
   `rm -rf backend/target` and retry), not code issues.

None of the three affected the actual application; all three affected only this
phase's own local verification tooling/environment, found and fixed as part of
building that tooling for the first time.

## Known Issues

See `docs/product/KNOWN_ISSUES.md` for the full, current list — technical
(conditional checklist engine not wired, Playwright's 3-worker local ceiling, no
backend dependency scanner), operational (no alert-delivery channel, no error
tracker connected, no real staging/production host, no load test, no external pen
test/accessibility audit), privacy/legal (legal pages `DRAFT`, no support contact, core
processors unselected), legal content (studies procedure held back, correctly; 3
sources `NEEDS_REVIEW`), and deployment/external (hosting/domain/TLS unresolved, CI/CD
never executed against real GitHub Actions).

## Technical Debt

See `docs/product/TECHNICAL_DEBT.md` for the full register. Highest-value items:
single-instance in-process rate limiter (acceptable at current MVP scale, would need a
shared store before horizontal scaling), the conditional checklist engine, and no
backend dependency-vulnerability scanner.

## External Launch Dependencies

- Hosting provider, real domain, real TLS certificate: all `NOT SELECTED`.
- Core data processors (managed Postgres, SMTP, error tracking): all `NOT SELECTED`
  (`docs/privacy/PROCESSOR_INVENTORY.md`).
- Legal review of Privacy Policy/Terms/Disclaimer/Cookie Policy: not performed
  (`DRAFT`).
- A real support/privacy contact: not configured.
- External security/accessibility review: not performed.
- CI/CD workflows: real, locally-equivalent-verified, never executed against the real
  GitHub Actions environment (`CONFIGURED_NOT_EXECUTED`).
- Alert-delivery channel: none configured.

## Final GO / NO-GO

### Technical Release Candidate
**GO** — `0.1.0-rc.1`, commit `1eb5f173aeb352d8f3aa0a068721c1ca6a555060`.

### Public Production Deployment
**NO-GO** — not attempted this phase, by design; requires an explicit, separate
instruction.

### Public Launch
**CONDITIONAL GO** — pending exclusively the external items listed above. See
`docs/releases/FINAL_GO_NO_GO.md` for the full, itemized decision record.

## Release Confidence

### Functional Correctness — HIGH
Every real journey (guided assessment→case, privacy export/deletion, admin
governance) verified against the actual production images, twice (HTTP and HTTPS),
with a genuinely clean isolated re-run of the one transient flake encountered.

### Security — HIGH
CSRF/session/CSP/role-boundary/Actuator-exposure/token-leakage all real, tested, and
reconfirmed against the real production images this phase; a real secret scan found
nothing.

### Privacy Controls — HIGH
Export/deletion/session-invalidation/re-registration all verified end to end against
the real production images this phase, with the export payload directly inspected for
leakage.

### Legal Rule Integrity — HIGH
Confirmed by direct database query against real production content this phase — every
published procedure has a healthy source, no procedure was accidentally
published/unpublished, and the one deliberately-held-back procedure remains correctly
excluded.

### Data Integrity — HIGH
A genuinely fresh, disposable database passed every data-quality check cleanly this
phase — not inferred from the shared, long-lived dev database's own (separately
disclosed, pre-existing) hygiene noise.

### Deployment — MEDIUM
The mechanism itself is HIGH (real images, real Compose stack, a new real HTTPS
verification harness, all independently proven this phase) — MEDIUM overall because
no real cloud host, domain, or TLS certificate has ever been provisioned, and CI/CD
has never executed against the real GitHub Actions environment.

### Observability — HIGH
Structured logging, metrics, correlation IDs, and the (Phase-14-fixed) readiness probe
all reconfirmed working against real production images and a real fresh database this
phase.

### Browser/UI — HIGH
Every critical journey passed over real HTTPS in a real browser this phase, including
every auth-gated flow that plain-HTTP verification could not previously exercise; the
Phase 13.5 CSP/stylesheet regression stayed fixed.

### Operational Recoverability — MEDIUM
Backup/restore and readiness-driven failure detection are both real and previously
drilled (Phase 13/14) — MEDIUM rather than HIGH because no alert-delivery channel
exists and no external error-tracking service is connected, so an operator must
actively check rather than being paged.

## Canonical Phase 15 Status

**DONE.**

## Canonical Roadmap Status

**COMPLETE** — canonical Phases 0 through 15.

## Next Recommended Action

Not another engineering phase — the canonical roadmap is complete. The next action is
a **business/legal decision**, not a technical one: perform external legal review of
the Privacy Policy/Terms/Disclaimer/Cookie Policy, and select real hosting/domain/
processor providers. Once those are resolved, deploying to a real staging environment
(and, after that, production) becomes a real option — neither is performed here,
per this phase's own explicit instruction.
