# ADR-015: Deployment promotion and release artifact strategy

Status: Accepted — 2026-09-04 (Phase 13)

## Context

ADR-013 (Phase 11) established the production **topology** (same-origin two-container
Compose, provider-neutral) and built/verified it once, locally. What it deliberately
left open: how a specific, tested git commit becomes a specific, identifiable running
release; how that same artifact moves through staging before production; what happens
when a migration or a deploy fails; and whether a rollback is actually possible. Phase
13 had to answer these concretely, not just describe them.

## Decision

**Build once, promote the same immutable image.**

```text
Git commit
  ↓ CI (ci.yml - real regression: backend verify, frontend lint/test/build, Playwright)
  ↓ release-build.yml - builds & tags both images with the git SHA (never `latest`
    alone), validates the frontend's nginx config inside the built image, generates
    release-manifest.json
  ↓ deploy-staging.yml (manual workflow_dispatch, image_tag input) - the exact same
    images, never rebuilt
  ↓ Staging smoke/E2E (scripts/release-smoke.sh; Playwright via BASE_URL)
  ↓ Manual production approval (GitHub `production` Environment, required reviewers)
  ↓ deploy-production.yml - the exact same images promoted, one more time never rebuilt
  ↓ Production non-destructive smoke
```

- **Image identity is the git SHA**, always co-present with an optional short tag - a
  deployment is never identified only by `latest`. `GET /api/v1/platform/status`
  independently confirms which commit a running instance actually serves (already real,
  Phase 11 - re-verified this phase against the built release images).
- **Staging and production are the same topology, same images, different environment
  variables only** (`docs/operations/ENVIRONMENTS.md`/`STAGING.md`) - never a separately
  rebuilt "staging-flavored" image.
- **Migration stays Flyway-at-backend-startup** for this MVP's single-instance scale
  (unchanged from ADR-013/Phase 11's own choice) - a dedicated pre-deploy migration step
  is deferred until more than one backend instance ever runs simultaneously, at which
  point two instances racing to migrate becomes a real risk this doesn't yet have.
  Migration failure blocks the backend from ever becoming ready (verified this phase via
  real failure exercises), which is what actually prevents a broken schema from ever
  serving traffic - no separate gate was needed to achieve that property.
- **Manual, gated production approval, always** (brief §19/§169) - `deploy-production.yml`
  requires GitHub's own `production` Environment (repository-configured required
  reviewers - cannot be set from workflow YAML, documented as such rather than
  fabricated). No path from a `main` merge to production exists.
- **Rollback is code-first, evaluated per-migration, never blind.** `docs/operations/
  ROLLBACK.md` documents a real, tested compatibility result (Phase 13's rollback
  exercise): the previous release runs correctly against the current schema for every
  path except the one column the newest migration made `NOT NULL` with no default -
  found and precisely characterized by actually building the old image and running it
  against a disposable copy of the real schema, not assumed compatible or incompatible.
  Database rollback (reversing a migration) is never attempted - forward-fix, or accept
  the documented compatibility boundary for the rollback window.

## Verified, not assumed (this phase)

Mirroring ADR-013's own convention - every claim above was checked against real,
running artifacts, not just written:

- Both images build clean from the current source tree (`docker build`, real commit SHA
  tags).
- The full three-container stack, built from those exact images, serves a real
  register → verify (via real Mailpit) → login → account export round trip, and a
  real guided assessment → recommendation → case flow, through the real reverse proxy -
  `PHASE_13_REPORT.md`'s "Production-Like Deployment".
- Three real failure exercises (bad DB password, missing required env var, invalid
  nginx config) all fail safely - the application never reports ready/serves traffic on
  bad configuration, and the config error is caught by `nginx -t` before an invalid
  reverse-proxy config would ever run.
- A real rollback compatibility test (previous release image against the current
  schema) - see "Decision" above.
- A restore drill repeated against the current (V48) schema, not a stale pre-Phase-12
  one (`docs/operations/DATABASE_RESTORE.md`).
- Two real, previously-undiscovered bugs were found and fixed in the process: the
  frontend container's own `HEALTHCHECK` had been failing since Phase 11 (`localhost`
  resolving to `::1` inside the Alpine/musl image against nginx's IPv4-only `listen`;
  `docker ps` still showed the container "Up" throughout because nothing in
  `infra/docker-compose.prod.yml` gates on the frontend container's own health) - fixed
  by pinning `127.0.0.1` in both Dockerfiles' `HEALTHCHECK`; and `TokenCleanupService`'s
  `@Scheduled` entry point threw `TransactionRequiredException` on every real run
  (same-class self-invocation bypassing the `@Transactional` proxy - the unit test
  never caught it because it calls the bean externally) - fixed by moving
  `@Transactional` onto the proxied entry point itself.

## Consequences

- A release is always independently, cheaply identifiable (`platform/status`,
  `release-manifest.json`) and the exact tested artifact is what gets promoted -
  no "works in staging, rebuilt differently for production" class of bug is possible.
- Registry push, real staging/production hosts, DNS/TLS, and real CD execution remain
  `CONFIGURED, NOT EXECUTED` / `DOCUMENTED, NOT EXECUTED` (`PHASE_13_REPORT.md`'s "Known
  Deployment Gaps") - this ADR records the strategy and the real local proof of every
  mechanism it depends on, not a claim that a real cloud deployment has happened.
- The rollback compatibility boundary this phase found (one write path, one migration)
  is not a general guarantee for every future migration - `ROLLBACK.md` states plainly
  that each future migration needs the same real test, not an assumption based on this
  one result.
