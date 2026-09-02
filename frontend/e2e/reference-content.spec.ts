import { expect, test } from '@playwright/test';
import { grantRole } from './db';
import { extractTokenFromHtml, findLatestMessageTo } from './mailpit';

/**
 * Phase 4 E2E: the full create-through-publish lifecycle via the real internal content API,
 * followed by proving the public Angular pages render exactly that real content (brief §84) - not
 * a hardcoded fixture. Uses synthetic {@code TEST_*} content only (brief §54/§116); no production
 * migration seeds any published procedure, so this test creates its own before asserting on it.
 * Phase 1-3 Playwright scenarios are unaffected by anything here.
 */

const PASSWORD = 'correct-horse-battery';

function uniqueEmail(): string {
  return `e2e-content-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}

test('a real published procedure created via the content API renders on the public Browse Procedures pages', async ({
  page,
  context,
}) => {
  const email = uniqueEmail();
  const procedureCode = `TEST_PROCEDURE_${Date.now()}`;

  // Register + verify a real account (same real flow as auth.spec.ts), then grant it
  // every Phase 4 content role directly - no public API can self-escalate a role
  // (brief §45), so this is the one legitimate way for a test to get an authorized
  // actor.
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

  grantRole(email, 'CONTENT_EDITOR');
  grantRole(email, 'LEGAL_REVIEWER');
  grantRole(email, 'ADMIN');

  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  const xsrfCookie = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN');
  if (!xsrfCookie) {
    throw new Error('XSRF-TOKEN cookie was not set after login');
  }
  const headers = { 'X-XSRF-TOKEN': xsrfCookie.value, 'Content-Type': 'application/json' };
  const base = '/api/v1/internal/content';

  await page.request.post(`${base}/procedures`, {
    headers,
    data: {
      code: procedureCode,
      categoryCode: 'OTHER',
      canonicalName: `Test Procedure ${procedureCode}`,
      shortDescription: 'Created by an automated test - never real legal content',
      jurisdictionScope: 'NATIONAL',
    },
  });
  await page.request.post(`${base}/procedures/${procedureCode}/versions`, {
    headers,
    data: { title: `Test Procedure ${procedureCode}`, summary: 'A synthetic test summary', description: 'A synthetic test description' },
  });
  await page.request.post(`${base}/procedures/${procedureCode}/versions/1/steps`, {
    headers,
    data: {
      stableCode: 'TEST_STEP',
      title: 'Do the test thing',
      description: 'Step description',
      stepType: 'PREPARATION',
      sortOrder: 1,
      mandatory: true,
    },
  });
  await page.request.post(`${base}/procedures/${procedureCode}/versions/1/documents`, {
    headers,
    data: {
      stableCode: 'TEST_DOC',
      name: 'Test document',
      requirementType: 'DEFAULT_REQUIRED',
      requiredByDefault: true,
      sortOrder: 1,
    },
  });

  const sourceResponse = await page.request.post(`${base}/sources`, {
    headers,
    data: { title: 'Test source (E2E)', sourceUrl: 'https://example.gov.pl/e2e-test-source', sourceType: 'OFFICIAL_SERVICE_PAGE' },
  });
  const source = await sourceResponse.json();

  await page.request.post(`${base}/sources/${source.id}/verify`, { headers, data: { status: 'VERIFIED' } });
  await page.request.post(`${base}/procedures/${procedureCode}/versions/1/sources`, {
    headers,
    data: { officialSourceId: source.id, role: 'PRIMARY' },
  });
  await page.request.post(`${base}/procedures/${procedureCode}/versions/1/submit`, { headers });
  await page.request.post(`${base}/procedures/${procedureCode}/versions/1/approve`, { headers });
  const publishResponse = await page.request.post(`${base}/procedures/${procedureCode}/versions/1/publish`, {
    headers,
    data: { effectiveFrom: new Date().toISOString().slice(0, 10) },
  });
  expect(publishResponse.ok()).toBe(true);

  // The actual point of this test: the public pages now show it, entirely sourced
  // from the backend (brief §65).
  const procedureName = `Test Procedure ${procedureCode}`;
  await page.goto('/procedures');
  await expect(page.getByRole('link', { name: procedureName, exact: false })).toBeVisible();

  await page.getByRole('link', { name: procedureName, exact: false }).click();
  await expect(page).toHaveURL(new RegExp(`/procedures/${procedureCode}$`));
  await expect(page.getByRole('heading', { name: procedureName })).toBeVisible();
  await expect(page.getByText('Do the test thing')).toBeVisible();
  await expect(page.getByText('Test document')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Test source (E2E)' })).toBeVisible();
});
