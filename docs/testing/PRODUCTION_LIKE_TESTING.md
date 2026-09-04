# Production-Like Testing

Canonical Phase 11 brief §101-§104. "Production-like" here means: the real built Docker images
(`backend/Dockerfile`, `frontend/Dockerfile`) run together via `infra/docker-compose.prod.yml`,
so the frontend is served as static files through nginx and every `/api`/`/actuator` call is
reverse-proxied to the backend - **never** the Angular dev server, and the backend's port is
never exposed directly to the test client. This is the same topology ADR-013 documents for real
deployment; testing against it (rather than only the dev-stack Playwright suite) is what actually
proves cookie/CSRF/header/SPA-routing behavior survives the reverse-proxy hop.

## What was verified this way, and how (Phase 11's production-readiness work)

Real, manual verification was performed while building `infra/docker-compose.prod.yml` this
project's Phase 11 (production-readiness) turn: build both images, run all three containers
(`nginx` frontend, backend, postgres) together, then `curl` through the published frontend port
for `/`, `/api/v1/platform/status`, `/sitemap.xml`, and the security-header set on both `/` and a
hashed static asset - see `docs/product/PHASE_11_REPORT.md`'s "real bugs found" list (2 of the 10
were nginx bugs found specifically by this kind of test, not by unit/integration tests, which
cannot see nginx's own header-inheritance or `envsubst` behavior at all).

**Not yet automated as a repeatable script or CI job** - the verification above was real but
manual (a sequence of `docker build`/`docker compose up`/`curl` commands run once, this session).
This is a disclosed gap, not a false "we have production-like CI" claim.

## How to run it yourself

```bash
docker build -t foreigner-warsaw-backend:local backend/
docker build -t foreigner-warsaw-frontend:local frontend/
cp infra/.env.production.example infra/.env.production   # fill in real-looking local values
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.production up -d
curl -sf http://localhost/api/v1/platform/status
curl -sf http://localhost/sitemap.xml
curl -sI http://localhost/ | grep -i content-security-policy
```

(`unset DB_USERNAME DB_PASSWORD` first if your shell has them set - the same OS-env-shadowing
gotcha applies to `docker compose --env-file` as it does to a bare `mvnw spring-boot:run` - see
`docs/operations/DEPLOYMENT.md`.)

## What differs from real production

- No real TLS termination - `docker-compose.prod.yml` runs plain HTTP between the test client and
  nginx; a real deployment terminates TLS in front of this same stack (ADR-013, provider-neutral
  by design - this repo does not choose or configure a specific TLS provider).
- No real managed Postgres, SMTP provider, or DNS - the compose file's `postgres` service (behind
  the `self-hosted-db` profile) or a placeholder connection string stands in for whatever real
  infrastructure a deployment target provides.
- Single instance only - no load balancer, no horizontal scaling, matching the single-instance
  MVP scope the rest of Phase 11's documentation is explicit about (rate limiting, session state).

## Critical journeys this layer should cover (not all yet scripted - see below)

Per canonical brief §101-§104: login, a full assessment → recommendation → case round trip,
admin login → dashboard → audit, and the cookie/CSRF contract specifically (cookie set, cookie
sent back, CSRF-missing rejected, CSRF-present accepted, logout clears the session) - all through
the reverse proxy, never a direct backend call. The cookie/CSRF/security-header checks above were
performed manually and are the highest-value part of this layer (they're the ones a dev-stack
Playwright run structurally cannot catch, since the dev stack has no reverse proxy at all). Full
Playwright specs pointed at this stack (rather than `ng serve`) are a real, valuable follow-up
this phase named but did not build - `playwright.config.ts` would need a second project/config
targeting `http://localhost` (the compose stack's published port) instead of
`http://localhost:4200`, with its own `webServer` block replaced by "assume the compose stack is
already up" (mirroring how the existing suite already assumes the backend is already up).
