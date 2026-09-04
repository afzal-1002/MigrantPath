# Logging Privacy Policy

Canonical Phase 12. What must never appear in an application log line, and what's
already been verified against this codebase (not assumed).

## Never log

```text
Passwords (raw or hashed)
Verification/reset tokens (raw - only the hash is ever persisted, and even the hash is
  never logged either)
Session identifiers
CSRF token values
Full assessment answer bodies (salary, date of birth, citizenship, legal status, etc.)
Case notes (UserCaseDocument.userNote)
Export payloads (the personal-data export itself is never logged - see
  AccountExportService, which logs only PERSONAL_DATA_EXPORT_REQUESTED/COMPLETED audit
  events with an account id, never the exported content)
Raw email addresses in security-event logs where an internal id would do (see below -
  this one is only partially achieved today, disclosed honestly)
```

## What's already verified (re-checked this phase, not newly built)

- `SecurityEventLogger` logs `userId`, never email, for every event by design (its own
  Javadoc: "user identity is the user ID, never the email, to keep PII out of log
  aggregators by default").
- `GlobalExceptionHandler` logs full exception detail server-side but returns only a
  generic message to the client - never a stack trace or internal detail leaks to a
  caller.
- `AuditLog.metadata` is deliberately minimal, structured before/after value pairs
  only, never a full entity dump (its own Javadoc, unchanged this phase).
- `AccountDeletionService`/`AccountExportService` audit calls carry no email, no
  answer content, no export payload - only the account's own UUID as `entityId`.
- `TokenCleanupService` logs only counts of tokens removed, never a token value or hash.

## Known, disclosed gap

Some existing log lines (e.g. registration/login flow debug logging in the `local`
profile, `logging.level.com.foreignerwarsaw: DEBUG`) may include the email address at
DEBUG level in local development - this is a real, pre-existing, low-risk gap (DEBUG
logging is never enabled in `application-staging.yml`/`application-production.yml`,
both of which inherit the base `application.yml`'s non-DEBUG default) rather than a
production concern, but it is named here rather than silently assumed clean. A future
pass could audit every DEBUG-level log statement for stray PII the way this phase
audited security-event/audit logging specifically.

## Structured (JSON) logging - future scope

Not implemented yet (`docs/operations/OBSERVABILITY.md`'s own disclosed gap). When it
is, the field allowlist approach that document names must be cross-checked against this
policy before shipping - a structured logger makes it easier, not harder, to
accidentally emit a field like `answer.value` verbatim if the encoder is configured
naively (e.g. logging a full request/response object). Correlation ids remain
operational-only (random, no personal content) regardless of logging format.

## Browser storage audit (canonical brief item)

`grep -r "localStorage\|sessionStorage\|indexedDB" frontend/src` - zero matches,
verified this phase. The frontend keeps no personal data (not even the current-user
summary) in any browser storage mechanism - `AuthService`'s `currentUser`/`authState`
are plain in-memory Angular signals, reset on every page load and re-populated via a
real `/api/v1/users/me` call (`app.config.ts`'s app initializer). The only client-side
persistence at all is the two cookies documented in `docs/privacy/
COOKIE_INVENTORY.md` (`SESSION`, `XSRF-TOKEN`), both first-party, both already
HttpOnly/Secure or scoped appropriately. No auth token, assessment answer, or case
payload is ever written to browser storage.

## Error monitoring - future scope

Not integrated yet. When it is, request bodies, cookies, and Authorization headers must
be excluded/redacted by that integration's own configuration before any event leaves
this process - the same list above applies to error reports, not just log lines.
