# Phase 2 Completion Report — Authentication + User Management

Date: 2026-09-01

## Authentication architecture

```
Browser (Angular)
  → same-origin via dev-server proxy locally / reverse proxy in production
  → HttpOnly SESSION cookie (+ readable XSRF-TOKEN cookie for CSRF)
  → Spring Security (manual controller-driven login, not the default filter -
    see LoginService's Javadoc for why)
  → Spring Session JDBC (session data persisted, not in-memory)
  → PostgreSQL 18 (SPRING_SESSION / SPRING_SESSION_ATTRIBUTES tables)
```

Full rationale is in [ADR-005](../architecture/ADR/ADR-005-authentication-strategy.md),
updated this phase with the session-storage decision and a real finding: Angular's
built-in CSRF interceptor deliberately refuses to attach its header on genuinely
cross-origin requests, so local dev now runs through an Angular CLI dev-server proxy
(`frontend/proxy.conf.json`) rather than pointing straight at `localhost:8080` — this
also makes local dev topology mirror production's same-origin-behind-a-reverse-proxy
design instead of being a special case.

## Database

Six new Flyway migrations (`backend/src/main/resources/db/migration/`):

| Migration | Contents |
|---|---|
| V1 | `users` (case-insensitive email via a functional `lower(email)` unique index) |
| V2 | `roles`, `user_roles` |
| V3 | `email_verification_tokens`, `password_reset_tokens` (hash-only storage) |
| V4 | `user_consents` (ToS/Privacy Policy acceptance, append-only) |
| V5 | Spring Session JDBC's official `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` schema, copied verbatim from the resolved dependency version, applied via Flyway (never Spring Session's own auto-init) |
| V6 | Seeds the `USER` role |

Verified applying cleanly against a fresh Postgres 18 instance every backend test run
(Testcontainers) and against the real docker-compose instance manually.

## API

```
POST /api/v1/auth/register             public
POST /api/v1/auth/login                public (establishes session)
POST /api/v1/auth/logout               declarative Spring Security logout (session invalidation + cookie clear)
POST /api/v1/auth/verify-email         public
POST /api/v1/auth/resend-verification  public, rate-limited, generic response
POST /api/v1/auth/forgot-password      public, rate-limited, generic response
POST /api/v1/auth/reset-password       public

GET   /api/v1/users/me                 authenticated
PATCH /api/v1/users/me                 authenticated (first name / preferred language only)
POST  /api/v1/users/me/change-password authenticated (invalidates all sessions)
```

All documented via springdoc `@Operation`/`@Tag` annotations (Swagger UI at
`/swagger-ui.html`, local/staging only).

## Security

- **Password hashing**: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  (bcrypt by default, upgradeable later without rewriting stored hashes).
- **Password policy**: min 10 / max 128 characters, no composition rules — deliberately
  avoids the "one uppercase, one digit, one symbol" pattern that fights password
  managers for no real security benefit.
