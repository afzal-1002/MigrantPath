# Release Process

Status: **updated Phase 13.** CI (build/test/e2e validation) is real and has been
running since Phase 1. `release-build.yml`/`deploy-staging.yml`/`deploy-production.yml`
now exist and are YAML-valid (Phase 13, ADR-015) - `release-build.yml`'s image build/tag/
config-validation/manifest steps are real, locally-equivalent-verified this phase;
registry push and the two deploy workflows' actual remote-deployment steps are
CONFIGURED, NOT EXECUTED - no real staging/production host exists yet to deploy to, and
no registry credentials exist in this environment to prove a real push. This document
marks each stage's actual status; see `docs/product/PHASE_13_REPORT.md` for the full
account.

## Versioning

Semantic Versioning (SemVer). Current stage: pre-1.0 (`0.x.y`) - the product has not yet
had its first real production release; `1.0.0` is reserved for that milestone, not
forced prematurely (brief §79's own "do not force 1.0"). `pom.xml`'s
`<version>0.0.1-SNAPSHOT</version>` should be bumped to a real released version (e.g.
`0.1.0`) at the point of the actual first deploy, not before.

## Pipeline stages

```
Pull Request
  ↓
CI (backend verify, frontend lint/test/build, full Playwright e2e)   -- REAL, existing (.github/workflows/ci.yml)
  ↓
Merge to main
  ↓
release-build.yml: build immutable images, tag with git SHA,          -- REAL mechanism, build/tag/nginx-config-validation/
  validate nginx config, generate release-manifest.json,                 manifest-generation all verified locally this
  optionally push to GHCR                                                phase; registry push CONFIGURED, NOT EXECUTED
  ↓
deploy-staging.yml (manual workflow_dispatch, image_tag input)        -- workflow exists, YAML-valid; CONFIGURED, NOT
  ↓                                                                       EXECUTED - no real staging host provisioned yet
Staging smoke/E2E (scripts/release-smoke.sh; Playwright via BASE_URL) -- both real and locally verified against a real
  ↓                                                                       production-like stack this phase (PHASE_13_REPORT.md)
Manual production approval (GitHub `production` Environment)          -- workflow exists; repository-side required-reviewer
  ↓                                                                       config must be set in GitHub Settings, not this file
Production backup (docs/operations/DATABASE_BACKUP.md)                -- REAL, drilled mechanism; not yet a scheduled job
  ↓
DB migration (docs/operations/DEPLOYMENT.md step 4)                   -- REAL mechanism (Flyway-on-startup), re-verified this phase
  ↓
deploy-production.yml                                                 -- workflow exists; CONFIGURED, NOT EXECUTED - no real
  ↓                                                                       production host provisioned yet
Production non-destructive smoke                                      -- scripts/release-smoke.sh, real and locally verified
```

**No commit is ever deployed to production automatically** (brief §75/§76) - production
remains an explicit, gated, approved action (`deploy-production.yml`'s `environment:
production`), never a side effect of merging to `main`. Every workflow above is real,
committed YAML (`.github/workflows/release-build.yml`, `deploy-staging.yml`,
`deploy-production.yml`) - what remains CONFIGURED, NOT EXECUTED is a real remote
run of any of them (no live GitHub Actions execution has fired them in this
environment) and the actual remote-deployment commands inside the two deploy
workflows (placeholder steps, documented as such, until a real host exists - see
ADR-015 and `docs/product/PHASE_13_REPORT.md`).

## Image tagging (brief §77)

Never deploy `latest` alone. Tag every built image with the real git commit SHA and/or
a SemVer release tag:

```bash
docker build --build-arg BUILD_COMMIT=$(git rev-parse HEAD) \
  -t foreigner-warsaw-backend:0.1.0 \
  -t foreigner-warsaw-backend:$(git rev-parse --short HEAD) \
  backend/
```

The deployed version is always independently confirmable at runtime via
`GET /api/v1/platform/status` (`{"version": "...", "commit": "..."}`) - verified this
session to correctly report the real build commit when built this way.

## Rollback strategy (brief §82)

- **Code rollback**: redeploy the previous image tag. Simple, safe, always available -
  every image is tagged and (once a registry exists) retained.
- **Database rollback**: **never** a blind migration reversal. Prefer a forward-fix
  migration. If a genuinely non-backward-compatible schema change is unavoidable in the
  future, use the expand → deploy → migrate → contract pattern: add new
  columns/tables alongside the old ones, deploy code that can read both, backfill,
  deploy code that only uses the new shape, then drop the old columns in a later,
  separate migration - never a single migration that breaks the currently-running old
  code mid-deploy.
- **Legal content rollback**: see `docs/operations/INCIDENT_RESPONSE.md`'s "Bad legal
  content incident" - end-date/archive the bad version through the real Admin workflow,
  never a SQL delete (brief §83).

## Release notes

Each real release should record, in a dated entry under this directory (a
`RELEASE_NOTES.md` or per-version file - format not yet fixed, since no release has
happened yet):

- Product changes
- Migrations included
- Legal-content changes (see the template below - brief §81)
- New/changed production Rules or Thresholds
- Security-relevant changes
- Known issues carried into this release
- Rollback notes specific to this release, if any

### Legal-content release-note template (brief §81)

```text
<PROCEDURE_CODE>
Procedure vN → vN+1  (or: new Rule <CODE> published)

Reason: <why - e.g. "official source updated", "Phase 10.5 rule wiring">
Effective: <date>
Source: <OfficialSource title, no internal-only detail>
```

Never include personal data (no user IDs, no case counts tied to identifiable
individuals) in a release note - aggregate, anonymous facts about the content itself
only.
