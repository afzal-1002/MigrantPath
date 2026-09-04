# Incident Response

Status: manual runbook for a first, low-traffic release. No paging/on-call tooling
exists or is being implemented (brief §139's own explicit "no need to implement a
paging system").

## General approach

1. Check `GET /actuator/health/readiness` and `GET /actuator/health/liveness` first -
   they distinguish "can't serve traffic" from "process itself is wedged."
2. Check container logs (`docker compose logs -f backend` / `frontend` / `postgres`) -
   stdout/stderr only, no in-container log files (brief §170). In staging/production
   these are structured JSON (`OBSERVABILITY.md`'s "Structured logging" section) -
   pipe through `jq` for readability, e.g. `docker compose logs backend | jq
   'select(.level=="ERROR")'`.
3. Note the affected request(s)' `X-Correlation-ID` (returned on every response,
   `CorrelationIdFilter`, and now also inside the JSON body of any error response,
   `ApiError.correlationId`) if a specific user report is involved - every log line
   for that request carries the same id; see `DIAGNOSTICS.md`'s "Something went
   wrong" runbook for the exact search flow.
4. Check the relevant metric trend for the incident window against `METRICS.md`'s
   catalog and `ALERTS.md`'s thresholds (the local Grafana profile,
   `DASHBOARDS.md`, if running, is the fastest way to see this) - confirms
   scope/duration and whether this matches a known alert condition.
5. Fix, verify, document (a brief postmortem note - what happened, what was affected,
   what changed).

## Application down

- Symptom: `/actuator/health/liveness` unreachable or the container itself has
  exited/is restart-looping.
- Check: `docker compose ps`, then `docker compose logs backend` for the crash reason
  (an unhandled startup exception, `IllegalStateException` from a missing seed row,
  etc. - never silent).
- Action: fix the root cause, redeploy. If the previous release was known-good,
  consider `docs/releases/RELEASE_PROCESS.md`'s rollback procedure first, investigate
  after service is restored.

## Database unavailable

- Symptom: `/actuator/health/readiness` reports `DOWN` with a `db` component failure
  (locally only - production never shows component detail, per
  `show-details: never`; check logs instead in production).
- Check: is the database instance itself reachable (network/firewall/credentials), or
  is it a connection-pool exhaustion (HikariCP logs "Connection is not available")?
- Action: restore connectivity/credentials, or scale the pool only with evidence (brief
  §27 - never blindly raise `maximum-pool-size`). The backend will resume automatically
  once the database is reachable again - no manual restart needed unless HikariCP itself
  gave up (its own retry/timeout settings, `application.yml`).

## Migration failed on deploy

- Symptom: the backend container never becomes healthy; logs show a Flyway error at
  startup.
- Action: **do not run `flyway repair` automatically** (brief §29) - investigate the
  actual migration first. If the previous release was healthy, roll back the code
  deploy (redeploy the previous image tag) while the migration is fixed; the database
  itself is unaffected by a failed migration attempt (Flyway wraps each migration in a
  transaction where the database supports DDL transactions, which PostgreSQL does).

## Mail unavailable

- Symptom: registration/password-reset emails aren't arriving; `email.send.failure`
  log lines and the `email.send.failure{type=...}` metric climbing (`METRICS.md`).
- Impact, by design (brief §57, already implemented in `RegistrationService`/
  `PasswordResetService`): a verification-email failure leaves the account `PENDING_
  VERIFICATION` (the user can resend); a password-reset email failure still returns the
  same generic API response (never reveals account existence) - the failure is an
  operational problem, not a user-visible security leak.
- Action: check the SMTP provider's own status/credentials (`EMAIL_PRODUCTION.md`);
  application health is unaffected (`management.health.mail.enabled: false` - a mail
  outage never fails the readiness probe, by design).

## Incorrect legal content published

See `LEGAL_CONTENT_MONITORING.md` and the dedicated procedure below.

## Rule misconfiguration