- **Cookie policy**: `SESSION` (not `JSESSIONID`), `HttpOnly`, `SameSite=Lax` in every
  environment, `Secure=false` locally (plain HTTP) / `true` in staging+production
  (HTTPS). `XSRF-TOKEN` is the one intentionally non-HttpOnly cookie (Angular/Spring
  Security's shared convention).
- **CSRF**: enabled everywhere (Phase 1's blanket `csrf().disable()` removed).
  `CookieCsrfTokenRepository` + a `CsrfCookieFilter` that force-resolves the deferred
  token on every response (Spring Security's own documented SPA recipe). Proven, not
  assumed: `AuthIntegrationTest` has explicit "rejected without token / accepted with
  token" cases through the real filter chain.
- **CORS**: `allowCredentials(true)` with an explicit configured origin, never `*`
  — kept for direct (non-proxied) clients and exercised by the MockMvc integration
  tests, even though local dev's Angular now goes through same-origin proxying instead.
- **Rate limiting**: an in-memory, single-instance `RateLimiter` (documented as
  replaceable by a gateway/Redis-based limiter later) gates resend-verification and
  forgot-password by email address; login brute-force uses a separate, *persistent*
  mechanism.
- **Account lock strategy**: 5 failed attempts → 15-minute lock
  (`app.auth.max-failed-login-attempts` / `lockout-duration`, centralized, not scattered
  literals). A lock past its expiry self-clears on the next successful login.
- **Token design**: 32 bytes of `SecureRandom`, base64url-encoded for the emailed raw
  token, SHA-256 hex for the persisted hash — the raw value never touches the database
  or a log line. Verification tokens: 24h TTL. Reset tokens: 30min TTL (shorter — a
  reset token grants a password change, a higher-value target if leaked).
- **Session invalidation**: password reset and change-password both invalidate *every*
  active session for the account (via Spring Session's principal-name index) — for
  change-password this includes the session that made the request itself, a documented
  simplification the brief explicitly allows over the more complex "keep this one"
  variant.
- **401 vs 403 vs 404**: formalized and tested — unauthenticated + non-public path → 401
  regardless of route existence (prevents a 404 from becoming an existence oracle);
  authenticated + insufficient authority → 403 (wired, not yet exercised by any
  role-gated endpoint); authenticated + genuinely unmapped route → 404.
- **Account enumeration**: login/forgot-password/resend-verification give identical
  responses regardless of account existence (enforced partly by Spring Security's own
  `DaoAuthenticationProvider` default of masking "unknown user" as "bad credentials").
  Registration is the one documented exception — it reveals a duplicate email, a
  deliberate trade-off explained in `RegistrationService`'s Javadoc.
- **Security event logging**: `USER_REGISTERED`, `EMAIL_VERIFIED`, `LOGIN_SUCCESS`,
  `LOGIN_FAILURE`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`,
  `PASSWORD_CHANGED`, `ACCOUNT_LOCKED` — structured, user-ID-only (never email), never
  passwords/tokens. A formal queryable `AuditLog` remains Phase 9's job.

## Emails

`EmailService` (generic send, swallows failures so a bad send never rolls back an
already-committed registration — brief §33) → `VerificationEmailService` /
`PasswordResetEmailService` (subject + HTML body, links built from
`app.auth.frontend-base-url`, never hard-coded). Captured by Mailpit locally
(http://localhost:8025) and by a throwaway Testcontainers Mailpit in backend
integration tests.

## Frontend

**Screens**: Register, Login, Verify Email (with inline resend), Forgot Password,
Reset Password, and a minimal Dashboard that only proves authenticated navigation
works (brief §29 — the real case dashboard is Phase 8).

**Auth state**: `AuthService` (signals: `authState` = `UNKNOWN | AUTHENTICATED |
UNAUTHENTICATED`, `currentUser`, `isAuthenticated` computed) — resolved once at startup
via `provideAppInitializer` before the app renders, specifically to prevent route-guard
flicker on reload. Stores only the non-sensitive `CurrentUser` summary; no token,
password, or session identifier ever touches Angular state or storage.

**Guards**: `authGuard` (redirect to `/login`), `guestGuard` (redirect an already-signed-
in user away from `/login`/`/register`) — both explicitly documented as UX only; the
backend is authoritative regardless (proven by `AuthIntegrationTest`'s 401 tests, which
don't involve the frontend at all).

**CSRF integration**: `provideHttpClient(withXsrfConfiguration(...), 
withInterceptors([credentialsInterceptor]))` plus the dev-server proxy — Angular's
built-in XSRF cookie/header handling, made to actually work locally by removing the
cross-origin condition it refuses to operate under (see ADR-005).

## Tests

| Suite | Count | Result |
|---|---|---|
| Backend unit (Mockito, fixed-`Clock` token lifecycle) | 20 | ✅ pass |
| Backend integration (`AuthIntegrationTest`, real Postgres 18 + real Testcontainers Mailpit, full Spring Security filter chain) | 17 | ✅ pass |
| Backend Phase 1 regression (`BackendApplicationTests`, `PlatformStatusControllerTest`) | 4 | ✅ pass |
| Backend UserAccountServiceTest | 3 | ✅ pass |
| **Backend total** | **44** | ✅ **0 failures** |
| Frontend unit (Vitest: `AuthService`, both guards, `Login`, `Register`, `Dashboard`, `App`, `Home`) | 23 | ✅ pass |
| Playwright E2E (`home.spec.ts` Phase 1 + `auth.spec.ts`'s 4 named scenarios, real backend/Postgres/Mailpit) | 6 | ✅ pass |

The 17 backend integration tests cover, concretely: registration (hashing, role
assignment, verification-token creation, real email arrival), duplicate-email
conflict, valid/expired/used verification and reset tokens, login before/after
verification, wrong password, unknown email, session cookie issuance, `/users/me`
with/without a session, CSRF rejected-without/accepted-with-token, logout invalidating
the session, forgot-password's identical generic response for known/unknown emails,
full reset-password lifecycle including replay rejection, change-password's
current-password check and session invalidation, unauthenticated-vs-authenticated
authorization, and 404 on a genuinely unmapped authenticated route.

## Verification (commands actually executed)

```
./mvnw clean verify                    → BUILD SUCCESS, 44/44 tests (run 3× total during this phase)
./mvnw spotless:apply                  → used once after adding all new files
npm ci                                 → clean install (one retry needed, see Known Issues)
npx ng lint                            → all files pass
npx ng test                            → 23/23 pass
npx ng build                           → production bundle, 335 kB initial (budget: 500 kB/1 MB)
docker compose config / ps             → validated, both services healthy
mvnw spring-boot:run (local profile)   → started clean against real Postgres, 6/6 migrations applied
npx playwright test                    → 6/6 pass, run 3 times total (including after the CSRF proxy fix and once fully fresh)
```

## Deviations from the specification (and why)

1. **Hibernate cascade bug found and fixed**: `User.roles` originally cascaded
   `PERSIST`, which made Hibernate try to re-persist the already-seeded, already-managed
   `Role` entity on every registration ("detached entity passed to persist"). Removed
   the cascade — role rows are seeded independently and should never be write-cascaded
   from a `User` save. Caught by the integration suite, not assumed correct.
2. **Angular's default XSRF handling doesn't work cross-origin** (see Security/ADR-005
   above) — fixed with a dev-server proxy rather than a manual workaround interceptor,
   because the proxy also makes local dev topology match production's same-origin
   design, which a bypass interceptor would not have.
3. **MockMvc + Spring Session session-continuity quirk**: `MvcResult.getRequest()`
   doesn't reflect the session Spring Session's filter wraps the request with, so
   integration tests reuse the actual `SESSION` cookie value across calls (exactly as
   a browser would) rather than reusing a `MockHttpSession` object reference.
4. **`/actuator/health`'s mail sub-indicator disabled** (`management.health.mail.enabled:
   false`): a transient SMTP-reachability hiccup against the Testcontainers Mailpit
   caused one flaky 503 during this phase's own testing. A degraded external mail
   dependency shouldn't fail the whole liveness/readiness rollup — a real, defensible
   production posture, not just a test workaround.
5. **`AuthIntegrationTest` (Playwright) runs serially, not with full parallelism**:
   `auth.spec.ts` needed `test.describe.configure({ mode: 'serial' })` after a genuine,
   load-dependent flake (never reproduced in isolation) surfaced when 6 tests hit one
   shared backend/database/Mailpit concurrently. The backend's own
   `AuthIntegrationTest` runs under Surefire's default (already sequential per class)
   and needed no such change.
6. **`SecurityEventLogger`/`SessionInvalidator` moved to `common.security`** rather than
   living in the `auth` package as first drafted — `user` package code needed them too,
   and leaving them in `auth` would have made `auth` and `user` depend on each other
   both ways.
7. **`Region`-not-`Voivodeship` and other Phase 0 deviations** carry forward unchanged
   from prior phases; nothing new deviates from the approved Java 25/Boot 4.1.x/
   PostgreSQL 18/Angular 22/Spring Session JDBC stack.

None of the above required deviating from anything explicitly specified in this
phase's brief — every item is either a bug the test suite caught and fixed, or a
documented judgment call the brief itself allowed ("if disproportionate at MVP stage,
X is acceptable").

## Known issues

1. **`npm ci` hit a transient `EBUSY` file lock on the first attempt** (Windows,
   likely OneDrive syncing `node_modules` mid-install) — succeeded immediately on
   retry. Not a code issue; consistent with the Windows file-lock quirks already noted
   in Phase 1's report.
2. **CI has not been run on a real GitHub Actions runner for Phase 2** (same caveat as
   Phase 1 — no GitHub remote connected in this environment). Every step was verified
   locally with the equivalent command; the workflow file itself remains
   unverified-in-CI until the first real PR.
3. **No formal, queryable `AuditLog` table yet** — Phase 2 deliberately ships only the
   structured security-event log foundation (brief §23 explicitly allows deferring the
   full audit table to the phase that needs it, Phase 9's admin panel).
4. **Occasional Windows file-lock on `mvnw clean`** — same pre-existing quirk noted in
   Phase 1, unrelated to Phase 2's changes; workaround (`rm -rf backend/target`)
   documented in LOCAL_SETUP.md.

## Phase 3 readiness

**READY.**

Every Phase 2 acceptance criterion (brief §55) was checked against a real, executed
command or a passing automated test rather than assumed: registration, hashing, email
verification (real Mailpit delivery), resend, login (including reject-before-
verification), session cookie issuance and Spring Session JDBC persistence, session
survival across a real page reload, `/users/me`, logout, forgot/reset password
(including one-time-use enforcement), change password, automatic `USER` role
assignment, server-side authorization independent of the frontend, the 401/403/404
convention, CSRF (rejected without a token, accepted with one), CORS with credentials,
Mailpit capture, all five Angular auth screens, both route guards, zero tokens stored
client-side, the full backend/frontend/Playwright test suites, Flyway migrating cleanly
from an empty database, and updated documentation (ADR-005, LOCAL_SETUP.md, README,
CLAUDE.md). Nothing here blocks starting Phase 3 (Reference/Geographic Data).
