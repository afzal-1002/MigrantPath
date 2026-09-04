# Provider Comparison

Post-MVP Milestone L1 (Launch Enablement & Production Provider Selection). Real
research, dated — provider pricing is volatile (Hetzner alone raised its shared-vCPU
line 144–176% in a single June 2026 adjustment); re-verify before acting on any figure
here more than a few months old. All figures **checked on: 2026-09-04**.

## Compute / hosting

| Provider | Service | EU Region | Monthly Cost (entry) | Managed DB | Backups | TLS | Complexity | Pros | Cons | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| **Hetzner Cloud** | CPX22 (2 vCPU shared, 4 GB RAM, 40 GB NVMe) | Germany, Finland | ~€19.49/mo + 19% VAT (post-June-2026 price adjustment — was €7.99 before) | No native offering | Manual (or Hetzner's own paid snapshot/backup add-on, ~20% of server cost) | Self-managed (Let's Encrypt via a proxy) | Low-medium (real VPS, real OS patching) | Cheapest raw compute; generous included bandwidth (20 TB/mo in EU) | No managed DB; no managed platform conveniences; recent steep price rises make future cost less predictable | RECOMMENDED (compute only) |
| **DigitalOcean** | Basic Droplet (2 vCPU, 4 GB RAM) or App Platform | Frankfurt (FRA1), Amsterdam (AMS3) | ~$24/mo Droplet | **Yes** — Managed PostgreSQL, same-VPC private networking | Automated, PITR included | Automated on App Platform; self-managed on a Droplet | Low (App Platform) / Low-medium (Droplet) | One vendor, one VPC, real private networking to its own Managed DB, predictable flat pricing | Costs more than Hetzner for equivalent raw compute | **RECOMMENDED (primary)** |
| **Render** | Standard web service (2 GB, 1 CPU) | Frankfurt available | $25/mo | Yes — Render Postgres | Yes on paid tiers | Automatic | Very low | Simplest possible deploy path, git-push style | Postgres pricing scales steeply past small tiers; less control than a VPS | ALTERNATIVE |
| **Railway** | Usage-based | EU regions available | ~$5/mo base + usage (~$20/vCPU, $10/GB RAM per month, per-second billed) | Yes, usage-based Postgres | Yes | Automatic | Low | Simple, usage-based billing scales down with idle traffic | Usage-based billing is harder to budget precisely for a fixed-cost-conscious MVP | ALTERNATIVE |
| **Fly.io** | shared-cpu-1x (1 GB) Machine | Frankfurt/Amsterdam regions | ~$5.92/mo per machine + egress | Yes — Fly Managed Postgres, Basic $38/mo | Yes on Managed Postgres | Automatic | Low-medium | Fine-grained machine sizing, good multi-region story if ever needed | Managed Postgres pricing is high relative to DigitalOcean for a comparable single instance | ALTERNATIVE |
| **AWS** | ECS Fargate / App Runner + RDS | Frankfurt (eu-central-1), Ireland (eu-west-1) | Variable, typically $40–80/mo minimum for Fargate + RDS at comparable sizing | Yes — RDS PostgreSQL, confirmed PostgreSQL 18 support (18.1+, current minor 18.3) | Yes, automated + PITR | Automated (ALB + ACM) | High (IAM, VPC, ALB, ECS task definitions) | Enterprise-grade, deep ecosystem, mandatory budget alarms available | Real operational complexity for a solo/small team; easy to accidentally spend more than intended without budget guardrails (§99) | ALTERNATIVE (not recommended for day-one MVP) |
| **Azure / Google Cloud** | Container Apps / Cloud Run + managed Postgres | EU regions available | Comparable to AWS | Yes | Yes | Automated | High | Same enterprise-grade trade-offs as AWS | Not evaluated in further depth — no differentiating reason over AWS for this project's needs | NOT SHORTLISTED FURTHER |

## Managed PostgreSQL (evaluated separately from compute, per L1 brief §14)

