import { expect, test } from '@playwright/test';

/**
 * Real backend + real PostgreSQL required (same standard as auth.spec.ts: "Do not
 * simulate backend authentication" extends to "do not simulate backend data" here) -
 * exercises the Phase 3 reference API through the actual /reference-demo page, proving
 * the country select, the region -> city -> district cascade, and the real seeded
 * Warsaw district list all work end to end in a real browser.
 */

test('selecting Pakistan then Poland -> Mazowieckie -> Warsaw resolves the real, full 18-district list', async ({
  page,
}) => {
  await page.goto('/reference-demo');

  const countryInput = page.getByRole('combobox', { name: 'Country of citizenship' });
  await countryInput.click();
  await countryInput.fill('Pakistan');
  await page.getByRole('option', { name: /Pakistan/ }).click();

  // Region/city/district remain disabled for a moment while Pakistan's (empty)
  // region list resolves - re-selecting Poland below is the actual assertion target.
  await countryInput.click();
  await countryInput.fill('');
  await countryInput.fill('Poland');
  await page.getByRole('option', { name: /Poland \(PL\)/ }).click();

  const regionSelect = page.getByRole('combobox', { name: 'Region' });
  await expect(regionSelect).toBeEnabled();
  await regionSelect.click();
  await page.getByRole('option', { name: 'Mazowieckie' }).click();

  const citySelect = page.getByRole('combobox', { name: 'City' });
  await expect(citySelect).toBeEnabled();
  await citySelect.click();
  await page.getByRole('option', { name: 'Warszawa' }).click();

  const districtSelect = page.getByRole('combobox', { name: 'District' });
  await expect(districtSelect).toBeEnabled();
  await districtSelect.click();

  // All 18 official Warsaw districts, from the real Flyway-seeded database - not a
  // hardcoded fixture list in this test.
  const expectedDistricts = [
    'Bemowo',
    'Białołęka',
    'Bielany',
    'Mokotów',
    'Ochota',
    'Praga-Południe',
    'Praga-Północ',
    'Rembertów',
    'Śródmieście',
    'Targówek',
    'Ursus',
    'Ursynów',
    'Wawer',
    'Wesoła',
    'Wilanów',
    'Włochy',
    'Wola',
    'Żoliborz',
  ];
  for (const districtName of expectedDistricts) {
    await expect(page.getByRole('option', { name: districtName, exact: true })).toBeVisible();
  }
  await expect(page.getByRole('option')).toHaveCount(expectedDistricts.length);

  await page.getByRole('option', { name: 'Śródmieście', exact: true }).click();
  await expect(districtSelect).toHaveText('Śródmieście');
});
