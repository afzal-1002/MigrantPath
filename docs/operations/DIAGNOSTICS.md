# Diagnostics Runbook

Status: real, concrete flows, each keyed off a signal Canonical Phase 14 actually
wired (structured logs, correlation IDs, the metric catalog in `METRICS.md`) - not a
speculative encyclopedia.

## User reports "Something went wrong"

1. Ask for the **Reference** id shown in the error message (`ApiError.correlationId`,
   also the `X-Correlation-ID` response header - Canonical Phase 14).
2. In staging/production's JSON logs, search for that exact `correlationId` field
   value - every log line for that request, across every filter/service/exception
   handler, carries it (MDC-threaded, `CorrelationIdFilter`).
3. Identify the failing endpoint/error code from the matched lines (the request-
   summary line - `RequestLoggingFilter` - shows method/path-template/status/
   duration; an `ERROR`-level line from `GlobalExceptionHandler.handleUnexpected`
   shows the real exception, server-side only).
4. Cross-reference the relevant metric trend for that time window (`METRICS.md`) -
   was this an isolated event or part of a spike?
5. **Do NOT** open unrelated private user data (another user's assessment/case/export)
   while investigating - the correlation id and the structured logs above are
   sufficient for almost every real issue; if genuinely more context is needed,
   follow `docs/privacy/DATA_SUBJECT_REQUESTS.md`'s own access discipline, not an ad
   hoc database query.

## Recommendation missing / unexpected

1. Check `rule.evaluation.error` for the relevant time window - a spike means a real
   content/configuration defect, not a user-data issue.
2. Check `recommendation.partial`/`.failed` - a PARTIAL run means at least one
   candidate hit `UNAVAILABLE_FOR_ANALYSIS` (no active published content for that
   procedure); a FAILED run means the whole analysis threw.
3. Confirm the relevant `RuleVersion`/`ProcedureVersion` is genuinely `PUBLISHED` and
   effective on the evaluation date (`docs/legal-content/PRODUCTION_RULE_COVERAGE.md`) -
   a rule with no active version is silently skipped, by design (never surfaced as
   `ERROR`), so "recommendation missing" is very often a content-coverage question,
   not a bug.
4. Check `legal.sources.outdated`/`legal.content.with_outdated_source` - a genuinely
   outdated source does not make anything technically fail, but is worth knowing when
   explaining a surprising result to a user or content reviewer.
5. Correlate with the actual request's `correlationId` (its structured log line
   includes the assessment id - operational, not personal data by itself) if the user
   can supply one.

## Login/auth issue

1. `auth.login.failure` trend - a spike may indicate a credential-stuffing attempt
   (brief §80/§81) or a genuine outage upstream (e.g. session store).
2. Structured logs never contain the password or a full email by default
   (`LOGGING_PRIVACY.md`) - do not expect to find one; that absence is correct
   behavior, not a missing diagnostic.
3. Check Hikari pool metrics (`hikaricp.connections.active`/`.pending`) if login is
   slow/timing out broadly - a connection-pool exhaustion looks like "everyone's login
   is slow," not an auth-specific bug.

## Email not received (verification/reset)

1. `email.send.failure{type=VERIFICATION|PASSWORD_RESET}` - did the send actually
   fail, or is it a delivery/spam-filter issue outside this application's visibility?
2. `docs/operations/EMAIL_PRODUCTION.md` for the provider's own delivery
   dashboard/logs, if send succeeded on this side.
3. Never search logs for the verification/reset link itself - it is never logged
   (`EmailService`'s own Javadoc; confirmed by `LoggingPrivacyRegressionTest`).

## Cleanup job / scheduled task silent failure

1. `token.cleanup.run` vs `token.cleanup.failure` - a run count that stops advancing,
   or a failure count that jumps, is the signal (exactly the class of bug Phase 13
   found manually before this metric existed - see `ROLLBACK.md`/`PHASE_13_REPORT.md`).
2. `token.cleanup.deleted` - a persistently-zero count over a long window may indicate
   the job is running but never matching anything (worth a second look, not
   necessarily broken).

## Privacy operation (export/deletion) issue

1. `account.export.failed`/`account.deletion.failed` - counts only, never a user
   identifier.
2. The two-row `AuditLog` pair for that operation (`ACCOUNT_DELETION_REQUESTED`/
   `COMPLETED`, `PERSONAL_DATA_EXPORT_REQUESTED`/`COMPLETED`) is the durable,
   governance-facing record if a specific user's own request needs following up -
   see `docs/privacy/DATA_SUBJECT_REQUESTS.md`, never a raw database query outside
   that documented process.

## Database unavailable

1. `/actuator/health/readiness` reports `DOWN` - the application itself already
   refuses to claim readiness (verified this phase - see "Failure Exercises" in
   `docs/product/PHASE_14_REPORT.md`).
2. The startup/request logs show a clear `PSQLException`/Hikari connection failure -
   never a credential value (confirmed - see the same failure exercise).
3. Hikari's own metrics (`hikaricp.connections.*`) show the pool starved/empty.

## SMTP unavailable

1. `/actuator/health/readiness` stays `UP` (mail is deliberately excluded from
   readiness - a well-known health-check anti-pattern this codebase avoids, see
   `OBSERVABILITY.md`).
2. `email.send.failure` climbs; a WARN-level log line names the failure (never
   message content).
3. User-facing registration/reset flows still succeed (the database write always
   commits before the email send is attempted) - only the email itself is affected.
