# Technical Debt Register

Canonical Phase 15 (Release Readiness). Only genuinely meaningful items — a deliberate,
disclosed architecture decision (single-instance MVP scope, a plain-text content
policy) is not debt and is called out below specifically to avoid being mistaken for
it.

## Real debt

| Item | What it costs | When to pay it down |
|---|---|---|
| Rate limiter is single-instance, in-process | Brute-force protection resets per-instance; would need a shared store (Redis or equivalent) before running >1 backend instance | Before horizontal scaling — not before, per this project's own explicit single-instance MVP scope decision |
| Conditional checklist engine (`RuleTargetType` beyond `PROCEDURE`) is schema-ready, not implemented | A `UserCase` checklist cannot yet personalize which documents/steps/fees apply per user | Real, valuable post-MVP feature work — see `POST_MVP_ROADMAP.md` |
| No backend dependency-vulnerability scanner | New CVEs in backend dependencies surface only via manual review, not automatically | Wire OWASP Dependency-Check (or equivalent) into `./mvnw verify` once validated against this exact build |
| Playwright's local suite worker ceiling (3) is a workaround, not a fix | The underlying cause — every worker shares one single-instance dev backend/Postgres with no per-worker isolation — is unaddressed | Only worth solving if local E2E iteration speed becomes a real bottleneck; a per-worker ephemeral backend/DB is the real fix |
| No Postgres-server-level metrics exporter | Database health beyond Hikari's client-side pool view is invisible to Prometheus/Grafana | Add once a managed Postgres provider's own metrics endpoint is available and worth integrating |
| No consolidated, exhaustive per-endpoint authorization matrix | `AuthorizationMatrixTest` covers representative endpoints per boundary category, not all ~70 admin endpoints individually (a deliberate Phase 12 scope decision) | Low priority — a role-check regression outside the representative set is still likely caught by that endpoint's own controller test |

## Explicitly NOT debt (intentional architecture, named to prevent future confusion)

- **No backend HTML sanitizer** — a deliberate plain-text content policy
  (`THREAT_MODEL.md`): nothing in this system is ever interpreted as HTML, so there is
  nothing to sanitize. Adding a sanitizer "just in case" would be solving a problem
  that does not exist here.
- **No Kubernetes** — Docker Compose is the deliberate, documented target (ADR-013);
  introducing an orchestrator before a demonstrated need would be premature complexity,
  not debt avoidance.
- **Metric names avoid an OpenMetrics-reserved `_created` suffix** (`case.creation`,
  not `case.created`) — a real, deliberate naming choice after Phase 14 found the
  collision, not a workaround needing further cleanup.
- **No error-tracking SDK wired without a real account** — adding one now would mean
  shipping unvalidated third-party code with no real destination to send data to; the
  integration boundary exists and is tested, ready for a real SDK the moment an account
  exists (`ERROR_TRACKING.md`).
- **No payments/subscriptions/monetisation code** — explicitly out of MVP scope
  (`PRODUCT_REQUIREMENTS.md` non-scope, this phase's own brief), not an oversight.

## Debt already paid down (do not re-list as open)

- Structured JSON logging (Canonical Phase 14 — was debt through Phase 13, resolved).
- Readiness probe correctly reflecting real database reachability (Canonical Phase 14
  — was a real, previously-undiscovered defect, not merely debt; now fixed and
  guarded by a permanent regression test).
- The frontend container `HEALTHCHECK` IPv6/IPv4 mismatch (Canonical Phase 13).
- `TokenCleanupService`'s scheduled self-invocation bypassing `@Transactional`
  (Canonical Phase 13).
