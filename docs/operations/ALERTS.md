# Alert Catalogue

Status: `INITIAL/TUNING_REQUIRED` throughout - every threshold below is a reasoned
starting point (based on the metric's own semantics, not on real production traffic
history, since none exists yet for this first release) and MUST be revisited once
real traffic volume is observed. No paging/on-call tool is wired (`INCIDENT_RESPONSE.md`
already states this explicitly, brief §139) - these are the rules to configure in
whichever alerting layer eventually sits on top of `/actuator/prometheus` (see
`DASHBOARDS.md`), not alerts that fire today.

Each row: **Signal** (the PromQL-shape expression against the metrics in
`METRICS.md`) - **Severity** - **Initial threshold** - **Why**.

## Availability

| Signal | Severity | Initial threshold | Why |
|---|---|---|---|
| `up{job="foreigner-warsaw-backend"} == 0` | CRITICAL | any scrape miss for 2 consecutive intervals | The instance is unreachable to the scraper at all - almost certainly also unreachable to users. |
| `/actuator/health/readiness` reports DOWN (via a blackbox/synthetic probe, `DIAGNOSTICS.md`) | CRITICAL | DOWN for 1 minute | Readiness DOWN means the app itself has already decided it cannot serve traffic - do not wait for user reports. |

## Errors

| Signal | Severity | Initial threshold | Why |
|---|---|---|---|
| `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | HIGH | >5 req/min sustained for 5m | A real 5xx spike (brief's own "unexpected 5xx get ERROR + stack trace" distinction) - excludes the expected 4xx traffic that never triggers this. |
| `rate(rule_evaluation_error_total[15m])` | MEDIUM | >0 sustained for 15m | Any nonzero rate is worth a look (this codebase's rule content is small and hand-authored - a genuine parse/config/threshold-resolution error should be rare, see `RuleEvaluator`'s `categorize`), but a single blip is not yet an emergency. |
| `increase(recommendation_failed_total[15m])` | HIGH | >0 | A FAILED (not PARTIAL) recommendation run means the whole analysis threw - always worth immediate attention since it fully blocks a user's guided flow. |
| `increase(email_send_failure_total[30m])` | MEDIUM | >5 | A handful of transient SMTP hiccups is expected; a sustained run indicates the mail provider itself is down (`INCIDENT_RESPONSE.md`'s "Mail unavailable" procedure). |
| `increase(case_upgrade_failed_total[30m])` | MEDIUM | >0 | Rare code path; any occurrence is worth investigating given how few real cases exist in this first release. |
| `increase(account_export_failed_total[30m])` or `increase(account_deletion_failed_total[30m])` | HIGH | >0 | A privacy-rights operation failing is a compliance-relevant event, not just an operational one - treat as HIGH regardless of volume. |
| `increase(token_cleanup_failure_total[1h])` | LOW | >0 | Non-user-facing background job; a single failure self-heals on the next scheduled run, but a repeated failure should be looked at. |

## Resource pressure

| Signal | Severity | Initial threshold | Why |
|---|---|---|---|
| `hikaricp_connections_pending > 0` sustained | MEDIUM | pending > 0 for 5m | Requests are queuing for a database connection - an early warning before requests start timing out. |
| `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85` | MEDIUM | >0.85 for 10m | Approaching heap exhaustion; `> 0.95` for 5m should be escalated to HIGH. |
| `process_cpu_usage > 0.9` sustained | LOW | >0.9 for 15m | Worth a look, rarely an emergency by itself for this application's traffic profile. |

## Security-adjacent

| Signal | Severity | Initial threshold | Why |
|---|---|---|---|
| `rate(auth_login_failure_total[5m])` | MEDIUM | a sharp step-change vs. the same window's rolling baseline (not a fixed number - traffic-dependent) | Consistent with `DIAGNOSTICS.md`'s own "credential-stuffing vs. genuine outage" framing; needs real traffic data before a fixed number is meaningful - flagged as the most clearly `TUNING_REQUIRED` row in this table. |
| `increase(http_server_requests_seconds_count{uri="/actuator/prometheus",status!="401"}[5m]) > 0` from an unexpected source | HIGH | any hit not from the known internal scraper IP/network | Would indicate the internal-only gating (`SecurityConfig`) has somehow been bypassed - see `METRICS.md`'s exposure section. |

## Legal-content health (explicitly informational, never gates readiness)

| Signal | Severity | Initial threshold | Why |
|---|---|---|---|
| `legal_sources_outdated > 0` | LOW | >0 | A prompt to run the monthly review early (`LEGAL_CONTENT_MONITORING.md`), never an application malfunction. |
| `legal_content_with_outdated_source > 0` | LOW | >0 | Same as above, procedure-scoped. Per the brief's own explicit instruction, this NEVER affects `/actuator/health/readiness` and MUST NOT be wired to any CRITICAL/HIGH severity - a stale legal source is a content-governance matter, not an outage. |

## Notes on severity meaning (informal, matches this project's own scale)

- **CRITICAL** - users cannot use the application at all; respond immediately.
- **HIGH** - a real user-facing capability is broken or a compliance-relevant operation
  failed; respond same-day.
- **MEDIUM** - a degradation or an early warning sign; respond within a few days /
  next working session.
- **LOW** - informational; batch into the next regular review (`LEGAL_CONTENT_
  MONITORING.md`'s own monthly cadence is a good model).

## What is deliberately NOT alerted on

- Expected 4xx responses (validation errors, 401/403 auth boundary results,
  idempotent-repeat "no-op" cases) - these are normal traffic, not incidents, per the
  brief's own explicit "expected 4xx get no ERROR log / no alert" distinction.
- `recommendation.zero_candidates` - a real, valid outcome (a user's answers genuinely
  match no current procedure), not a system fault; tracked as a metric for product/
  content-coverage insight only (`LEGAL_CONTENT_MONITORING.md`'s "procedures lacking a
  recommendation Rule" check is the right follow-up venue, not an alert).
