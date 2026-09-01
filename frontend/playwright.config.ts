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
