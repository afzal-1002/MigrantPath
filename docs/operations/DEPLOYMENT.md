# Deployment Runbook

Status: reflects what was actually built and verified in Phase 11 (ADR-013) and
extended/re-verified in Phase 13 (ADR-015 - promotion strategy, release artifacts,
failure exercises, rollback). This procedure has been exercised end to end **locally**
(a real three-container stack, real HTTP verification, a real registration→verification→
login→export round trip through Mailpit, real failure/rollback exercises - see ADR-013's
and ADR-015's "Verified, not assumed" sections) but **not yet against a real cloud
target with a real domain/TLS certificate** - that first real deployment is the next
concrete action after this document (`FIRST_PRODUCTION_DEPLOYMENT.md`), not something
already claimed done here.

For a first-ever production stand-up, use `FIRST_PRODUCTION_DEPLOYMENT.md` instead - it
covers provisioning, admin bootstrap, and initial legal-content authoring in addition to
everything below, which is the *recurring* release procedure once production already
exists.

## Prerequisites

- A Docker-capable host (any provider - ADR-013) or an equivalent managed container
  platform.
- A PostgreSQL 18 instance - either the bundled `postgres` Compose service
  (`self-hosted-db` profile) or a managed instance (recommended - brief §166).
- A real domain name, pointed at the host (see `DNS_AND_TLS.md`).
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

See `docs/operations/ROLLBACK.md` - **code** rollback (redeploy the previous image tag)
is simple and, per a real compatibility test performed in Phase 13, safe for every path
except one specific, precisely-documented write; **database** rollback is never a blind
migration reversal (brief §82).

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

## Troubleshooting

Real, encountered-and-diagnosed-this-phase failure modes, not a speculative
encyclopedia:

- **Image pull fails** - if pulling from a registry (once one is wired), check
  `docker login`/credential expiry first; a build-from-source fallback
  (`docker build ...` directly, as every step above already shows) always works
  independent of registry availability.
- **DB unavailable / wrong credentials** - the backend container never becomes ready
  and exits non-zero (confirmed this phase - see ADR-015's failure exercises); check
  `docker logs <backend-container>` for `FATAL: password authentication failed` or a
  connection-refused/timeout. Never partially serves traffic in this state.
- **Flyway fails** - same "never becomes ready" behavior; check the log for the exact
  migration and checksum mismatch, if any. Never auto-`flyway repair`
  (`ROLLBACK.md`'s "Migration failure").
- **Backend unhealthy but process is running** - check
  `docker inspect <container> --format '{{json .State.Health}}'` for the actual
  healthcheck output, not just `docker ps`'s status column, which only reflects the
  container process, not the healthcheck (this exact distinction hid a real bug this
  phase - see "A real bug found this phase" below).
- **nginx config invalid** - `nginx -t` inside the built frontend image catches this
  before the container ever starts serving (real, verified this phase against a
  deliberately broken template) - `release-build.yml`'s own "Validate nginx config"
  step runs this on every release build.
- **Cookie/CSRF failure through the reverse proxy** - confirm the request actually went
  through the `frontend` container's proxy (never point a browser/client straight at
  the backend - ADR-013), and that the `X-XSRF-TOKEN` header carries the value from the
  real `XSRF-TOKEN` cookie, not a stale one from a different origin/port.
- **SMTP failure** - the application's own health check deliberately does not fail on a
  mail-provider hiccup (`application.yml`'s `management.health.mail.enabled: false` -
  a degraded external dependency should never take down the whole app); check
  `docs/operations/EMAIL_PRODUCTION.md` and the backend logs for the specific SMTP
  error rather than assuming readiness failure means mail is broken.

### A real bug found this phase

The frontend container's own `HEALTHCHECK` (`wget http://localhost:8080/healthz`) had
been failing on every single run since Phase 11 - `localhost` resolved to `::1` (IPv6)
first inside the Alpine/musl runtime image, and nginx's `listen 8080;` in
`nginx.conf.template` is IPv4-only, so the healthcheck always got "Connection refused."
`docker ps` still showed the container "Up" the entire time - nothing in
`infra/docker-compose.prod.yml` currently `depends_on`-gates on the *frontend*
container's own health (only the backend's), so this was invisible unless you happened
to run `docker inspect --format '{{json .State.Health}}'` specifically. Fixed by
pinning `127.0.0.1` in both Dockerfiles' `HEALTHCHECK` (the backend's was defensively
fixed the same way, though it was never observed to fail - Tomcat binds dual-stack by
default). See ADR-015.

## Known Issues (honest, current)

- Not yet run against a real cloud target/domain/TLS certificate - local-stack-verified
  only (ADR-013/ADR-015).
- No dedicated pre-deploy migration step yet (see step 4 above) - fine at current scale,
  a real hardening item once multi-instance deployment begins.
- CD workflows (`release-build.yml`/`deploy-staging.yml`/`deploy-production.yml`) exist
  and are YAML-valid (Phase 13) but have never had a real GitHub Actions run - CONFIGURED,
  NOT EXECUTED. No registry credentials exist in this environment to prove a real image
  push. See `docs/product/PHASE_13_REPORT.md`.
- Migration DB role is not separated from the application DML role - an accepted MVP
  limitation, not fixed this phase (disproportionate overhead for current scale).
