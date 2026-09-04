# Processor Inventory

Canonical Phase 12. Categories of third party that would process personal data on this
application's behalf, with an honest status for each - never listing a provider as
selected/active when no such decision has actually been made.

| Category | Status | Notes |
|---|---|---|
| Hosting / compute | NOT SELECTED | ADR-013 is deliberately provider-neutral (Docker Compose runs on any Docker host) |
| Managed PostgreSQL | NOT SELECTED | `docs/operations/DEPLOYMENT.md` documents connecting to a managed instance via env vars; no specific provider chosen |
| Transactional email (SMTP) | NOT SELECTED | `docs/operations/EMAIL_PRODUCTION.md` documents the requirement (real SMTP, never Mailpit in production) without naming a provider |
| Error monitoring | NOT SELECTED, NOT INTEGRATED | Named as a future integration point only (`docs/operations/OBSERVABILITY.md`); no SDK exists in this codebase today |
| DNS / TLS | NOT SELECTED | Deployment-time, outside this repo's scope |
| Object/file storage | NOT APPLICABLE | Personal-data export uses direct authenticated download, deliberately not object storage (`docs/product/PHASE_12_REPORT.md`'s export design) |
| Analytics / advertising | LOCAL/DEVELOPMENT ONLY - NOT USED | No analytics or advertising integration exists anywhere in this codebase (brief's own repeated "still no analytics" instruction across phases) |
| Development/local-only tooling | LOCAL/DEVELOPMENT ONLY | Mailpit (local SMTP catcher, `docker-compose.yml`) - never used in staging/production |

## International transfer review

Not applicable until a real hosting/database/SMTP provider is selected - once one is,
whether that provider processes data outside the EEA (and what safeguard applies, e.g.
SCCs) is a legal/business due-diligence item, not something this codebase can determine
or declare on its own. Tracked as an open item in `docs/privacy/GDPR_READINESS.md`.

## Updating this document

The moment any of the "NOT SELECTED" rows above becomes a real, chosen vendor, this
file must be updated in the same change that wires the integration - never left stale
claiming "not selected" once a provider is actually receiving personal data in
production.
