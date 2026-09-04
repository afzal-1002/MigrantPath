# Release Manifest

`release-build.yml` (Phase 13) generates `release-manifest.json` as a build artifact for
every release build. It captures exactly what was built, not what was deployed (a
separate, later fact - see "Deployed version" below).

## Schema

```json
{
  "version": "0.0.1-SNAPSHOT",
  "commit": "1a102b18bbea634b93a3440c6125ca413bc6be0b",
  "backendImage": "foreigner-warsaw-backend:1a102b18bbea634b93a3440c6125ca413bc6be0b",
  "frontendImage": "foreigner-warsaw-frontend:1a102b18bbea634b93a3440c6125ca413bc6be0b",
  "flywayVersion": 48,
  "buildTimestamp": "2026-09-04T11:00:00Z"
}
```

- `version` - read directly from `backend/pom.xml`'s `<version>`. Still
  `0.0.1-SNAPSHOT` as of this phase (`docs/releases/RELEASE_PROCESS.md`'s own
  "bumped at the point of the actual first deploy, not before" - no real deploy has
  happened, so it has not been bumped).
- `commit` - the full git SHA the images were built from (`BUILD_COMMIT`/`github.sha`).
- `backendImage` / `frontendImage` - the local image tags built, `name:commit-sha`. Once
  a real registry is wired (brief §100), the manifest should also carry the immutable
  digest (`sha256:...`) here, not just the tag - a tag can move, a digest cannot.
- `flywayVersion` - the highest `V<n>__*.sql` migration number present in
  `backend/src/main/resources/db/migration/` at build time (currently 48). Computed
  directly from the migration filenames, never hand-maintained (a hand-maintained
  number would drift the moment a new migration lands without this doc being
  remembered).
- `buildTimestamp` - UTC, ISO-8601.

## No secrets

Nothing above is sensitive - a manifest is safe to attach to a GitHub Actions run, log,
or release note (`release-build.yml` uploads it as a build artifact with 90-day
retention).

## Deployed version (distinct from the manifest)

The manifest describes a **built** artifact; which one is actually **running** in a
given environment is a separate fact, confirmable live via:

```bash
curl https://<environment>/api/v1/platform/status
# {"status":"UP","application":"Foreigner Warsaw","version":"...","commit":"..."}
```

`docs/operations/ROLLBACK.md`'s "Secret rotation"/`RELEASE_PROCESS.md` reference keeping
a simple `DEPLOYED_VERSION`/`DEPLOYED_COMMIT` record on the deploy host itself (brief
§83) - the manifest and the live `/platform/status` endpoint together are the two
sources of truth; neither alone proves a specific commit is what real users are
currently being served.
