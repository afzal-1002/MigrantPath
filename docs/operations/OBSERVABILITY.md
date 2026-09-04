# Observability

Status: the health/readiness/actuator-exposure baseline is real and tested (see below);
one real custom metric is wired and tested; the rest of the named counters are a
specified, not-yet-implemented plan - honestly marked as such, not claimed done.

## Health checks

- `GET /actuator/health/liveness` - is the process itself functioning (JVM up, no
  fatal internal state). Never depends on the database or mail.
- `GET /actuator/health/readiness` - can this instance actually serve traffic (database
  reachable). Mail is deliberately excluded from both (`management.health.mail.enabled:
  false`, `application.yml`) - a transient SMTP hiccup must never fail a health check
  (brief §38's own "well-known health-check anti-pattern," found originally via a real
  flaky Testcontainers run, not assumed).
- Both are explicitly enabled via `management.endpoint.health.probes.enabled: true`
  (Phase 11 addition) rather than relying on Spring Boot's Kubernetes-only
  auto-detection - works identically on plain Docker Compose or any other target
  (brief §4's "no Kubernetes unless demonstrated need").
- The backend container's own `HEALTHCHECK` (`backend/Dockerfile`) calls
  `/actuator/health/readiness` directly - verified working end to end this session
  (ADR-013).

## Actuator exposure (verified, not assumed)

Only `health` and `info` are ever exposed (`management.endpoints.web.exposure.include`,
base `application.yml`, unchanged by any profile). `ActuatorExposureTest` proves, against
a real Spring context, that `/actuator/env`, `/beans`, `/configprops`, `/heapdump`,
`/threaddump`, `/mappings`, and `/metrics` are all unreachable - and unreachable in the
*stronger* way (`401`, route existence itself undiscoverable to an anonymous caller,
not merely `404`) than originally assumed when that test was first written - see that
test class's own Javadoc for the real finding.

## Structured logging

**Not yet JSON** - the base Spring Boot console pattern is unchanged this phase. What
*is* in place: every request carries a correlation id
(`CorrelationIdFilter` → SLF4J `MDC` key `correlationId`), returned as the
`X-Correlation-ID` response header and available to thread into a log pattern
(`%X{correlationId}`) once one is configured. JSON logging (brief §43's "preferred if
compatible") would need the `logstash-logback-encoder` dependency plus a
`logback-spring.xml` - deliberately not added this phase (a new dependency this late,
unvalidated against a real log-aggregation target, was judged higher-risk than
value for a first release with no log aggregator chosen yet). Tracked as a clear,
scoped follow-up: add the dependency, a JSON encoder pattern including
`correlationId`/`level`/`logger`/`message`/`duration`, and confirm no sensitive field
(brief §45's list - passwords, tokens, session/CSRF values, assessment answers, salary,
DOB, family data, case notes) ever appears, matching the log-scrub discipline already
audited manually across every module this session (`GlobalExceptionHandler`, `Login
Service`, `TokenGenerator`, `SecurityEventLogger` - all confirmed, unchanged from prior
phases, never log a raw token/password/answer).

## Metrics

Micrometer's core is already on the classpath transitively (`spring-boot-starter-
actuator`) - no new dependency needed for in-process counters. **One is wired and
tested**:

- `auth.login.failure` - incremented via a `SecurityMetricsListener` that listens for
  Spring Security's own `AbstractAuthenticationFailureEvent` (bad credentials, locked,
  disabled) - zero changes to any already-tested `LoginService` code path. Verified by
  `SecurityMetricsListenerTest` against a real login attempt through the real HTTP/
  Security stack. No PII on the metric (no tags at all, just a count).

**Specified, not yet implemented** (brief §42's own named list) - each would follow the
exact same pattern (an event listener or a thin call at the existing service boundary,
no tags carrying user-identifying data):

```text
assessment.completed          - AssessmentCompletionService.complete()
rule.evaluation                - RuleEvaluator.evaluate()
rule.evaluation.error           - RuleEvaluator, ERROR-status results
recommendation.completed        - RecommendationService.analyze(), COMPLETED runs
recommendation.partial          - ", PARTIAL runs
recommendation.failed           - ", FAILED runs
case.created                    - UserCaseCreationService.createFromRecommendation()
case.upgrade                    - UserCaseController's upgrade endpoint
email.send.failure              - VerificationEmailService/PasswordResetService catch blocks
```

**No Prometheus registry is wired** (brief §41 - explicitly optional, "do not force a
complete Prometheus/Grafana stack unless deployment plan will actually use it"). Adding
one is a one-dependency change (`micrometer-registry-prometheus`) plus exposing
`/actuator/prometheus` - deliberately deferred until an actual scraper/dashboard target
is chosen, per the brief's own guidance.

**Metrics exposure**: `/actuator/metrics` (and any future `/actuator/prometheus`) must
never be publicly reachable - already true today (only `health,info` are in
`exposure.include`); widening exposure for internal-only access (a private network,
VPN, or a separate authenticated port) is a deployment-time decision for whichever
monitoring target is eventually chosen, not something to open publicly "just in case."

## Error monitoring (brief §47/§48 - integration point specified, not wired)

No error-tracking service (Sentry or equivalent) is integrated this phase. The
integration point, when chosen: `GlobalExceptionHandler.handleUnexpected` already
`log.error`s every unhandled exception server-side with full detail and returns only a
generic message to the client (unchanged, confirmed this session) - a Sentry/equivalent
SDK's Logback/Log4j appender would pick these up with no controller-level code change
needed. Frontend: Angular's `ErrorHandler` is not yet overridden for global unhandled-
error capture - a real, scoped follow-up (brief §48), distinct from the per-request
`HttpErrorInterceptor` pattern this app already uses for expected API errors (401/403/
500 handling in the UI). Any real integration must scrub PII before it ever leaves the
process - the same brief §45 list applies to error reports, not just logs.

## Correlation ID

Real, tested (`CorrelationIdFilter`, highest-precedence-but-one filter order so it
covers the whole request including Spring Security's own exception handling). An
incoming `X-Correlation-ID` is honored only if it matches a safe pattern (alphanumeric/
hyphen, ≤100 chars) - an arbitrary/oversized value from the network is replaced with a
freshly generated one, never trusted as-is (brief §44's own explicit warning).
