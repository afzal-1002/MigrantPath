# ADR-005: Authentication strategy

Status: Accepted — 2026-09-01

## Context

MVP needs secure account registration/login without over-collecting data at signup, and
without ruling out social login later. Bearer tokens in `localStorage` are vulnerable to
XSS exfiltration.

## Decision

Email + password authentication for MVP (registration requires only email, password,
and ToS/Privacy acceptance — no 30-field signup form). Session/credential handling uses
secure, HTTP-only cookies rather than storing tokens in browser storage, with CSRF
protection and CORS restrictions configured to match. Roles: `USER`, `ADMIN` in MVP; the
role model reserves `CONSULTANT`, `LEGAL_REVIEWER`, `CONTENT_EDITOR`, `COMPANY_ADMIN` for
later without a schema redesign. OAuth2 Client dependency is included but Google/Apple/
Microsoft login is not implemented until a later phase.

## Consequences

- Lower XSS blast radius than `localStorage` tokens; requires CSRF defenses that a pure
  bearer-token API wouldn't need.
- Adding social login later is additive (new `AuthenticationProvider`s), not a rearchitecture,
  because the session layer is already cookie-based.
- Role-based authorization is enforced server-side on every mutating endpoint regardless
  of what the frontend shows.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §11.
