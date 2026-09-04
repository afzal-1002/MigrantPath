# Metric Catalog

Status: every metric below is real and wired this phase (Canonical Phase 14), verified
against a real `MeterRegistry`/`/actuator/prometheus` scrape, not just written and
assumed. `auth.login.failure` (Phase 11) is the one pre-existing metric, unchanged.

No metric in this codebase ever carries a high-cardinality or personal-data tag
(`userId`, `email`, `caseId`, `assessmentId`, `correlationId`, a source URL, a raw
exception message) - `MetricCardinalityTest` enforces this as a real, automated policy
test, not a promise kept only by code review.

## Domain metrics

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `assessment.completed` | Counter | none | Assessments successfully completed. Once per `Assessment`, never per question. |
| `rule.evaluation` | Counter | `result` (`SATISFIED`/`NOT_SATISFIED`/`INDETERMINATE`/`ERROR`) | Every real (non-preview) Rule evaluation outcome. |
| `rule.evaluation.error` | Counter | `errorCategory` (`CONFIGURATION`/`THRESHOLD_RESOLUTION`/`FACT_RESOLUTION`/`UNKNOWN`) | A dedicated breakdown of `rule.evaluation{result=ERROR}` by cause. |
| `recommendation.completed` | Counter | none | `RecommendationRun`s that finished with every candidate procedure evaluated cleanly. |
| `recommendation.partial` | Counter | none | Runs where at least one candidate hit `UNAVAILABLE_FOR_ANALYSIS` (no active published content) - still a real result shown to the user, not a failure. |
| `recommendation.failed` | Counter | none | Runs that threw an unexpected exception. |
| `recommendation.zero_candidates` | Counter | none | Diagnostic only - a completed run that happened to match zero procedures. Never counted as a failure. |
| `case.creation` | Counter | none | New `UserCase` rows actually inserted - an idempotent duplicate request never increments this a second time. Named `.creation`, not `.created` - see "Semantics that matter" below. |
| `case.upgrade` | Counter | none | Successful case upgrades to the current procedure content. |
| `case.upgrade.failed` | Counter | none | Upgrades that failed unexpectedly - excludes the two expected 409 conflicts (already-current, wrong case status). |
| `email.send.success` | Counter | `type` (`VERIFICATION`/`PASSWORD_RESET`) | Emails handed to the SMTP transport successfully. |
| `email.send.failure` | Counter | `type` | Emails that failed to send. |
| `account.export.completed` | Counter | none | Personal-data exports successfully generated. |
| `account.export.failed` | Counter | none | Export requests that failed unexpectedly. |
| `account.deletion.completed` | Counter | none | Accounts successfully, permanently deleted. |
| `account.deletion.failed` | Counter | none | Deletion requests that failed unexpectedly - excludes the expected reauthentication (wrong password) failure. |
| `token.cleanup.run` | Counter | none | Every real scheduled token-cleanup execution (enabled + actually fired). |
| `token.cleanup.failure` | Counter | none | Cleanup runs that threw. |
| `token.cleanup.deleted` | Counter | none | Total expired/stale token rows actually removed, summed across runs. |
| `legal.sources.outdated` | Gauge | none | Current count of `OfficialSource` rows marked `OUTDATED`. Refreshed every 30 minutes, not per-scrape (brief §150). |
| `legal.content.with_outdated_source` | Gauge | none | Currently `PUBLISHED` `ProcedureVersion`s citing at least one `OUTDATED` source. Same 30-minute refresh. |
| `auth.login.failure` | Counter | none | Pre-existing (Phase 11) - failed authentication attempts (bad credentials/locked/disabled). |

## Semantics that matter (brief §144-§149)

- **A real naming collision, found and fixed this phase**: the metric is named
  `case.creation`, not the more obvious `case.created`. `case.created` sanitizes,
  via the modern Prometheus client library's naming convention
  (`io.prometheus.metrics.model.snapshots.PrometheusNaming`, used transitively by
  `micrometer-registry-prometheus`), to the bare series `case_total` instead of
  `case_created_total` - a trailing `_created` segment is a reserved OpenMetrics
  suffix (denoting a counter's own creation timestamp) that gets silently stripped,
  with no error or warning anywhere. Found by driving a real case creation against a
  live instance and diffing actual `/actuator/prometheus` output - not something a
  code read alone would catch. Every other metric name in this catalog was checked
  against the same sanitizer and passes through unchanged (see
  `docs/product/PHASE_14_REPORT.md`'s Production-Like Verification section).
- `case.creation` is checked *after* `UserCaseCreationService`'s own idempotency
  early-return - a second request for the same recommendation returns the existing
  case without incrementing this again (proven by a real end-to-end curl flow this
  phase, see `PHASE_14_REPORT.md`).
- `case.upgrade.failed`/`account.deletion.failed`/`account.export.failed` only ever
  count a genuinely unexpected exception - an `ApiException` (expected 4xx: already-
  current, wrong status, wrong password) never reaches the catch block that
  increments them.
- `recommendation.completed`/`.partial`/`.failed` are recorded exactly once per
  `RecommendationRun`, after `run.complete(status, ...)` - never once per candidate
  procedure within the run.
- `rule.evaluation`/`rule.evaluation.error` only fire from `RuleEvaluator.evaluate()`
  (the real production entry point) - `previewEvaluate()` (admin dry-run rule
  authoring) deliberately never touches either, so an author testing a
  known-incomplete draft condition tree never pollutes these with expected noise.

## Framework metrics (already provided by Micrometer/Spring Boot - none custom-built)

- **HTTP server**: `http.server.requests` (method/status/URI-template tags, via
  Spring's own `WebMvcTagsProvider` - already route-template-based, so no raw-UUID
  cardinality risk).
- **JVM**: `jvm.memory.used`, `jvm.gc.pause`, `jvm.threads.live`, and the rest of
  Micrometer's standard JVM binder set.
- **Hikari**: `hikaricp.connections.active`/`.idle`/`.pending`/`.max` - registered
  automatically once a `HikariDataSource` bean exists (already true).
- **Process/system**: `process.uptime`, `system.cpu.usage`.

All of the above are visible at `/actuator/prometheus` alongside the domain metrics
above - no separate exporter needed for any of them.

## Not implemented (honest gap)

No Postgres-server-level exporter (connection counts, replication lag, etc.) - Hikari's
own client-side pool metrics are judged sufficient for this MVP's scale (brief §39's own
"do not implement a full Postgres exporter unless the deployment stack genuinely owns
one"). Revisit if a managed Postgres provider's own metrics endpoint becomes available
and worth integrating.
