# Observability

Status: Canonical Phase 14 closed the gap the previous version of this document
honestly disclosed. Structured JSON logging, correlation-ID propagation into error
response bodies, a real Prometheus registry with a full domain-metric catalog, a
legal-content health signal, an error-tracking integration boundary (documented, not
connected - no real account exists), an initial alert catalogue, an optional local
dashboard profile, and nginx access-log token redaction are all real and tested. See
`METRICS.md` (full metric catalog), `ALERTS.md` (thresholds), `DASHBOARDS.md` (the
optional local Grafana profile), `ERROR_TRACKING.md` (status), and `DIAGNOSTICS.md`
(runbooks) for the detail this document only summarizes.

## Health checks

- `GET /actuator/health/liveness` - is the process itself functioning (JVM up, no
  fatal internal state). Never depends on the database or mail.
- `GET /actuator/health/readiness` - can this instance actually serve traffic
  (database reachable). Mail is deliberately excluded from both
  (`management.health.mail.enabled: false`, `application.yml`) - a transient SMTP
  hiccup must never fail a health check (a well-known health-check anti-pattern,
  found originally via a real flaky Testcontainers run, not assumed). The new
  legal-content health gauges (below) are excluded from readiness for the identical
  reason - a stale legal source is a content-governance fact, never an outage.
- **A real, previously-undetected bug this phase found and fixed**: Spring Boot's
  `readiness` health group, by default, includes ONLY its own internal
  `readinessState` indicator (graceful-shutdown/startup `AvailabilityState`) - it does
  NOT automatically fold in `db` or any other registered health indicator. Every prior
  claim in this codebase's own docs that readiness meant "database reachable" was
  therefore inaccurate until now: a real Postgres outage occurring *after* a
  successful startup left `/actuator/health/readiness` reporting `UP` indefinitely,
  even though the aggregate `/actuator/health` correctly showed `db: DOWN`. Found via
  a real production-like verification exercise (stopping the actual Postgres
  container while the app was already serving traffic, comparing both endpoints) -
  every prior failure exercise (Phase 13) only ever tested a DB outage present at
  *startup*, a different failure mode (Flyway/JPA can't initialize, so the process
  never finishes starting) that never exercised this path. Fixed by explicitly setting
  `management.endpoint.health.group.readiness.include: readinessState,db`; guarded
  against regressing back with `ReadinessGroupConfigTest`. Re-verified end to end after
  the fix: readiness correctly went to `503`/`DOWN` while Postgres was stopped and
  recovered to `200`/`UP` once it was restarted, with liveness staying `UP`
  throughout.
- Both are explicitly enabled via `management.endpoint.health.probes.enabled: true`
  rather than relying on Spring Boot's Kubernetes-only auto-detection - works
  identically on plain Docker Compose or any other target.
- The backend container's own `HEALTHCHECK` (`backend/Dockerfile`) calls
  `/actuator/health/readiness` directly (ADR-013).

## Actuator exposure (verified, not assumed)

`health`, `info`, and (new this phase) `prometheus` are exposed
(`management.endpoints.web.exposure.include`, base `application.yml`, unchanged by
any profile). `ActuatorExposureTest` proves, against a real Spring context, that
`/actuator/env`, `/beans`, `/configprops`, `/heapdump`, `/threaddump`, `/mappings`,
and `/metrics` remain unreachable - and unreachable in the *stronger* way (`401`,
route existence itself undiscoverable to an anonymous caller, not merely `404`).
`/actuator/prometheus` is gated exactly the same way (anonymous → `401`, proven by
`prometheusEndpointIsExposedToActuatorButNotToAnonymousCallers`) - exposure in
`management.endpoints.web.exposure.include` and reachability past `SecurityConfig`'s
authentication requirement are two independent layers, and both must agree before an
endpoint is genuinely public; only `health`/`info` are ever allowed to be. See that
test class's own Javadoc for a documented, genuine Spring Boot 4 test-infrastructure
limitation around asserting the authenticated `200`-with-real-Prometheus-content case
inside `MockMvc` - proven instead by real curl in this phase's Production-Like
Verification pass (`docs/product/PHASE_14_REPORT.md`).

## Structured logging

**Real, JSON, staging/production only** (`backend/src/main/resources/
logback-spring.xml`, profile-gated via `<springProfile>`) - local/test keep Spring
Boot's own human-readable console pattern unchanged, so nothing about the everyday
dev-loop console output changed this phase. In staging/production, every log line is
a JSON object via `LogstashEncoder`, carrying a deliberate, intentional field
whitelist:

