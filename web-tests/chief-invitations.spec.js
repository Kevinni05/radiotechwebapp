import { test, expect } from '@playwright/test';
import { session } from './fixtures/control-room-session.js';

test('chief invitations send the email without granting a role and render recipient text safely', async ({ page }) => {
  let sent, listed = false;
  await session(page, 'CHIEF_EXECUTIVE', { respond(path, method, request) {
    if (path !== '/chief-invitations') return;
    expect(request.headers().authorization).toBe('Bearer test-token');
    if (method === 'POST') { sent = request.postDataJSON(); listed = true; return { id: 'invite-1', status: 'PENDING' }; }
    return { items: listed ? [{ id: 'invite-1', name: '<img src=x onerror=alert(1)>', email: 'new@example.test', status: 'READY' }] : [] };
  } });
  await page.locator('.nav button[data-view="profile"]').click();
  await page.locator('#chiefInviteForm').getByLabel('Nome completo', { exact: true }).fill('Nuovo Capo');
  await page.locator('#chiefInviteForm').getByLabel('Email del nuovo capo', { exact: true }).fill('new@example.test');
  await page.getByRole('button', { name: 'Invia invito', exact: true }).click();
  await expect(page.locator('#chiefInviteStatus')).toContainText('Nessun privilegio');
  expect(sent).toEqual({ name: 'Nuovo Capo', email: 'new@example.test', role: 'CHIEF_EXECUTIVE' });
  await expect(page.locator('#chiefInviteList')).toContainText('<img src=x onerror=alert(1)>');
  await expect(page.locator('#chiefInviteList img')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Approva con passkey' })).toHaveCount(0);
});

test('viewer cannot see chief invitation controls', async ({ page }) => {
  await session(page, 'VIEWER');
  await page.locator('.nav button[data-view="profile"]').click();
  await expect(page.locator('#chiefInviteForm')).toBeHidden();
});

test('operational privileged invitations bind the selected role without assigning it directly', async ({ page }) => {
  let sent;
  await session(page, 'CHIEF_EXECUTIVE', { respond(path, method, request) {
    if (path === '/chief-invitations' && method === 'POST') { sent = request.postDataJSON(); return { status: 'PENDING' }; }
    if (path === '/chief-invitations') return { items: [] };
  } });
  await page.locator('.nav button[data-view="profile"]').click();
  await page.locator('#chiefInviteName').fill('Responsabile tecnico');
  await page.locator('#chiefInviteEmail').fill('worker@example.test');
  await page.locator('#chiefInviteRole').selectOption('ENGINEER');
  await page.getByRole('button', { name: 'Invia invito', exact: true }).click();
  await expect(page.locator('#chiefInviteStatus')).toContainText('Nessun privilegio');
  expect(sent.role).toBe('ENGINEER');
});

test('cancelling a pending invitation uses the authenticated invitation endpoint', async ({ page }) => {
  let revoked = false;
  await session(page, 'CHIEF_EXECUTIVE', { respond(path, method) {
    if (path === '/chief-invitations/invite-1' && method === 'DELETE') { revoked = true; return { revoked: true }; }
    if (path === '/chief-invitations') return { items: [{ id: 'invite-1', name: 'Nuovo Capo', email: 'new@example.test', status: revoked ? 'REVOKED' : 'PENDING' }] };
  } });
  await page.locator('.nav button[data-view="profile"]').click();
  await page.getByRole('button', { name: 'Aggiorna inviti', exact: true }).click();
  await page.getByRole('button', { name: 'Revoca invito', exact: true }).click();
  await expect(page.locator('#chiefInviteList')).toContainText('Invito revocato');
  expect(revoked).toBe(true);
});
