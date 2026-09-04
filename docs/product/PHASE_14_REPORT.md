# Phase 14 (Canonical) Report — Monitoring, Logging, Metrics & Operational Observability

Status: ✅ substantially complete. This was framed, correctly, as a gap-closing
observability phase against a real existing foundation (Actuator, liveness/readiness,
correlation IDs, one custom metric, production docs, a logging privacy policy,
reverse-proxy/container deployment) rather than an infrastructure rewrite. Everything
below is either real and verified against a real running instance, or an
honestly-labeled `DOCUMENTED_ONLY`/`CONFIGURED_NOT_CONNECTED`/`INITIAL_TUNING_REQUIRED`
gap — nothing is fabricated.

## Executive summary

Structured JSON logging, correlation-ID propagation into the API error body, a full
domain metric catalog on a real Prometheus registry, a legal-content health signal, a
frontend/backend error-tracking integration boundary, an initial alert catalogue, an
optional local dashboard profile, and nginx access-log token redaction are all real,
wired, and independently verified — including, critically, against the actual
production-built Docker images, not just the test suite.

**Three real, previously-undiscovered bugs were found and fixed** this phase, each
through direct production-like verification rather than code review alone:

1. **A Testcontainers/Spring-context-cache lifecycle bug** in `AbstractIntegrationTest`
   — the shared Mailpit container's JUnit-managed start/stop (`@Testcontainers`/
   `@Container`) was completely independent of Spring's own `ApplicationContext` cache,
   so once enough integration test classes existed to make Spring reuse a cached
   context, a later test class ran against a context whose `spring.mail.port` was baked
   in from an *earlier*, already-stopped-and-restarted container on a different port —
   every email send in that class failed with connection-refused. Fixed with the
   standard Testcontainers "singleton container" pattern (start once, never explicitly
   stop, let Ryuk reap it at JVM exit).
