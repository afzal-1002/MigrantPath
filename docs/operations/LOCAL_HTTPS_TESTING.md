# Local HTTPS Testing

Status: real, working, local-only. Canonical Phase 15 (Release Readiness) closes the
verification gap Phase 14 disclosed: `application-production.yml` correctly sets
`secure: true` on session/CSRF cookies (HTTPS-only, ADR-013) — a real browser (unlike
`curl`, which ignores the `Secure` attribute entirely) correctly refuses to send such a
cookie back over plain HTTP, so a genuine browser-driven Playwright run against the
production-profile image over `http://localhost:18080` cannot exercise any
auth-dependent flow. This harness adds a real, throwaway TLS listener in front of the
**unmodified** production frontend image so that gap can be closed without weakening
production security in any way.

## What this is not

- **Not a change to production security.** `frontend/nginx.conf.template` (the actual
  release image's own nginx config) is never touched by anything here — it still only
  ever terminates plain HTTP on 8080, exactly as ADR-013 documents ("fronted by your
  real TLS terminator"). This harness *is* that terminator, for local verification
  only.
- **Not a real certificate.** `generate-local-cert.sh` produces a throwaway,
  self-signed, 30-day certificate for `localhost` — gitignored
  (`infra/local-https/.gitignore`), never committed, never used for any real
  deployment. Real production TLS remains an entirely separate, unresolved
  hosting/domain decision (`DNS_AND_TLS.md`).
- **Not a weakening of Playwright's own TLS policy for real targets.** Certificate
  validation is skipped only when `PW_IGNORE_HTTPS_ERRORS=true` is explicitly set — a
  real `BASE_URL=https://staging.example.com` run keeps full certificate validation,
  exactly as it should.

## Usage

```bash
# 1. Generate the throwaway local cert (once, or whenever the 30-day cert expires)
./infra/local-https/generate-local-cert.sh

# 2. Fill infra/.env.production from infra/.env.production.example (see
#    FIRST_PRODUCTION_DEPLOYMENT.md) - point DB_HOST/MAIL_HOST at whatever real or
#    local instance you're verifying against (e.g. host.docker.internal for the
#    existing local dev Postgres/Mailpit containers).

# 3. Build and start the real production images plus the local TLS overlay
docker compose -f infra/docker-compose.prod.yml \
  -f infra/local-https/docker-compose.local-https.yml \
  --env-file infra/.env.production build
docker compose -f infra/docker-compose.prod.yml \
  -f infra/local-https/docker-compose.local-https.yml \
  --env-file infra/.env.production up -d

# 4. Run Playwright against the real production images over real HTTPS
cd frontend
PW_IGNORE_HTTPS_ERRORS=true BASE_URL=https://localhost:8443 \
  npx playwright test --project=chromium --workers=1

# 5. Tear down
docker compose -f infra/docker-compose.prod.yml \
  -f infra/local-https/docker-compose.local-https.yml down
```

## How it works

`infra/local-https/nginx-tls.conf` is a minimal nginx server block: it terminates TLS
on `8443` using the throwaway local cert, then reverse-proxies every request to the
existing `frontend` container's own internal `8080` (plain HTTP, container-to-container
only — never published to the host), setting `X-Forwarded-Proto: https` so the
backend's `forward-headers-strategy: framework` setting (Spring's own trusted-proxy
handling, safe in this exact single-hop topology per ADR-013) sees a genuine `https://`
scheme — exactly what a real TLS-terminating load balancer would send in production, so
cookie/redirect behavior is identical to what a real deployment would produce.

## Known limitation

Chromium (and every real browser) will show a certificate-warning interstitial for a
self-signed cert unless `ignoreHTTPSErrors` is set — that's real, correct browser
behavior, not a bug in this harness. `PW_IGNORE_HTTPS_ERRORS=true` tells Playwright's
own test client (not the browser's default policy in general) to proceed past that
warning for this one, explicitly-opted-into, throwaway target.
