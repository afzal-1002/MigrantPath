# Database Backup

Status: the procedure below was **actually run** against this project's real dev
Postgres 18 container this session (a real `pg_dump`, copied out of the container,
restored into a fresh throwaway database, and verified - see
`DATABASE_RESTORE.md`'s own "Restore drill actually performed" section for the exact
commands and results). No automated backup schedule is wired up yet in any real
deployment - that is the concrete gap this document discloses, not something to assume
already running.

## What must be backed up

- **The full PostgreSQL database** - includes every table: users, `AuditLog` (brief
  §144 - audit/legal-governance records live in the same database, not a separate
  transient log store), Procedure/Rule/Threshold/QuestionnaireVersion content, and
  Spring Session's own session table.
- Nothing else needs backing up: no uploaded files exist (no file-upload feature), and
  application code/config is already recoverable from git + the container registry.

## Minimum policy (brief §32)

- **Daily automated backup**, retained:
  - Daily backups: 7-14 days
  - Weekly backups: several weeks
  - Monthly backups: as long as storage cost allows
- **Encrypted** at rest (brief §145/§162) and **off-instance** (brief §32) - never only
  on the same disk/instance as the live database.
- **Access restricted** - a database backup contains real personal data (email
  addresses, assessment answers, case progress) once real users exist; treat it with
  the same access discipline as production database credentials themselves, never
  copied to a developer's own machine casually (brief §162).

## How (self-hosted Postgres, the `self-hosted-db` Compose profile)

```bash
# From the deploy host, against the running postgres container:
docker exec <postgres-container> pg_dump -U <db_user> -d <db_name> -Fc \
  -f /tmp/backup-$(date +%Y%m%d-%H%M%S).dump
docker cp <postgres-container>:/tmp/backup-<timestamp>.dump ./  # then upload off-instance
```

`-Fc` (custom format) is deliberate - it's the format `pg_restore` (not raw `psql`)
consumes, supports parallel restore, and is what this document's own verified restore
drill used.

## How (managed PostgreSQL - recommended, brief §166)

Use the provider's own backup mechanism (most managed Postgres offerings include
automated daily backups and point-in-time recovery out of the box - see
`DISASTER_RECOVERY.md`'s PITR section). This repository's own `pg_dump` procedure above
remains available as a portable, provider-independent fallback/export mechanism
regardless of which is primary.

## What is NOT yet configured (honest gap)

- No cron/scheduled job actually runs the command above automatically in any real
  environment yet - a manual or platform-native (e.g. the managed Postgres provider's
  own backup schedule) mechanism must be turned on before real user data exists in
  production. Do not treat this document as proof a backup is currently running -
  it is the documented procedure, verified to work, not yet automated.
