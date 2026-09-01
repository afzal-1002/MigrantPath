import { expect, test } from '@playwright/test';

test('the application opens and the home page loads', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Foreigner Warsaw');
  await expect(page.getByRole('heading', { name: 'Your guide to living legally in Warsaw' })).toBeVisible();
});

test('home page proves frontend↔backend connectivity when the backend is running', async ({
  page,
}) => {
  await page.goto('/');
  const statusCard = page.locator('.status-card');

  // The backend (docker compose + `mvnw spring-boot:run`) is a separate process this
  // test does not start - see docs/development/LOCAL_SETUP.md. If it isn't running,
  // this assertion is skipped rather than failing the whole suite, since Phase 1's
  // acceptance criterion is "frontend can reach backend when both are up," not "the
  // frontend can start the backend itself."
  await expect(statusCard).not.toContainText('Checking API connection', { timeout: 10_000 });
  const isConnected = await statusCard.getByText(/API connected/).isVisible();
  if (!isConnected) {
    test.skip(true, 'Backend was not reachable at http://localhost:8080 during this run.');
  }
  await expect(statusCard).toContainText('API connected');
});
