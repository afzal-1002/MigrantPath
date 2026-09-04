# Provider Decision Log

Post-MVP Milestone L1. Recommendations pending user approval — **no provider account
has been created, no purchase made.** "Date" below is this milestone's research date;
"Review date" is a suggested check-in point given how volatile provider pricing has
proven to be this year (Hetzner alone raised prices twice in 2026).

## Hosting / compute

- **Decision (recommended)**: DigitalOcean Droplet (2 vCPU / 4 GB RAM, Frankfurt or
  Amsterdam).
- **Reason**: same-provider private VPC networking to its own Managed PostgreSQL, no
  new operational surface beyond this project's existing Docker Compose model, EU
  region, predictable flat pricing.
- **Alternatives**: Hetzner Cloud CPX22 (cheaper raw compute, no managed DB, higher
  operator burden); Render/Railway/Fly.io (fully managed, costs more at this scale).
- **Cost**: ~$24/mo.
- **Date**: 2026-09-04.
- **Review date**: re-confirm pricing/fit before any real purchase, and again ~6
  months after launch.

## Database

- **Decision (recommended)**: DigitalOcean Managed PostgreSQL, single-node, same VPC
  as the compute Droplet above.
- **Reason**: confirmed PostgreSQL 18 support; automated backups + PITR included; no
  public network exposure needed.
- **Alternatives**: AWS RDS for PostgreSQL (also PG18-confirmed, more operational
  complexity); Aiven for PostgreSQL (also PG18-confirmed, cross-cloud flexibility not
  needed here); self-hosted on the same VPS (minimum-cost tier only — see
  `PRODUCTION_COST_ESTIMATE.md`).
- **Cost**: $15/mo (single-node, 1 GiB RAM).
- **Date**: 2026-09-04.
- **Review date**: before purchase; re-check storage-overage pricing once real data
  volume is known.

## Domain / DNS

- **Decision**: not selected — depends on `BUSINESS_DECISIONS_REQUIRED.md` items 1–2.
- **DNS recommendation (once a domain exists)**: the domain registrar's own DNS, or
  DigitalOcean's own DNS (co-located with the chosen compute provider) — Cloudflare
  only if its proxy/additional protection features are specifically wanted, not by
  default (brief §23's own "clarify which Cloudflare capabilities are actually
  needed").
- **Date**: 2026-09-04.

## TLS

- **Decision (recommended)**: Let's Encrypt via a TLS-terminating reverse proxy
  (Caddy, or certbot-managed nginx) in front of the existing frontend container —
  automatic renewal, zero manual certificate handling. Matches the exact pattern this
  milestone's own local-HTTPS Playwright harness already proved works with this
  project's real production image (Canonical Phase 15).
- **Alternative**: a DigitalOcean-managed load balancer with automatic TLS, if the
  operational simplicity is preferred over the reverse-proxy approach.
- **Cost**: $0 (Let's Encrypt) or DigitalOcean load balancer pricing if that
  alternative is chosen (not separately researched this pass).
- **Date**: 2026-09-04.

## Transactional email

- **Decision (recommended)**: Amazon SES.
- **Reason**: lowest cost by a wide margin ($0.10/1,000 emails, uniform across EU
  regions), full SPF/DKIM/DMARC and SMTP support (no application code change needed —
  `EmailService` is already provider-agnostic), 3,000 free messages/month for the
  first 12 months.
- **Alternative**: Postmark (simpler account setup, no AWS account needed, higher
  cost at $15/mo for 10,000 emails).
- **Cost**: negligible at MVP volume (a few dollars/month at most).
- **Operational note**: requires exiting SES "sandbox" mode via an AWS support
  request before real users can receive email — a lead-time item, not a cost item.
- **Date**: 2026-09-04.

## Container registry

- **Decision (recommended)**: GitHub Container Registry (GHCR).
- **Reason**: already the target in `release-build.yml` — zero new integration work;
  free at this project's realistic image count/size.
- **Alternative**: none seriously considered — no reason to diverge from the
  already-built CI path.
- **Cost**: $0.
- **Date**: 2026-09-04.

## Error tracking

- **Decision**: `DEFER` (recommended) — structured JSON logs, a full Prometheus
  metric catalog, and correlation-ID tracing (all real, Canonical Phase 14) are
  judged sufficient for a small, early MVP launch. Revisit once real user volume or
  operational maturity justifies the added cost/complexity.
- **If adopted instead**: Sentry, EU data-residency selectable at org creation
  (Germany region), Team plan ~$29/mo (monthly billing).
- **Date**: 2026-09-04.

## Alert delivery

- **Decision (recommended)**: email, to the support/privacy-adjacent operational
  mailbox — simplest possible channel, zero new provider, sufficient for a solo/small-
  team early launch.
- **Alternative**: Slack/Telegram, if the operator already uses one of those daily.
- **Date**: 2026-09-04.

## What remains genuinely undecided

Business identity, domain, sender-alias mailboxes, legal-review owner, and the
final go/no-go on adopting error tracking at launch — all listed in
`BUSINESS_DECISIONS_REQUIRED.md`, none decided here.