| Provider | PostgreSQL 18 | EU Region | Encrypted connection | Automated backups | PITR | Price (entry) | Status |
|---|---|---|---|---|---|---|---|
| **DigitalOcean Managed Databases** | **Yes**, confirmed | Frankfurt, Amsterdam | Yes (required) | Yes, included | Yes, included on supported versions | $15/mo (single-node, 1 GiB RAM) | **RECOMMENDED** |
| **AWS RDS for PostgreSQL** | **Yes**, confirmed (18.1+, current minor 18.3) | Frankfurt, Ireland | Yes | Yes | Yes | ~$25–40/mo comparable sizing (db.t4g.micro class) | ALTERNATIVE |
| **Aiven for PostgreSQL** | **Yes**, confirmed, across AWS/GCP/Azure/DigitalOcean | Multiple EU options | Yes | Yes | Yes | Startup plan from ~$19/mo (varies by cloud/region) | ALTERNATIVE |
| **Supabase** | **No** — supports up to PostgreSQL 15 as of this research | EU (Frankfurt) available | Yes | Yes | Paid tiers only | N/A — disqualified on version grounds | NOT SHORTLISTED (version gap) |
| **Crunchy Bridge** | **No** — supports up to PostgreSQL 16 as of this research | EU available | Yes | Yes | Yes | ~$36/mo entry | NOT SHORTLISTED (version gap) |
| **Neon** | Not confirmed in this research pass — modern storage/compute-split architecture, popular for serverless/branching workflows | EU (Frankfurt) available | Yes | Yes | Yes (branching-based) | Free tier + usage-based paid | NOT SHORTLISTED (PG18 support unconfirmed; serverless-branching model is a mismatch for this project's steady-state OLTP workload, not a version problem) |
| **Hetzner (self-hosted or via Ubicloud)** | Yes if self-managed (any version installable) | Germany, Finland | Self-configured | Self-managed, or Ubicloud's managed layer | Self-managed, or via Ubicloud | Self-hosted: free (runs on the compute VPS); Ubicloud: separate pricing, not deeply researched this pass | Self-hosted = MINIMUM-COST option; Ubicloud = ALTERNATIVE, less-established third party |

**PostgreSQL 18 resolution (brief §15)**: real, confirmed managed PostgreSQL 18 support
exists today at DigitalOcean, AWS RDS, and Aiven. **No silent downgrade is needed or
recommended** — the project's PostgreSQL 18 requirement (ADR-002) is fully satisfiable
by a real managed provider.

## Container registry

| Provider | Cost | Notes | Status |
|---|---|---|---|
| **GitHub Container Registry (GHCR)** | Free for public images; private images: 500 MB storage / 1 GB transfer per month free on the Free plan, 2 GB / 10 GB on Team, then $0.008/GB storage + $0.50/GB transfer overage | Already the target in `release-build.yml` (`ghcr.io/...`) — zero new integration work | **RECOMMENDED** |
| Docker Hub | Free tier has pull-rate limits even for authenticated pulls on some tiers; paid plans from ~$5/user/mo | No existing integration; no compelling reason to switch | NOT SHORTLISTED |
| Provider-native registry (e.g. AWS ECR) | Only relevant if AWS is the chosen compute provider | Adds IAM complexity for no benefit under the DigitalOcean/Hetzner recommendation | NOT SHORTLISTED |

## Transactional email

| Provider | Free tier | Entry paid | EU data residency | SPF/DKIM/DMARC | SMTP support | API support | Status |
|---|---|---|---|---|---|---|---|
| **Amazon SES** | 3,000 msgs/mo for 12 months (new accounts) | $0.10 per 1,000 emails — uniform across all AWS regions | Sending can originate from an EU region (Frankfurt/Ireland/etc.); account/billing sits in AWS's own global account structure | Full support | Yes | Yes | **RECOMMENDED (primary)** |
| **Postmark** | 100 msgs/mo | New 2026 plan structure: Basic $15/mo for 10,000 emails, $1.80/1,000 overage; Pro $16.50/mo, $1.30/1,000 overage | Not confirmed as EU-only hosted in this research pass — check at signup | Full support | Yes | Yes | ALTERNATIVE (secondary) |
| **Resend** | 3,000 msgs/mo (100/day) | Pro $20–35/mo | Sending can originate in Ireland; **account data, metadata, and logs remain in the US** even so — a real limitation if strict EU data residency for *all* email metadata is required | Full support | Not confirmed as a first-class integration path (API-first product) | Yes | NOT SHORTLISTED (SMTP fit weaker; US metadata residency) |
| Mailgun / SendGrid / Brevo | Vary | Vary | Vary — not deeply researched this pass | Full support (industry standard) | Yes | Yes | NOT SHORTLISTED FURTHER (SES/Postmark already satisfy the requirement cleanly) |

**SES operational note**: new AWS accounts start SES in "sandbox" mode (can only send
to verified addresses) — moving to production sending requires a real AWS support
request, a real business justification, and typically a short review period. This is
an operational lead-time item, not a cost item — factor it into the launch timeline.

## Error tracking

| Provider | Free tier | Entry paid | EU data residency | Status |
|---|---|---|---|---|
| **Sentry** | Yes, limited volume | Team ~$29/mo (monthly billing; ~$26/mo billed annually) | Real, selectable at organization creation (EU region hosted in Germany, `de.sentry.io`) — permanent once chosen | RECOMMENDED IF error tracking is adopted at launch (see "Observability" decision below) |

## Uptime monitoring

Not deeply researched this pass — a low-stakes, low-cost decision (most hosting
providers include a basic health-check/alert mechanism, and several free-tier
third-party uptime monitors exist). Deferred to the actual provider-selection
conversation rather than pre-researched here; does not block this milestone's other
decisions.