- Symptom: a `Recommendation` is unexpectedly `NOT_APPLICABLE`/`MORE_INFORMATION_
  REQUIRED`/a false `PRIMARY_MATCH` for real users, or `rule.evaluation.error`
  (`METRICS.md`) climbs for a specific `ruleType`/`errorCategory` tag combination -
  `DIAGNOSTICS.md`'s "Recommendation missing / unexpected" runbook is the fastest
  path from a metric spike to the specific rule.
- Action: **never hand-edit a published `RuleVersion` row.** Use the real Admin
  workflow to create a corrected draft version, get it reviewed/approved, and publish
  it (the same DRAFT → REVIEW → APPROVE → PUBLISH cycle every real Rule in this
  codebase already goes through - see `docs/legal-content/PRODUCTION_RULE_COVERAGE.md`).
  Every historical `RecommendationRun` remains exactly as it was computed
  (`RecommendationEngineIntegrationTest`'s own "immutable historical reproducibility"
  guarantee) - a corrected Rule only affects analyses run after the fix.

## A source becomes outdated

- Not an emergency by itself - use the real Admin source-verification workflow
  (`AdminSourceController`) to mark it `NEEDS_REVIEW`/`OUTDATED` with notes, same as
  Phase 10/10.5 already did for sources this project itself couldn't reach. See
  `LEGAL_CONTENT_MONITORING.md`'s recurring review process.

## Suspected security incident

1. Do not attempt to "clean up" logs/audit records - `AuditLog` is append-only by
   design and is itself part of the evidence trail.
2. Rotate any credential that may have been exposed (database password, SMTP
   credentials, admin bootstrap credentials if ever logged - they are not, by design,
   but rotate on suspicion regardless).
3. Check `AuditLog` for the affected time window - every admin/content mutation is
   recorded with its actor (Phase 9's own governance model).
4. If a specific account is compromised: an `ADMIN` can already deactivate/lock it
   through the existing user-management surface - no new tooling needed for this first
   release.
5. Document what happened, even if contained quickly - this is exactly the kind of
   record `docs/releases/RELEASE_PROCESS.md`'s release-notes "Security changes" section
   exists to eventually reference.

---

## Bad legal content incident (detailed procedure, brief §140)

If a published `Procedure`/`Rule`/`Threshold` version is found to be materially wrong
(a fee, deadline, document requirement, or eligibility condition that doesn't match the
real official source):

1. **Do not SQL-delete the bad publication.** History must be preserved (brief §83).
2. **End-date it**: use the real Admin publish workflow to set an `effectiveTo` on the
   bad version (archiving it) - it stops being the *active* version at the Active-
   Version Predicate's evaluation date, but the row and its full history remain.
3. **Stop new confident recommendations if necessary**: if the error affects
   eligibility (not just informational text), consider temporarily un-publishing the
   associated `Rule` (same archive mechanism) so `RecommendationService` stops
   surfacing it as a confident match until the content is fixed - a Procedure/Rule
   with no active published version already, structurally, produces
   `UNAVAILABLE_FOR_ANALYSIS` rather than a confident wrong answer (verified this
   session, Phase 10.5's own end-to-end tests).
4. **Publish the corrected version** through the real DRAFT → REVIEW → APPROVE →
   PUBLISH workflow, with a corrected `OfficialSource` citation.
5. **Preserve old `UserCase`s/`Recommendation`s** - never mutate a user's existing case
   silently (ARCHITECTURE.md §5's own long-standing rule); a `UserCase` created against
   the bad version keeps its own snapshot and, per the existing requirement-change
   mechanism, would surface an explicit, opt-in diff to the user rather than silently
   changing under them.
6. **Identify affected historical cases** - `SELECT count(*) FROM user_cases WHERE
   procedure_version_id = '<bad-version-id>'` (or the equivalent Admin-panel impact
   view, `AdminProcedureController`'s existing `/impact` endpoint pattern) gives the
   real count.
7. **User notification** - out of scope for this release (no notification system
   exists yet); tracked as a Phase 12+ product decision, not something to improvise
   ad hoc during an incident.
8. **Every action above is already audited** by the real Admin workflow - confirm the
   `AuditLog` entries exist rather than assuming.
