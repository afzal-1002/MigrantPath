# Deployment Runbook

Status: reflects what was actually built and verified in Phase 11 (ADR-013). This
procedure has been exercised end to end **locally** (a real three-container stack, real
HTTP verification - see ADR-013's "Verified, not assumed" section) but **not yet against
a real cloud target with a real domain/TLS certificate** - that first real deployment is
the next concrete action after this document, not something already claimed done here.

## Prerequisites

- A Docker-capable host (any provider - ADR-013) or an equivalent managed container
  platform.
- A PostgreSQL 18 instance - either the bundled `postgres` Compose service
  (`self-hosted-db` profile) or a managed instance (recommended - brief §166).
- A real domain name, pointed at the host (see `DNS.md`... - not yet a separate file;
  tracked as a follow-up, see Known Issues in the Phase 11 report).
- A TLS certificate for that domain (Let's Encrypt via your reverse-proxy/host
  platform's own automation is the common choice - this repo does not bundle a
  certificate-issuance tool itself, since that's usually the hosting platform's job or
  a dedicated tool like Caddy/certbot running alongside the two application containers).
- A real transactional-email SMTP account (see `EMAIL_PRODUCTION.md`).
- The environment variables in `infra/.env.production.example`, filled with real values
  - **never** commit the filled file (already `.gitignore`d - see that file's own
  header).

## Steps

1. **Build images**, tagged with the real release version and/or git SHA (never
   `latest` alone - brief §77):
   ```bash
   docker build --build-arg BUILD_COMMIT=$(git rev-parse HEAD) \
     -t <registry>/foreigner-warsaw-backend:<version> backend/
   docker build -t <registry>/foreigner-warsaw-frontend:<version> frontend/
   ```
2. **Push** to your registry (or skip, if building directly on the deploy host).
3. **Back up the database** before any migration-bearing deploy (`DATABASE_BACKUP.md`)
   - non-negotiable for a schema-changing release (brief §31).
4. **Run migrations** - for this first release, Flyway runs at backend startup (the
   `spring-boot-starter-flyway` default, unchanged) rather than a separate migration
   step. This is a deliberate, disclosed choice for the MVP's scale (brief §28's own
   "may remain if rollback and deployment ordering are controlled") - revisit as a
   dedicated pre-deploy migration step once more than one backend instance/replica is
   ever run simultaneously, since two instances racing to apply the same migration set
   is the scenario a separate step exists to avoid.
5. **Deploy**, e.g. via the provided Compose file:
   ```bash
   docker compose -f infra/docker-compose.prod.yml \
     --env-file infra/.env.production up -d
   # add --profile self-hosted-db only if DB_HOST=postgres (the bundled container)
   ```
6. **Wait for health**: the backend container's own `HEALTHCHECK` (curl against
   `/actuator/health/readiness`) and the frontend's (`/healthz`) both gate
   `docker compose`'s own startup ordering already (ADR-013) - do not manually route
   traffic to a container before Docker reports it healthy (brief §68's "deployment
   health gate").
7. **Run the smoke checklist** (`docs/releases/PRODUCTION_RELEASE_CHECKLIST.md`'s
   "Pre-release smoke checklist" section) against the real deployed URL.
8. **First deploy only - admin bootstrap** (brief §24): set
   `APP_ADMIN_BOOTSTRAP_ENABLED=true` plus `ADMIN_BOOTSTRAP_EMAIL`/
   `ADMIN_BOOTSTRAP_PASSWORD` for this one deploy, confirm the log line
   ("Admin bootstrap: created the first ADMIN account for ..."), sign in as that
   account, then **unset all three variables and redeploy** (the bootstrap runner is a
   structural no-op forever after once any `ADMIN` exists, but unsetting the password
   is still good practice - never leave a real credential sitting in a deployment
   platform's env var history longer than needed).

## Rollback

See `docs/releases/RELEASE_PROCESS.md`'s "Rollback strategy" - **code** rollback
(redeploy the previous image tag) is simple and safe; **database** rollback is never a
blind migration reversal (brief §82).

## A real gotcha this session found

`docker compose --env-file <file> up` gives precedence to variables already present in
the **calling shell's own environment** over the same-named variable in `--env-file` -
verified directly (a stray `DB_USERNAME`/`DB_PASSWORD` left in this project's own dev
shell - the exact, already-documented `LOCAL_SETUP.md` gotcha - silently overrode the
test env file's values during this phase's own smoke test). Before any real deploy:
`unset DB_USERNAME DB_PASSWORD DB_HOST DB_NAME MAIL_HOST MAIL_PORT` (or run from a clean
shell/CI runner) so the `--env-file` values are the ones that actually take effect.

## Logs

Both containers log to stdout/stderr only (brief §170) - `docker compose logs -f
<service>`, or your platform's own log aggregation, is the only supported way to view
them. No log file is written inside either container.

## Known Issues (honest, current)

- Not yet run against a real cloud target/domain/TLS certificate - local-stack-verified
  only (ADR-013).
- No dedicated pre-deploy migration step yet (see step 4 above) - fine at current scale,
  a real hardening item once multi-instance deployment begins.
- No automated CD/production-approval pipeline yet - see
  `docs/releases/RELEASE_PROCESS.md`'s own disclosed gap.
