# Error Tracking

Status: **DOCUMENTED_ONLY.** No external error-tracking service (Sentry or equivalent)
is connected - no account/DSN exists for this project. What this phase built instead is
the real, exact **integration boundary** on both sides, so wiring a real provider later
is a config change, not a code hunt.

## Why not just add the Sentry SDK now (brief §40's own "if provider is not selected...
if useful")

Adding an unvalidated third-party SDK dependency with no real account to test the
integration against (brief §113's own "do not send real external event during test")
was judged higher-risk-than-value for this phase, the same judgment call this project
already made once for structured JSON logging in an earlier phase before actually
adding it once its time came (`docs/operations/OBSERVABILITY.md`'s own history). The
boundary below is real, tested, and is *exactly* where that SDK call goes the day a
provider is chosen - nothing else in the app needs to change.

## Backend integration point

`GlobalExceptionHandler.handleUnexpected` (`backend/src/main/java/com/foreignerwarsaw/
common/web/GlobalExceptionHandler.java`) already `log.error`s every unhandled exception
with full detail server-side and returns only a generic `ApiError` (now carrying
`correlationId`, Canonical Phase 14) to the client. A future Sentry Logback appender
would pick these up with **zero controller-level code change** - Sentry's own Logback
integration hooks the logging framework directly, it does not need a bespoke call at
each throw site.

## Frontend integration point

`frontend/src/app/core/error-handling/global-error-handler.ts` - a real `ErrorHandler`
override (new this phase; previously the framework default), registered in
`app.config.ts`. Currently logs to the console (the same thing Angular's own default
handler already did) and carries an explicit, commented integration point for
`Sentry.captureException(error)` inside `handleError`.

## Privacy scrubbing (brief §41 - mandatory before any real integration)

Whichever provider is eventually chosen, before it is wired for real:

- **Never sent**: cookies, auth headers, assessment answers, salary, DOB, case
  payload, export payload, password/reset tokens, email (by default - an explicit,
  reviewed decision would be needed to change this, not a default).
- **Backend**: only the exception itself (class, message, stack trace - developer-
  facing, not user data) plus `correlationId`/release version/environment.
- **Frontend**: only the `Error` object plus release/environment - no user profile.
- A synthetic-request scrubber test (brief §47 - "a synthetic request containing
  email/token-like value/assessment answer must not appear in captured event") is a
  concrete precondition for connecting a real provider, not optional polish -
  `LoggingPrivacyRegressionTest` already proves the equivalent property for structured
  logs (Canonical Phase 14) and is the template to extend once a real event payload
  exists to assert against.

## Release/environment tagging

Once connected, both sides use the same identity Phase 13 already established:
`BUILD_COMMIT`/`/api/v1/platform/status` on the backend, the same commit on the
frontend build - never a second, disconnected version scheme.

## Local/test behavior

Nothing to disable - no SDK is present at all yet, so there is nothing that could
accidentally fire a real event locally or in CI. Once a provider is wired, the
following remain mandatory (brief §45):

- Local dev never sends to the real service unless explicitly enabled.
- The `test` Spring/Vitest profile is disabled by default.
- An unreachable/misconfigured tracker must never block a user request or fail
  application startup (brief §137/§138) - the SDK's own async/non-blocking transport
  mode is the mechanism for this once wired.

## Status summary

| Component | Status |
|---|---|
| Backend integration boundary (`GlobalExceptionHandler`) | IMPLEMENTED (pre-existing, re-confirmed) |
| Frontend integration boundary (`GlobalErrorHandler`) | IMPLEMENTED (new, Canonical Phase 14) |
| Real provider (Sentry or equivalent) connected | DOCUMENTED_ONLY - no account/DSN |
| PII scrub policy | DOCUMENTED - not yet tested against a real event payload (none exists) |
