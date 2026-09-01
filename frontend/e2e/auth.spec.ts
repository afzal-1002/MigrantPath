import { expect, test } from '@playwright/test';
import { extractTokenFromHtml, findLatestMessageTo } from './mailpit';

/**
 * Real backend + real PostgreSQL + real Mailpit required (brief §41: "Do not simulate
 * backend authentication") - see docs/development/LOCAL_SETUP.md for how to start the
 * full stack locally; CI (.github/workflows/ci.yml) starts it the same way.
 */

const PASSWORD = 'correct-horse-battery';

function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}

// Deliberately no global Mailpit cleanup between tests: with fullyParallel execution,
// a shared "delete everything" beforeEach would race against another test's
// in-flight email (a real bug caught during this suite's first run, not a
// hypothetical). Every test instead uses a globally-unique recipient address and
// searches Mailpit by that exact address, so accumulated messages from other tests
// never interfere.
//
// Serial, not parallel (overriding playwright.config.ts's global fullyParallel):
// these tests share one real backend, one real database, and one real Mailpit -
// running them concurrently produced a genuine, reproducible-under-load flake
// (Scenario 4 intermittently observed as already-authenticated) that never
// reproduced when run in isolation. Each test still gets its own isolated browser
// context (Playwright's default); only the *scheduling* is sequential.
test.describe.configure({ mode: 'serial' });

test('Scenario 1: register, verify, login, dashboard, logout, dashboard unavailable', async ({ page }) => {
  const email = uniqueEmail();

  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByLabel('I accept the Terms of Service').check();
  await page.getByLabel('I accept the Privacy Policy').check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible();

  const html = await findLatestMessageTo(email);
  const token = extractTokenFromHtml(html);

  await page.goto(`/verify-email?token=${token}`);
  await expect(page.getByRole('heading', { name: 'Email verified' })).toBeVisible();

  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: `Welcome, ${email}` })).toBeVisible();

  // Two "Logout" buttons exist on /dashboard (the toolbar's and the dashboard card's
  // own, per brief §29) - scope to the main content area's one specifically.
  await page.getByRole('main').getByRole('button', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

test('Scenario 2: forgot password, reset, old password rejected, new password works', async ({ page }) => {
  const email = uniqueEmail();

  // Arrange: a verified account (reuses the register+verify flow, not itself under
  // test here).
  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByLabel('I accept the Terms of Service').check();
  await page.getByLabel('I accept the Privacy Policy').check();
  await page.getByRole('button', { name: 'Create account' }).click();
  const verifyToken = extractTokenFromHtml(await findLatestMessageTo(email));
  await page.goto(`/verify-email?token=${verifyToken}`);
  await expect(page.getByRole('heading', { name: 'Email verified' })).toBeVisible();

  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Send reset instructions' }).click();
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible();

  const resetToken = extractTokenFromHtml(await findLatestMessageTo(email));
  const newPassword = 'new-correct-horse-battery';

  await page.goto(`/reset-password?token=${resetToken}`);
  // exact: true - "Confirm new password" otherwise also matches "New password" as a
  // substring under Playwright's default (non-exact) text matching.
  await page.getByLabel('New password', { exact: true }).fill(newPassword);
  await page.getByLabel('Confirm new password').fill(newPassword);
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();

  // Old password now fails.
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Invalid email or password')).toBeVisible();

  // New password succeeds.
  await page.getByLabel('Password').fill(newPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('Scenario 3: session persists across a page reload', async ({ page }) => {
  const email = uniqueEmail();
  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByLabel('I accept the Terms of Service').check();
  await page.getByLabel('I accept the Privacy Policy').check();
  await page.getByRole('button', { name: 'Create account' }).click();
  const token = extractTokenFromHtml(await findLatestMessageTo(email));
  await page.goto(`/verify-email?token=${token}`);
  // Wait for the async verify-email call to actually complete before navigating away
  // - otherwise this test races the request itself.
  await expect(page.getByRole('heading', { name: 'Email verified' })).toBeVisible();

  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.reload();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: `Welcome, ${email}` })).toBeVisible();
});

test('Scenario 4: an unauthenticated visitor opening /dashboard is redirected to /login', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});
