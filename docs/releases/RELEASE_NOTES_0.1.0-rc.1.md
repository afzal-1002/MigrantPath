# Release Notes — 0.1.0-rc.1

Canonical Phase 15 (Release Candidate / Launch Readiness / Project Closeout). The
first release candidate cut from this project's canonical engineering track (Phases
0–15). Technically ready to deploy; not yet cleared for a real public launch — see
`docs/releases/FINAL_GO_NO_GO.md` for the precise, separated decision.

## Major capabilities

- Full guided-eligibility flow: registration → email verification → login → a real
  branching questionnaire → deterministic rule evaluation → personalized
  recommendations → a case with a real checklist.
- Five real Warsaw procedures researched and sourced; four published with active
  eligibility Rules (PESEL, address registration, EU citizen residence registration,
  temporary residence and work); one (temporary residence for studies) intentionally
  held behind its own source-verification gate.
- Real Admin content-governance UI — draft → review → approve → publish, with
  enforced separation of duties and a full audit trail.
- GDPR-style personal-data export and account deletion, both session-invalidating.
- Structured JSON logging, a full Prometheus metric catalog, health/readiness probes,
  correlation-ID tracing end to end, an alert catalogue, an optional local dashboard.

## Security / privacy

CSRF enforced everywhere unsafe; strict CSP; role-based access with enforced
separation of duties; no personal/sensitive field ever reaches a log line or metric
tag (both enforced by automated regression tests); verification/reset tokens never
appear in any log (application or reverse-proxy access log).

## Legal content

Sourced from official channels only (Polish legislation, UDSC, MOS, gov.pl,
Mazowieckie Voivodeship Office, Warsaw municipal government/19115) — never a blog, law
firm site, or forum. Every published fact traces to a real `OfficialSource`. See
`docs/legal-content/PRODUCTION_RULE_COVERAGE.md`.

## Known limitations (see `docs/product/KNOWN_ISSUES.md` for the complete list)

- The conditional checklist engine (per-user document/step/fee filtering) is
  schema-ready, not yet functionally wired — every case currently shows the full,
  unconditional checklist for its procedure.
- No error-tracking service or alert-delivery channel is connected — both are
  `DOCUMENTED_ONLY`/unconfigured; an operator must actively check logs/dashboards.
- No load test, external penetration test, or external accessibility audit has been
  performed.
- Temporary residence for studies remains unpublished, correctly, behind its source-
  verification gate.

## Database migrations

Flyway V1 through V48. No migration in this release changes the schema — this is a
documentation/observability/release-readiness cut, not a schema-affecting one (see
"Deployment notes" below).

## Deployment notes

No application source code changed in this phase's own commit beyond
`frontend/playwright.config.ts` (a test-harness-only change — never baked into the
built Docker image). The Docker images verified for this RC were built from the prior
commit (Canonical Phase 14, `9ab6e6d`) and are therefore byte-for-byte behaviorally
identical to what this RC would produce — confirmed by inspection, not assumed. A real
public deployment still requires: a hosting provider, a real domain, a real TLS
certificate, and legal review of the Privacy Policy/Terms/Disclaimer/Cookie Policy —
none of which is a code change.

## Launch blockers (external, not technical — see `FINAL_GO_NO_GO.md`)

Hosting/domain/TLS: not selected. Core data processors (managed Postgres, SMTP, error
tracking): not selected. Legal review of public-facing legal pages: not performed. A
real support/privacy contact: not configured.
