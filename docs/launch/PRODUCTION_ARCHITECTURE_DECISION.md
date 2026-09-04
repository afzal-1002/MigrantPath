# Production Architecture Decision

Post-MVP Milestone L1. A recommendation, not an executed decision — no infrastructure
has been provisioned. **USER APPROVAL REQUIRED** before any account is created or
resource provisioned.

## Chosen architecture (recommended)

```text
Internet
   ↓
DNS (provider's own, or Cloudflare — see below) + TLS
   ↓
DigitalOcean Droplet (2 vCPU / 4 GB RAM, Frankfurt or Amsterdam)
   ↓
Docker Compose: nginx (frontend, real production image, unchanged) + backend
   ↓ (private VPC networking — never public)
DigitalOcean Managed PostgreSQL (single-node, same VPC, Frankfurt/Amsterdam)

Alongside:
   Amazon SES (transactional email, decoupled from compute location)
   GitHub Container Registry (already wired into release-build.yml)
```

This is **Option A (simple VPS) for compute, with a managed-platform database** —
deliberately a hybrid, not a pure "Option A" or "Option B." The existing
`infra/docker-compose.prod.yml` already supports this exactly as built: omit
`--profile self-hosted-db`, point `DB_HOST` at the DigitalOcean Managed PostgreSQL
private endpoint. Zero code changes required for this topology.

## Why this, over the alternatives

- **Over a pure Hetzner VPS + self-hosted Postgres** (the minimum-cost option, still
  documented below): a managed database buys real, provider-operated automated
  backups and point-in-time recovery for the single most valuable, hardest-to-recover
  piece of state in the system — the versioned legal content and every user's personal
  data. For a project whose own architecture (ADR-004, ADR-011) already treats
  content/case history as something that must never be silently lost, paying for a
  managed DB's backup guarantee is the highest-value €15/mo this budget can spend.
- **Over a fully managed platform (Render/Railway/Fly.io)**: those are real,
  credible alternatives (see `PROVIDER_COMPARISON.md`) but cost more for comparable
  resources at this project's current, modest scale, and this project's own Docker
  Compose deployment model (ADR-013) already targets "any Docker-capable host,"
  which a plain Droplet satisfies directly with zero adaptation.
- **Over AWS**: real IAM/VPC/ECS complexity for a solo/small team, for no capability
  this project currently needs (ADR-013's own explicit "no Kubernetes unless
  demonstrated need" reasoning applies equally to AWS's own managed-container
  complexity here).
- **Same-provider compute + database (DigitalOcean for both)**: keeps the database on
  private VPC networking, never publicly reachable — the cleanest way to satisfy
  brief §18's "production DB should not be publicly reachable where avoidable"
  without cross-provider firewall/IP-allowlist plumbing.

## Alternatives rejected (not eliminated forever — reconsider if circumstances change)

- **AWS ECS Fargate + RDS**: rejected for day-one MVP on operational-complexity and
  cost grounds, not capability — RDS itself would be a fine PostgreSQL 18 choice if
  the team were already AWS-native.
- **Render/Railway/Fly.io**: rejected as *primary* only because they cost somewhat
  more for equivalent resources today; any of the three remains a completely
  reasonable secondary/fallback if the DigitalOcean relationship turns out
  unsatisfactory in practice.
- **Hetzner + Ubicloud managed Postgres**: rejected as primary because Ubicloud is a
  smaller, less-established third party layered on top of Hetzner's own
  infrastructure — real, but a materially different risk profile than a Tier-1
  cloud provider's own native managed database offering.

## Operational model

- **Patching**: the Droplet's OS (recommend Ubuntu 24.04 LTS) is the operator's
  responsibility — unattended-upgrades for security patches, manual review for
  anything requiring a reboot. The Managed PostgreSQL instance's OS/engine patching is
  DigitalOcean's responsibility.
- **Backups**: automated by DigitalOcean for the database (daily + PITR on supported
  versions). The application's own Docker images and configuration are already
  reproducible from this git repository plus the (separately backed-up) production
  secrets — no separate compute-host backup is required beyond the database.
- **Firewall**: only 80/443 public; SSH restricted to a known IP range or a bastion;
  the database has no public endpoint at all (VPC-private).
- **TLS**: automatic renewal via a TLS-terminating reverse proxy in front of the
  frontend container (Let's Encrypt via Caddy, or Cloudflare-managed — see the DNS/TLS
  decision below), never a manually-renewed long-lived certificate.
- **Docker image cleanup**: a periodic `docker image prune` policy that always
  preserves the currently-running tag and the immediately-previous release tag (for
  rollback), never more aggressive than that.

## Security/privacy considerations

Matches the existing, unchanged `ADR-013` topology exactly: only the frontend/reverse-
proxy container is ever reachable from the internet; the backend has no published
port; the database is never publicly reachable in this recommended topology (an
improvement over the current local-only self-hosted-db profile's own documented
"public if not firewalled" caveat). `forward-headers-strategy: framework` remains
correct and safe in this exact single-hop proxy chain (browser → TLS terminator →
nginx → Spring Boot), unchanged.

## Scaling path

1. Vertically resize the Droplet (more vCPU/RAM) — a few minutes of downtime or none,
   depending on DigitalOcean's live-resize support for the chosen plan.
2. Vertically resize the Managed PostgreSQL instance similarly.
3. Only once genuinely needed: multiple backend instances behind a load balancer —
   requires first replacing the single-instance in-process rate limiter
   (`TECHNICAL_DEBT.md`) with a shared store (Redis or equivalent); Spring Session JDBC
   already supports multi-instance sessions with zero additional work.
4. Read replicas on the Managed PostgreSQL instance if read load (not write load)
   becomes the bottleneck — not needed at any traffic level currently anticipated.

**This is a design document, not an executed migration** — no step above has been
performed.
