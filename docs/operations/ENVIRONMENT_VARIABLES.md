# Environment Variable Contract

Status: reflects every environment variable actually referenced by
`application-{staging,production}.yml`, `infra/docker-compose.prod.yml`,
`docker-compose.yml`, and `frontend/nginx.conf.template` as of this phase (Phase 13) -
verified by grepping every `${VAR}` placeholder in those files, not written from memory.
No variable listed here is aspirational.

**SECRET** variables must never be committed, logged, echoed in CI output, or baked into
the frontend image (brief §32/§174-§179). **PUBLIC CONFIG** is safe to appear in the
Angular build output, container labels, or CI logs.

## Database

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `DB_HOST` | staging, production | Yes, no default (fail-fast) | SECRET-adjacent (infra topology) | Postgres host/endpoint | `db.internal.example.com` |
| `DB_PORT` | staging, production | Yes, no default | PUBLIC CONFIG | Postgres port | `5432` |
| `DB_NAME` | staging, production | Yes, no default | PUBLIC CONFIG | Database name | `foreigner_warsaw` |
| `DB_USERNAME` | staging, production | Yes, no default | SECRET | Application DB role | *(no default - see §22 note below)* |
| `DB_PASSWORD` | staging, production | Yes, no default | SECRET | Application DB role password | *(no default)* |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | local (`docker-compose.yml`), production self-hosted-db profile only | Yes when the bundled Postgres container is used | SECRET (user/password), PUBLIC CONFIG (db name) | Initializes the bundled Postgres container itself - must equal `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` | `foreigner_warsaw_local_dev_only` (local placeholder only) |

Backend only. Never present in the frontend image or its build.

## Mail (SMTP)

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `MAIL_HOST` | staging, production | Yes, no default | PUBLIC CONFIG (hostname itself) | SMTP provider host | `smtp.provider.example` |
| `MAIL_PORT` | staging, production | Yes, no default | PUBLIC CONFIG | SMTP port | `587` |
| `MAIL_USERNAME` | staging, production | No (blank default) - required in practice for any real provider | SECRET | SMTP auth username | *(blank locally - Mailpit needs none)* |
| `MAIL_PASSWORD` | staging, production | No (blank default) - required in practice for any real provider | SECRET | SMTP auth password | *(blank locally)* |

Backend only. See `docs/operations/EMAIL_PRODUCTION.md` for provider setup and the
staging-must-not-email-real-users rule (brief §35/§182).

## Public URL / CORS

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `APP_PUBLIC_URL` | staging, production (Compose-level name) | Yes | PUBLIC CONFIG | The real HTTPS origin; mapped internally to `FRONTEND_URL` for the backend container (`infra/docker-compose.prod.yml`) - used for CORS allow-list, email verification/reset links, and `SitemapController`'s absolute URLs | `https://staging.example.com` / `https://app.example.com` |
| `FRONTEND_URL` | local (default), staging, production | Yes in staging/production (no default there); defaults to `http://localhost:4200` locally | PUBLIC CONFIG | Backend's own name for the same value as `APP_PUBLIC_URL` above | see above |

Never `localhost` in staging/production - both profiles have no fallback, so a missing
value fails application startup rather than silently leaking a dev URL into a real email
(brief §34/§124).

## Admin bootstrap

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `APP_ADMIN_BOOTSTRAP_ENABLED` | all | No, defaults `false` | PUBLIC CONFIG (a boolean flag, not a secret itself) | Enables `AdminBootstrapRunner` for exactly one startup | `false` (always, except the single first-setup startup) |
| `ADMIN_BOOTSTRAP_EMAIL` | all, only meaningful when enabled | No, blank default | Not secret, but sensitive (PII) | The first ADMIN account's email | *(unset)* |
| `ADMIN_BOOTSTRAP_PASSWORD` | all, only meaningful when enabled | No, blank default | SECRET | The first ADMIN account's initial password | *(unset - never a default password, brief §36/§37)* |

Disable and unset all three immediately after the first successful bootstrap (see
`docs/operations/DEPLOYMENT.md` step 8 and `FIRST_PRODUCTION_DEPLOYMENT.md`).

## Token cleanup (Phase 12)

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `APP_TOKEN_CLEANUP_ENABLED` | all | No | PUBLIC CONFIG | Enables the scheduled expired/stale-token cleanup job | `false` base default; `true` in staging/production `.yml` directly (not env-driven there - see those files) |
| `APP_TOKEN_CLEANUP_USED_RETENTION` | all | No | PUBLIC CONFIG | ISO-8601 duration a used token is kept before cleanup | `P1D` |
| `APP_TOKEN_CLEANUP_INTERVAL` | all | No | PUBLIC CONFIG | ISO-8601 duration between cleanup runs | `PT1H` |

## Release / image identity (Phase 13)

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `BUILD_COMMIT` | image build time (backend Dockerfile `ARG`, also read by `infra/docker-compose.prod.yml`'s build args) | No, defaults `unknown` | PUBLIC CONFIG | Embedded into `BuildProperties`, surfaced at `/api/v1/platform/status` | `$(git rev-parse HEAD)` |
| `IMAGE_TAG` | deploy time (`infra/docker-compose.prod.yml`) | No, defaults `latest` (never rely on this in a real release - brief §8) | PUBLIC CONFIG | Which built image tag to run | `857c13c` or `0.1.0` |
| `BACKEND_IMAGE` / `FRONTEND_IMAGE` | deploy time | No, default to the local image name | PUBLIC CONFIG | Full image repository name (e.g. a registry path) | `ghcr.io/<org>/foreigner-warsaw-backend` |
| `HTTP_PORT` | deploy time | No, defaults `8080` | PUBLIC CONFIG | Host port the frontend/reverse-proxy container listens on (fronted by the real TLS terminator) | `8080` |

## Frontend-container-internal

| Variable | Environment(s) | Required? | Classification | Purpose | Example / default |
|---|---|---|---|---|---|
| `BACKEND_UPSTREAM` | frontend image runtime (nginx envsubst) | No, defaults `backend:8080` | PUBLIC CONFIG | Which Compose service/port nginx proxies `/api` and `/actuator` to | `backend:8080` |

## What the frontend build never receives

Per brief §32/§177: the Angular build (`environment.ts`, see that file) contains only
`production: true` and the relative `apiBaseUrl: '/api/v1'` - no database credential, no
SMTP credential, no admin-bootstrap value, nothing from the tables above. Confirmed by
inspection - the frontend `Dockerfile`'s build stage never receives any of the backend
`environment:` block from `infra/docker-compose.prod.yml` (Compose scopes `environment:`
per-service), and no `--build-arg` is passed to the frontend `docker build` at all.

## `local`/`test` profiles

Not listed above - `local` uses `.env`/`.env.example` (already documented there) with
safe placeholder defaults meant only for a developer's own machine; `test` uses
Testcontainers, which generates its own ephemeral credentials per run and never reads
any of the variables above.
