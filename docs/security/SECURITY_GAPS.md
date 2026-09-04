# Security Gap Register

Canonical Phase 12 (Security/Privacy/GDPR). Only real, currently-unresolved gaps -
resolved work is not listed here (see `PRODUCTION_SECURITY.md` and `THREAT_MODEL.md`
for what *is* in place).

| Gap | Why it's not closed yet | Real impact |
|---|---|---|
| Rate limiter is single-instance in-memory | Acceptable for a genuine single-instance MVP (explicit scope allowance); a shared store (Redis or equivalent) would be needed before horizontal scaling | Brute-force protection weakens if the app is ever run as >1 instance without also adding shared state |
| No dependency-vulnerability scanner wired into the backend build | OWASP Dependency-Check (or equivalent) was deliberately not added unvalidated this late in the project; frontend `npm audit` was run manually (0 vulnerabilities) | New CVEs in backend dependencies won't surface automatically between manual checks |
| No external/professional security or privacy review | Out of scope for an internal engineering pass; this is a real prerequisite before a genuine public launch with real user data at scale | Internal review (this document set, `THREAT_MODEL.md`, `AuthorizationMatrixTest`) is real but not independently verified |
| No consolidated, exhaustive per-endpoint authorization matrix | `AuthorizationMatrixTest` (canonical Phase 12) covers representative endpoints per boundary category, not all ~70 admin endpoints individually - a deliberate scope decision (brief's own "do not mechanically duplicate all endpoints"), not an oversight | A role-check regression on an endpoint outside the representative set wouldn't be caught by this specific test (existing per-controller tests still cover many of them individually) |
| No GDPR self-service data-portability format beyond JSON | JSON-only export, no CSV/other format | Low impact - JSON is a genuinely portable, machine-readable format; not a security gap so much as a feature-completeness note |
| No dedicated export-endpoint rate limit | Relies on ordinary session/CSRF auth; no per-account throttle specific to repeated export calls | Low impact for a single-instance MVP with no unauthenticated access to this endpoint |
| Backend logging is not yet structured (JSON) | Named in `docs/operations/OBSERVABILITY.md`, unchanged this phase | Makes log-based security monitoring/alerting harder to automate, not a leak itself |
| No error-monitoring integration | Integration point named, not wired (`docs/operations/OBSERVABILITY.md`) | Errors are only visible via server-side logs today, no external alerting |
| One disclosed DEBUG-level logging gap (local profile only) | See `docs/privacy/LOGGING_PRIVACY.md` | Never active in staging/production; local-development-only exposure |

## Explicitly not gaps (named here to prevent future confusion)

- **No backend HTML sanitizer**: this is an intentional plain-text content policy
  (`THREAT_MODEL.md`), not a missing sanitizer - there is nothing to sanitize because
  nothing is ever interpreted as HTML.
- **No CSRF exemptions**: CSRF is enforced on every unsafe endpoint including public
  auth routes, by design (ADR-005) - not a gap to "simplify."
