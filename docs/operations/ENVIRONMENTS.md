# Environment Matrix

Status: reflects the actual committed `application-{local,test,staging,production}.yml`
profiles and `infra/docker-compose.prod.yml` - not aspirational. See
`ENVIRONMENT_VARIABLES.md` for every variable's exact name, requiredness, and
secret/public classification, and `STAGING.md`/`FIRST_PRODUCTION_DEPLOYMENT.md` for the
deploy procedures themselves.

| | Local | Test | Staging | Production |
|---|---|---|---|---|
| **Purpose** | Developer machine | `./mvnw verify` (Testcontainers) | Pre-release rehearsal, full Playwright suite | Real users |
| **Config file** | `application-local.yml` | `application-test.yml` | `application-staging.yml` | `application-production.yml` |
| **Database** | `docker compose up -d postgres` (local dev Postgres 18) | Testcontainers PostgreSQL 18 (fresh per run) | Dedicated Postgres 18 instance, **separate from production** (brief §161) | Managed Postgres 18 (recommended) or the bundled `self-hosted-db` Compose profile |
| **Mail** | Mailpit (`docker compose up -d mailpit`) | Testcontainers Mailpit (fresh per run) | Real SMTP provider, or a staging-only Mailpit-equivalent - **never production's real provider/domain** | Real transactional SMTP (`docs/operations/EMAIL_PRODUCTION.md`) |
| **HTTPS** | No (plain HTTP, `localhost`) | N/A | Yes - mirrors production | Yes |
| **CORS** | `http://localhost:4200` (explicit, from `FRONTEND_URL` fallback) | N/A (MockMvc, no browser) | `${FRONTEND_URL}` (fail-fast if unset) | `${FRONTEND_URL}` (fail-fast if unset) |
| **Session cookie `Secure`** | `false` | N/A | `true` | `true` |
| **Swagger/OpenAPI** | Enabled | N/A | Enabled (base default, no override) | **Disabled** (`springdoc.swagger-ui.enabled: false`) |
| **Actuator `show-details`** | `always` | `never` (base default) | `never` (base default) | `never` (base default) |
| **Logging level** | `DEBUG` (`com.foreignerwarsaw`) | Base default (`INFO`) | Base default | Base default |
| **Admin bootstrap** | Off by default; may be manually enabled for local testing | Off | Off by default; enabled deliberately for the first staging admin, then unset | Off by default; enabled deliberately for the first production admin, then unset |
| **Test/synthetic data** | Freely created during dev | Fresh Testcontainers DB every run - nothing persists | Synthetic accounts/content only (`docs/operations/LEGAL_CONTENT_MONITORING.md`'s staging note) - **no real user data ever restored here** (brief §161/§163) | Real content only, authored exclusively through the Admin governance workflow |
| **Metrics/Actuator exposure** | `health,info` only (base default - no environment widens this) | Same | Same | Same |
| **Forwarded headers trusted** | No (no reverse proxy in front locally) | N/A | Yes (`server.forward-headers-strategy: framework`) | Yes |

## Staging vs. production database separation (brief §161)

Non-negotiable: staging **never** points at the production database, and a production
backup is **never** restored into staging without a real anonymization pass first - none
exists yet (tracked as a Known Issue; today, staging is populated with synthetic data
only, never a production restore).

## What every profile shares (never overridden)

- Flyway owns the schema (`ddl-auto: validate` everywhere - `application.yml`).
- UTC timestamps throughout (`hibernate.jdbc.time_zone`, `jackson.time-zone`).
- The public `/actuator/health`, `/actuator/info`, `/api/v1/platform/status`,
  `/api/v1/reference/**`, `/api/v1/procedures/**`, and `/sitemap.xml` routes - the only
  ones ever reachable unauthenticated, in every environment (`SecurityConfig`).
