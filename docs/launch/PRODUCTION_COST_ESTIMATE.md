# Production Cost Estimate

Post-MVP Milestone L1. All figures **checked on: 2026-09-04** against the sources in
`PROVIDER_COMPARISON.md`. Provider pricing is volatile — re-verify before budgeting
against any figure here more than a few months old. Currency: provider-billed amount
first, approximate PLN in parentheses at **1 EUR ≈ 4.25 PLN, 1 USD ≈ 3.95 PLN**
(rounded, illustrative only — check a live rate before any real budget commitment).
Most listed prices exclude VAT unless stated; Poland's VAT rate (23%) or the
provider's own billing-country VAT rules may apply — confirm with actual accounting
before finalizing a budget, not from this document.

## Traffic assumptions

Per brief §11 — cost-sensitivity scenarios, not a capacity guarantee:

| Scenario | Assumption |
|---|---|
| 100 MAU | Comfortably within every tier below, including Minimum |
| 1,000 MAU | Comfortably within Recommended; likely fine on Minimum too, unverified by load test |
| 10,000 MAU | Likely requires the Higher-Reliability tier's larger compute/DB sizing at minimum; **no load test has been performed at any tier** (`KNOWN_ISSUES.md`) — treat this row as a planning trigger to revisit sizing, not a proven ceiling |

## Minimum viable launch

Single provider (Hetzner), self-hosted database on the same VPS
(`--profile self-hosted-db`, already supported by `infra/docker-compose.prod.yml`
unmodified).

| Component | Monthly fixed | Monthly variable | Notes |
|---|---:|---:|---|
| Hetzner Cloud CPX22 (2 vCPU, 4 GB RAM, 40 GB NVMe) | €19.49 + 19% VAT ≈ €23.19 (~99 PLN) | — | Post-June-2026 pricing; runs frontend + backend + self-hosted Postgres in one Compose stack |
| Backup (manual `pg_dump`/`pg_basebackup` to Hetzner Object Storage or equivalent) | ~€1–5 (object storage is priced per GB, negligible at this data size) | — | Operator-managed, not automatic — real risk, see "Single-point-of-failure" below |
| Domain (.pl or .com) | — | — | Annual, see "One-time / annual costs" |
| TLS | €0 | — | Let's Encrypt, automatic renewal |
| Transactional email (Amazon SES) | €0 fixed | ~$0.10/1,000 emails — negligible at MVP volume | First 12 months include 3,000 free/mo |
| Container registry (GHCR) | €0 | — | Free tier sufficient at this image count/size |
| Error tracking | €0 | — | Deferred (see Observability decision) |
| **Total (approx.)** | **~€25–30/mo (~110–130 PLN)** | negligible | |

**Risk accepted at this tier**: database and application share one failure domain;
backup is a real, ongoing operator responsibility, not automatic; no PITR.

## Comfortable MVP production (recommended)

DigitalOcean Droplet + DigitalOcean Managed PostgreSQL, same VPC.

| Component | Monthly fixed | Monthly variable | Notes |
|---|---:|---:|---|
| DigitalOcean Droplet (2 vCPU, 4 GB RAM) | ~$24 (~95 PLN) | — | Frontend + backend containers |
| DigitalOcean Managed PostgreSQL (single-node, 1 GiB RAM) | $15 (~59 PLN) | Storage overage $0.21/GiB/mo past the included base — unlikely to matter at MVP data volume | Automated daily backups + PITR included |
| Domain (.pl or .com) | — | — | Annual, see below |
| TLS | $0 | — | Let's Encrypt or DigitalOcean-managed |
| Transactional email (Amazon SES) | $0 fixed | ~$0.10/1,000 emails | Negligible at MVP volume |
| Container registry (GHCR) | $0 | — | Free tier |
| Error tracking (Sentry Team, if adopted at launch) | $29 (~114 PLN) | Overage past included event quota | See "Error Tracking Decision" — optional at this tier |
| **Total, without Sentry** | **~$39/mo (~154 PLN)** | negligible | |
| **Total, with Sentry Team** | **~$68/mo (~269 PLN)** | negligible | |

