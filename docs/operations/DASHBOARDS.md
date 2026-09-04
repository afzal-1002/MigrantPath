# Dashboards

Status: a real, local-only Prometheus + Grafana Compose profile ships this phase
(`infra/monitoring/docker-compose.monitoring.yml`) - optional, never merged into the
production `docker-compose.yml`/`docker-compose.prod.yml`, and never part of the
release/deploy pipeline. It scrapes a locally-running backend's `/actuator/prometheus`
and gives the panels below something real to render against, but no dashboard JSON
is treated as load-bearing for production - the metrics themselves (`METRICS.md`) and
the alert catalogue (`ALERTS.md`) are the source of truth; the dashboard is a
convenience for reading them.

## Running it locally

```bash
docker compose -f infra/monitoring/docker-compose.monitoring.yml up -d
```

Prometheus (`localhost:9090`) scrapes `host.docker.internal:8080/actuator/prometheus`
every 15s - point the backend at `local` or `staging` profile first so
`/actuator/prometheus` is actually exposed and reachable (see `METRICS.md`'s exposure
section for why it's gated). Grafana (`localhost:3001`, default `admin`/`admin` -
change on first login, local-only convenience credential, never used outside this
throwaway profile) comes pre-provisioned with a single "Foreigner Warsaw - Overview"
dashboard (`infra/monitoring/grafana/dashboards/overview.json`) sourced from this
document's own panel list.

Tear down with `docker compose -f infra/monitoring/docker-compose.monitoring.yml down
-v` - it uses its own named volumes, isolated from the real application stack.

## Panel specification

### Row 1 - Availability & traffic

- **Request rate** - `sum(rate(http_server_requests_seconds_count[1m])) by (status)`,
  stacked by status class (2xx/3xx/4xx/5xx).
- **p50/p95/p99 latency** - `histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket[5m]))` (and 0.5/0.99).
- **Readiness** - single-stat panel driven by the same synthetic check
  `DIAGNOSTICS.md`/`OBSERVABILITY.md` describe (not a Prometheus metric by default -
  annotated as a manual/synthetic panel, not scraped).

### Row 2 - Domain funnel

- **Assessments completed** - `increase(assessment_completed_total[1h])`.
- **Recommendation run outcomes** - `sum(increase(recommendation_completed_total[1h]))`
  vs `partial` vs `failed`, stacked.
- **Zero-candidate rate** - `recommendation_zero_candidates_total /
  (recommendation_completed_total + recommendation_partial_total)` - a content-coverage
  signal, not a fault signal (matches `ALERTS.md`'s own explicit non-alerting note).
- **Cases created / upgraded** - `increase(case_creation_total[1h])`,
  `increase(case_upgrade_total[1h])` vs `case_upgrade_failed_total`.

### Row 3 - Rule/content health

- **Rule evaluation error rate** - `rate(rule_evaluation_error_total[15m])` by
  `errorCategory` (CONFIGURATION/FACT_RESOLUTION/THRESHOLD_RESOLUTION/UNKNOWN -
  `RuleEvaluator`'s own categorization).
- **Legal content health gauges** - `legal_sources_outdated`,
  `legal_content_with_outdated_source` - explicitly annotated on the panel itself
  ("informational - never gates readiness") to prevent this dashboard from being
  misread as an outage signal.

### Row 4 - Privacy & background jobs

- **Email send outcomes** - `increase(email_send_success_total[1h])` vs
  `email_send_failure_total`, split by `type` tag (VERIFICATION/PASSWORD_RESET).
- **Account export/deletion outcomes** - `increase(account_export_completed_total[1d])`
  vs `.failed`, same for deletion.
- **Token cleanup job** - `token_cleanup_run_total` (should climb steadily on its own
  schedule), `token_cleanup_deleted_total`, `token_cleanup_failure_total`.

### Row 5 - Framework/infra

- **JVM heap usage** - `jvm_memory_used_bytes{area="heap"} /
  jvm_memory_max_bytes{area="heap"}`.
- **Hikari pool** - `hikaricp_connections_active`, `hikaricp_connections_pending`,
  `hikaricp_connections_idle`.
- **CPU** - `process_cpu_usage`.

## What this dashboard deliberately does not include

- Any panel keyed by `userId`/`email`/`caseId`/`assessmentId` or any other banned
  high-cardinality/personal tag (`MetricCardinalityPolicyTest` enforces this at the
  metric-registration level, so no such panel could be built even if attempted).
- Postgres-server-level panels (connections/replication/disk) - no exporter is
  deployed for the database itself this phase (`METRICS.md`'s own honest "Not
  implemented" gap) - a real follow-up, not silently assumed covered by Hikari's
  client-side pool metrics.
- Any alerting/paging integration - Grafana's own alerting engine is left disabled in
  this profile; `ALERTS.md`'s thresholds are the specification to wire into whichever
  alerting layer is chosen later, not something this throwaway local profile
  implements.
