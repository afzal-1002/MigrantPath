# First Production Deployment

Status: **the procedure below, not a record of it having happened.** No real production
environment has ever been stood up (`DEPLOYMENT.md`'s own "Known Issues"). Every step
here has its individual mechanism verified for real this phase (image builds, health
gating, migration, admin bootstrap, backup/restore, smoke) - see
`docs/product/PHASE_13_REPORT.md` for exactly what was and wasn't executed - but never
all together against a real host with a real domain. This is the runbook for whoever
performs that first real deployment, distinct from `DEPLOYMENT.md` (the *recurring*
release procedure, once a production environment already exists).

## 1. Provision a host/platform

Any Docker-capable host or managed container platform (ADR-013, provider-neutral).
`HOSTING PROVIDER: NOT SELECTED` as of this phase (brief §188) - modest sizing is
enough for a first, low-traffic release (`infra/docker-compose.prod.yml`'s own
`deploy.resources` comments: 1 CPU/768M for the backend, 0.5 CPU/128M for the
frontend, revisit with real measured usage - brief §189-§191, no capacity claims made
here).

## 2. Configure DNS and TLS

`docs/operations/DNS_AND_TLS.md` - point the production subdomain at the host, arrange
TLS termination (managed load balancer or a self-hosted reverse proxy/ACME client in
front of the two application containers).

## 3. Provision the database

Managed PostgreSQL 18 recommended (`ENVIRONMENTS.md`), or the bundled
`self-hosted-db` Compose profile for a single-VPS deployment. Either way: **a
database dedicated to production**, never shared with staging (brief §161).

## 4. Create the database user

A dedicated application role, not a superuser (`DEPLOYMENT.md` "Database"). Real
least-privilege schema-migration-vs-application-DML role separation is a documented,
accepted limitation for this MVP's scale, not implemented - see
`docs/product/PHASE_13_REPORT.md`'s "Known Deployment Gaps".

## 5. Configure secrets

Fill `infra/.env.production` from `infra/.env.production.example` (or inject the same
variables through the platform's own secret store and skip a file entirely) - every
variable's purpose and secret/public classification is in
`docs/operations/ENVIRONMENT_VARIABLES.md`. Never commit the filled file.

## 6. Run migrations

For this first release, Flyway runs at backend startup (the deliberate, disclosed
MVP-scale choice - `DEPLOYMENT.md` step 4). Starting the backend container in step 8
below **is** this step - there is no separate migration command to run first for a
single-instance deployment.

## 7. Back up before the very first deploy is optional but starting the habit now is not

There is no existing production data to lose on a from-empty first deploy, but
performing the real backup command once now (`DATABASE_BACKUP.md`) confirms the
mechanism works against the real target *before* any real user data exists, cheaper
than discovering a broken backup command during a real incident later.

## 8. Deploy backend and frontend

```bash
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.production up -d
# add --profile self-hosted-db only if DB_HOST=postgres (the bundled container)
```

Wait for both containers' own `HEALTHCHECK` to report healthy (`docker compose ps`) -
the backend's own readiness gate (`/actuator/health/readiness`) already ensures
migrations completed and the database is usable before it ever reports healthy
(verified this phase: a deliberately-bad DB password never lets the container become
ready, and a missing required variable fails the same way - see
`docs/product/PHASE_13_REPORT.md`'s "Failure Exercises").

## 9. Bootstrap the first Admin

Set `APP_ADMIN_BOOTSTRAP_ENABLED=true` plus `ADMIN_BOOTSTRAP_EMAIL`/
`ADMIN_BOOTSTRAP_PASSWORD` for this one deploy, confirm the log line ("Admin bootstrap:
created the first ADMIN account for ..."), sign in as that account
(`DEPLOYMENT.md` step 8's full detail).

## 10. Verify legal content

A fresh production database has **no legal content** - `SCHEMA READY` is not
`CONTENT PROVISIONED` (brief §156). Author the real Warsaw MVP procedures/rules through
the real Admin governance workflow, the same way staging content is authored
(`STAGING.md`'s "Legal content: staging vs. production") - never a bulk import, never a
restore from another environment's database (brief §39/§40/§41). Run
`infra/scripts/db-quality-check.sql` afterward to confirm no overlapping active
versions, no orphaned records, and no test-content leakage before considering the
catalogue release-ready.

## 11. Smoke

`docs/releases/PRODUCTION_RELEASE_CHECKLIST.md`'s "After deploy" section, or
`BASE_URL=https://<production-domain> ./scripts/release-smoke.sh` - non-destructive
only.

## 12. Disable bootstrap

Unset `APP_ADMIN_BOOTSTRAP_ENABLED`/`ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD`
and redeploy/restart. The runner is a structural no-op forever after once any `ADMIN`
exists, but never leave a real credential sitting in a deployment platform's env var
history longer than needed.

## 13. Verify backups and monitoring

Turn on the platform's own scheduled backup (managed Postgres provider's automated
backup, or a real cron running the `pg_dump` command in `DATABASE_BACKUP.md` for a
self-hosted database) - a documented, drilled procedure is not the same as an actually
scheduled job (`DATABASE_BACKUP.md`'s own disclosed gap). Structured logging,
metrics dashboards, and alerting are canonical Phase 14's scope, not a precondition for
this first deployment - see `docs/product/IMPLEMENTATION_PLAN.md`.
