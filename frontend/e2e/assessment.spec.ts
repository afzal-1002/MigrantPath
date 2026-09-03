import { expect, test } from '@playwright/test';
import { extractTokenFromHtml, findLatestMessageTo } from './mailpit';

/**
 * Real backend + real PostgreSQL + real Mailpit required, against the real seeded
 * WARSAW_GENERAL_ASSESSMENT questionnaire (brief §83) - no mocked backend anywhere here.
 */

const PASSWORD = 'correct-horse-battery';

function uniqueEmail(): string {
  return `e2e-assessment-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}

test.describe.configure({ mode: 'serial' });

async function registerVerifyAndLogin(page: import('@playwright/test').Page, email: string): Promise<void> {
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
}

async function selectCountry(page: import('@playwright/test').Page, label: string, countryName: string): Promise<void> {
  await page.getByLabel(label).fill(countryName);
  await page.getByRole('option', { name: new RegExp(countryName) }).click();
}

test('Scenario 1: work branch end to end, completes, then analyzes with no fabricated match', async ({ page }) => {
  await registerVerifyAndLogin(page, uniqueEmail());

  await page.goto('/assessment/start');
  await expect(page).toHaveURL(/\/assessment\/[0-9a-f-]+$/);
  await expect(page.getByRole('heading', { name: /Step 1 of \d+: About you/ })).toBeVisible();

  await selectCountry(page, 'Which country are you a citizen of?', 'Pakistan');
  await page.getByRole('radiogroup', { name: 'Are you currently in Poland?' }).getByLabel('Yes').check();
  await page.locator('input[type="date"]').fill('1995-06-15');
  await page.getByRole('button', { name: 'Next' }).click();

  // Current status section - resolve with UNSURE to keep the flow short.
  await expect(page.getByRole('heading', { name: /Your current status/ })).toBeVisible();
  await page.getByRole('combobox', { name: 'What is your current legal status in Poland?' }).click();
  await page.getByRole('option', { name: 'I am not sure' }).click();
  await page.getByRole('button', { name: 'Next' }).click();

  // Goals - select Work.
  await expect(page.getByRole('heading', { name: /What do you want to do\?/ })).toBeVisible();
  await page.getByRole('checkbox', { name: 'Work', exact: true }).check();
  // Same-section branch reveal: salary/contract questions appear in the WORK step
  // once HAS_JOB_OFFER is answered, without another Next click.
  await page.getByRole('button', { name: 'Next' }).click();

  await expect(page.getByRole('heading', { name: /Step \d+ of \d+: Work/ })).toBeVisible();
  await page.getByRole('radiogroup', { name: 'Do you have a job offer in Poland?' }).getByLabel('Yes').check();
  await expect(page.getByText('Monthly gross salary').first()).toBeVisible();
  await page.getByLabel('What is the monthly gross salary offered (in PLN)?').fill('9000');
  await page.getByRole('combobox', { name: 'What type of contract does the job offer involve?' }).click();
  await page.getByRole('option', { name: 'Employment contract (umowa o prace)' }).click();
  await page.getByRole('button', { name: 'Review answers' }).click();

  await expect(page.getByRole('heading', { name: 'Review your answers' })).toBeVisible();
  await expect(page.getByText('9000')).toBeVisible();

  await page.getByRole('button', { name: 'Complete assessment' }).click();
  await expect(page.getByRole('heading', { name: 'Your assessment is complete' })).toBeVisible();

  // Phase 7: "Analyze my pathways" runs the real backend recommendation engine end to
  // end - no production Rule content is ever seeded (Rules Engine brief §58-60), so the
  // honest, correct outcome here is the empty-result state, never a fabricated match.
  await page.getByRole('link', { name: 'Analyze my pathways' }).click();
  await expect(page).toHaveURL(/\/assessment\/[0-9a-f-]+\/results$/);
  await expect(page.getByRole('heading', { name: 'Your pathways' })).toBeVisible();
  await expect(page.getByText("couldn't identify a matching pathway")).toBeVisible();
  // Recommendation Engine brief §52: never a confidence/probability figure anywhere.
  await expect(page.getByText(/\d+%/)).toHaveCount(0);

  // Phase 8: "My Cases" is reachable end to end through the real backend too - no case
  // exists (no production Rule content means no PRIMARY_MATCH to start one from), so the
  // honest outcome is the empty-cases state, never a fabricated case.
  await page.goto('/dashboard');
  await page.getByRole('link', { name: 'View my cases' }).click();
  await expect(page).toHaveURL(/\/cases$/);
  await expect(page.getByRole('heading', { name: 'My cases' })).toBeVisible();
  await expect(page.getByText("haven't started tracking")).toBeVisible();
});

test('Scenario 2: removing Work after entering the branch hides salary and still allows completion', async ({ page }) => {
  await registerVerifyAndLogin(page, uniqueEmail());

  await page.goto('/assessment/start');
  await selectCountry(page, 'Which country are you a citizen of?', 'Germany');
  await page.getByRole('radiogroup', { name: 'Are you currently in Poland?' }).getByLabel('No').check();
  await page.locator('input[type="date"]').fill('1990-01-01');
  await page.getByRole('button', { name: 'Next' }).click();

  // Not in Poland -> the current-status section is skipped entirely; straight to goals.
  await expect(page.getByRole('heading', { name: /What do you want to do\?/ })).toBeVisible();
  await page.getByRole('checkbox', { name: 'Work', exact: true }).check();
  await page.getByRole('button', { name: 'Next' }).click();

  await expect(page.getByText('Do you have a job offer in Poland?')).toBeVisible();
  await page.getByRole('radiogroup', { name: 'Do you have a job offer in Poland?' }).getByLabel('Yes').check();
  await expect(page.getByText('Monthly gross salary').first()).toBeVisible();

  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page.getByRole('heading', { name: /What do you want to do\?/ })).toBeVisible();
  await page.getByRole('checkbox', { name: 'Work', exact: true }).uncheck();
  await page.getByRole('checkbox', { name: 'Get a PESEL number' }).check();
  await page.getByRole('button', { name: 'Review answers' }).click();

  await expect(page.getByRole('heading', { name: 'Review your answers' })).toBeVisible();
  await expect(page.getByText('Monthly gross salary')).toHaveCount(0);

  await page.getByRole('button', { name: 'Complete assessment' }).click();
  await expect(page.getByRole('heading', { name: 'Your assessment is complete' })).toBeVisible();
});

test('Scenario 3: logging out mid-assessment and back in resumes with answers preserved', async ({ page }) => {
  const email = uniqueEmail();
  await registerVerifyAndLogin(page, email);

  await page.goto('/assessment/start');
  await selectCountry(page, 'Which country are you a citizen of?', 'Ukraine');
  await page.getByRole('radiogroup', { name: 'Are you currently in Poland?' }).getByLabel('Yes').check();
  const assessmentUrl = page.url();

  // The wizard page has no logout button of its own - only the app shell's toolbar
  // one (unlike /dashboard, which has both - see AuthIntegrationTest's own comment on
  // that ambiguity), so it's unambiguous here.
  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole('link', { name: 'Resume assessment' }).click();
  await expect(page).toHaveURL(assessmentUrl);
  await expect(page.getByRole('radiogroup', { name: 'Are you currently in Poland?' }).getByLabel('Yes')).toBeChecked();
});
