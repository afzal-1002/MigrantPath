# Production Release Checklist

Status: this checklist has **not yet been run against a real production
environment** - no production deploy has happened yet (ADR-013, DEPLOYMENT.md). It is
written now, ready to use for the first real release, and should be copy-pasted (or
linked and checked off) into that release's own entry under this directory.

## Before merging the release

- [ ] Full regression green: `cd backend && ./mvnw verify` (unit + Testcontainers
      integration + Spotless), `cd frontend && npm run lint && npm test -- --no-watch &&
      npm run build`, `cd frontend && npm run e2e` (Playwright, backend+Postgres+Mailpit
      running).
- [ ] `npm audit` (frontend) and a backend dependency check reviewed - no known-fixable
      critical/high left unaddressed (see docs/security/PRODUCTION_SECURITY.md
      "Dependency scanning").
- [ ] Every new/changed `Rule`/`Threshold`/`DocumentRequirement`/`Fee` was authored
      through the real Admin workflow and is either `PUBLISHED` with a `VERIFIED`
      primary source, or intentionally left at `READY_FOR_PUBLICATION` with the reason
      documented (never bulk-imported, never SQL).
- [ ] `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` still accurately reflects which
      published procedures have a wired eligibility Rule.
- [ ] Migrations reviewed for the expand/deploy/migrate/contract pattern if any are
      breaking (docs/operations/DEPLOYMENT.md).
- [ ] Release notes drafted (docs/releases/RELEASE_PROCESS.md template, including the
      legal-content template if applicable).

## Environment/config

- [ ] `infra/.env.production` (never committed - see `.gitignore`) has every variable
      from `infra/.env.production.example` set to a real value; no placeholder text
      (`CHANGE_ME`, `REPLACE_WITH_...`) remains.
- [ ] `SPRING_PROFILES_ACTIVE=production` confirmed - not `local`/`staging`/default.
- [ ] `ADMIN_BOOTSTRAP_ENABLED` is `false` (or unset) unless this specific release is the
      one intentionally creating the first admin account, in which case it is flipped
      back to `false` and redeployed **immediately after** bootstrap succeeds (see
      `AdminBootstrapRunner`'s own self-disabling behavior and
      docs/operations/DEPLOYMENT.md).
- [ ] Real SMTP credentials configured, not Mailpit (docs/operations/
      EMAIL_PRODUCTION.md) - confirm with a real test send before relying on it for
      password-reset/verification email.
- [ ] `APP_AUTH_FRONTEND_BASE_URL` (or equivalent) points at the real production origin,
      not `localhost` - used in verification/reset email links and `SitemapController`.
- [ ] Cookie `Secure`/`SameSite` flags confirmed active (only true when the app actually
      runs behind HTTPS - see SecurityConfig).
- [ ] Database connection string points at the real managed Postgres instance, with a
      non-superuser application role (docs/operations/DEPLOYMENT.md "Database").

## Infrastructure

- [ ] Images built and tagged with the real release version **and** git commit SHA
      (docs/releases/RELEASE_PROCESS.md "Image tagging") - never `latest` alone.
- [ ] `docker compose -f infra/docker-compose.prod.yml config` reviewed for the target
      environment (confirms env-var substitution resolved correctly, catches the
      shell-env-shadowing gotcha documented in DEPLOYMENT.md).
- [ ] A recent, verified-restorable backup exists before running migrations
      (docs/operations/DATABASE_BACKUP.md) - confirm the daily backup job actually ran,
      don't assume.
- [ ] TLS termination in front of the stack is real and valid (reverse proxy/load
      balancer - outside this repo's scope per ADR-013, but a hard release
      pre-condition; HSTS is meaningless without it).

## After deploy (smoke tests - brief §86)

- [ ] `GET /api/v1/platform/status` returns `200` with the expected `version`/`commit`.
- [ ] `GET /actuator/health/readiness` returns `200`/`UP`.
- [ ] `GET /` (frontend) loads the real app shell, not a blank page or nginx default.
- [ ] `GET /sitemap.xml` and `GET /robots.txt` both resolve through the reverse proxy.
- [ ] A real register → verify-email → login round trip succeeds (proves DB, SMTP, and
      CSRF/session cookies together, not just individually).
- [ ] A real guided-assessment → recommendation → case-creation round trip succeeds for
      at least one of the five wired procedures (proves the rules engine end to end
      against production data, not just against test fixtures).
- [ ] Admin login (once bootstrapped) can view the content-governance panel.
- [ ] Response headers on `/` and on a hashed static asset both carry the expected
      CSP/HSTS/X-Frame-Options/security-headers set (curl -I, compare against
      `frontend/security-headers.conf`).
- [ ] `/actuator/env`, `/actuator/beans`, and other non-exposed Actuator paths return
      `401`, not `200` or a stack trace.

## Rollback readiness

- [ ] The previous image tag is still available/pullable, so a code rollback
      (RELEASE_PROCESS.md) is actually possible within minutes, not theoretical.
- [ ] If this release includes a migration, confirm it followed expand/deploy/migrate/
      contract if breaking, so the previous code version could still run against the new
      schema for the duration of a rollback window.
