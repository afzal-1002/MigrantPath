# Cookie Inventory

Status: a complete, accurate list of every cookie this application actually sets -
verified against `SecurityConfig`/`CsrfCookieFilter`/Spring Session defaults, not
assumed. Backs the real `/cookies` page.

| Cookie | Set by | Purpose | Lifetime | Notes |
|---|---|---|---|---|
| `JSESSIONID` | Spring Session (framework default) | Authenticated session identity - the only thing that keeps a user logged in between requests. | Session (deleted when the browser closes) or server-side session timeout, whichever first. | `HttpOnly`, `Secure` (when served over HTTPS), `SameSite=Lax` (Spring Security defaults, unchanged this project). Never readable by JavaScript. |
| `XSRF-TOKEN` | `CsrfCookieFilter` (this app's own code, ADR-005) | CSRF protection - read by Angular's `HttpXsrfInterceptor` and echoed back as the `X-XSRF-TOKEN` header on every unsafe request. | Session. | Deliberately **not** `HttpOnly` (the double-submit pattern requires JavaScript to read it) - `Secure` when served over HTTPS, `SameSite=Lax`. Contains no personal data, just an opaque token. |

## What this application deliberately does NOT set

- No analytics cookies (no analytics tooling is integrated - brief §108).
- No advertising/marketing cookies.
- No third-party cookies of any kind - both cookies above are first-party, set only by
  this application's own backend, on its own origin (the same-origin reverse-proxy
  topology in ADR-013 means the frontend never talks to a separate cookie-setting
  origin).
- No preference/"remembered settings" cookies exist yet (no such feature exists).

## Consent

Because both cookies set are strictly necessary (session authentication and CSRF
protection - there is no functioning login without them), no cookie-consent banner is
implemented; under the EU ePrivacy Directive's own "strictly necessary" exemption, a
consent banner is not required for cookies used solely for this purpose. If a future
phase adds any non-essential cookie (analytics, preferences, etc.), a real consent
mechanism must be added before that cookie is ever set to a user who hasn't consented -
tracked here as a forward-looking constraint on future work, not a promise something
is already built.