- `timestamp`, `level`, `logger`, `message`, `thread`
- `application` (`foreigner-warsaw-backend`), `environment`, `buildCommit` (the
  latter two via `<springProperty scope="context">`, registered as logback Context
  properties and auto-serialized by the encoder - listing them again under
  `customFields` was a real bug this phase found and fixed, since it produced
  duplicate JSON keys in the actual emitted output)
- `correlationId` (via `<includeMdcKeyName>`) - the same id threaded through the
  response header and (new this phase) the `ApiError` response body
- Any explicit structured key-value pair a call site adds via
  `net.logstash.logback.argument.StructuredArguments.kv(...)` - used today by
  `RequestLoggingFilter`'s per-request summary line and `RuleEvaluator`'s error
  logging.

**Never present, by deliberate design, matching the brief's own explicit
whitelist**: email, salary/threshold values, date of birth, legal status, any
questionnaire answer value, any raw token (verification/reset), session/CSRF values.
Enforced by an automated regression, not just a one-time audit:
`LoggingPrivacyRegressionTest` attaches a real `ListAppender` to the root logger and
drives a real failed login (with a synthetic password), a real registration, a real
verification-token issuance, and a real assessment-answer save, then asserts none of
the corresponding synthetic secret value ever appears in any emitted log line -
across every class in the call path, not just the ones this phase touched.

## Request-summary logging

`RequestLoggingFilter` (`@Order(Ordered.HIGHEST_PRECEDENCE + 11)`) logs exactly one
line per request: HTTP method, a **path template** (`HandlerMapping.
BEST_MATCHING_PATTERN_ATTRIBUTE`, read after `filterChain.doFilter` returns - e.g.
`/api/v1/assessments/{id}/answers/{questionCode}`, never the raw UUID/code, keeping
log-line cardinality low and avoiding any incidental PII-in-a-path-segment leak),
response status, and duration in ms. INFO by default; WARN if `durationMs >=
app.observability.slow-request-threshold-ms` (`ObservabilityProperties`, default
3000ms, overridable via `APP_SLOW_REQUEST_THRESHOLD_MS`). The request/response
**body** is never logged by this filter - questionnaire answers, credentials, and
tokens all travel through bodies, not path templates.

## Expected 4xx vs. unexpected 5xx

`GlobalExceptionHandler` already distinguished these classes before this phase (a
known `ApiException` → the specific, intended 4xx; anything else → generic 500).
This phase's addition is purely on the logging side: an expected `ApiException`
produces no `ERROR`-level noise (a validation failure or an auth-boundary rejection
is normal traffic, not an incident); `handleUnexpected`'s catch-all for a genuinely
unanticipated exception still `log.error`s with the full stack trace server-side
only, exactly as before, now additionally tagged with the same `correlationId` a
support conversation can reference.

## Correlation ID

