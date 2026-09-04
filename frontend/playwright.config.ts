import { defineConfig, devices } from '@playwright/test';

/**
 * Phase 1 E2E baseline (docs/product/IMPLEMENTATION_PLAN.md 1.22): "the app opens, the
 * home page loads, and API connectivity is proven" - nothing auth-related yet
 * (brief §22). `webServer` starts the Angular dev server automatically; the backend is
 * NOT started here (docker-compose + `mvnw spring-boot:run` are separate, documented
 * in docs/development/LOCAL_SETUP.md) - a test that needs it skips gracefully if it
 * isn't running rather than failing the whole suite.
 */
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
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
