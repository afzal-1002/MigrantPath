# Disaster Recovery

Status: initial targets for a first production release, honestly labeled as
**aspirational and not yet tested at production scale** (brief §142's own explicit
requirement). The one thing that *is* tested is the underlying backup/restore mechanism
itself - see `DATABASE_RESTORE.md`'s real drill.

## Scenarios covered

| Scenario | Response |
|---|---|
| Backend container crashes/OOMs | Container `restart: unless-stopped` (Compose) restarts it automatically; `-XX:+ExitOnOutOfMemoryError` (backend Dockerfile) ensures an OOM condition actually exits the JVM rather than limping in a corrupted state, so the restart policy gets a clean trigger. |
| Frontend/reverse-proxy container crashes | Same `restart: unless-stopped`. |
| Database instance lost entirely | Restore the most recent backup (`DATABASE_RESTORE.md`) into a new instance; point `DB_HOST` at it; redeploy. |
| Entire host/VPS lost (self-hosted DB path) | Provision a new host, redeploy both images, restore the database from the last off-instance backup. RPO = time since that backup (see targets below). |
| Managed database provider outage | Provider-dependent - most managed Postgres offerings carry their own multi-AZ/failover story; this application has no code-level dependency preventing that (ADR-013's "database stays swappable"). |

## Recovery targets (initial, aspirational)

- **RPO (Recovery Point Objective): 24 hours** - matches the daily backup cadence in
  `DATABASE_BACKUP.md`. Any writes since the last backup are lost in a worst-case total
  loss.
- **RTO (Recovery Time Objective): a few hours** - provisioning a new host/container,
  restoring the backup, and redeploying, done manually by an operator following
  `DEPLOYMENT.md`/`DATABASE_RESTORE.md`. No automated failover exists.

**These are not tested SLAs.** They are the honest starting targets for a first,
low-traffic release - revisit once real production incident/backup-size data exists.
Do not represent this document as an enterprise disaster-recovery guarantee.

## Point-in-time recovery (brief §33)

Not implemented in this first release. If/when a managed PostgreSQL provider with PITR
support is chosen for production (recommended - `docs/operations/ENVIRONMENTS.md`/
ADR-013), document its specific PITR window and restore procedure here at that point.
Docker Compose's own bundled `postgres` service has no PITR capability - faking it (a
raw WAL-archiving setup in Compose) is explicitly out of scope for this phase (brief
§33's own "do not fake PITR in Docker Compose").

## What would make this stronger (tracked, not done)

- An automated, scheduled backup job (see `DATABASE_BACKUP.md`'s own disclosed gap).
- A real cross-region/off-provider backup copy, beyond "off-instance."
- A recurring (not one-time) restore drill against the real production backup store.
- A documented, rehearsed host-loss drill (provisioning a brand-new host end to end),
  not just the database-restore half.
