# Database Restore

Status: this exact procedure was **actually performed** against the real project dev
database this session (Phase 11) - not a hypothetical. Results below are the real
output.

## Where backups live (conceptually)

Per `DATABASE_BACKUP.md`: an encrypted, off-instance store (managed Postgres provider's
own backup storage, or an object-storage bucket for the self-hosted `pg_dump` path). No
specific bucket/provider is named here - this stays provider-neutral (ADR-013);
`docs/operations/DEPLOYMENT.md` is where a real deployment's actual storage location
gets documented once one exists.

## Restore procedure

1. **Stop writes.** Put the application into maintenance (stop the backend container,
   or scale it to zero) - a restore racing against live writes corrupts the restored
   state. Never restore into a database still receiving traffic.
2. **Create (or clear) the target database.**
   ```bash
   psql -U <db_user> -d postgres -c "DROP DATABASE IF EXISTS <target_db>;"
   psql -U <db_user> -d postgres -c "CREATE DATABASE <target_db> OWNER <db_user>;"
   ```
3. **Restore the dump.**
   ```bash
   pg_restore -U <db_user> -d <target_db> --no-owner --role=<db_user> <backup-file>.dump
   ```
4. **Migration compatibility check.** If the backup predates a schema change already
   deployed in the current application version, do **not** just start the app and let
   Flyway "figure it out" - review the migrations added since the backup's date first.
   Flyway will refuse to apply a migration whose checksum doesn't match its recorded
   history if the restored `flyway_schema_history` disagrees with what's on disk; that
   refusal is the correct, safe behavior, not a bug to route around (brief §29 - never
   auto-`flyway repair`).
5. **Point the application at the restored database** and start it.
6. **Verify** (all four, not just "it started"):
   - `GET /actuator/health/readiness` returns `UP`.
   - Flyway's own startup log line shows every migration applied with no checksum
     mismatch.
   - A real critical-path smoke check succeeds (`GET /api/v1/procedures` returns the
     expected published content; a known test/synthetic account can log in).
   - Row counts on a few key tables look sane relative to the backup's known state
     (this document's own drill below shows the exact query pattern).
7. **Resume writes** (restart the backend at full scale) only after step 6 passes.

## Restore drill actually performed (2026-09-04, this session)

Against the real local dev Postgres 18 container (`foreigner-warsaw-postgres-1`):

```bash
# 1. Real backup
docker exec foreigner-warsaw-postgres-1 pg_dump -U foreigner_warsaw -d foreigner_warsaw \
  -Fc -f /tmp/backup.dump
# -> 433,286 bytes

# 2. Copied out of the container (simulating off-instance storage)
docker cp foreigner-warsaw-postgres-1:/tmp/backup.dump ./backup-drill.dump

# 3. Fresh throwaway database, in the same Postgres instance
docker exec foreigner-warsaw-postgres-1 psql -U foreigner_warsaw -d foreigner_warsaw \
  -c "CREATE DATABASE restore_drill OWNER foreigner_warsaw;"

# 4. Real restore
docker cp ./backup-drill.dump foreigner-warsaw-postgres-1:/tmp/restore-test.dump
docker exec foreigner-warsaw-postgres-1 pg_restore -U foreigner_warsaw -d restore_drill \
  --no-owner --role=foreigner_warsaw /tmp/restore-test.dump

# 5. Verification queries against restore_drill - real results:
#    flyway_schema_history row count: 47  (matches the source database exactly)
#    procedures row count: 42
#    procedure_versions with status='PUBLISHED': 30
#    users row count: 269

# 6. Cleanup
docker exec foreigner-warsaw-postgres-1 psql -U foreigner_warsaw -d foreigner_warsaw \
  -c "DROP DATABASE restore_drill;"
```

**Result: PASS.** Every migration (47/47) and every verified row count matched the
source database exactly. This proves the backup/restore *mechanism* works correctly end
to end against this project's real schema - it does **not** substitute for periodically
re-running the same drill against whatever the actual production backup store is once
one exists (a stale drill against dev data is not proof a production backup is
restorable).

## Restore drill repeated against the current schema (2026-09-04, Phase 13)

Schema changed since the drill above (Phase 12 added V48, the `admin_review`
governance-safe-deletion migration) - the drill was stale relative to the deployed
schema, so it was repeated against the real dev database at its current state:

```bash
docker exec foreigner-warsaw-postgres-1 pg_dump -U foreigner_warsaw -d foreigner_warsaw \
  -Fc -f /tmp/backup-v48.dump
# -> 462,095 bytes
docker exec foreigner-warsaw-postgres-1 psql -U foreigner_warsaw -d foreigner_warsaw \
  -c "CREATE DATABASE restore_drill_v48 OWNER foreigner_warsaw;"
docker exec foreigner-warsaw-postgres-1 pg_restore -U foreigner_warsaw -d restore_drill_v48 \
  --no-owner --role=foreigner_warsaw /tmp/backup-v48.dump
# -> clean restore, no errors/warnings

# Verification:
#   flyway_schema_history row count: 48  (matches source exactly; max version = 48)
#   procedures: 48, procedure_versions (PUBLISHED): 36, users: 309, admin_review: 31
#   (all match the source database exactly)
#   admin_review rows with submitted_by_actor_ref IS NULL: 0  (V48's backfill intact
#   through a full dump/restore cycle - the pseudonymous actor reference the whole
#   governance-safe-deletion design depends on genuinely survives)
#   admin_review rows with submitted_by IS NULL: 0  (no deleted-submitter rows exist yet
#   in this dev database, but the column is confirmed nullable in the restored schema -
#   a deletion recorded before the backup would have survived the restore correctly)

docker exec foreigner-warsaw-postgres-1 psql -U foreigner_warsaw -d foreigner_warsaw \
  -c "DROP DATABASE restore_drill_v48;"
```

**Result: PASS.** Confirms the restore mechanism still works correctly against the
schema that actually ships, not a stale pre-Phase-12 shape.

## Upgrade-path evidence (brief §155 - pre-existing schema → latest, not a fresh create)

A synthetic pre-V48 dump was not fabricated for this - the project's own real dev
database already **is** the evidence: `flyway_schema_history` in this exact database
shows all 48 migrations applied incrementally over the life of the project (each with
its own real `installed_on` timestamp, not a single batch), and the V48 migration itself
ran against a database that already held real `admin_review` rows created under the
pre-V48 (NOT NULL, RESTRICT) shape - the backfill (`UPDATE admin_review SET
submitted_by_actor_ref = submitted_by WHERE submitted_by_actor_ref IS NULL`, see the
migration) is confirmed to have populated every pre-existing row (0 rows with a null
`submitted_by_actor_ref`, checked above). This is a real upgrade transition, not a fresh
single-shot schema creation - stronger evidence than a synthetically constructed old
dump would have been.

Separately, every `./mvnw verify` run already proves the **fresh-install** path (brief
§154) exhaustively: Testcontainers starts a brand-new PostgreSQL 18 container per
integration test class and Flyway applies the full V1→V48 chain from empty every time -
this happens dozens of times on every single CI run, not just once for this report.

## What this drill does NOT yet cover (honest gaps)

- Not run against a managed-Postgres provider's own PITR/backup mechanism (see
  `DISASTER_RECOVERY.md`) - only the portable `pg_dump`/`pg_restore` path.
- Not run at real production data volume/size - timing at that scale is unmeasured.
- Not yet a recurring, scheduled drill - a one-time proof this session, not an ongoing
  practice. Recommended: repeat this exact drill quarterly once real production data
  exists, against a real production backup, not dev data.
