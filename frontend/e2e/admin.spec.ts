import { Browser, BrowserContext, Page, expect, test } from '@playwright/test';
import { grantRole } from './db';
import { extractTokenFromHtml, findLatestMessageTo } from './mailpit';

/**
 * Pre-Phase-10 hardening checkpoint (brief §A) - Phase 9 shipped the Admin panel with no
 * browser-level verification at all. This is that verification: a real CONTENT_EDITOR creates a
 * synthetic draft through the actual Angular Admin UI (not raw API calls), a real LEGAL_REVIEWER
 * account approves it, a real ADMIN account publishes it, the public Browse Procedures page shows
 * it, the source-verification workflow is exercised, and the Audit page shows every action. No
 * mocked backend anywhere - real HTTP, real Postgres, real Mailpit, run against the real dev
 * stack. Uses synthetic {@code TEST_*} content only (brief §54/§116) - never real legal content.
 */

const PASSWORD = 'correct-horse-battery';

function uniqueEmail(prefix: string): string {
  return `e2e-admin-${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}

async function registerAndVerify(page: Page, email: string): Promise<void> {
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
}

async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function registerVerifyLogin(page: Page, email: string): Promise<void> {
  await registerAndVerify(page, email);
  await login(page, email);
}

/**
 * Roles must be granted BEFORE the first login, not after (even with a reload) - Spring
 * Security bakes the authenticated principal's authorities into the HTTP session at login time
 * (`AppUserDetailsService.loadUserByUsername`), so a role granted straight into the database
 * after a session already exists is invisible to that session's own authorization checks even
 * though `/api/v1/users/me` (a live DB read) and thus the frontend's own `adminGuard` would
 * already show it - a real distinction found by this test failing with 403 ACCESS_DENIED on the
 * exact request its own UI navigation had just proven the role should allow. Mirrors {@code
 * reference-content.spec.ts}'s working order (grant, then log in) exactly.
 */
async function newActor(browser: Browser, prefix: string, roles: string[]): Promise<{ context: BrowserContext; page: Page; email: string }> {
  const email = uniqueEmail(prefix);
  const context = await browser.newContext();
  const page = await context.newPage();
  await registerAndVerify(page, email);
  for (const role of roles) {
    grantRole(email, role);
  }
  await login(page, email);
  return { context, page, email };
}

test('USER without an admin role is denied the Admin area', async ({ page }) => {
  const email = uniqueEmail('plain-user');
  await registerVerifyLogin(page, email);

  await page.goto('/admin');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('link', { name: 'Admin' })).toHaveCount(0);

  // Server-side enforcement, not just the frontend guard (brief §69/§A).
  const response = await page.request.get('/api/v1/admin/procedures');
  expect(response.status()).toBe(403);
});

test.describe('full admin governance lifecycle through the real UI', () => {
  test.describe.configure({ mode: 'serial' });

  let editor: { context: BrowserContext; page: Page; email: string };
  let reviewer: { context: BrowserContext; page: Page; email: string };
  let admin: { context: BrowserContext; page: Page; email: string };
  const procedureCode = `TEST_ADMIN_E2E_${Date.now()}`;
  const procedureName = `E2E Admin Procedure ${procedureCode}`;
  let sourceId = '';

  test.beforeAll(async ({ browser }) => {
    editor = await newActor(browser, 'editor', ['CONTENT_EDITOR']);
    reviewer = await newActor(browser, 'reviewer', ['LEGAL_REVIEWER']);
    admin = await newActor(browser, 'admin', ['ADMIN']);
  });

  test.afterAll(async () => {
    await editor.context.close();
    await reviewer.context.close();
    await admin.context.close();
  });

  test('1. CONTENT_EDITOR creates a synthetic draft procedure through the Admin UI', async () => {
    const page = editor.page;
    await page.goto('/admin/procedures');
    await expect(page.getByRole('heading', { name: 'Procedures' })).toBeVisible();

    await page.getByRole('button', { name: 'New procedure' }).click();
    await page.getByLabel('Code', { exact: true }).fill(procedureCode);
    await page.getByLabel('Category code').fill('OTHER');
    await page.getByLabel('Canonical name').fill(procedureName);
    await page.getByRole('button', { name: 'Create' }).click();
    await expect(page.getByRole('link', { name: procedureCode })).toBeVisible();

    await page.getByRole('link', { name: procedureCode }).click();
    await expect(page).toHaveURL(new RegExp(`/admin/procedures/${procedureCode}$`));

    await page.getByLabel('New draft title').fill(procedureName);
    await page.getByRole('button', { name: 'Create draft version' }).click();
    await expect(page.getByRole('link', { name: 'v1' })).toBeVisible();

    await page.getByRole('link', { name: 'v1' }).click();
    await expect(page).toHaveURL(new RegExp(`/admin/procedures/${procedureCode}/versions/1$`));

    // Overview - a summary is required before this version can ever be published (real
    // ProcedurePublishingService readiness rule), so set it now while still DRAFT (the field is
    // disabled once submitted).
    await page.getByLabel('Summary', { exact: true }).fill('Synthetic E2E test procedure summary.');
    await page.getByRole('button', { name: 'Save overview' }).click();
    await expect(page.getByLabel('Summary', { exact: true })).toHaveValue('Synthetic E2E test procedure summary.');

    // Steps
    await page.getByRole('button', { name: /^Steps/ }).click();
    // The tab-switch re-render is async (Angular signals); wait for the panel that actually owns
    // this "Stable code" input before filling it, or a fast fill() can land on a stale sibling
    // panel's same-labelled input mid-transition (see the Documents section below for the bug
    // this raced into once).
    await expect(page.getByRole('heading', { name: 'Add step' })).toBeVisible();
    await page.getByLabel('Stable code').fill('TEST_STEP');
    await page.getByLabel('Title', { exact: true }).fill('Do the synthetic test step');
    await page.getByLabel('Sort order').fill('1');
    await page.getByRole('button', { name: 'Add step' }).click();
    await expect(page.getByText('Do the synthetic test step')).toBeVisible();

    // Documents
    await page.getByRole('button', { name: /^Documents/ }).click();
    // Real bug found by this test: without this wait, fill() raced Angular's async @switch
    // re-render and landed on the just-departed Steps panel's "Stable code" input (identical
    // label), silently writing into newStepCode instead of newDocCode and submitting the
    // document with a blank stableCode (400 Request validation failed). Wait for a
    // documents-panel-unique element first.
    await expect(page.getByRole('heading', { name: 'Add document requirement' })).toBeVisible();
    await page.getByLabel('Stable code').fill('TEST_DOC');
    await page.getByLabel('Name').fill('Synthetic test document');
    await page.getByRole('button', { name: 'Add document' }).click();
    await expect(page.getByText('Synthetic test document')).toBeVisible();

    // A source (create + navigate back, since the source is managed under /admin/sources)
    await page.goto('/admin/sources');
    await page.getByRole('button', { name: 'New source' }).click();
    await page.getByLabel('Title').fill(`E2E test source ${procedureCode}`);
    await page.getByLabel('Official URL').fill(`https://example.gov.pl/e2e-admin-${procedureCode}`);
    await page.getByRole('button', { name: 'Create' }).click();
    await expect(page.getByRole('link', { name: `E2E test source ${procedureCode}` })).toBeVisible();

    const sourceLink = page.getByRole('link', { name: `E2E test source ${procedureCode}` });
    const href = await sourceLink.getAttribute('href');
    sourceId = href!.split('/').pop()!;

    // Back to the procedure version to attach it.
    await page.goto(`/admin/procedures/${procedureCode}/versions/1`);
    await page.getByRole('button', { name: /^Sources/ }).click();
    await expect(page.getByRole('heading', { name: 'Attach a source' })).toBeVisible();
    await page.getByLabel('Official source id').fill(sourceId);
    await page.getByRole('button', { name: 'Attach' }).click();
    await expect(page.getByText(`E2E test source ${procedureCode}`)).toBeVisible();

    await page.getByRole('button', { name: 'Submit for review' }).click();
    await expect(page.locator('.status-IN_REVIEW')).toBeVisible();
  });

  test('6. Source verification workflow, via the real Admin UI', async () => {
    const page = reviewer.page;
    await page.goto(`/admin/sources/${sourceId}`);
    await expect(page.getByRole('heading', { name: `E2E test source ${procedureCode}` })).toBeVisible();

    await page.getByLabel('Outcome').selectOption('VERIFIED');
    await page.getByRole('button', { name: 'Record verification' }).click();
    await expect(page.getByText('VERIFIED', { exact: true }).first()).toBeVisible();
    // Verification history shows the recorded outcome.
    await expect(page.locator('table')).toContainText('VERIFIED');
  });

  test('3. LEGAL_REVIEWER approves another user\'s submission through the real UI', async () => {
    const page = reviewer.page;
    await page.goto(`/admin/procedures/${procedureCode}/versions/1`);
    await expect(page.locator('.status-IN_REVIEW')).toBeVisible();

    await page.getByRole('button', { name: 'Approve' }).click();
    await expect(page.locator('.status-APPROVED')).toBeVisible();
  });

  test('4. ADMIN publishes the approved version through the real UI', async () => {
    const page = admin.page;
    await page.goto(`/admin/procedures/${procedureCode}/versions/1`);
    await expect(page.locator('.status-APPROVED')).toBeVisible();

    // Scoped to .admin-actions (the lifecycle-action button row) - the Overview tab's own
    // "Effective from" date input is a separate input[type=date] elsewhere on the same page.
    const today = new Date().toISOString().slice(0, 10);
    await page.locator('.admin-actions input[type="date"]').fill(today);
    await page.getByRole('button', { name: 'Publish' }).click();
    await expect(page.locator('.status-PUBLISHED')).toBeVisible();
  });

  test('5. the published synthetic Procedure appears on the public Browse Procedures pages', async () => {
    const page = admin.page;
    await page.goto('/procedures');
    await expect(page.getByRole('link', { name: procedureName, exact: false })).toBeVisible();

    await page.getByRole('link', { name: procedureName, exact: false }).click();
    await expect(page).toHaveURL(new RegExp(`/procedures/${procedureCode}$`));
    await expect(page.getByRole('heading', { name: procedureName })).toBeVisible();
    await expect(page.getByText('Do the synthetic test step')).toBeVisible();
    await expect(page.getByText('Synthetic test document')).toBeVisible();
  });

  test('7. the Audit page (ADMIN-only) contains the administrative actions taken above', async () => {
    const page = admin.page;
    await page.goto('/admin/audit');
    await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();

    await page.getByPlaceholder('Business code (e.g. procedure code)').fill(procedureCode);
    await page.getByRole('button', { name: 'Search' }).click();

    const table = page.locator('table');
    await expect(table).toContainText('CONTENT_SUBMITTED');
    await expect(table).toContainText('CONTENT_APPROVED');
    await expect(table).toContainText('CONTENT_PUBLISHED');
  });
});