Real, tested since Phase 11 for MDC-threading (`CorrelationIdFilter`, ordered so it
covers the whole request including Spring Security's own exception handling). An
incoming `X-Correlation-ID` is honored only if it matches a safe pattern
(alphanumeric/hyphen, ≤100 chars) - anything else is replaced with a freshly
generated one, never trusted verbatim. **New this phase**: the same id now also
appears inside every `ApiError` JSON response body (`correlationId` field, populated
from `MDC.get("correlationId")`) - a phone/email support conversation can quote it
back even though it never opens browser devtools to read the response header
(`CorrelationIdIntegrationTest`, 4 tests, including the header/body-match assertion).

## Metrics

Real Micrometer + `micrometer-registry-prometheus`, exposed at
`/actuator/prometheus` (internal-only - see Actuator exposure above). The full
catalog, exact tags, and semantics (once-per-real-event, idempotent-repeat
exclusion, expected-4xx exclusion from `.failed` counters) live in `METRICS.md` -
this section only summarizes the categories:

- **Framework metrics** - `http.server.requests` (by path template/status/method),
  JVM (`jvm.memory.*`), Hikari (`hikaricp.connections.*`), process CPU - all free
  from `spring-boot-starter-actuator`'s existing Micrometer auto-configuration, no
  new wiring needed.
- **Auth** - `auth.login.failure` (real since an earlier phase, unchanged).
- **Assessment/rule/recommendation** - `assessment.completed`, `rule.evaluation`
  (tagged by outcome), `rule.evaluation.error` (tagged by `errorCategory`),
  `recommendation.completed`/`.partial`/`.failed`, `recommendation.zero_candidates`.
- **Case/privacy/email** - `case.creation` (named `.creation`, not the more obvious
  `.created` - a real Prometheus naming-sanitizer collision found and fixed this
  phase, see `METRICS.md`'s "Semantics that matter"), `case.upgrade`/
  `.upgrade.failed`,
  `email.send.success`/`.failure` (tagged by `type`), `account.export.completed`/
  `.failed`, `account.deletion.completed`/`.failed`.
- **Background jobs** - `token.cleanup.run`/`.failure`/`.deleted`.
- **Legal content health** (gauges, informational only - see below).

**Cardinality policy, automated**: `MetricCardinalityPolicyTest` scans every tag key
on every registered meter (after driving real traffic) against a banned list
(`userId`, `email`, `caseId`, `assessmentId`, `recommendationRunId`, `correlationId`,
`source`/`sourceUrl`, and their snake_case variants) - a future accidental
high-cardinality or personal-data tag, including one introduced by a Spring Boot
auto-instrumentation upgrade, fails this test immediately rather than silently
shipping.

**A genuine, disclosed test-infrastructure limitation**: Spring Boot 4's test-scoped
Micrometer auto-configuration (via the new split `-test` starter modules) wires a
test-friendly `MeterRegistry` that is not a `PrometheusMeterRegistry`, so
`PrometheusScrapeEndpoint` never activates under `@SpringBootTest`/MockMvc. This is
an application-agnostic framework behavior, not a defect in this codebase - proven
by a full real curl-based verification against a real running instance (register →
verify → login → curl `/actuator/prometheus` → 200 with real content). See
`ActuatorExposureTest`'s own Javadoc and the Production-Like Verification section of
`docs/product/PHASE_14_REPORT.md`.

## Legal-content health signal

Two gauges (`LegalContentHealthMetrics`, refreshed every 30 minutes via
`@Scheduled`, each self-transactional repository call - no shared transactional
boundary, avoiding the Phase-13 self-invocation pitfall class entirely):
`legal.sources.outdated`, `legal.content.with_outdated_source`. **Never wired to
`/actuator/health/readiness` or any CRITICAL/HIGH alert** - see
`LEGAL_CONTENT_MONITORING.md`'s "Automated health signals" section for the full
rationale and `ALERTS.md` for the (LOW-severity, informational-only) alert row.

## Error tracking

**`DOCUMENTED_ONLY`** - no Sentry-or-equivalent account exists for this project, so
none is connected; fabricating a "connected" status would violate this project's own
provenance discipline. Both integration boundaries are real and ready for an SDK to
attach to with no further application-code change: backend
(`GlobalExceptionHandler.handleUnexpected`, already logs every unhandled exception
server-side with full detail); frontend (`GlobalErrorHandler`, a real `ErrorHandler`
DI override, new this phase, alongside the pre-existing
`provideBrowserGlobalErrorListeners()`). Full detail, including the privacy-scrubbing
requirement any future connected provider must honor, in `ERROR_TRACKING.md`.

## Alerting

An initial catalogue exists (`ALERTS.md`), explicitly marked `INITIAL/
TUNING_REQUIRED` throughout - reasoned starting thresholds, not thresholds derived
from real production traffic history (none exists yet). No paging/on-call tool is
wired; the catalogue is the specification for whichever alerting layer eventually
sits on top of `/actuator/prometheus`.

## Dashboards

An optional, local-only Prometheus + Grafana Compose profile
(`infra/monitoring/docker-compose.monitoring.yml`) ships this phase - never merged
into the production compose files, never part of the deploy pipeline. See
`DASHBOARDS.md` for the panel specification and run instructions.

## Proxy-level observability

nginx's access log (`frontend/nginx.conf.template`) uses a custom `safe` log format
keyed on `$uri` (query-string-free) rather than `$request`/`$request_uri`, and
deliberately omits `$http_referer` - a real finding this phase: the frontend reads
verification/reset tokens from a query parameter, and the *default* nginx log
formats would have written that token to the access log on the page load, while
`$http_referer` on a *subsequent* asset request could carry the same token forward
from the previous page's URL. Verified with a real throwaway nginx container serving
requests carrying synthetic tokens and inspecting `docker logs` output directly -
zero token leakage with the `safe` format.

## What remains a known, honestly-disclosed gap

- No Postgres-server-level exporter (connections/replication/disk at the database
  process level) - only Hikari's client-side pool metrics exist today.
- Synthetic (non-destructive) production monitoring is documented as a runbook
  concept (`DIAGNOSTICS.md`) rather than an automated scheduled prober.
- Error tracking is integration-ready but not connected to a real service (see
  above) - connecting one is a clear, scoped follow-up once an account exists.
