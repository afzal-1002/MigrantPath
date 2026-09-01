# ADR-005: Authentication strategy

Status: Accepted — 2026-09-01; session-storage detail added 2026-09-01 (Phase 2)

## Context

MVP needs secure account registration/login without over-collecting data at signup, and
without ruling out social login later. Bearer tokens in `localStorage` are vulnerable to
XSS exfiltration.

## Decision

Email + password authentication for MVP (registration requires only email, password,
and ToS/Privacy acceptance — no 30-field signup form). Roles: `USER`, `ADMIN` in MVP;
the role model reserves `CONSULTANT`, `LEGAL_REVIEWER`, `CONTENT_EDITOR`,
`COMPANY_ADMIN` for later without a schema redesign. OAuth2 Client dependency is
included but Google/Apple/Microsoft login is not implemented until a later phase.

### Session architecture (Phase 2)

```
Browser → Angular → HttpOnly session cookie → Spring Security → Spring Session → PostgreSQL
```

**Selected**: Spring Security server-side sessions, persisted via **Spring Session
JDBC** against the same PostgreSQL instance, identified to the browser by an `HttpOnly`
session cookie (`SESSION`, not the default `JSESSIONID`, to avoid signaling "this is a
Java app").

**Rejected for the web MVP**: JWT access/refresh tokens stored in `localStorage`,
`sessionStorage`, or IndexedDB.

**Why JDBC-backed sessions over in-memory**:
- Server-controlled and revocable — a password reset or admin action can kill a
  specific session by deleting its row, which an in-memory session (or a stateless JWT)
  cannot do without an additional denylist mechanism.
- Not dependent on a single application instance's memory — sessions survive an
  application restart and are visible to more than one backend instance, which matters
  the moment this stops being a single-instance deployment (ARCHITECTURE.md §13).
- No new infrastructure dependency for Phase 2 — it reuses the PostgreSQL instance
  already required for everything else, rather than introducing Redis before there's a
  concrete scaling reason to.

**Future**: if session volume or multi-instance latency ever makes JDBC session storage
a bottleneck, migrate the session *store* to Redis (`spring-session-data-redis`) without
touching the authentication model itself — Spring Session's abstraction is exactly what
makes that swap additive rather than a rearchitecture.

### Local development topology: a dev-server proxy, not raw cross-origin

Discovered while actually running the Playwright suite in a real browser (not assumed):
Angular's built-in XSRF interceptor deliberately does **not** attach the
`X-XSRF-TOKEN` header on a genuinely cross-origin request (`xsrfInterceptorFn` compares
`location.origin` against the request's origin and no-ops if they differ) — a real
browser-security feature, not a bug, meant to stop a page from leaking its CSRF token
to a third-party origin it happens to call. Pointing the frontend straight at
`http://localhost:8080` therefore made every unsafe request fail CSRF validation in a
real browser, even though tooling that attaches the header manually (MockMvc-based
backend tests, curl) never showed the problem.

**Selected**: the Angular CLI's dev-server proxy (`frontend/proxy.conf.json`, wired into
`angular.json`'s `serve` target) forwards `/api` and `/actuator` to `localhost:8080`
server-side, so the browser only ever talks to `localhost:4200` — same-origin, from
Angular's perspective, exactly mirroring production's reverse-proxy topology
(ARCHITECTURE.md §13) instead of being a special cross-origin case. This is also why
`environment.development.ts`'s `apiBaseUrl` is a relative `/api/v1`, identical to
`environment.ts` (production) — local dev and production now share the same
same-origin assumption end to end.

CORS support (`SecurityConfig`'s `CorsConfigurationSource`) is kept regardless — it's
what the backend's own MockMvc-based integration tests exercise directly (they don't go
through the Angular dev-server proxy), and it's what any future direct, non-proxied
client (a mobile app, a different frontend) would need.

**Schema ownership**: Spring Session JDBC's tables are created by a Flyway migration
(`SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES`, following Spring Session's official
PostgreSQL schema), not by Spring Session's own auto-schema-initialization — consistent
with ADR-002's "Flyway owns the schema" rule.
`spring.session.jdbc.initialize-schema=never` enforces this at the framework level so
this can never silently drift.

**CSRF**: enabled (not disabled — Phase 1's blanket `csrf().disable()` was explicitly a
placeholder for "no session/cookie auth exists yet," documented as such at the time).
Cookie-based session authentication is exactly the case CSRF protection exists for.
Spring Security's SPA-friendly `CookieCsrfTokenRepository` is used: the CSRF token
itself is exposed via a **non-HttpOnly** `XSRF-TOKEN` cookie (readable by Angular's
`HttpClient`, which already knows to read `XSRF-TOKEN` and send it back as an
`X-XSRF-TOKEN` header — this is Angular's built-in convention, not custom code) while
the session cookie itself stays `HttpOnly`. Angular never stores or manually manages
either cookie's value.

**Cookie policy by environment**: `SameSite=Lax` in every environment — `localhost:4200`
and `localhost:8080` are different origins but the *same site* (same registrable
domain, `localhost`), so `SameSite=Lax` cookies are sent on the cross-origin XHR/fetch
requests local development needs, without requiring `SameSite=None` (which itself would
force `Secure`, which local plain-HTTP development can't satisfy). `Secure` is `false`
locally (plain HTTP) and `true` in staging/production (HTTPS-only, reverse-proxy
terminated, same-origin per ARCHITECTURE.md §13).

## Consequences

- Lower XSS blast radius than `localStorage` tokens; CSRF defenses are required (and
  implemented, not just noted as a future task) because cookie auth is what makes CSRF
  a real risk.
- Adding social login later is additive (new `AuthenticationProvider`s), not a
  rearchitecture, because the session layer is already cookie-based.
- Role-based authorization is enforced server-side on every mutating endpoint regardless
  of what the frontend shows.
- Session storage is a PostgreSQL table today; swapping to Redis later is a
  configuration/dependency change, not an authentication redesign.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §11 and
[DATABASE.md](../../database/DATABASE.md) §1.
