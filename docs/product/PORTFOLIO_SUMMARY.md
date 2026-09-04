# Portfolio Summary

Canonical Phase 15. A factual, external-facing summary of this project's technical
substance — no confidential or private information included.

## Problem

Foreigners moving to or living in Warsaw, Poland face a fragmented landscape of
immigration/administrative procedures spread across national, regional, and municipal
authorities, in a language they often don't speak. Foreigner Warsaw is a guided-
eligibility and case-tracking web application that walks a user through a short
questionnaire and tells them, based on real sourced legal content, which real
procedures likely apply to them — then tracks their progress through a personalized
checklist.

## Architecture

A package-by-feature modular monolith (Spring Boot 4.1.x / Java 25 / PostgreSQL 18 /
Flyway backend, Angular 22 standalone-component frontend) — deliberately not
microservices, deliberately not Kubernetes, for a system at this scale (ADR-001,
ADR-013). The most architecturally significant decision: legal/administrative content
(which procedure applies, what documents are required, fees, thresholds) is modeled
as versioned, sourced database data with a real draft → review → approve → publish
governance workflow — never hard-coded in application logic (ADR-003, ADR-004). A
deterministic, database-driven condition-tree rules engine — never an LLM — is the
sole eligibility decision-maker.

## Stack

Java 25, Spring Boot 4.1.x, PostgreSQL 18, Flyway, Spring Security (cookie session +
CSRF), Angular 22, Angular Material, Docker Compose, nginx, Micrometer/Prometheus,
Logback/Logstash JSON encoding, Playwright.

## Major technical challenges solved

- **A deterministic condition-tree rules engine** (ADR-009) evaluating real eligibility
  logic against versioned facts, with threshold and country-group resolution, full
  error categorization, and observability — built without an off-the-shelf rules
  engine (Drools/SpEL), by deliberate choice.
- **The Active-Version Predicate**: every read of versioned content
  (procedure/rule/threshold/questionnaire) resolves through one consistent temporal
  predicate, so draft content never leaks and published content is never hidden — the
  single most load-bearing piece of logic in the system.
- **Immutable case snapshots**: a user's case captures the exact procedure/rule content
  active at creation, surfacing later content changes as an explicit, opt-in diff
  rather than silently mutating a user's existing case.
- **A real, previously-undiscovered Kubernetes-adjacent readiness-probe bug**: Spring
  Boot's `readiness` health group excludes the database health indicator by default —
  found by actually stopping a live database mid-traffic and observing the gap between
  documented and real behavior, not by code review.
- **A real Prometheus metric-naming collision**: a metric named `case.created` was
  silently exported under the wrong series name because the modern Prometheus client
  treats a trailing `_created` as a reserved OpenMetrics suffix — found by diffing real
  scrape output, not assumed.

## Security & privacy

CSRF enforced on every unsafe request including public auth routes; strict CSP with no
inline-script exception; role-based admin access with enforced separation of duties (an
author can never approve their own submission); GDPR-style personal-data export and
account deletion, both real and session-invalidating; structured, privacy-scrubbed
logging with an intentional field whitelist, enforced by an automated regression test,
not a one-time audit.

## Testing

Backend: 379 tests (JUnit 5, Testcontainers-backed real PostgreSQL integration tests,
Mockito unit tests). Frontend: 123 tests (Vitest). End-to-end: 18 Playwright specs
covering auth, the full guided-assessment→case journey, and the full admin governance
lifecycle — verified against both the local dev stack and the actual production Docker
images over real HTTPS (a self-signed local TLS harness built specifically to prove
this, since production correctly requires HTTPS for its session cookies).

## Deployment & observability

Real, multi-stage, non-root Docker images; a reverse-proxy topology where only the
frontend container is ever reachable; a tested backup/restore drill; structured JSON
logging in staging/production; a full Prometheus metric catalog with an automated
cardinality-policy test; correlation IDs threaded from the HTTP layer through logs and
error responses; an optional local Grafana dashboard profile.

## What's honestly not done

No real cloud deployment has ever occurred (deliberately, out of engineering scope for
a pre-launch project); no external legal, security, or accessibility review has been
performed; core infrastructure providers (hosting, managed database, SMTP, error
tracking) remain unselected. See `docs/product/PROJECT_STATUS.md` and
`docs/releases/FINAL_GO_NO_GO.md` for the complete, precise picture.
