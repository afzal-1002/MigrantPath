# Processor Inventory

Canonical Phase 12. Categories of third party that would process personal data on this
application's behalf, with an honest status for each - never listing a provider as
selected/active when no such decision has actually been made.

| Category | Status | Notes |
|---|---|---|
| Hosting / compute | CANDIDATE | Post-MVP Milestone L1 recommends DigitalOcean (Droplet, EU region) as primary, Hetzner Cloud as secondary — real research, not yet approved/purchased (`docs/launch/PROVIDER_COMPARISON.md`) |
| Managed PostgreSQL | CANDIDATE | L1 recommends DigitalOcean Managed PostgreSQL (EU region, confirmed PostgreSQL 18 support) — not yet approved/purchased (`docs/launch/PROVIDER_COMPARISON.md`) |
| Transactional email (SMTP) | CANDIDATE | L1 recommends Amazon SES (can send from an EU region; account/billing sits in AWS's own global structure) — not yet approved/configured (`docs/launch/PROVIDER_COMPARISON.md`) |
| Error monitoring | CANDIDATE, RECOMMENDED TO DEFER | L1 recommends deferring adoption at launch (structured logs/metrics/correlation IDs judged sufficient for a small MVP); if adopted, Sentry with its EU (Germany) data-residency option is the candidate (`docs/launch/PROVIDER_DECISIONS.md`) |
| DNS / TLS | CANDIDATE (DNS), STRATEGY DEFINED (TLS) | DNS: the eventual domain registrar's own DNS or DigitalOcean DNS once a domain is chosen; TLS: Let's Encrypt via a reverse proxy, automatic renewal — see `docs/launch/PROVIDER_DECISIONS.md` |
| Object/file storage | NOT APPLICABLE | Personal-data export uses direct authenticated download, deliberately not object storage (`docs/product/PHASE_12_REPORT.md`'s export design) |
| Analytics / advertising | LOCAL/DEVELOPMENT ONLY - NOT USED | No analytics or advertising integration exists anywhere in this codebase (brief's own repeated "still no analytics" instruction across phases) |
| Development/local-only tooling | LOCAL/DEVELOPMENT ONLY | Mailpit (local SMTP catcher, `docker-compose.yml`) - never used in staging/production |

## International transfer review

Not yet resolved - the candidates above are recommendations, not selections
(`docs/launch/PROVIDER_DECISIONS.md`). Flagged specifically for the legal reviewer:
Amazon SES (an AWS service, global account structure even when sending originates from
an EU region) and, if adopted, Sentry (EU data-residency is a real, selectable option,
not the default) both warrant an explicit international-transfer/DPA check once
actually selected - a legal/business due-diligence item, not something this codebase
can determine or declare on its own. Tracked as an open item in
`docs/privacy/GDPR_READINESS.md` and `docs/launch/LEGAL_REVIEW_HANDOFF.md`.

## Updating this document

The moment any of the "NOT SELECTED" rows above becomes a real, chosen vendor, this
file must be updated in the same change that wires the integration - never left stale
claiming "not selected" once a provider is actually receiving personal data in
production.
