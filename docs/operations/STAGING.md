# Staging

Status: architecture and configuration ready, real image build/deploy/smoke exercised
**locally** this phase against the exact `infra/docker-compose.prod.yml` topology
staging would use - see `docs/product/PHASE_13_REPORT.md`'s "Production-Like
Deployment" section for what was actually run and verified. **No staging environment
has ever been stood up on a real host/domain** - that first real deployment is the next
concrete action after this document, not something already claimed done here (mirrors
`DEPLOYMENT.md`'s own honesty convention for production).

## What staging is

Staging runs the **exact same immutable images** production will run (brief §7 "build
once, promote"), against the exact same `infra/docker-compose.prod.yml` topology and
reverse-proxy config - the only differences are the environment variables in
`infra/.env.staging.example` (a dedicated database, a sandboxed/test-only mail
provider, and the staging domain). Never the Angular dev server, never a separately
rebuilt image (brief §5/§7).

## Deploying

Identical to `DEPLOYMENT.md`'s procedure, with `infra/.env.staging` instead of
`infra/.env.production`:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.staging up -d
# add --profile self-hosted-db only if DB_HOST=postgres
```

## Reset policy (brief §118 - staging may reset, production never does)

Staging's database may be dropped/recreated at any time for a clean rehearsal - it
holds only synthetic data. **Never** do this to production. If a "clean slate" staging
reset is needed: drop the staging database, recreate it, let Flyway migrate from empty
on the next backend startup (the same fresh-install path every Testcontainers test run
already exercises), then re-bootstrap the first staging Admin
(`APP_ADMIN_BOOTSTRAP_ENABLED=true` for one startup - see `DEPLOYMENT.md` step 8).

## Synthetic accounts / data policy

- Every staging account is a synthetic/test identity (an `@example.com`-style address
  or a real inbox the operator controls for verification purposes) - **never** a real
  user's account or a restored production backup (`ENVIRONMENTS.md`'s own "staging
  never points at the production database" rule; no anonymization pass exists yet to
  make a production restore into staging safe - see `DATA_FLOW.md`).
- Legal content authored/reviewed in staging through the real Admin governance
  workflow is either promoted to production the same way (re-authored/re-approved
  there through the same workflow - see "Legal Content" below) or discarded; it is
  never bulk-copied database-to-database (brief §187/§39/§40 - no unsafe production
  import).

## Legal content: staging vs. production (brief §40)

This system's Admin content-governance workflow (Phase 9) is authored **directly per
environment** - there is no export/import pipeline moving `Procedure`/`Rule`/
`Threshold` rows between databases, and none should be built as a shortcut. A procedure
verified in staging is **re-authored and re-approved in production through the same
real Admin UI/API**, citing the same `OfficialSource`, not copied via SQL or a bulk
import tool. This keeps every environment's content independently attributable to a
real human approval action (ADR-004/ADR-012), at the cost of staging not being a
literal preview of production's exact content set - an accepted tradeoff, not an
oversight.

## Email in staging (brief §35/§182)

`MAIL_HOST`/`MAIL_PORT` must point at a sandboxed provider or a dedicated
staging-only sender identity - **never** the real production SMTP account/domain. A
staging E2E run (registration, password reset) must never be able to reach a real
user's inbox. Mailpit itself is acceptable for a *local rehearsal* of the staging
topology (as this phase's own verification did, via `host.docker.internal` reaching the
developer's local Mailpit) but is not a real staging provider for an actual deployed
host - see `EMAIL_PRODUCTION.md`.

## Targeting the Playwright suite at a deployed staging host (brief §59/§63)

```bash
BASE_URL=https://staging.example.com npx playwright test
```

`playwright.config.ts` skips starting a local dev server whenever `BASE_URL` is set (no
code change needed per environment - see that file). This is proven this phase against
a **local** production-like stack (`BASE_URL=http://localhost:18080`) - 17/18 specs
green; see `PHASE_13_REPORT.md` for the one open finding.

### A real, disclosed limitation: role-granting specs need direct DB access

`e2e/db.ts`'s `grantRole()` (used by `admin.spec.ts` and `reference-content.spec.ts` to
give a test-created user a `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN` role, since no
public API can self-escalate a role, by design) works by `docker exec`-ing directly
into a named Postgres container - now env-overridable (`E2E_DB_CONTAINER`/
`E2E_DB_USER`/`E2E_DB_NAME`, defaulting to the local dev stack's own container name) so
a local production-like rig can be targeted too. **This does not generalize to a real
remote staging host** - a CI runner has no `docker exec` access into a remote deploy
target's Postgres container over the network. Running the full suite (not just the
BASE_URL-portable, non-role-granting specs) against a genuinely remote staging host
needs a different mechanism - e.g. a dedicated, narrowly-scoped internal
role-granting endpoint gated to a non-production environment, or an SSH-tunneled
`psql` step in the staging E2E workflow - not built this phase; a real, disclosed gap
for whoever stands up the first real remote staging host.

## Admin bootstrap in staging

Same procedure as production (`DEPLOYMENT.md` step 8) - a distinct, staging-only first
Admin account, never the same credentials as production's.