2. **A Prometheus metric-naming collision**: `case.created` silently exported as the
   bare series `case_total` instead of `case_created_total` — the modern Prometheus
   client library's naming sanitizer treats a trailing `_created` segment as a reserved
   OpenMetrics suffix (a counter's own creation timestamp) and strips it, with no error
   or warning anywhere. Found by driving a real case creation against a live instance
   and diffing actual `/actuator/prometheus` output, not by code inspection. Fixed by
   renaming the metric to `case.creation`.
3. **A significant readiness-probe bug**: Spring Boot's `readiness` health group, by
   default, includes ONLY its own internal `readinessState` indicator — NOT the `db`
   health indicator, despite every piece of this codebase's own documentation
   (including `CLAUDE.md`) claiming readiness meant "database reachable" since Phase
   11. A real Postgres outage occurring *after* a successful startup left
   `/actuator/health/readiness` reporting `UP` indefinitely, even though the aggregate
   `/actuator/health` correctly showed `db: DOWN`. Every prior failure exercise (Phase
   13) only ever tested a DB outage present *at startup* (a different failure mode
   entirely — Flyway/JPA can't initialize, so the process never finishes starting) and
   never exercised this path. Found by stopping the real, persistent dev Postgres
   container while the app was already serving real traffic. Fixed by explicitly
   setting `management.endpoint.health.group.readiness.include: readinessState,db`,
   re-verified end to end (503/DOWN while stopped, 200/UP on recovery), and guarded
   against regressing with a new permanent config test.

Also found and fixed (mid-phase, lower-severity): a duplicate-JSON-key bug in the
staging/production log encoder (`environment`/`buildCommit` appearing twice), and a
real nginx access-log token-leakage risk (verification/reset tokens would have reached
the container's access log under the default log format) — see "Bugs found" below for
the full list with how each was found.

## Logging

Real, JSON, staging/production only (`backend/src/main/resources/logback-spring.xml`).
Local/test keep Spring Boot's own human-readable console pattern unchanged — confirmed
unchanged throughout this phase's own local dev-loop and test runs. In staging/
production every log line is a JSON object (`LogstashEncoder`) carrying: `timestamp`,
`level`, `logger`, `message`, `thread`, `application`, `environment`, `buildCommit`,
`correlationId` (via MDC), and any explicit structured key-value pair a call site adds
(`RequestLoggingFilter`'s summary line, `RuleEvaluator`'s categorized error line).
Verified as real, well-formed JSON against the actual production-built Docker image's
`docker logs` output this phase (see "Production-Like Verification" below), including
confirming the request-summary line's structured fields
(`correlationId`/`requestMethod`/`requestPath`/`responseStatus`/`durationMs`) appear
correctly and without duplication.

**Request-summary logging**: `RequestLoggingFilter` logs exactly one line per request —
method, a low-cardinality **path template** (`HandlerMapping.
BEST_MATCHING_PATTERN_ATTRIBUTE`, never a raw UUID/code), status, and duration. INFO by
default; WARN if the configurable slow-request threshold
(`app.observability.slow-request-threshold-ms`, default 3000ms) is exceeded. The
request/response body is never logged.

**Expected 4xx vs. unexpected 5xx**: an expected `ApiException` produces no
`ERROR`-level noise; `GlobalExceptionHandler.handleUnexpected`'s catch-all for a
genuinely unanticipated exception still `log.error`s with the full stack trace
server-side only, tagged with the same `correlationId` a support conversation can
reference. Verified for real twice this phase: once via the DB-outage failure exercise
(a real registration attempt against a database-down instance returned a generic
`500`/`INTERNAL_ERROR` with a fresh `correlationId` and zero leaked detail), and once
via `TokenCleanupServiceFailureTest`'s mocked-repository-throws scenario.

## Logging privacy

Enforced by an automated regression, not a one-time audit:
`LoggingPrivacyRegressionTest` attaches a real `ListAppender` to the root logger and
drives a real failed login (synthetic password), registration, verification-token
issuance, and assessment-answer save, then asserts none of the corresponding synthetic
secret ever appears in any emitted log line. Never present, by design: password,
salary/threshold values, date of birth, legal status, any questionnaire answer value,
any raw token, session/CSRF values — matching the intentional field whitelist above.

## Correlation ID

Real since Phase 11 for MDC-threading (`CorrelationIdFilter`); this phase adds the same
id inside every `ApiError` JSON response body (`correlationId` field), not just the
`X-Correlation-ID` response header — verified by `CorrelationIdIntegrationTest` (4
tests, including a header/body-match assertion) and observed directly in the real
`500` responses produced during the DB-outage and unmapped-endpoint exercises below.

## Metrics

Real Micrometer + `micrometer-registry-prometheus`, exposed at `/actuator/prometheus`
(gated exactly like every other non-public Actuator endpoint — `401` for anonymous,
`200` with real content for an authenticated user, both proven — see "Production-Like
Verification"). Full catalog, tags, and semantics in
[METRICS.md](../operations/METRICS.md); summary:

- **Assessment/rule/recommendation**: `assessment.completed`, `rule.evaluation` (by
  outcome), `rule.evaluation.error` (by `errorCategory`),
  `recommendation.completed`/`.partial`/`.failed`, `recommendation.zero_candidates`.
- **Case/privacy/email**: `case.creation` (renamed from the originally-planned
  `case.created` — see Bugs Found), `case.upgrade`/`.upgrade.failed`,
  `email.send.success`/`.failure`, `account.export.completed`/`.failed`,
  `account.deletion.completed`/`.failed`.
- **Background jobs**: `token.cleanup.run`/`.failure`/`.deleted`.
- **Legal content health** (gauges, informational only): `legal.sources.outdated`,
  `legal.content.with_outdated_source`.
- **Framework**: `http.server.requests`, JVM, Hikari, process — free from Spring Boot's
  own Micrometer auto-configuration.

**Cardinality policy, automated**: `MetricCardinalityPolicyTest` scans every registered
meter's tag keys (after driving real traffic) against a banned list (`userId`, `email`,
`caseId`, `assessmentId`, `recommendationRunId`, `correlationId`, `source`/`sourceUrl`,
snake_case variants) — a future accidental high-cardinality/personal-data tag fails
this test immediately.

**A genuine, disclosed test-infrastructure limitation**: Spring Boot 4's test-scoped
Micrometer auto-configuration wires a non-`PrometheusMeterRegistry` `MeterRegistry`
under `@SpringBootTest`/MockMvc, so `PrometheusScrapeEndpoint` never activates in that
context — an application-agnostic framework behavior, not a defect here.
`ActuatorExposureTest` keeps the real, passing `401`-for-anonymous test plus a
`meterRegistryIsRealAndFunctional()` sanity check, with the real `200`-with-content
proof performed manually via curl this phase (below) instead of inside `MockMvc`.

## Rule / recommendation observability

`RuleEvaluator.evaluate()` (the real production entry point, never `previewEvaluate()`)
records `rule.evaluation{result}` and, on `ERROR`, `rule.evaluation.error{errorCategory}`
plus a categorized WARN log line (`rule=... ruleType=... errorCategory=...` — real,
observed output: `errorCategory=THRESHOLD_RESOLUTION` and `errorCategory=CONFIGURATION`
from the two dedicated failure-exercise tests in `RuleEvaluatorTest`).
`RecommendationService.analyze()` records the run outcome exactly once per
`RecommendationRun` and `recommendation.zero_candidates` separately (never counted as a
failure). All four — `assessment.completed`, `rule.evaluation` (real result tags),
`recommendation.completed`, and `case.creation` — were verified firing correctly
against real, governance-authored production content in the shared persistent dev
database (a full register → verify → login → assessment → complete → recommendation-run
→ case-creation flow driven via real curl, evaluating the real PESEL/EU-residence/
Meldunek/temporary-residence Rules and producing a real `PRIMARY_MATCH`).

## Case / privacy observability

`case.creation` fires only after `UserCaseCreationService`'s own idempotent-duplicate
early return (a second request for the same recommendation never double-counts).
`case.upgrade.failed`/`account.export.failed`/`account.deletion.failed` only count a
genuinely unexpected exception — the corresponding expected 4xx paths (already-current,
wrong status, wrong password) never reach the catch block that increments them.
`email.send.success`/`.failure` are tagged by `type` (`VERIFICATION`/
`PASSWORD_RESET`) — verified for real via the mail-outage failure exercise below.

## Legal content health

Two gauges (`LegalContentHealthMetrics`, refreshed every 30 minutes,
non-`@Transactional` scheduled method calling two independently-transactional
repository methods — deliberately avoiding the Phase-13 self-invocation pitfall class):
`legal.sources.outdated`, `legal.content.with_outdated_source`. **Never wired to
`/actuator/health/readiness` or any CRITICAL/HIGH alert** — confirmed by inspection of
both the readiness group config and `ALERTS.md`'s own LOW-severity-only rows for these
two signals.

## Error tracking

**`DOCUMENTED_ONLY`** — no real Sentry-or-equivalent account exists, so none is
connected; a fabricated "connected" status would violate this project's own provenance
discipline. Both integration boundaries are real: backend
(`GlobalExceptionHandler.handleUnexpected`) and frontend (`GlobalErrorHandler`, a real
`ErrorHandler` DI override, new this phase, 2 passing tests). Full detail in
[ERROR_TRACKING.md](../operations/ERROR_TRACKING.md).

## Privacy scrubbing

Applies identically to logs and to the (currently unconnected) error-tracking
boundary — the same field whitelist, enforced by the same
`LoggingPrivacyRegressionTest` for the logging half; the error-tracking boundary is
documented to require the identical scrubbing before any future SDK is wired
(`ERROR_TRACKING.md`).

## Health

`/actuator/health/liveness` and `/actuator/health/readiness` both real and correctly
gated. **The readiness-group bug and its fix are the single most significant finding
of this phase** — see Executive Summary and "Bugs found" below; re-verified end to end
post-fix (DOWN/503 while Postgres was really stopped, UP/200 on real recovery, liveness
staying UP throughout). Mail and the legal-content gauges remain deliberately excluded
from readiness, unchanged and reconfirmed.

## Alerting

An initial catalogue exists ([ALERTS.md](../operations/ALERTS.md)), explicitly marked
`INITIAL/TUNING_REQUIRED` throughout — reasoned starting thresholds, not thresholds
derived from real production traffic history (none exists yet for this first release).
No paging/on-call tool is wired, matching `INCIDENT_RESPONSE.md`'s own standing
disclosure.

## Dashboards

A real, optional, local-only Prometheus + Grafana Compose profile
(`infra/monitoring/docker-compose.monitoring.yml`) ships this phase — provisioned
datasource, one pre-built "Overview" dashboard covering every panel row specified in
[DASHBOARDS.md](../operations/DASHBOARDS.md). Never merged into, or referenced by, the
production compose files or the deploy pipeline.

## Synthetic monitoring

Documented as a runbook concept in [DIAGNOSTICS.md](../operations/DIAGNOSTICS.md)
rather than implemented as an automated scheduled prober this phase — an honestly
disclosed gap, not claimed done.

## Proxy observability

nginx's access log uses a custom `safe` log format keyed on `$uri` (query-string-free),
deliberately omitting `$http_referer`. **Verified twice this phase**: once via a real
throwaway nginx container serving synthetic-token requests (mid-phase diagnostic), and
again against the actual freshly-built production frontend image (Production-Like
Verification below) — a real registration → real Mailpit-delivered verification link
→ real fetch of `/verify-email?token=...` through nginx produced an access-log line
showing only `GET /index.html`, with the raw token appearing nowhere. The same log
line's `correlationId=...` field, sourced from `$upstream_http_x_correlation_id`, was
also confirmed real and correctly populated.

## Failure exercises

| Exercise | Method | Result |
|---|---|---|
| **Database unavailable** | Real: stopped the persistent dev `postgres` container while the app was already serving traffic | Found the readiness-group bug (above). Post-fix: `/actuator/health/readiness` → `503`/`DOWN` with a generic error detail (no credential leaked), `/actuator/health/liveness` stayed `200`/`UP`, a real write request (`POST /api/v1/auth/register`) failed `500`/`INTERNAL_ERROR` with a fresh `correlationId` and no leaked detail. Recovered cleanly (`200`/`UP`) once Postgres was restarted, no manual intervention needed. |
| **Mail/SMTP unavailable** | Real: stopped the persistent dev `mailpit` container | `POST /api/v1/auth/register` still returned `201` (the DB write commits before the email send is attempted); `email.send.failure{type=VERIFICATION}` incremented; readiness stayed `UP` throughout (mail deliberately excluded). |
| **Rule evaluation error** | Real, via `RuleEvaluatorTest`'s two dedicated exercises (malformed condition tree; no active published threshold version) | `rule.evaluation{result=ERROR}` and `rule.evaluation.error{errorCategory=CONFIGURATION\|THRESHOLD_RESOLUTION}` both verified via real Mockito `verify()` assertions (not just result-status checks, as they were before this phase); the categorized WARN log line was observed in real test output. |
| **Synthetic 500 (never production-reachable)** | Real, naturally-occurring: the DB-outage exercise above produced a genuine, unexpected `500` from a live, non-test-profile instance | Generic `INTERNAL_ERROR` message, real `correlationId`, no stack trace or credential in the response body — stronger evidence than a purpose-built toggle endpoint would have been, and adds no new production-reachable attack surface. |
| **Cleanup job failure** | Real, via `TokenCleanupServiceFailureTest` (mocked repository throws) | `token.cleanup.run` recorded before the failure, `token.cleanup.failure` recorded after, `token.cleanup.deleted` correctly never registered (never reached), the exception correctly rethrown afterward so Spring's own scheduled-task error logging still sees it, and the second (password-reset) repository correctly never touched once the first call throws. |

## Production-like verification

Rebuilt both Docker images from the current source and deployed the real
`infra/docker-compose.prod.yml` stack (production profile) twice this phase, pointed at
the existing persistent dev Postgres/Mailpit via `host.docker.internal` (backend has no
published host port in this topology, matching the documented security posture —
confirmed unreachable directly, only reachable through the frontend/nginx proxy):

- **JSON logging**: real, well-formed JSON observed directly in `docker logs`, including
  the structured request-summary fields and the correlationId, with no duplicate keys.
- **`/actuator/prometheus` through the real production topology** (via nginx, not the
  backend directly — the actual path any real client uses): `401` anonymous, `200` with
  real content once authenticated through a real register → verify (real Mailpit) →
  login flow. This is the formal proof `ActuatorExposureTest`'s own Javadoc deferred to.
- **nginx token redaction**: confirmed against the freshly-built frontend image (above).
- **`rule.evaluation`/`case.creation` against real content**: confirmed against the
  locally-run (non-containerized) backend pointed at the same persistent dev database —
  see "Rule / recommendation observability" above; the containerized-image run was used
  specifically for the JSON-logging/nginx/Prometheus proofs.
- **Full backend regression** (`./mvnw verify`) against this exact source tree: real,
  green (see Tests below).

**A real, disclosed limitation surfaced by this pass, not a Phase 14 regression**: a
full browser-driven Playwright run against the production-profile image over plain
HTTP cannot meaningfully exercise any auth-dependent flow, because
`application-production.yml` correctly sets `secure: true` on session/CSRF cookies
(HTTPS-only, per ADR-013) and a real browser (unlike `curl`, which ignores the `Secure`
attribute entirely) correctly refuses to send such a cookie back over plain HTTP. Every
one of the 6 production-image Playwright failures this phase observed was auth-gated;
the 2 non-auth-gated tests (home page load, frontend↔backend connectivity) passed
cleanly, confirming the deployed image itself is healthy. This is the same
already-documented "no real TLS/domain exists yet" gap `ADR-013`/`DNS_AND_TLS.md`
already disclosed before this phase — closing it is a hosting/deployment-time decision
outside Phase 14's own scope, not something this phase needed to unblock, and every
Phase-14-specific claim above was independently proven via real `curl`-based flows
instead (which are not subject to this browser-only restriction).

## Documentation

New this phase: `OBSERVABILITY.md` (full rewrite), `METRICS.md`, `ALERTS.md`,
`DASHBOARDS.md`, `DIAGNOSTICS.md`, `ERROR_TRACKING.md`, this report.
`infra/monitoring/` (Compose profile + Prometheus/Grafana provisioning + one dashboard
JSON).

Updated: `LEGAL_CONTENT_MONITORING.md` (automated health-signal section),
`INCIDENT_RESPONSE.md` (JSON-log/metric/dashboard cross-references throughout),
`IMPLEMENTATION_PLAN.md` (Phase 14 section, item-by-item status against the original
roadmap subtasks 14.1–14.6).

## Tests

- Backend: `./mvnw verify` — **379/379 tests, 0 failures, 0 errors, BUILD SUCCESS**
  (includes the Spotless formatting check). New this phase: `CorrelationIdIntegrationTest`
  (4), `DomainMetricsIntegrationTest` (2, against real HTTP flows), `MetricCardinalityPolicyTest`
  (1), `LoggingPrivacyRegressionTest` (4), `ReadinessGroupConfigTest` (2, guarding the
  readiness-group fix), `TokenCleanupServiceFailureTest` (2, the cleanup-job failure
  exercise) — plus real Mockito `verify()` assertions added to `RuleEvaluatorTest`'s two
  existing error-path tests, closing a gap where the metric/log side effects of a rule
  evaluation error were previously unverified.
- Frontend: `npm run lint` clean; `npm test -- --no-watch` — **123/123 tests passing**;
  `npm run build` clean.
- Playwright, local target (`npm run e2e`): **18/18 passing** (one test hit a 30s
  timeout on a country-autocomplete interaction on the first pass, under this session's
  own heavy concurrent Docker/Maven/curl load; an isolated re-run of that spec
  immediately after passed 3/3 cleanly — confirmed transient, not a real regression).
- Playwright, production image: could not be meaningfully completed — see
  "Production-like verification" above for the real, disclosed reason (no local TLS).
- Database quality checks (`infra/scripts/db-quality-check.sql`) against the real
  persistent dev database: no PUBLISHED procedure with zero PUBLISHED steps (0 rows,
  healthy); a pre-existing, **not new this phase** hygiene finding — 36 `TEST_*`-prefixed
  procedures are currently `PUBLISHED` in the shared dev database, accumulated across
  many prior phases' own Playwright/E2E runs (each is unambiguously synthetic per this
  project's own naming convention, `LEGAL_CONTENT_MONITORING.md`) — disclosed here as a
  known dev-environment hygiene item, not remediated (a destructive cleanup of a shared
  dev database was outside this phase's scope and was not requested).

## Bugs found

1. **Logback duplicate JSON keys** (`environment`/`buildCommit` appearing twice) — found
   by starting the app under the staging profile and inspecting real stdout JSON
   directly; fixed by removing the duplicate `customFields` entries (the
   `<springProperty scope="context">` registrations already supply them).
2. **`AbstractIntegrationTest` Mailpit container lifecycle bug** (real, significant) —
   see Executive Summary #1. Found while re-running the full backend regression after
   adding this phase's new observability integration tests; fixed with the
   Testcontainers singleton-container pattern.
3. **`case.created` → `case_total` Prometheus naming collision** (real, significant) —
   see Executive Summary #2. Found via a real HTTP case-creation flow and a diff of
   actual `/actuator/prometheus` output; fixed by renaming to `case.creation`, verified
   empirically against the Prometheus client library's actual naming-sanitizer
   behavior (every other metric name in the catalog was checked the same way and
   passes through unchanged).
4. **Readiness group excludes `db` by default** (real, the most significant finding
   this phase) — see Executive Summary #3. Found by stopping the real persistent dev
   Postgres container while the app was already serving traffic; fixed by explicitly
   configuring the readiness group and guarded with a new permanent config test.
5. **nginx access-log token-leakage risk** — found by inspecting the frontend's own
   query-param-reading routes (`/verify-email?token=...`, `/reset-password?token=...`)
   against nginx's default log format; fixed with a custom `safe` log format
   (`$uri`-based, no `$http_referer`); verified with zero leakage twice (a throwaway
   nginx container and the real production image).

## Security / privacy findings

No new security or privacy defect was found this phase beyond the nginx token-leakage
risk above (already counted in "Bugs found") — every failure exercise's error responses
were checked and confirmed to leak no credential, token, or internal detail; the
metric-cardinality policy test confirms no personal/high-cardinality data reaches any
metric tag; the logging-privacy regression test confirms none reaches a log line.

## Deviations from the brief

- Error tracking: integration boundary only, not connected — no real account exists
  (brief's own explicit fallback for this situation).
- Synthetic production monitoring: documented as a runbook, not automated — an honest,
  scoped-down delivery given the size of everything else in this phase's brief.
- The full production-image Playwright run could not be completed for the disclosed,
  pre-existing TLS-related reason above — every Phase-14-specific claim it would have
  covered was independently verified through other real means (curl-based flows against
  the same deployed image).

## Known monitoring gaps

- No Postgres-server-level exporter (connection counts, replication lag) — Hikari's
  client-side pool metrics only.
- No real alerting/paging tool wired to the alert catalogue's thresholds.
- No real error-tracking service connected.
- No real remote staging/production host exists to observe real traffic against (an
  unchanged, pre-existing gap from Phase 13).
- 36 synthetic `TEST_*` procedures currently sit `PUBLISHED` in the shared dev
  database (pre-existing, not from this phase — see Tests above).

## Observability readiness

### Logging — HIGH
Real structured JSON in staging/production, real correlation-ID propagation into both
the log stream and the API error body, an automated privacy regression, and a real,
independently-verified production-image proof (well-formed JSON observed directly in
`docker logs`).

### Metrics — HIGH
The full named catalog is real, wired at the correct call sites with correct
idempotency/exclusion semantics, protected by an automated cardinality policy test, and
independently proven against real production content (assessment/rule/recommendation/
case metrics all observed incrementing correctly through a real end-to-end flow) and
against the real production topology (`/actuator/prometheus` 401/200 through nginx).

### Health / Readiness — HIGH
Was the phase's single most significant finding (readiness silently excluded `db`) —
now HIGH specifically *because* it was found and fixed through a real failure exercise
this phase performed, with an automated regression test guarding against it recurring,
rather than remaining an unverified assumption the way it was before this phase.

### Error Tracking — LOW
Both integration boundaries are real and tested, but no service is actually connected —
correctly rated LOW rather than fabricated as higher, matching this project's own
provenance discipline.

### Alerting — MEDIUM
A real, reasoned initial catalogue exists with every threshold honestly marked
`INITIAL/TUNING_REQUIRED` — MEDIUM rather than HIGH because no real alerting tool is
wired and no real traffic history exists yet to calibrate against; MEDIUM rather than
LOW because the catalogue itself is thorough, real, and directly actionable once a tool
is chosen.

### Dashboards — MEDIUM
A real, working, provisioned local Grafana profile with one comprehensive dashboard
exists — MEDIUM rather than HIGH because it has only been exercised locally, never
against a real production Prometheus deployment (none exists), and remains explicitly
optional/local-only by design.

### Legal Content Health — HIGH
Both gauges are real, correctly excluded from readiness/alerting severity by design,
and independently confirmed reading `0` against the current (healthy) production
content during this phase's own verification pass.

### Synthetic Monitoring — LOW
Documented only, not automated — correctly rated LOW, an honest gap rather than a
partial claim.

### Proxy Observability — HIGH
The token-redaction fix is real, found through genuine investigation (not assumed),
and independently verified twice — including against the actual production-built
image, the strongest form of proof available this phase.

## Canonical Phase 14 status

**DONE**, in the same "substantially complete, gaps honestly disclosed" sense this
project's prior phase reports have used — every numbered requirement in the brief was
either implemented and verified, or explicitly and honestly marked as a documented gap
with a clear reason (no real account/host/traffic to build against). Three real,
previously-undiscovered bugs were found and fixed as a direct result of this phase's
own insistence on production-like verification rather than trusting the design on
paper — consistent with, and reinforcing, this project's established "prove it, don't
assume it" discipline.

## Canonical Phase 15 readiness

**NOT READY**, by design and per the brief's own explicit instruction not to begin it
this session. No assessment of Phase 15's own scope was performed as part of this
report — this line exists solely to record that Phase 14 stopped here as instructed,
not to imply any judgment about Phase 15 itself.
