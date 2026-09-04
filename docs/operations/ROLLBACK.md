# Rollback

Status: application rollback mechanism is real and simple (redeploy a previous
immutable image tag); a real compatibility test was performed this phase (Phase 13)
against the actual previous release and the actual current schema - see "App rollback"
below for the precise, tested result (not a guess).

## App rollback

Redeploy the previous image tag:

```bash
IMAGE_TAG=<previous-git-sha> docker compose -f infra/docker-compose.prod.yml \
  --env-file infra/.env.production up -d
```

Every release is tagged with its real git commit SHA (`docs/releases/RELEASE_PROCESS.md`
"Image tagging") - a rollback target is always identifiable, and (once a registry with
retention exists) always pullable.

### Real compatibility test performed (2026-09-04, Phase 13)

The previous release (commit `36832b4`, pre-Phase-12/pre-V48 code) was built as a real
image and run against a disposable copy of the **current** (V48) schema:

```text
Flyway:    "Schema has a version (48) that is newer than the latest available
           migration (47)" - WARN, not a failure; validates and continues correctly.
Hibernate: ddl-auto: validate passed clean.
Health:    UP. Application started normally.
```

**Result: the previous release starts up and serves traffic correctly against the
current schema for every existing read/write path except one, found by directly
testing it:** V48 added `admin_review.submitted_by_actor_ref` as `NOT NULL` with no
database-level default. The old code's `AdminReview` entity has no mapping for that
column at all, so an INSERT it issues (creating a **new** content-review submission -
`POST /api/v1/admin/procedures/.../submit` and equivalents) omits the column entirely
and is rejected by the database:

```text
ERROR: null value in column "submitted_by_actor_ref" of relation "admin_review"
violates not-null constraint
```

Confirmed directly (not inferred) by issuing the exact INSERT the old entity would
generate against a disposable copy of the real schema.

**Practical meaning:** rolling back to the previous release after a V48-bearing deploy
is safe for every user-facing flow (registration, login, assessment, recommendation,
case management, personal-data export/deletion, existing admin governance reads) and
unsafe for exactly one action - **submitting new content for review** - which fails
loudly (a clean 500, not silent corruption or data loss) rather than succeeding
incorrectly. This is not a hypothetical "may not work" - it is a tested, precise
boundary. If a rollback to a pre-V48 release is ever needed in practice, disable new
content submission (or accept that specific action failing) for the rollback window,
then forward-fix and redeploy current code rather than trying to patch the old release.

This result is specific to the V48 migration's own design choice (a `NOT NULL` actor
pseudonym with no default, deliberately, so the governance-safe-deletion design in
`docs/architecture/ADR/ADR-014-personal-data-lifecycle.md` has a reliable value to read)
- it is not a general property of this application's rollback story, and every future
migration should be evaluated the same concrete way (build the previous image, point it
at the new schema, test the paths the migration touched) rather than assumed compatible
or incompatible.

## Migration failure

Flyway's own migration-locking and checksum verification (already relied on, not
rebuilt) means:

- **Never** auto-`flyway repair` on a checksum mismatch - investigate why the recorded
  history disagrees with what's on disk first (`DATABASE_RESTORE.md` step 4).
- A migration that fails partway leaves `flyway_schema_history` reflecting exactly what
  applied - the backend's `entityManagerFactory`/`flywayInitializer` bean creation fails
  as part of the same startup sequence, so the application **never becomes ready**
  (confirmed this phase via the bad-DB-credentials failure exercise below, which
  exercises the identical "backend never becomes ready, exits non-zero" code path a
  migration failure would also take) - no orchestrator ever routes traffic to a
  partially-migrated instance.
- The previous release, still running (or redeployable per "App rollback" above), stays
  the safe fallback - never attempt a second, different migration to "fix forward"
  under incident pressure without the same real-schema testing discipline above.

## Legal-content rollback

See `docs/operations/INCIDENT_RESPONSE.md`'s "Bad legal content incident" - end-date/
archive the bad `ProcedureVersion`/`RuleVersion` through the real Admin governance
workflow. **Never** a SQL `UPDATE`/`DELETE` against `procedure_versions`/`rule_versions`
- every change must remain attributable to a real reviewed action (ADR-012).

## Configuration rollback

Environment variable changes are not tracked by this repository (real values live in
`infra/.env.production`/the deployment platform's secret store, never committed - see
`.env.production.example`). Keep a change record outside version control (the
deployment platform's own secret-history, if it has one, or a simple dated log entry
kept alongside operational runbooks) so a bad config change can be identified and
reverted independently of a code rollback. No Vault/Consul-style system is introduced
for this (brief §121's own "no need").

## Secret rotation

- **Database credentials**: rotate at the provider, update `infra/.env.production`
  (or the platform's secret store), restart the backend container (`docker compose up
  -d backend` - HikariCP does not hot-reload credentials). No credential is ever baked
  into an image.
- **SMTP credentials**: same pattern - update the secret, restart the backend.
- **Admin bootstrap secret**: never persists beyond the one startup that uses it -
  `APP_ADMIN_BOOTSTRAP_ENABLED`/`ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` are
  unset immediately after the first successful bootstrap (`DEPLOYMENT.md` step 8) -
  there is nothing to "rotate" since it is never left active.
- **Deploy-token rotation** (GHCR/registry credentials, once a real registry is wired):
  rotate at the registry, update the CI secret store (`release-build.yml`'s
  `secrets.GITHUB_TOKEN` is GitHub-managed and rotates automatically; a
  separately-issued deploy key/token would need manual rotation at whatever interval
  the org's own policy requires).

No real rotation was performed this phase - no real credential exists yet to rotate.
