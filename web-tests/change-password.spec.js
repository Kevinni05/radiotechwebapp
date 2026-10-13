import { test, expect } from '@playwright/test';
import { session } from './fixtures/control-room-session.js';

async function openPassword(page) {
  await page.locator('.nav button[data-view="profile"]').click();
  await page.getByRole('button', { name: 'Cambia password', exact: true }).click();
}

async function fillPassword(page, confirmation = 'NewPass2!') {
  await page.getByLabel('Password attuale', { exact: true }).fill('OldPass1!');
  await page.getByLabel('Nuova password', { exact: true }).fill('NewPass2!');
  await page.getByLabel('Conferma nuova password', { exact: true }).fill(confirmation);
}

test('changes password without email and clears session after success', async ({ page }) => {
  await session(page, 'CHIEF_EXECUTIVE');
  let payload;
  await page.route('**/api/v1/auth/change-password', async route => {
    expect(route.request().headers().authorization).toBe('Bearer test-token');
    payload = route.request().postDataJSON();
    await route.fulfill({ json: { success: true } });
  });
  await openPassword(page);
  await fillPassword(page);
  await page.getByRole('button', { name: 'Salva password', exact: true }).click();
  await expect(page.locator('#loginStatus')).toHaveText('Password aggiornata. Accedi con la nuova password.');
  expect(payload).toEqual({ currentPassword: 'OldPass1!', newPassword: 'NewPass2!' });
  expect(await page.evaluate(() => sessionStorage.getItem('radiotech_control_token'))).toBeNull();
  await expect(page.locator('#currentPassword')).toHaveValue('');
  await expect(page.locator('#newPassword')).toHaveValue('');
});

test('mismatched confirmation makes no request and cancel clears password fields', async ({ page }) => {
  await session(page, 'CHIEF_EXECUTIVE');
  let requests = 0;
  await page.route('**/api/v1/auth/change-password', async route => {
    requests++;
    await route.fulfill({ json: { success: true } });
  });
  await openPassword(page);
  await fillPassword(page, 'Different3!');
  await page.getByRole('button', { name: 'Salva password', exact: true }).click();
  await expect(page.locator('#passwordStatus')).toContainText('non coincidono');
  expect(requests).toBe(0);
  await page.locator('#passwordModal').getByRole('button', { name: 'Annulla', exact: true }).click();
  await expect(page.locator('#currentPassword')).toHaveValue('');
  await expect(page.locator('#newPassword')).toHaveValue('');
  await expect(page.locator('#confirmPassword')).toHaveValue('');
});

test('wrong current password keeps session usable and displays the error', async ({ page }) => {
  await session(page, 'CHIEF_EXECUTIVE');
  await page.route('**/api/v1/auth/change-password', route => route.fulfill({
    status: 400, json: { success: false, message: 'Password attuale non corretta.' }
  }));
  await openPassword(page);
  await fillPassword(page);
  await page.getByRole('button', { name: 'Salva password', exact: true }).click();
  await expect(page.locator('#passwordStatus')).toHaveText('Password attuale non corretta.');
  await expect(page.locator('#app')).toHaveClass(/ready/);
  expect(await page.evaluate(() => sessionStorage.getItem('radiotech_control_token'))).toBe('test-token');
  await expect(page.locator('#currentPassword')).toHaveValue('');
  await expect(page.locator('#savePasswordBtn')).toBeEnabled();
});
