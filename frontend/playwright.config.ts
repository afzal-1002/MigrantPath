import { defineConfig, devices } from '@playwright/test';

/**
 * Phase 1 E2E baseline (docs/product/IMPLEMENTATION_PLAN.md 1.22): "the app opens, the
 * home page loads, and API connectivity is proven" - nothing auth-related yet
 * (brief §22). `webServer` starts the Angular dev server automatically; the backend is
 * NOT started here (docker-compose + `mvnw spring-boot:run` are separate, documented
 * in docs/development/LOCAL_SETUP.md) - a test that needs it skips gracefully if it
 * isn't running rather than failing the whole suite.
 *
 * Canonical Phase 13 (Deployment) brief §59/§63: BASE_URL, when set, points the whole
 * suite at an already-deployed stack (e.g. `BASE_URL=https://staging.example.com npx
 * playwright test` - docs/operations/STAGING.md) instead of the local dev server - no
 * code change needed per environment. `webServer` is only started when BASE_URL is
 * unset, since a real deployed target already runs its own frontend and starting a
 * second, local `npm start` alongside it would be pointless (and, against a real
 * remote origin, `webServer.url`'s own readiness probe would never resolve to it).
 */
const baseURL = process.env['BASE_URL'] ?? 'http://localhost:4200';

/**
 * Canonical Phase 15 (Release Readiness) brief §11/§12 - a real, self-signed
 * certificate is expected (and correct) to fail normal browser TLS validation.
 * `ignoreHTTPSErrors` is opt-in, never a global default: a real staging/production
 * `BASE_URL` run must keep full certificate validation (a real cert failure there is
 * a genuine finding, not noise to suppress). Set only by the throwaway local HTTPS
 * harness itself (`docs/operations/LOCAL_HTTPS_TESTING.md`) - production browser TLS
 * policy is entirely unaffected; this only relaxes Playwright's own test client.
 */
const ignoreHTTPSErrors = process.env['PW_IGNORE_HTTPS_ERRORS'] === 'true';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // Canonical Phase 11 (Testing Completeness) brief §112/§113 - a real finding, not a guess: the
  // full suite at this host's CPU-count default (7 workers) produced 2 timeouts, both waiting on
  // the same country-reference-data autocomplete, because every worker shares one dev-profile
  // backend/Postgres instance (docker-compose.yml has no per-worker isolation). A serial re-run
  // of the same 2 specs passed in 3-12s. 3 is the documented supported ceiling for this suite
  // against the current single-instance dev backend - see docs/testing/E2E_GUIDE.md. Local runs
  // may still override with `--workers=N` for faster iteration; this cap only fixes the default.
  workers: 3,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
    ignoreHTTPSErrors,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env['BASE_URL']
    ? undefined
    : {
        command: 'npm start',
        url: baseURL,
        reuseExistingServer: !process.env['CI'],
        timeout: 120_000,
      },
});
