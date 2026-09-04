# Project Status

Canonical Phase 15 (Release Readiness). The authoritative current summary — read this
first; every phase report under `docs/product/PHASE_*.md` is the detailed history
behind it.

## Product purpose

Foreigner Warsaw is a guided-eligibility and case-tracking web application for
foreigners living in or moving to Warsaw, Poland. It walks a user through a short
questionnaire, deterministically evaluates which real immigration/administrative
procedures they likely qualify for against versioned, sourced legal content, and
tracks their progress through a personalized checklist. It is an independent
informational service, not the Polish government, and does not provide legal advice.

## Warsaw-first scope

V1 is deliberately scoped to Warsaw / Mazowieckie Voivodeship only, but every
jurisdiction reference (country, region, city, district, authority, office) is modeled
as data, not hard-coded — later cities/countries are a content-authoring exercise, not
a rewrite (`ARCHITECTURE.md` §13, `docs/database/DATABASE.md`).

## Architecture

Java 25 / Spring Boot 4.1.x / PostgreSQL 18 / Flyway backend, package-by-feature
modular monolith (ADR-001); Angular 22 standalone-component frontend. Legal content
(procedures, rules, thresholds, document requirements, fees) is versioned database
data with a real draft → review → approve → publish governance workflow — never
hard-coded (ADR-003/ADR-004, CLAUDE.md's own standing rule). A deterministic,
database-driven condition-tree rules engine (ADR-009) — never an LLM — is the sole
eligibility decision-maker (ADR-003). User cases are immutable content snapshots, not
live joins (ADR-011), so a later content correction never silently changes a user's
existing case. See `ARCHITECTURE.md` for the full design.

## Implemented capabilities

- **Auth**: registration, email verification, login/logout, password reset, session
  cookie + CSRF, role-based access (`USER`/`CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN`).
- **Assessment**: a real guided questionnaire (`WARSAW_GENERAL_ASSESSMENT`, currently
  v2), branching, resumable, pinned per-assessment for reproducibility.
- **Rules**: a deterministic condition-tree engine evaluating real published `Rule`s
  against real answers, with threshold/country-group resolution and full error
  categorization + observability.
- **Recommendations**: a separate, immutable `RecommendationRun` produced from a
  completed assessment, ranking real candidate procedures.
- **Procedures**: public Browse pages backed by the Active-Version Predicate — only
  currently-published content is ever shown.
- **Cases**: a personalized `UserCase` with a real checklist (steps, documents, fees),
  created from a `PRIMARY_MATCH` recommendation, upgradeable when content changes.
- **Admin governance**: a real Admin UI (`/admin`) for the full content lifecycle —
  procedures, rules, thresholds, sources, questionnaires, audit log — with role
  separation enforced (an author can never approve their own submission).
- **Privacy**: GDPR-style personal-data export and account deletion, both real, tested,
  and session-invalidating; token cleanup; audit logging.
- **Deployment**: real, tested production Docker images and Compose stack, reverse
  proxy, backup/restore drills, CI/CD workflow definitions.
- **Observability**: structured JSON logging (staging/production), correlation IDs
  end-to-end, a full Prometheus metric catalog, health/readiness probes, an alert
  catalogue, an optional local Grafana dashboard.

## The five MVP procedures

| Procedure | Publication state |
|---|---|
| PESEL number assignment | `PUBLISHED`, real Rule active |
| Address registration (meldunek) | `PUBLISHED`, real Rule active |
| EU citizen residence registration | `PUBLISHED`, real Rule active |
| Temporary residence and work | `PUBLISHED`, 3 real Rules active |
| Temporary residence for studies | `APPROVED`, not published — held behind the source-verification gate, unchanged since Phase 10.5; Browse/recommendation surfaces correctly exclude it |

See `docs/product/PHASE_15_REPORT.md`'s "MVP Procedure Matrix" for the full per-field
breakdown and `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` for Rule detail.

## Guided-flow readiness

Real end to end: register → verify → login → assessment → complete → recommendation
run → real `PRIMARY_MATCH` → case creation → checklist, proven against real production
content this phase (and every phase since 10.5) via both automated tests and live
curl/browser verification.

## Case readiness

Steps/documents/fees exist and render for every published procedure's checklist.
Conditional (per-user) filtering of which document/step/fee applies is schema-ready
but not yet functionally wired (`RuleTargetType` beyond `PROCEDURE`) — see
`KNOWN_ISSUES.md`. Nothing currently shown is misleading; it is simply not yet
personalized beyond the procedure level.

## Admin governance

Real, role-separated draft → review → approve → publish workflow, audited at every
step. Verified this phase via a full governance-lifecycle Playwright journey
(`e2e/admin.spec.ts`) against real (synthetic-content) data.

## Privacy controls

Export and deletion are both real, tested, and invalidate every session. See
`docs/privacy/GDPR_READINESS.md` for the precise, honest compliance-posture summary —
technical controls are real; legal review of Privacy Policy/Terms/Disclaimer/Cookie
Policy content has not occurred (`DRAFT` status).

## Deployment state

Real production Docker images and Compose stack, verified locally multiple times
(Phases 13, 13.5, 14, 15) including a real HTTPS-terminated browser E2E run this
phase. No real cloud host, domain, or TLS certificate has ever been provisioned —
`CONFIGURED_NOT_EXECUTED`/`NOT SELECTED` throughout (`FINAL_GO_NO_GO.md`).

## Observability state

Real structured logging, metrics, correlation IDs, health/readiness (including a
significant readiness-probe bug found and fixed in Phase 14), and an alert catalogue.
No error-tracking service or alert-delivery channel is actually connected
(`DOCUMENTED_ONLY`) — an accepted operational risk for a small MVP, not a technical
gap (`docs/operations/ERROR_TRACKING.md`, `ALERTS.md`).

## Testing status

Backend and frontend full regression, Playwright (local target), and a real
HTTPS-terminated Playwright run against the production images — see
`docs/product/PHASE_15_REPORT.md`'s "Testing" section for exact current counts.

## External launch blockers

- Hosting provider, domain, and real TLS: all unresolved (`PROCESSOR_INVENTORY.md`,
  `DNS_AND_TLS.md`).
- Legal review of Privacy Policy/Terms/Disclaimer/Cookie Policy: not performed.
- A real support/privacy contact: not configured.
- Core data processors (hosting, managed Postgres, SMTP, error tracking): all
  `NOT SELECTED`.
- External security/accessibility review: not performed.

None of these block having a real, technically-verified release candidate — they block
a real *public launch*. See `FINAL_GO_NO_GO.md` for the precise, separated technical
vs. public-launch readiness decision.

## Next post-MVP roadmap

See `docs/product/POST_MVP_ROADMAP.md` — fast-follow procedures (EU Blue Card, family
reunification, driving licence exchange), the conditional checklist engine,
notifications, monitoring/hosting maturity, and (unrelated to this canonical phase) the
existing roadmap's own separately-scoped, unimplemented monetisation slot.
