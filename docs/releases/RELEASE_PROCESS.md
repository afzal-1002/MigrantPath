# Release Process

Status: the pipeline described below is **partially implemented** - CI (build/test/e2e
validation) is real and has been running since Phase 1; the staging/production
deploy stages, the manual-approval gate, and CD automation do not exist yet. This
document describes the intended process and marks each stage's actual status.

## Versioning

Semantic Versioning (SemVer). Current stage: pre-1.0 (`0.x.y`) - the product has not yet
had its first real production release; `1.0.0` is reserved for that milestone, not
forced prematurely (brief §79's own "do not force 1.0"). `pom.xml`'s
`<version>0.0.1-SNAPSHOT</version>` should be bumped to a real released version (e.g.
`0.1.0`) at the point of the actual first deploy, not before.

## Pipeline stages

```
Commit/PR
  ↓
CI (backend verify, frontend lint/test/build, full Playwright e2e)   -- REAL, existing (.github/workflows/ci.yml)
  ↓
Build artifacts/images (backend/Dockerfile, frontend/Dockerfile)     -- REAL, build-tested this session
  ↓
Staging deploy                                                        -- NOT YET AUTOMATED
  ↓
Smoke/E2E against staging                                             -- procedure documented (PRODUCTION_RELEASE_CHECKLIST.md), not yet run against a real staging deploy
  ↓
Manual production approval                                            -- NOT YET IMPLEMENTED (no CD platform chosen)
  ↓
DB migration (docs/operations/DEPLOYMENT.md step 4)                   -- REAL mechanism (Flyway-on-startup), verified this session
  ↓
Production deploy                                                     -- NOT YET PERFORMED
  ↓
Smoke tests                                                            -- procedure documented, not yet run against real production
```

**No commit is ever deployed to production automatically** (brief §75/§76) - even once
CD exists, production remains an explicit, gated, approved action, never a side effect
of merging to `main`. Until a CD platform is actually chosen and wired
(`.github/workflows/cd.yml` does not exist yet - a real, disclosed gap, not "coming
soon" filler), every deploy is manual, following `docs/operations/DEPLOYMENT.md`.

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
