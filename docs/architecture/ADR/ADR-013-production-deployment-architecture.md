# ADR-013: Production deployment architecture

Status: Accepted — 2026-09-04 (Phase 11)

## Context

The application had run only as three native processes (Postgres/Mailpit in Docker,
backend via `mvnw spring-boot:run`, frontend via `ng serve`) since Phase 1.
`ARCHITECTURE.md §13` already committed, from Phase 0, to a cloud-neutral shape -
"HTTPS reverse proxy → Angular → Spring Boot → PostgreSQL, deployable to a single
Docker Compose VPS or managed services" - but nothing had actually built or run that
shape until now. Phase 11 had to turn that sentence into real, working artifacts.

## Decision

**Same-origin, two-container production topology**, exactly as already specified:

```
Internet → HTTPS (TLS terminated at or in front of the frontend container)
         → frontend container (nginx: serves the Angular build, proxies /api and
           /actuator to the backend container)
         → backend container (Spring Boot, no directly-exposed port)
         → PostgreSQL (self-hosted container under a Compose profile, or a managed
           instance - the application never assumes which)
```

- **Same-origin, not cross-origin.** The frontend is never pointed at a separate API
  origin in production - `frontend/src/environments/environment.ts` already builds
  with a relative `apiBaseUrl: '/api/v1'`, and nginx (`frontend/nginx.conf.template`)
  proxies `/api/` and `/actuator/` to the backend container on the same Docker network.
  This is what makes CSRF/cookies/CORS simple (ADR-005's own reasoning, now proven
  against a real reverse proxy, not just `ng serve`'s dev proxy - see
  `docs/operations/DEPLOYMENT.md`'s "verified" section).
- **Two application images, multi-stage, non-root, pinned base images**
  (`backend/Dockerfile`, `frontend/Dockerfile`) - no Maven/Node/source tree in the
  runtime images, no `latest` tags.
- **Modular monolith stays a monolith.** One backend container, one JAR - no service
  split (brief §3). The backend has no publicly-mapped port at all; it is reachable
  exclusively through the frontend container's reverse proxy.
- **Database stays swappable.** `infra/docker-compose.prod.yml`'s `postgres` service is
  profile-gated (`self-hosted-db`) - a single-VPS deployment can start it; a deployment
  against a managed PostgreSQL 18 instance (recommended, brief §166) simply never
  activates that profile and points `DB_HOST` elsewhere. No application code, migration,
  or Dockerfile depends on which one is in use.
- **Provider-neutral.** Nothing in either Dockerfile or the Compose file assumes AWS/
  Azure/Hetzner/DigitalOcean-specific APIs - the same two images run on any
  Docker-capable host (brief §4).
- **No Kubernetes.** A two-container, single-database application has no demonstrated
  need for orchestration beyond Docker Compose/a platform's own container service
  (brief §4's explicit "unless a demonstrated deployment need").

## Verified, not assumed

Every claim above was checked against a real, running instance of this exact topology
this session (not merely written and hoped to work):

- Both images build successfully from a clean `docker build` (Temurin 25
  JDK/JRE, `nginxinc/nginx-unprivileged`).
- The full three-container stack (`postgres` under the `self-hosted-db` profile,
  `backend`, `frontend`) starts, and the backend's own health-check-gated
  `depends_on` correctly sequences startup.
- A real HTTP request to the frontend container's `/api/v1/platform/status` is
  correctly proxied to the backend and returns the real build commit.
- CSRF is genuinely enforced through the reverse proxy: a mutating request without an
  `X-XSRF-TOKEN` header is rejected (403); the same request with the token from the
  `XSRF-TOKEN` cookie succeeds (201, a real account created).
- Security headers (CSP, Referrer-Policy, Permissions-Policy, HSTS, X-Content-Type-
  Options, X-Frame-Options) are present on every response type (index.html, hashed
  static assets, proxied API responses) with no duplication between nginx and Spring
  Security.

Three real bugs were found and fixed in the process, each documented at its own fix
site: a GID collision with the base image's own "ubuntu" user; `curl` absent from the
minimal JRE runtime image (needed for the container `HEALTHCHECK`); and nginx's
`add_header` inheritance rule silently dropping every server-level security header on
any location that also sets its own `add_header` (the exact-match `/index.html` and
hashed-asset locations both did, for `Cache-Control`) - fixed with a shared
`security-headers.conf` `include`d explicitly wherever needed.

## Consequences

- A deployment target only ever needs to run two images plus a Postgres connection
  string - no bespoke per-provider tooling to maintain.
- Adding a CDN/edge cache in front of the frontend container later is additive (it
  would sit in front of nginx, which already sets correct `Cache-Control` per response
  type) - no architecture change needed.
- If this application's traffic profile ever genuinely outgrows a single backend
  instance, the in-memory rate limiter (`RateLimiter`) and Spring Session JDBC (already
  shared-state, not in-process) are the two components that would need attention first
  - documented as a known scaling boundary in `docs/security/PRODUCTION_SECURITY.md`,
  not a blocker for this first release.