## Higher-reliability option (optional third tier)

DigitalOcean Managed Platform (App Platform) + DigitalOcean Managed PostgreSQL with a
standby node (high-availability, automatic failover).

| Component | Monthly fixed | Notes |
|---|---:|---|
| DigitalOcean App Platform (comparable sizing) | ~$25–50 | Fully managed compute, no OS patching burden |
| DigitalOcean Managed PostgreSQL, HA (primary + standby) | $60 ($30 + $30 matching standby) | Automatic failover |
| Sentry Team | $29 | Recommended at this tier |
| **Total (approx.)** | **~$115–140/mo (~455–555 PLN)** | Meaningfully higher cost for automatic DB failover and zero-OS-patching compute — worth it once real revenue or real user trust is at stake, not necessary on day one |

## Total cost table

| Component | Minimum | Recommended | Higher Reliability |
|---|---:|---:|---:|
| Compute | ~€23/mo | ~$24/mo | ~$25–50/mo |
| Database | included (self-hosted) | $15/mo | $60/mo |
| Backups | manual, near-zero cost, real operator burden | included | included |
| Email | negligible | negligible | negligible |
| Registry | $0 | $0 | $0 |
| Error tracking | $0 (deferred) | $0 or $29/mo | $29/mo |
| **Monthly total** | **~€25–30 (~110–130 PLN)** | **~$39–68 (~154–269 PLN)** | **~$115–140 (~455–555 PLN)** |
| **Annual total (recurring only)** | **~€300–360 (~1,300–1,550 PLN)** | **~$470–815 (~1,850–3,225 PLN)** | **~$1,380–1,680 (~5,450–6,650 PLN)** |

## Staging cost

Recommend **B — ephemeral staging**: bring up `infra/docker-compose.prod.yml`
(pointed at a small, separate Managed PostgreSQL instance or a throwaway self-hosted
one) only when actually verifying a release candidate, then tear down — matching this
project's own established local-verification practice (Phases 13–15 all used exactly
this pattern locally). At current MVP scale, an always-on staging environment is a
real, avoidable recurring cost (roughly the same as the Minimum tier, ~€25–30/mo,
running 24/7 for no additional verification value over on-demand). Reconsider once a
real team (not a solo operator) needs staging to always be reachable.

## One-time / annual costs

| Item | Estimate | Notes |
|---|---:|---|
| Domain registration (.pl) | ~$4–22/yr registration, often higher on renewal (e.g. ~$22/yr renewal at one researched registrar) — **compare registrars at time of purchase, first-year promotional pricing is common and renewal pricing often differs** | See `PROVIDER_COMPARISON.md`'s domain research |
| Domain registration (.com), if chosen instead/additionally | Typically $10–15/yr | Not deeply researched this pass |
| Legal review (Privacy Policy/Terms/Disclaimer) | `QUOTE REQUIRED` | No lawyer fee invented — a real quote must come from an actual legal professional |
| External penetration test | `QUOTE REQUIRED` | Optional for a small MVP launch (`KNOWN_ISSUES.md`); genuinely variable cost depending on scope |
| External accessibility audit | `QUOTE REQUIRED` | Optional for a small MVP launch |

## Cost monitoring

Recommend a monthly budget alert at whichever provider is selected (DigitalOcean
supports billing alerts; Hetzner's own usage is largely flat-rate so less critical to
alert on). **If AWS is ever selected for any component, a billing alarm/AWS Budget is
a mandatory recommendation, not optional** (brief §99) — AWS's usage-based pricing
model can produce genuine surprise bills in a way flat-rate VPS/Droplet pricing
cannot.

## What this estimate deliberately excludes

- Any provider account has not been created; every figure above is a published-price
  estimate, not a real invoice.
- Currency conversion is illustrative only — see the note at the top of this document.
- Salaries/labor cost (this is an infrastructure cost estimate only).
